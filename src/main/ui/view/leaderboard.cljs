(ns main.ui.view.leaderboard
  (:require
    [breaking-point.core :as bp]
    [main.ads :as ads]
    [main.rule-engine :as re]
    [main.scene.network :refer [dispatch-pro-sync]]
    [main.ui.subs :as subs]
    [main.utils :as utils]
    [re-frame.core :refer [subscribe]]
    [reagent.core :as r]))

(def shop-bg-color "#454545")
(def header-height "40px")
(def coin-color "rgb(251, 212, 19)")

(def section (r/atom :daily))

(defn- menu-button [_]
  (let [hover? (r/atom false)]
    (fn [{:keys [text on-click icon icon-hover class disabled? selected?]}]
      [:button
       {:class ["shop_type_button" (when selected? "selected") class]
        :style {:font-size (if @(subscribe [::bp/cg?]) "16px" "22px")
                :opacity (when disabled? 0.5)
                :cursor (when disabled? "not-allowed")
                :line-height "1.2em"
                :display "flex"
                :gap "6px"
                :flex-direction "row"
                :justify-content "center"
                :align-items "center"}
        :on-mouse-over #(reset! hover? true)
        :on-mouse-out #(reset! hover? false)
        :disabled disabled?
        :on-click (fn []
                    (when on-click (on-click)))}
       text
       (when (and icon (not @hover?) (not selected?))
         icon)
       (when (or selected? (and icon-hover @hover?))
         icon-hover)])))

(defn- leaderboard-types []
  [:div
   {:id "shop-type"
    :style {:display "flex"
            :flex-direction "column"
            :align-items "center"
            :width "100%"
            :padding "10px"
            :box-sizing "border-box"}}
   [:div
    {:style {:display "flex"
             :flex-direction "row"
             :gap "10px"
             :padding-right "10px"
             :overflow-y "auto"}}
    [menu-button {:text "Daily"
                  :on-click #(reset! section :daily)
                  :selected? (= @section :daily)}]
    [menu-button {:text "Weekly"
                  :on-click #(reset! section :weekly)
                  :selected? (= @section :weekly)}]
    [menu-button {:text "Monthly"
                  :on-click #(reset! section :monthly)
                  :selected? (= @section :monthly)}]
    [menu-button {:text "All Time"
                  :on-click #(reset! section :all-time)
                  :selected? (= @section :all-time)}]]])

(defn leaderboard-table []
  [:div {:style {:display "flex"
                 :flex-direction "column"
                 :width "100%"
                 :overflow-y "auto"
                 :padding "10px"
                 :box-sizing "border-box"
                 :background-color "#454545"
                 :color "white"
                 :border-radius "3px"}}
   ;; Header Row
   [:div {:style {:display "flex"
                  :flex-direction "row"
                  :justify-content "space-between"
                  :padding "10px 20px"
                  :font-weight "bold"
                  :border-bottom "1px solid #ffffff"
                  :background-color "#454545"
                  :position "sticky"
                  :letter-spacing "1px"
                  :top "-15px"
                  :z-index "1"}}
    [:div {:style {:width "40px" :text-align "left"}} ""]
    [:div {:style {:flex "1"
                   :text-align "left"
                   :overflow "hidden"
                   :white-space "nowrap"
                   :text-overflow "ellipsis"}} "Name"]
    [:div {:style {:flex "1" :text-align "left"}} "Kills"]
    [:div {:style {:flex "1" :text-align "left"}} "Deaths"]
    [:div {:style {:flex "1" :text-align "left"}} "KDR (Kill/Death Ratio)"]]
   ;; Player Rows
   (let [data (map-indexed vector (get @(subscribe [::subs/leaderboard]) @section))]
     (if (empty? data)
       [:div
        {:style {:display "flex"
                 :width "100%"
                 :justify-content "center"
                 :letter-spacing "1px"
                 :align-items "center"}}
        [:h2 {:style {:animation "blink_little 2s ease-in-out infinite"}}
         "Leaderboard loading..."]]
       (for [[idx {:keys [username kills deaths me? rank]}] data
             :let [kills (or kills 0)
                   deaths (or deaths 0)
                   kdr (double (/ kills (if (zero? deaths) 1 deaths)))]]
         ^{:key idx}
         [:div {:style {:display "flex"
                        :flex-direction "row"
                        :background (when (or me? rank) "white")
                        :color (when (or me? rank) "#454545")
                        :justify-content "space-between"
                        :padding "10px 20px"
                        :align-items "center"
                        :font-family "sans-serif"
                        :border-bottom "1px solid #ffffff"}}
          (if rank
            [:div {:style {:width "40px" :text-align "left"}} rank]
            [:div {:style {:width "40px" :text-align "left"}} (inc idx)])
          [:div {:style {:flex "1"
                         :text-align "left"
                         :overflow "hidden"
                         :white-space "nowrap"
                         :text-overflow "ellipsis"}} username]
          [:div {:style {:flex "1" :text-align "left"}} kills]
          [:div {:style {:flex "1" :text-align "left"}} deaths]
          [:div {:style {:flex "1" :text-align "left"}} (utils/format "%.2f" kdr)]])))])

