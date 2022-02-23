(ns me.untethr.nostr.event
  (:require
    [clojure.java.io :as io]
    [clojure.tools.logging :as log]
    [me.untethr.nostr.modal :as modal]
    [me.untethr.nostr.relay-conn :as relay-conn]
    [me.untethr.nostr.store :as store]
    [me.untethr.nostr.timeline :as timeline])
  (:import (javafx.scene.control DialogEvent Dialog)))

(defn relay-defaults
  []
  (read-string (slurp (io/resource "me/untethr/nostr/relay-defaults.edn"))))

(defn click-keycard
  [{:keys [public-key]}]
  [[:bg
    (fn [*state _db _dispatch!]
      (timeline/update-active-timeline! *state public-key))]])

(defn show-new-identity-effect
  [_]
  [[:bg
    (fn [*state _db _dispatch!]
      (swap! *state assoc
        :show-new-identity? true))]])

(defn new-identity-close-request
  [{^DialogEvent dialog-event :fx/event}]
  (let [dialog-result (.getResult ^Dialog (.getSource dialog-event))]
    (condp = dialog-result
      [[:bg
        (fn [*state _db _dispatch!]
          (swap! *state assoc
            :show-new-identity? false))]])))

(defn delete-keycard
  [{:keys [identity]}]
  (when (modal/blocking-yes-no-alert "" "Are you sure?")
    [[:bg
      (fn [*state db _dispatch!]
        (log/info "delete-keycard (TODO)")
        ;; todo change active timeline (possibly to empty)
        ;; todo (store/delete-identity! db identity)
        ;; todo dissoc state
        ;; todo update subscriptions
        )]]))

(defn replace-relays-effect
  [new-relays show-relays?]
  [[:bg
    (fn [*state db _dispatch!]
      (swap! *state
        assoc
        :relays (store/replace-relays! db new-relays)
        :show-relays? show-relays?
        :refresh-relays-ts (System/currentTimeMillis))
      (relay-conn/update-relays! new-relays))]])

(defn show-relays-effect
  [show-relays?]
  [[:fg
    (fn [*state _ _]
      (swap! *state assoc
        :show-relays? show-relays?
        ;; this is especially necessary when user is close-cancelling; they
        ;; may have mutated some text fields; and this forces a complete
        ;; re-render of the text fields, wiping out their mutations.
        :refresh-relays-ts (System/currentTimeMillis)))]])

(defn relays-close-request
  [{^DialogEvent dialog-event :fx/event}]
  (let [dialog-result (.getResult ^Dialog (.getSource dialog-event))]
    (condp = dialog-result
      :restore
      (do
        (.consume dialog-event) ;; prevents dialog closure
        ;; modal answers nil if 'no' which upstream will interpret as no-op
        (when (modal/blocking-yes-no-alert "" "Are you sure?")
          (replace-relays-effect (relay-defaults) true)))
      :cancel
      (show-relays-effect false)
      (replace-relays-effect dialog-result false))))

(defn handle
  [{:event/keys [type] :as event}]
  (case type
    :click-keycard (click-keycard event)
    :show-new-identity (show-new-identity-effect event)
    :new-identity-close-request (new-identity-close-request event)
    :delete-keycard (delete-keycard event)
    :show-relays (show-relays-effect true)
    :relays-close-request (relays-close-request event)))
