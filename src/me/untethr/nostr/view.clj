(ns me.untethr.nostr.view
  (:require
    [cljfx.api :as fx]
    [clojure.tools.logging :as log]
    [me.untethr.nostr.avatar :as avatar]
    [me.untethr.nostr.cache :as cache]
    [me.untethr.nostr.domain :as domain]
    [me.untethr.nostr.event :as ev]
    [me.untethr.nostr.style :as style :refer [BORDER|]]
    [me.untethr.nostr.util :as util]
    [me.untethr.nostr.util-domain :as util-domain]
    [me.untethr.nostr.view-home :as view-home]
    [me.untethr.nostr.view-new-identity :as view-new-identity]
    [me.untethr.nostr.view-relays :as view-relays]
    [me.untethr.nostr.util-java :as util-java]
    [me.untethr.nostr.util-fx :as util-fx]
    [me.untethr.nostr.view-common :as view-common]
    [me.untethr.nostr.view-reply :as view-reply])
  (:import (javafx.scene.canvas Canvas)
           (javafx.scene.paint Color)
           (javafx.geometry Pos)
           (javafx.scene.layout VBox Priority)
           (javafx.scene.control TextFormatter$Change TextArea)
           (javafx.beans.property ReadOnlyProperty)))

(defn avatar [{:keys [width picture-url]}]
  {:fx/type :image-view
   :image (cache/get* avatar/image-cache [picture-url width])})

(defn keycard
  [{:keys [active?]
    {:keys [public-key] :as identity_} :identity_
    ;; possibly nil:
    {:keys [name about picture-url nip05-id]} :this-identity-metadata}]
  (let [avatar-dim 75.0
        avatar-color (avatar/color public-key)]
    {:fx/type :h-box
     :cursor :hand
     :style (cond-> {}
              active? (assoc :-fx-border-color avatar-color))
     :style-class ["ndesk-keycard"
                   (when active?
                     "ndesk-keycard-active")]
     :on-mouse-clicked {:event/type :click-keycard :public-key public-key}
     :children
     [(if picture-url
        {:fx/type avatar
         :picture-url picture-url
         :width avatar-dim}
        {:fx/type :label
         :min-width avatar-dim
         :min-height avatar-dim
         :max-width avatar-dim
         :max-height avatar-dim
         :style {:-fx-background-color avatar-color}
         :style-class "ndesk-keycard-photo"
         :text (subs public-key 0 3)})
      {:fx/type :v-box
       :h-box/hgrow :always
       :children
       [{:fx/type :border-pane
         :max-width Integer/MAX_VALUE
         :left {:fx/type :label
                :style-class "ndesk-keycard-pubkey"
                :alignment :top-left
                :text (util/format-pubkey-short public-key)}
         :right {:fx/type :hyperlink :text "X"
                 :on-action {:event/type :delete-keycard :identity_ identity_}}}
        {:fx/type :label
         :style-class "ndesk-keycard-about"
         :text (or about "")}]}]}))

(defn keycard-create-new
  [{:keys [show-new-identity? new-identity-error]}]
  {:fx/type fx/ext-let-refs
   :refs {:dialog {:fx/type view-new-identity/dialog
                   :show-new-identity? show-new-identity?
                   :new-identity-error new-identity-error}}
   :desc {:fx/type :h-box
          :cursor :hand
          :style-class ["ndesk-keycard"]
          :on-mouse-clicked {:event/type :show-new-identity}
          :children
          [{:fx/type :label
            :h-box/hgrow :always
            :max-width Integer/MAX_VALUE
            :alignment :center
            :text "add new identity"}]}})

(defn notifications [_]
  {:fx/type :label
   :text "notifications"})

(defn messages [_]
  {:fx/type :label
   :text "messages"})

(defn profile [_]
  {:fx/type :label
   :text "profile"})

(defn search [_]
  {:fx/type :label
   :text "search"})

(defn ^:deprecated tab*
  [[label content]]
  {:fx/type :tab
   :closable false
   :text label
   :content content})

(defn ^:deprecated tab-pane
  [{:keys [home-ux]}]
  {:fx/type :tab-pane
   :pref-width 960
   :pref-height 540
   :tabs (mapv tab*
           {"Home" {:fx/type fx/ext-instance-factory
                    :create (constantly home-ux)}
            "Notifications" {:fx/type notifications}
            "Messages" {:fx/type messages}
            "Profile" {:fx/type profile}
            "Search" {:fx/type search}})})

(defn publish-box
  [{:keys [can-publish?]}]
  {:fx/type :text-area
   :disable (not can-publish?)
   :style-class ["text-area" "ndesk-publish-box"] ;; used for .lookup
   :prompt-text "What's on your mind?"
   :wrap-text true
   :text-formatter {:fx/type :text-formatter
                    :value-converter :default
                    :filter view-common/text-formatter-filter*}})

