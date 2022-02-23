;; @see https://stackoverflow.com/questions/24254000/how-to-force-anti-aliasing-in-javafx-fonts
;; @see https://docs.oracle.com/javafx/2/api/javafx/scene/text/FontSmoothingType.html
(System/setProperty "prism.lcdtext" "false")
(System/setProperty "io.netty.noUnsafe" "true") ;; ...needed w/ latest netty?
(System/setProperty "cljfx.style.mode" "true") ;; todo
(ns me.untethr.nostr.app
  (:require
    [cljfx.api :as fx]
    [me.untethr.nostr.consume :as consume]
    [me.untethr.nostr.file-sys :as file-sys]
    [me.untethr.nostr.metadata :as metadata]
    [me.untethr.nostr.relay-conn :as relay-conn]
    [me.untethr.nostr.store :as store]
    [me.untethr.nostr.subscribe :as subscribe]
    [me.untethr.nostr.timeline :as timeline]
    [me.untethr.nostr.view :as view]
    [me.untethr.nostr.view-home :as view-home]
    [me.untethr.nostr.event :as ev]
    [clojure.tools.logging :as log]
    [me.untethr.nostr.util :as util])
  (:import (java.util.concurrent ThreadFactory Executors ScheduledExecutorService TimeUnit)))

(defonce db (store/init! (file-sys/db-path)))

(defonce metadata-cache (metadata/create-cache db))

(defonce ^ScheduledExecutorService daemon-scheduled-executor
  (let [factory (reify ThreadFactory
                  (newThread [_ runnable]
                    (let [thread-name "nostr-desk-scheduled-executor-thread"]
                      (doto (Thread. runnable thread-name)
                        (.setDaemon true)))))]
    (Executors/newSingleThreadScheduledExecutor factory)))

(defn- schedule!
  [^Runnable f ^long delay]
  (.schedule daemon-scheduled-executor (util/wrap-exc-fn f) delay TimeUnit/MILLISECONDS))

(defn- schedule-with-fixed-delay!
  [^Runnable f ^long initial-delay ^long delay]
  (.scheduleWithFixedDelay daemon-scheduled-executor (util/wrap-exc-fn f) initial-delay delay TimeUnit/MILLISECONDS))

(defonce *state
  (atom
    {:show-relays? false
     :show-new-identity? false
     :identities [] ;; [domain/Identity]
     :identity-metadata {}
     :contact-lists {} ;; pubkey -> domain/ContactList
     :relays [] ;; [domain/Relay]
     :connected-info {}
     ;; note: changes to active-key and mutations to home-ux, timelines
     ;;   must be done w/in mutex--ie on fx thread!
     :active-key nil
     :home-ux (view-home/create-list-view metadata-cache) ;; stable forever reference.
     :identity-timeline {} ;; pubkey -> Timeline
     :timeline-watermarks {} ;; todo ? combine w/ ^^
     }))

(defn- load-relays!
  []
  (let [relays (store/load-relays db)]
    (swap! *state assoc :relays relays :refresh-relays-ts (System/currentTimeMillis))
    (relay-conn/update-relays! relays)))

(defn- init-subscriptions!
  []
  ;; note: we expect *state/:identities and /:contact-lists to have been loaded at this pt
  (let [{:keys [identities contact-lists]} @*state]
    (subscribe/overwrite-subscriptions! identities contact-lists)))

(defn- init-timelines!
  []
  ;; todo also limit timeline events to something, some cardinality?
  ;; todo also load watermarks and then init subscriptions
  ;; note: we expect *state/:identities and /:contact-lists to have been loaded at this pt
  (let [{:keys [identities contact-lists]} @*state
        identity-public-keys (mapv :public-key identities)
        contact-public-keys (subscribe/whale-of-pubkeys* identity-public-keys contact-lists)
        timeline-data (store/load-timeline-events db contact-public-keys)]
    (when-let [first-identity-key (first identity-public-keys)]
      (timeline/update-active-timeline! *state first-identity-key))
    (doseq [event-obj timeline-data]
      (timeline/dispatch-text-note! *state event-obj))
    (schedule! init-subscriptions! 50)))

(defn- load-contact-lists!
  []
  ;; note: we expect *state/:identities to have been loaded at this pt
  (let [{:keys [identities]} @*state
        contact-lists (store/load-contact-lists db identities)]
    (swap! *state update :contact-lists merge contact-lists)
    (schedule! init-timelines! 50)))

(defn- load-identities!
  []
  (let [identities (store/load-identities db)
        pubkeys (mapv :public-key identities)
        identity-metadata (store/load-metadata db (mapv :public-key identities))
        identity-timeline (into {} (map #(vector % (timeline/new-timeline))) pubkeys)]
    (swap! *state assoc
      :identities identities
      :identity-metadata identity-metadata
      :identity-timeline identity-timeline)
    (schedule! load-contact-lists! 50)))

(defn- update-connected-info!
  []
  (let [connected-info (relay-conn/connected-info)]
    (swap! *state assoc :connected-info connected-info)))

(defn fg-effect [f dispatch!]
  (fx/on-fx-thread
    (f *state db dispatch!)))

(defn bg-effect [f dispatch!]
  (.submit daemon-scheduled-executor
    ^Runnable
    (fn []
      (try
        (f *state db #(fx/on-fx-thread (dispatch! %)))
        (catch Throwable t
          (log/error t "on bg"))))))

(defonce map-event-handler
  (-> ev/handle
    (fx/wrap-effects {:fg fg-effect :bg bg-effect})))

(defonce renderer
  (fx/create-renderer
    :middleware (fx/wrap-map-desc assoc :fx/type view/stage)
    :opts {:fx.opt/map-event-handler map-event-handler}))

(defn -main
  [& _]
  (fx/mount-renderer *state renderer)
  (consume/start! db *state daemon-scheduled-executor)
  (schedule! load-identities! 1000)
  (schedule! load-relays! 3000)
  (schedule-with-fixed-delay! update-connected-info! 4000 10000)
  ;; CONSIDER shutdown hooks, graceful executor shutdown etc
  )

