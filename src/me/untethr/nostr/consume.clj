(ns me.untethr.nostr.consume
  (:require
    [manifold.stream :as s]
    [me.untethr.nostr.cache :as cache]
    [me.untethr.nostr.consume-verify :refer [verify-maybe-persist-event!]]
    [me.untethr.nostr.json :as json]
    [me.untethr.nostr.relay-conn :as relay-conn]
    [clojure.tools.logging :as log]
    [me.untethr.nostr.timeline :as timeline]
    [me.untethr.nostr.domain :as domain]
    [me.untethr.nostr.parse :as parse]
    [me.untethr.nostr.subscribe :as subscribe])
  (:import (me.untethr.nostr.timeline Timeline)
           (java.util.concurrent ScheduledExecutorService ScheduledFuture TimeUnit)))

;; todo where do we have stream buffers?
;; todo add relay info to events

(defn consume-set-metadata-event [db relay-url event-obj]
  (log/info "metadata (TODO): " relay-url (:id event-obj))

  )

;; todo we're adding everything right now -- need to respect timeline ux watermarks

(defn consume-text-note [db *state relay-url event-obj]
  (log/trace "text note: " relay-url (:id event-obj))
  (timeline/dispatch-text-note! *state event-obj))

(defn consume-recommend-server [db relay-url event-obj]
  (log/info "recommend server (TODO): " relay-url (:id event-obj))
  )

(defn resubscribe!
  [*state ^ScheduledExecutorService executor resubscribe-future-vol]
  (vswap! resubscribe-future-vol
    (fn [^ScheduledFuture fut]
      (when fut
        (.cancel fut false))
      (.schedule executor ^Runnable
        (fn []
          (let [{:keys [identities contact-lists]} @*state]
            (subscribe/overwrite-subscriptions! identities contact-lists)))
        15 TimeUnit/SECONDS))))

(defn consume-contact-list [_db *state ^ScheduledExecutorService executor resubscribe-future-vol relay-url
                            {:keys [id pubkey created_at] :as event-obj}]
  (log/info "contact list (TODO): " relay-url id pubkey created_at)
  (let [{:keys [identities contact-lists]} @*state]
    (when (some #(= % pubkey) (mapv :public-key identities))
      (let [{:keys [created-at]} (get contact-lists pubkey)]
        (when (or (nil? created-at) (> created_at created-at))
          (let [new-contact-list
                (domain/->ContactList pubkey created_at
                  (parse/parse-contacts* event-obj))]
            (swap! *state
              (fn [curr-state]
                (assoc-in curr-state [:contact-lists pubkey] new-contact-list)))
            (resubscribe! *state executor resubscribe-future-vol)))))))

(defn consume-direct-message [db relay-url event-obj]
  (log/info "direct message (TODO): " relay-url (:id event-obj))
  )

(defn- consume-verified-event
  [db *state executor resubscribe-future-vol relay-url _subscription-id {:keys [kind] :as verified-event}]
  (case kind
    0 (consume-set-metadata-event db relay-url verified-event)
    1 (consume-text-note db *state relay-url verified-event)
    2 (consume-recommend-server db relay-url verified-event)
    3 (consume-contact-list db *state executor resubscribe-future-vol relay-url verified-event)
    4 (consume-direct-message db relay-url verified-event)
    (log/warn "skipping kind" kind relay-url)))

(defn- consume-event
  [db *state executor resubscribe-future-vol cache relay-url subscription-id {:keys [id] :as event-obj} raw-event-tuple]
  (verify-maybe-persist-event! db cache relay-url event-obj raw-event-tuple
    (partial consume-verified-event db *state executor resubscribe-future-vol relay-url subscription-id)
    (fn [event-obj]
      ;; event from new relay
      (log/trace "on-new-relay-seen" relay-url id) ;; todo
      )))

(defn- consume-notice
  [relay-url message]
  (log/info "NOTICE (TODO): " relay-url message) ;; todo
  )

(defn- consume*
  [db *state executor resubscribe-future-vol cache [relay-url event-str]]
  (try
    (let [[type-str arg0 arg1] (json/parse event-str)]
      (condp = type-str
        "EVENT" (consume-event db *state executor resubscribe-future-vol cache relay-url arg0 arg1 event-str)
        "NOTICE" (consume-notice relay-url arg0)
        (log/warn "unknown event type" relay-url type-str)))
    (catch Exception e
      (log/warn "dropping event; bad parse?" relay-url event-str #_e))))

(def ^:private cache-spec
  "initialCapacity=5000,maximumSize=5000,expireAfterWrite=10m")

(defn start!
  [db *state daemon-scheduled-executor]
  (let [resubscribe-future-vol (volatile! nil)]
    (s/consume
      (partial consume* db *state daemon-scheduled-executor resubscribe-future-vol (cache/build cache-spec))
      (relay-conn/sink-stream))))
