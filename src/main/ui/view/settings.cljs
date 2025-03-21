(ns main.ui.view.settings
  (:require
    [applied-science.js-interop :as j]
    [goog.functions :as functions]
    [main.scene.settings :as settings]
    [main.ui.subs :as subs]
    [re-frame.core :refer [subscribe]]
    [reagent.core :as r]))

(def settings-open? (r/atom false))

(defn- option-slider [{:keys [text value display on-change min max step]
                       :or {min 1
                            max 100
                            step 1}}]
  [:div {:style
         {:display "flex"
          :align-items "center"
          :justify-content "left"
          :gap "12px"
          :width "100%"}}
   [:span {:style {:width "150px"
                   :font-family "sans-serif"}} text]
   [:input {:style {:width "200px"}
            :type "range"
            :step step
            :default-value value
            :min min
            :max max
            :on-change (functions/debounce on-change 50)}]
   [:span {:style {:font-family "sans-serif"}} display]])

(defn- option-check [{:keys [text checked? on-change]}]
  [:div {:style
         {:display "flex"
          :align-items "center"
          :justify-content "left"
          :gap "12px"
          :width "100%"}}
   [:span {:style {:width "150px"
                   :font-family "sans-serif"}} text]
   [:input {:type "checkbox"
            :checked checked?
            :on-change on-change}]])

(defn settings []
  (let [mobile? @(subscribe [::subs/mobile?])]
    (when @settings-open?
      [:div
       {:style {:display "flex"
                :user-select "none"
                :pointer-events "all"
                :position "absolute"
                :justify-content "center"
                :align-items "center"
                :width "100%"
                :height "100%"
                :top "50%"
                :left "50%"
                :z-index 998
                :transform "translate(-50%, -50%)"}}
       [:div {:style {:width "480px"
                      :height (if mobile? "calc(100% - 30px)" "390px")
                      :overflow "hidden"
                      :background "#454545"
                      :border-radius "5px"
                      :border "2px solid white"}}
        [:span {:on-click #(reset! settings-open? false)
                :style {:position "relative"
                        :top "5px"
                        :left "calc(100% - 25px)"
                        :font-size "24px"
                        :color "white"
                        :cursor "pointer"}} "X"]
        [:div {:style {:display "flex"
                       :flex-direction "column"
                       :align-items "flex-start"
                       :justify-content "flex-start"
                       :height (when mobile? "calc(100% - 50px)")
                       :gap "16px"
                       :padding "15px"
                       :overflow-y "auto"
                       :color "white"}}
         [:span {:style {:font-size "24px"}} "Settings"]

         [:div {:style {:display "flex"
                        :flex-direction "column"
                        :margin-left "20px"}}
          [:span {:style {:font-family "sans-serif"
                          :font-size "22px"
                          :padding "5px 0px"}} "Sound"]
          [option-slider {:text "Music volume"
                          :value @(subscribe [::subs/settings-music-volume])
                          :display (-> @(subscribe [::subs/settings-music-volume]) (* 100) int)
                          :min 0
                          :max 1
                          :step 0.01
                          :on-change (fn [e]
                                       (->> (j/get-in e [:target :value])
                                            parse-double
                                            (settings/apply-setting :music-volume)))}]
          [option-slider {:text "Sfx distance cutoff"
                          :value @(subscribe [::subs/settings-sfx-distance-cutoff])
                          :display @(subscribe [::subs/settings-sfx-distance-cutoff])
                          :min 0
                          :max 100
                          :step 1
                          :on-change (fn [e]
                                       (->> (j/get-in e [:target :value])
                                            parse-double
                                            (settings/apply-setting :sfx-distance-cutoff)))}]]

         [:div {:style {:display "flex"
                        :flex-direction "column"
                        :margin-left "20px"}}
          [:span {:style {:font-family "sans-serif"
                          :font-size "22px"
                          :padding "5px 0px"}} "Graphics"]
          [option-slider {:text "Quality"
                          :value @(subscribe [::subs/settings-quality])
                          :display (int (+ (* 50 @(subscribe [::subs/settings-quality])) 100))
                          :min -2
                          :max 0
                          :step 0.01
                          :on-change (fn [e]
                                       (let [v (j/get-in e [:target :value])
                                             v (parse-double v)]
                                         (->> (j/get-in e [:target :value])
                                              parse-double
                                              (settings/apply-setting :quality))))}]
          [option-check {:text "Anti-aliasing"
                         :checked? @(subscribe [::subs/settings-anti-alias?])
                         :on-change (fn [e]
                                      (->> (j/get-in e [:target :checked])
                                           (settings/apply-setting :anti-alias?)))}]]

         [:div {:style {:display "flex"
                        :flex-direction "column"
                        :margin-left "20px"}}
          [:span {:style {:font-family "sans-serif"
                          :font-size "22px"
                          :padding "5px 0px"}} "Controls"]
          [option-slider {:text "Mouse sensitivity"
                          :value @(subscribe [::subs/settings-mouse-sensitivity])
                          :display (int (* 100 @(subscribe [::subs/settings-mouse-sensitivity])))
                          :min 0.01
                          :max 0.9
                          :step 0.01
                          :on-change (fn [e]
                                       (->> (j/get-in e [:target :value])
                                            parse-double
                                            (settings/apply-setting :mouse-sensitivity)))}]
          [option-slider {:text "Mouse zoom sensitivity"
                          :value @(subscribe [::subs/settings-mouse-zoom-sensitivity])
                          :display (int (* 100 @(subscribe [::subs/settings-mouse-zoom-sensitivity])))
                          :min 0.01
                          :max 0.9
                          :step 0.01
                          :on-change (fn [e]
                                       (->> (j/get-in e [:target :value])
                                            parse-double
                                            (settings/apply-setting :mouse-zoom-sensitivity)))}]
          [option-check {:text "Invert mouse Y axis"
                         :checked? @(subscribe [::subs/settings-mouse-invert?])
                         :on-change (fn [e]
                                      (->> (j/get-in e [:target :checked])
                                           (settings/apply-setting :invert-mouse?)))}]]]]])))
