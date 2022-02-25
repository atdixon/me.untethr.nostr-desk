(ns me.untethr.nostr.event
  (:require
   [clojure.java.io :as io]
   [clojure.tools.logging :as log]
   [me.untethr.nostr.modal :as modal]
   [me.untethr.nostr.relay-conn :as relay-conn]
   [me.untethr.nostr.store :as store]
   [me.untethr.nostr.timeline :as timeline]
   [me.untethr.nostr.x.crypt :as crypt]
   [me.untethr.nostr.hydrate :as hydrate]
   [me.untethr.nostr.domain :as domain]
   [me.untethr.nostr.util :as util])
  (:import (javafx.scene.control DialogEvent Dialog)))

(defn relay-defaults
  []
  (read-string (slurp (io/resource "me/untethr/nostr/relay-defaults.edn"))))

(defn click-keycard
  [{:keys [public-key]}]
  [[:bg
    (fn [*state _db _exec _dispatch!]
      (timeline/update-active-timeline! *state public-key))]])

(defn show-new-identity-effect
  [show-new-identity?]
  [[:bg
    (fn [*state _db _exec _dispatch!]
      (swap! *state
        (fn [curr-state]
          (cond-> (assoc curr-state :show-new-identity? show-new-identity?)
            (not show-new-identity?) (assoc :new-identity-error "")))))]])

(defn delete-keycard
  [{:keys [identity_]}]
  (when (modal/blocking-yes-no-alert "" "Are you sure?")
    [[:bg
      (fn [*state db executor _dispatch!]
        (store/delete-identity! db (:public-key identity_))
        (let [{curr-identities :identities} @*state]
          (when (some #(= (:public-key identity_) (:public-key %)) curr-identities)
            (hydrate/dehydrate! *state db executor [identity_]))))]]))

(defn add-identity-and-close-dialog-effect
  [public-key maybe-private-key]
  (util/concatv
    (show-new-identity-effect false)
    [[:bg
      (fn [*state db executor _dispatch!]
        (store/insert-identity! db public-key maybe-private-key) ;; idempotent
        (let [{curr-identities :identities} @*state]
          ;; don't hydrate identity if it's already hydrated
          (when-not (some #(= public-key (:public-key %)) curr-identities)
            (hydrate/hydrate! *state db executor
              [(domain/->Identity public-key maybe-private-key)]))))]]))

(defn new-identity-close-request
  [{^DialogEvent dialog-event :fx/event}]
  (let [^Dialog dialog (.getSource dialog-event)
        dialog-result (.getResult dialog)]
    (condp = dialog-result
      :cancel
      (show-new-identity-effect false)
      (let [{:keys [val public?]} dialog-result]
        (cond
          (not= (count val) 64)
          (do
            (.consume dialog-event) ;; prevents dialog closure
            [[:bg (fn [*state _db _exec _dispatch!]
                    (swap! *state assoc
                      :new-identity-error "Key must be 64 characters"))]])
          public?
          (add-identity-and-close-dialog-effect val nil)
          :else
          (if-let [corresponding-pubkey
                   (some-> val
                     crypt/hex-decode
                     crypt/generate-pubkey
                     crypt/hex-encode)]
            (add-identity-and-close-dialog-effect corresponding-pubkey val)
            (do
              (.consume dialog-event) ;; prevents dialog closure
              [[:bg (fn [*state _db _exec _dispatch!]
                      (swap! *state assoc
                        :new-identity-error "Bad private key"))]])))))))

(defn replace-relays-effect
  [new-relays show-relays?]
  [[:bg
    (fn [*state db _exec _dispatch!]
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
    :show-new-identity (show-new-identity-effect true)
    :new-identity-close-request (new-identity-close-request event)
    :delete-keycard (delete-keycard event)
    :show-relays (show-relays-effect true)
    :relays-close-request (relays-close-request event)
    (log/error "no matching clause" type)))