(defn leaderboard-panel []
  (r/create-class
    {:component-will-mount
     (fn []
       (reset! section :daily)
       (dispatch-pro-sync :leaderboard nil)
       (when-not (re/query :game/mobile?)
         (ads/request-banner {:id "banner-container-728-90-2"
                              :width 728
                              :height 90})))
     :reagent-render
     (fn []
       (let [mobile? @(subscribe [::subs/mobile?])
             height (if mobile? "40px" "50px")
             cg? @(subscribe [::bp/cg?])]
         [:div
          {:style {:display "flex"
                   :user-select "none"
                   :pointer-events "all"
                   :position "absolute"
                   :justify-content "center"
                   :align-items "center"
                   :width "calc(100% - 100px)"
                   :height (str "calc(100% - " height ")")
                   :top "50%"
                   :left "50%"
                   :z-index 998
                   :transform "translate(-50%, -50%)"}}
          [:div {:style {:display "flex"
                         :flex-direction "row"
                         :width "100%"
                         :height "100%"
                         :background shop-bg-color
                         :border-radius "3px"}}

           [:div {:style {:display "flex"
                          :flex-direction "column"
                          :align-items "center"
                          :justify-content "center"
                          :width "100%"
                          :height "100%"}}

            [:div {:style {:display "flex"
                           :align-items "center"
                           :justify-content "center"
                           :width "100%"
                           :height header-height
                           :box-sizing "border-box"
                           :padding "15px"
                           :font-size "24px"
                           :color "white"}}
             [:span {:style {:position "absolute"
                             :left "22px"
                             :font-size "14px"
                             :color coin-color}}
              "Updates occur every 15 minutes"]

             [:span {:style {:left "50%"
                             :font-size "24px"
                             :color "white"}}
              "Leaderboard"]

             [:span {:on-click #(re/fire-rules :game/leaderboard? false)
                     :style {:position "fixed"
                             :left "calc(100% - 30px)"
                             :font-size "24px"
                             :color "white"
                             :cursor "pointer"}} "X"]]
            [leaderboard-types]

            [:div {:style {:display "flex"
                           :flex-direction "row"
                           :overflow "hidden"
                           :width "100%"
                           :height (str "calc(100% - " header-height ")")}}
             [leaderboard-table]]
            (when-not @(subscribe [::subs/mobile?])
              [:div
               {:id "banner-container-728-90-2"
                :style {:background "rgba(0, 0, 0, 0.25)"
                        :padding "10px"
                        :pointer-events "all"
                        :z-index 99999
                        :width "728px"
                        :height "90px"}}])]]]))}))
