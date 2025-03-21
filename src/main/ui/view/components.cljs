(ns main.ui.view.components
  (:require
    [breaking-point.core :as bp]
    [re-frame.core :refer [subscribe]]
    [reagent.core :as r]))

(defn button [_]
  (let [hover? (r/atom false)]
    (fn [{:keys [text style on-click icon icon-hover class disabled?]}]
      [:button
       {:class ["modal_button" class]
        :style (merge {:font-size (if @(subscribe [::bp/cg?]) "16px" "18px")
                       :min-width "80px"
                       :letter-spacing "1px"
                       :opacity (when disabled? 0.5)
                       :cursor (when disabled? "not-allowed")
                       :border "2px solid #b1b1b1"
                       :line-height "1.2em"
                       :display "flex"
                       :gap "6px"
                       :flex-direction "row"
                       :justify-content "center"
                       :align-items "center"}
                      style)
        :on-mouse-over #(reset! hover? true)
        :on-mouse-out #(reset! hover? false)
        :disabled disabled?
        :on-click on-click}
       text
       (when (and icon (not @hover?))
         icon)
       (when (and icon-hover @hover?)
         icon-hover)])))

(defn modal [{:keys [open?
                     header
                     content
                     close-fn
                     close-button-text
                     confirm-button]
              :or {close-button-text "Cancel"}}]
  (when open?
    [:div
     {:on-click (fn [e]
                  (when (= (.-target e) (.-currentTarget e))
                    (close-fn)))
      :style {:user-select "none"
              :pointer-events "all"
              :justify-content "center"
              :background "#00000040"
              :align-items "center"
              :width "100%"
              :height "100%"
              :z-index 999}}
     [:div {:style {:background "#454545"
                    :display "flex"
                    :padding "20px"
                    :border-radius "5px"
                    :flex-direction "column"
                    :user-select "none"
                    :pointer-events "all"
                    :position "absolute"
                    :justify-content "center"
                    :align-items "center"
                    :gap "12px"
                    :top "50%"
                    :left "50%"
                    :z-index 999
                    :transform "translate(-50%, -50%)"}}
      (when header
        [:span
         {:style {:letter-spacing "1px"
                  :font-size "20px"
                  :color "white"}}
         header])
      (if (string? content)
        [:span
         {:style {:color "white"
                  :font-family "sans-serif"}}
         content]
        content)
      [:div
       {:style {:display "flex"
                :flex-direction "row"
                :gap "12px"}}
       (when close-fn
         [button {:text close-button-text :on-click close-fn}])
       (when confirm-button
         confirm-button)]]]))
