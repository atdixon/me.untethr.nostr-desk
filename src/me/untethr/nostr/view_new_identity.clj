(ns me.untethr.nostr.view-new-identity
  (:require [me.untethr.nostr.style :as style]
            [cljfx.api :as fx])
  (:import (javafx.scene.control ButtonType Dialog ButtonBar$ButtonData DialogPane)
           (javafx.util Callback)))

(defn- result-converter
  [^ButtonType r ^Dialog d]
  (condp = (.getButtonData r)
    ButtonBar$ButtonData/CANCEL_CLOSE :cancel
    ButtonBar$ButtonData/OK_DONE
    (let [^DialogPane pane (.getDialogPane d)
          #_(.getChildren pane)]
      ;; todo todo make sure no duplicate identities !!!
      :okay-todo)))

(defn- on-create-dialog
  [^Dialog d]
  (doto d
    ;; note we have to set result-converter this way instead of via
    ;; cljfx prop as we need ref to the dialog to do the conversion
    (.setResultConverter
      (reify Callback
        (call [_ r]
          (result-converter r d))))))

(defn dialog
  [{:keys [show-new-identity?]}]
  {:fx/type fx/ext-on-instance-lifecycle
   :on-created on-create-dialog
   :desc
   {:fx/type :dialog
    :showing show-new-identity?
    :on-close-request {:event/type :new-identity-close-request}
    :title "New Identity"
    :dialog-pane
    {:fx/type :dialog-pane
     :stylesheets (style/css)
     :button-types [(ButtonType. "Cancel" ButtonBar$ButtonData/CANCEL_CLOSE)
                    (ButtonType. "Save" ButtonBar$ButtonData/OK_DONE)]
     :content
     {:fx/type :v-box
      :children [{:fx/type :text-field
                  :prompt-text (apply str (repeat 64 \0))
                  :pref-column-count 64}
                 {:fx/type :h-box
                  :style {:-fx-padding 5}
                  :alignment :center-left
                  :children
                  [{:fx/type :check-box
                    :style {:-fx-padding 5}}
                   {:fx/type :label
                    :h-box/hgrow :always
                    :text "This is a public key."}]}]}}}})
