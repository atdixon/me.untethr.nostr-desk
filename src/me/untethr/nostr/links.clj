(ns me.untethr.nostr.links
  (:require [clojure.tools.logging :as log]
            [clojure.string :as str])
  (:import (java.util.regex Matcher)))

(def ^:private http-basic-regex-str
  #"(https?)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]")

(defn detect
  [^String content]
  (try
    (let [m (re-matcher http-basic-regex-str content)
          result-vol (volatile! [])]
      (while (.find m)
        (let [m-start (.start m) m-end (.end m)]
          (when (or (empty? @result-vol)
                  ;; no overlapping--
                  (>= m-start (second (peek @result-vol))))
            (vswap! result-vol conj [m-start m-end]))))
      @result-vol)
    (catch Exception e
      [])))
