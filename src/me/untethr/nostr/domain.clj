(ns me.untethr.nostr.domain)

(defrecord Identity
  [public-key secret-key])

(defrecord Relay
  [url read? write?])

(defrecord ParsedContact
  [public-key main-relay-url petname])

(defrecord ContactList
  [pubkey created-at parsed-contacts])

(defrecord ParsedMetadata
  [name about picture-url nip05-id created-at])

(defrecord UITextNote
  [id pubkey #_... content timestamp e-tags children])

;; --

; https://github.com/fiatjaf/nostr/blob/master/nips/01.md
;{
;  "ids": <a list of event ids>,
;  "kinds": <a list of kind numbers>,
;  "#e": <a list of event ids that are referenced in an "e" tag>,
;  "#p": <a list of pubkeys that are referenced in a "p" tag>,
;  "since": <a timestamp, events must be newer than this to pass>,
;  "until": <a timestamp, events must be older than this to pass>,
;  "authors": <a list of pubkeys, the pubkey of an event must be one of these>
;}
(defn ->subscription-filter
  [ids kinds e# p# since until authors]
  ;; directly serializable payload (note the #e and #p keys):
  {:ids ids :kinds kinds :#e e# :#p p# :since since :until until :authors authors})
