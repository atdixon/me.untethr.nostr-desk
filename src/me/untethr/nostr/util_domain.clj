(ns me.untethr.nostr.util-domain)

(defn ->secret-key*
  [active-key identities]
  (let [x (group-by :public-key identities)]
    (some-> x (get active-key) first :secret-key)))
