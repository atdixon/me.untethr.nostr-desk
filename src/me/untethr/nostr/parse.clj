(ns me.untethr.nostr.parse
  (:require [me.untethr.nostr.domain :as domain]
            [me.untethr.nostr.json :as json]))

(defn raw-event-tuple->event-obj
  [raw-event-tuple]
  (-> raw-event-tuple json/parse (nth 2)))

(defn parse-contacts*
  [{:keys [tags] :as _event-obj}]
  (->> tags
    (filter #(= "p" (first %)))
    (mapv (fn [[_ arg0 arg1 arg2]]
            (domain/->ParsedContact arg0 arg1 arg2)))))

(defn parse-ptag-keys*
  [{:keys [tags] :as _event-obj}]
  (->> tags
    (filter #(= "p" (first %)))
    (mapv (fn [[_ arg0 _arg1]] arg0))))

(defn parse-etag-ids*
  [{:keys [tags] :as _event-obj}]
  (->> tags
    (filter #(= "e" (first %)))
    (mapv (fn [[_ arg0 _arg1]] arg0))))
