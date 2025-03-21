(ns main.ui.styles
  (:require
    [main.ui.colors :as colors]
    [main.ui.typography :as typography]
    [spade.core :refer [defglobal defclass defattrs]]))

(def defaults
  {:width "100%"
   :height "100%"
   :margin 0
   :touch-action :none
   :user-select :none
   :-webkit-tap-highlight-color "transparent"
   :-webkit-touch-callout :none
   :-webkit-user-select :none
   :-khtml-user-select :none
   :-moz-user-select :none
   :-ms-user-select :none})

(def body
  (assoc defaults
         :font-family typography/font
         :overscroll-behavior :none))

(def app
  {:position :absolute
   :box-sizing :border-box
   :pointer-events :none})

(defglobal theme
  [:html defaults]
  [":root"
   [:body body]
   [:div#app (merge defaults app)]
   [:canvas defaults]
   [:a {:font-family typography/font}]
   [:h1 {:font-family typography/font}]
   [:h2 {:font-family typography/font}]
   [:h3 {:font-family typography/font}]
   [:h4 {:font-family typography/font}]
   [:h5 {:font-family typography/font}]
   [:span {:font-family typography/font}]
   [:button {:font-family typography/font}]
   [:textarea {:font-family typography/font}]

   {:*primary-color* colors/primary}])

(defattrs app-container [ready-to-play?]
  (assoc defaults
         :background (if ready-to-play? :unset "#00000040")
         :display :flex
         :flex-direction :column))
