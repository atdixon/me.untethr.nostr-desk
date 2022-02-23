(ns me.untethr.nostr.subscribe
  (:require
   [me.untethr.nostr.domain :as domain]
   [me.untethr.nostr.relay-conn :as relay-conn]
   [clojure.tools.logging :as log]))

;; todo ultimately may need to partition whale-of-pubkeys

(defn whale-of-pubkeys*
  [pubkeys contact-lists]
  (let [contact-pubkeys (mapcat #(map :public-key (:parsed-contacts %)) (vals contact-lists))]
    (set (concat pubkeys contact-pubkeys))))

(defn overwrite-subscriptions!
  [identities contact-lists]
  (let [pubkeys (mapv :public-key identities)]
    (when-not (empty? pubkeys)
      (let [filters (filterv
                      some?
                      [(domain/->subscription-filter
                         nil [0 1 2 3] nil nil nil nil (whale-of-pubkeys* pubkeys contact-lists))
                       (domain/->subscription-filter
                         nil [1 4] nil pubkeys nil nil nil)
                       (domain/->subscription-filter
                         nil [4] nil nil nil nil pubkeys)])]
        (relay-conn/subscribe-all! "primary" filters)
        (log/info "overwrote subscriptions")))))