(defn main-pane
  [{:keys [home-ux can-publish? active-reply-context]}]
  {:fx/type fx/ext-let-refs
   :refs {:dialog {:fx/type view-reply/dialog
                   :active-reply-context active-reply-context}}
   :desc
   {:fx/type :v-box
    :children
    [{:fx/type :h-box
      :alignment :center
      :children
      [{:fx/type publish-box
        :can-publish? can-publish?
        :h-box/margin 5
        :h-box/hgrow :always}
       {:fx/type :label
        :h-box/margin 5
        :graphic {:fx/type :button
                  :on-action {:event/type :publish!}
                  :disable (not can-publish?)
                  :style-class ["button" "ndesk-publish-button"]
                  :text "Publish"}}]}
     {:fx/type fx/ext-instance-factory
      :create #(doto home-ux
                 (VBox/setVgrow Priority/ALWAYS))}]}})

(defn keycards
  [{:keys [active-key identities identity-metadata show-new-identity?
           new-identity-error]}]
  {:fx/type :v-box
   :style-class "ndesk-lhs-pane"
   :children (vec
               (concat
                 (map
                   #(hash-map
                      :fx/type keycard
                      :fx/key (:public-key %)
                      :active? (= active-key (:public-key %))
                      :identity_ %
                      :this-identity-metadata (get identity-metadata (:public-key %)))
                   identities)
                 [{:fx/type keycard-create-new
                   :show-new-identity? show-new-identity?
                   :new-identity-error new-identity-error}]))})

(defn relay-dot
  [{:keys [connected-info] {:keys [url read? write?] :as _relay} :relay}]
  {:fx/type :label
   :style {:-fx-padding [0 2]}
   :tooltip
   {:fx/type :tooltip
    :text (format "%s%s" url
            (cond
              (and read? write?) ""
              read? " (read-only)"
              write? " (write-only)"
              :else " (disabled)"))}
   :graphic
   (let [dim 12]
     {:fx/type :canvas
      :width dim
      :height dim
      :draw
      (fn [^Canvas canvas]
        (doto (.getGraphicsContext2D canvas)
          (.setFill (if (get connected-info url) Color/LIGHTGREEN Color/LIGHTGREY))
          (.fillOval 0 0 dim dim)))})})

(defn relay-dots
  [{:keys [relays connected-info]}]
  {:fx/type :h-box
   :style {:-fx-padding [0 5 0 0]}
   :children
   (mapv #(hash-map
            :fx/type relay-dot
            :relay %
            :connected-info connected-info) relays)})

(defn status-relays
  [{:keys [show-relays? relays refresh-relays-ts connected-info]}]
  {:fx/type fx/ext-let-refs
   :refs {:dialog {:fx/type view-relays/dialog
                   :show-relays? show-relays?
                   :relays relays
                   :refresh-relays-ts refresh-relays-ts}}
   :desc {:fx/type :h-box
          :style-class ["ndesk-status-relays"]
          :alignment :center
          :children [{:fx/type :text :text "Relays: "}
                     (if (nil? relays)
                       {:fx/type :text :text "..."}
                       {:fx/type relay-dots :relays relays :connected-info connected-info})]
          :cursor :hand
          :on-mouse-clicked {:event/type :show-relays}}})

(defn status-bar [{:keys [show-relays? relays refresh-relays-ts connected-info]}]
  {:fx/type :border-pane
   :style-class "ndesk-status-bar"
   :left {:fx/type :h-box :children []}
   :right {:fx/type status-relays
           :show-relays? show-relays?
           :relays relays
           :refresh-relays-ts refresh-relays-ts
           :connected-info connected-info}})

(defn root [{:keys [show-relays? active-key identities identity-metadata relays
                    refresh-relays-ts connected-info home-ux show-new-identity?
                    new-identity-error active-reply-context]}]
  {:fx/type :border-pane
   :left {:fx/type keycards
          :active-key active-key
          :identities identities
          :identity-metadata identity-metadata
          :show-new-identity? show-new-identity?
          :new-identity-error new-identity-error}
   :center {:fx/type main-pane
            :home-ux home-ux
            :can-publish? (util-domain/can-publish? active-key identities)
            :active-reply-context active-reply-context}
   :bottom {:fx/type status-bar
            :show-relays? show-relays?
            :relays relays
            :refresh-relays-ts refresh-relays-ts
            :connected-info connected-info}})

(defn stage [{:keys [show-relays? active-key identities identity-metadata relays
                     refresh-relays-ts connected-info home-ux show-new-identity?
                     new-identity-error active-reply-context]}]
  {:fx/type :stage
   :showing true
   :title "nostr desk"
   :width 1272
   :height 800
   :scene
   {:fx/type :scene
    :stylesheets (style/css)
    :root {:fx/type root
           :show-relays? show-relays?
           :show-new-identity? show-new-identity?
           :new-identity-error new-identity-error
           :active-reply-context active-reply-context
           :active-key active-key
           :identities identities
           :identity-metadata identity-metadata
           :relays relays
           :refresh-relays-ts refresh-relays-ts
           :connected-info connected-info
           :home-ux home-ux}}})
