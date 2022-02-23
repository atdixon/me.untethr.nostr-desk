(ns me.untethr.nostr.util
  (:require [clojure.tools.logging :as log])
  (:import (java.util Random Date)
           (java.text SimpleDateFormat)
           (java.util.function BiFunction)))

(defn rand-hex-color
  [seed]
  (let [random (Random. seed)]
    (apply (partial str "#")
      (map
        #(if (> % 9) (char (+ (int \a) (- % 10))) %)
        (repeatedly 6 #(.nextInt random 16))))))

(defn wrap-exc-fn
  (^Runnable [f]
   (wrap-exc-fn nil f))
  (^Runnable [context f]
   (fn [& args]
     (try
       (apply f args)
       (catch Throwable t
         (log/error t (or context "<no-context>")))))))

(defn compact
  [m]
  (into {}
    (filter (fn [[_ v]] (and (some? v) (or (not (coll? v)) (not (empty? v)))))) m))

(defn format-pubkey-short
  [pubkey]
  (str (subs pubkey 0 3) "..." (subs pubkey (- (count pubkey) 4))))

(defn format-timestamp
  [^long epoch-seconds]
  (let [sdt (SimpleDateFormat. "yyyy LLL dd h:mm a")]
    (.format sdt (Date. (long (* 1000 epoch-seconds))))))

(defn ->BiFunction
  ^BiFunction [f]
  (reify BiFunction
    (apply [_ t u]
      (f t u))))