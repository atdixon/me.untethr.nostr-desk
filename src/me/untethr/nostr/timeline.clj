(ns me.untethr.nostr.timeline
  (:require
   [cljfx.api :as fx]
   [clojure.set :as set]
   [clojure.tools.logging :as log]
   [loom.graph :as loom]
   [loom.attr :as loom-attr]
   [me.untethr.nostr.domain :as domain]
   [me.untethr.nostr.parse :as parse]
   [me.untethr.nostr.util :as util]
   [loom.graph :as g])
  (:import (javafx.collections FXCollections ObservableList)
           (javafx.collections.transformation SortedList)
           (java.util HashMap)
           (me.untethr.nostr.domain UITextNote UITextNoteWrapper)
           (javafx.scene.control ListView)))

(defrecord Timeline
  ;; these field values are only ever mutated on fx thread
  [^SortedList sorted-list
   ^ObservableList observable-list ;; contains UITextNoteWrapper
   ;; id -> index (for child events may point to same index); *all* events are represented in this map
   ^HashMap item-id->index
   ;; ...
   ^HashMap tagged-id->tagger-ids])

(defn new-timeline
  []
  (let [observable-list (FXCollections/observableArrayList)]
    (->Timeline
      (.sorted
        observable-list
        ;; latest wrapper entries first:
        (comparator #(< (:max-timestamp %2) (:max-timestamp %1))))
      observable-list
      (HashMap.)
      (HashMap.))))

(defn accept-text-note?
  [*state identity-pubkey {:keys [pubkey] :as event-obj}]
  (let [{:keys [contact-lists]} @*state
        {:keys [parsed-contacts] :as _contact-list} (get contact-lists identity-pubkey)
        ;; consider: optimization--not having to create contact set each note
        contact-keys-set (into #{} (map :public-key) parsed-contacts)
        ptag-keys-set (set (parse/parse-ptag-keys* event-obj))]
    (doto
     (or
       ;; identity's own note
       (= pubkey identity-pubkey)
       ;; the text-note's pubkey matches an identity's contact
       (contact-keys-set pubkey)
       ;; the text-note's ptags reference identity itself
       (ptag-keys-set identity-pubkey)
       ;; the text-note's ptags references one of identities contacts
       (not-empty (set/intersection contact-keys-set ptag-keys-set))))))

(defn contribute*
  [G {:keys [id timestamp e-tags] :as ^UITextNote node}]
  (let [G (loom/add-nodes G id)
        G (reduce #(loom/add-edges %1 [%2 id]) G e-tags)
        G (loom-attr/add-attr G id :ux-elem node)
        G (reduce
            #(cond-> %1
               (nil? (loom-attr/attr %1 %2 :ux-elem))
               (loom-attr/add-attr %2
                 ;; note: here we create a shell note; a vagary of our impl is that we
                 ;; inherit the timestamp of the node that originates us. this allows us
                 ;; to make heuristics over our graph where timestamps are never nil.
                 :ux-elem (domain/->UITextNote
                            ;; nil pubkey is what indicates this is a shell
                            %2 nil (format "<missing:%s>" %2)
                            timestamp nil nil))) G e-tags)]
    G))

(defn node->note*
  [G seen node]
  (let [^UITextNote curr-note (loom-attr/attr G node :ux-elem)
        unvisited-successors (filter (complement seen) (loom/successors G node))
        [G-next seen-next children] (reduce
                                      (fn [[G-acc seen-acc children-acc] successor-node]
                                        (let [[G-next seen-next note-next]
                                              (node->note* G-acc (conj seen-acc successor-node) successor-node)]
                                          [G-next seen-next (conj children-acc note-next)]))
                                      [G seen []]
                                      unvisited-successors)
        new-note (-> curr-note
                   (assoc :children children)
                   (vary-meta dissoc ::graph))]
    [(-> G-next
       (loom-attr/add-attr node :ux-elem new-note)) seen-next new-note]))

(defn rotate*
  ^UITextNoteWrapper [^UITextNoteWrapper wrapper ^UITextNote new-item]
  (let [G (contribute* (:loom-graph wrapper) new-item)
        node->successors (into {} (map (juxt identity (partial loom/successors G))) (loom/nodes G))
        likely-root-last (fn [G]
                           (juxt
                             (comp count node->successors) ;; least successors first
                             (comp - :timestamp #(loom-attr/attr G % :ux-elem)))) ;; youngest first
        edges-to-shed
        (reduce
          (fn [acc x]
            (into acc
              (map
                #(vector % x)
                (drop 1 (sort-by (likely-root-last G) (loom/predecessors G x))))))
          #{} (loom/nodes G))
        G-shed (apply loom/remove-edges G edges-to-shed)
        G-roots (filter #(-> (loom/predecessors G-shed %) empty?) (loom/nodes G-shed))
        use-root (last (sort-by (likely-root-last G) G-roots))
        [_ _ note] (node->note* G-shed #{use-root} use-root)]
    ;; retain un-shed graph on exit
    (domain/->UITextNoteWrapper
      G (:expanded? wrapper) (count (loom/nodes G)) (max (:max-timestamp wrapper) (:timestamp new-item)) note)))

(defn init-wrapper
  [^UITextNote note]
  (let [G (contribute* (loom/digraph) note)]
    (domain/->UITextNoteWrapper G false (count (loom/nodes G)) (:timestamp note) note)))

(defn dispatch-text-note!
  [*state {:keys [id pubkey created_at content] :as event-obj}]
  {:pre [(some? pubkey)]}
  ;; CONSIDER if is this too much usage of on-fx-thread - do we need to batch/debounce
  (fx/run-later
    (let [{:keys [identity-timeline] :as _state-snap} @*state]
      (doseq [[identity-pubkey timeline] identity-timeline]
        (let [{:keys [^ObservableList observable-list
                      ^HashMap item-id->index
                      ^HashMap tagged-id->tagger-ids]} timeline
              ;; if we've never seen the note or the note is a shell, then
              ;; this will be nil:
              existing-note-pubkey (some->>
                                     (.get ^HashMap item-id->index id)
                                     (.get observable-list)
                                     :pubkey)]
          (when-not (some? existing-note-pubkey)
            (when (accept-text-note? *state identity-pubkey event-obj)
              (let [etag-ids (parse/parse-etag-ids* event-obj)
                    ^UITextNote new-item (domain/->UITextNote id pubkey content created_at etag-ids [])
                    id-closure (cons id etag-ids)
                    all-tagger-ids (mapcat #(.get tagged-id->tagger-ids %) id-closure)
                    [curr-id curr-idx]
                    (first
                      (filter
                        (comp some? second)
                        (map
                          (juxt identity
                            #(.get item-id->index %)) all-tagger-ids)))]
                (doseq [etag-id id-closure]
                  (.merge tagged-id->tagger-ids etag-id [id]
                    (util/->BiFunction #(concat %1 %2))))
                (if (some? curr-id)
                  (let [new-wrapper (rotate* (.get observable-list curr-idx) new-item)]
                    (.put item-id->index id curr-idx)
                    ;; this is necessary b/c rotate* can originate <missing> notes:
                    (when-let [root-id (-> new-wrapper :root :id)]
                      (.put item-id->index root-id curr-idx))
                    (.set observable-list curr-idx new-wrapper))
                  (let [init-wrapper (init-wrapper new-item)]
                    (.put item-id->index id (.size observable-list))
                    (.add observable-list init-wrapper)))))))))))

(defn toggle!
  [*state id]
  (let [{:keys [active-key identity-timeline]} @*state
        ^Timeline active-timeline (get identity-timeline active-key)
        {:keys [^ObservableList observable-list
                ^HashMap item-id->index]} active-timeline]
    (fx/run-later
      (try
        (when-let [item-index (.get item-id->index id)]
          (when-let [^UITextNoteWrapper wrapper (.get observable-list item-index)]
            (.set observable-list item-index
              (update wrapper :expanded? #(not %)))))
        (catch Exception e
          (log/error 'toggle! e))))))

(defn update-active-timeline!
  [*state public-key] ;; note public-key may be nil!
  (fx/on-fx-thread
    (swap! *state
      (fn [{:keys [^ListView home-ux identity-timeline] :as curr-state}]
        (.setItems home-ux
          ^ObservableList (or
                            (:sorted-list (get identity-timeline public-key))
                            (FXCollections/emptyObservableList)))
        (assoc curr-state :active-key public-key)))))
