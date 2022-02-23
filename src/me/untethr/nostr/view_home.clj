(ns me.untethr.nostr.view-home
  (:require
   [cljfx.api :as fx]
   [clojure.tools.logging :as log]
   [me.untethr.nostr.domain]
   [me.untethr.nostr.metadata :as metadata]
   [me.untethr.nostr.style :as style :refer [BORDER|]]
   [me.untethr.nostr.util :as util]
   [me.untethr.nostr.cache :as cache]
   [me.untethr.nostr.avatar :as avatar])
  (:import
   (me.untethr.nostr.domain UITextNote)
   (javafx.scene.layout Region)
   (javafx.geometry Insets)
   (javafx.scene.control ListView)
   (javafx.scene.image Image)))

(def avatar-dim 40)

(defn avatar [{:keys [picture-url]}]
  {:fx/type :image-view
   :image (cache/get* avatar/image-cache [picture-url avatar-dim])})

(defn timeline-item
  [{:keys [^UITextNote item-data metadata-cache spacer-width]}]
  ;; note: we get nil item-data sometimes when the list-cell is advancing
  ;; in some ways -- for now just render label w/ err which we'll see if
  ;; this matters --
  (if (nil? item-data)
    {:fx/type :label :text "err"}
    (let [pubkey (:pubkey item-data)
          pubkey-for-avatar (or (some-> pubkey (subs 0 3)) "?")
          pubkey-short (or (some-> pubkey util/format-pubkey-short) "?")
          timestamp (:timestamp item-data)
          content (:content item-data)
          {:keys [name about picture-url nip05-id created-at]} (some->> pubkey (metadata/get* metadata-cache))
          avatar-color (or (some-> pubkey avatar/color) :lightgray)]
      {:fx/type :border-pane
       :left (if picture-url
               {:fx/type avatar
                :picture-url picture-url}
               {:fx/type :label
                :min-width avatar-dim
                :min-height avatar-dim
                :max-width avatar-dim
                :max-height avatar-dim
                :style {:-fx-background-color avatar-color}
                :style-class "ndesk-timeline-item-photo"
                :text pubkey-for-avatar})
       :center {:fx/type :border-pane
                :top {:fx/type :border-pane
                      :border-pane/margin (Insets. 0.0 5.0 0.0 5.0)
                      :left {:fx/type :label
                             :style-class "ndesk-timeline-item-pubkey"
                             :text pubkey-short}
                      :right {:fx/type :label
                              :text (util/format-timestamp timestamp)}}
                :bottom {:fx/type :label
                         :border-pane/margin (Insets. 0.0 5.0 0.0 5.0)
                         :max-width (- 800 spacer-width) ;; todo need different strate here; what's Region/USE_PREF_SIZE
                         :wrap-text true
                         :style-class "ndesk-timeline-item-content"
                         :text content}}})))

(defn- tree-rows*
  [indent ^UITextNote item-data metadata-cache]
  (let [spacer-width (* indent 25)]
    (cons
      {:fx/type :h-box
       :children [{:fx/type :label
                   :min-width spacer-width
                   :max-width spacer-width
                   :text ""}
                  {:fx/type timeline-item
                   :h-box/hgrow :always
                   :spacer-width spacer-width
                   :item-data item-data
                   :metadata-cache metadata-cache}]}
      (mapcat #(tree-rows* (inc indent) % metadata-cache) (:children item-data)))))

(defn- tree* [{:keys [^UITextNote item-data metadata-cache]}]
  {:fx/type :v-box
   :children (tree-rows* 0 item-data metadata-cache)})

(defn home [{:keys [metadata-cache]}]
  {:fx/type :list-view
   :focus-traversable false
   :cell-factory {:fx/cell-type :list-cell
                  :describe (fn [item]
                              {:graphic
                               {:fx/type tree*
                                :item-data item
                                :metadata-cache metadata-cache}})}})

(defn create-list-view
  ^ListView [metadata-cache]
  (fx/instance
    (fx/create-component {:fx/type home
                          :metadata-cache metadata-cache})))
