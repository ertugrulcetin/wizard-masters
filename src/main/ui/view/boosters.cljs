(ns main.ui.view.boosters
  (:require
    [applied-science.js-interop :as j]
    [breaking-point.core :as bp]
    [clojure.string :as str]
    [main.ads :as ads]
    [main.rule-engine :as re]
    [main.scene.network :as network]
    [main.scene.network :refer [dispatch-pro-sync]]
    [main.ui.subs :as subs]
    [main.ui.view.components :refer [modal button]]
    [main.utils :as utils]
    [re-frame.core :refer [subscribe]]
    [reagent.core :as r]))

(def bg-color "#454545")
(def header-height "70px")
(def coin-color "rgb(251, 212, 19)")

(def play-icon
  [:svg {:xmlns "http://www.w3.org/2000/svg"
         :width "32"
         :height "32"
         :fill "#fff"
         :viewBox "0 0 256 256"}
   [:path {:d (str "M208,40H48A24,24,0,0,0,24,64V176a24,24,0,0,0,24,24H208a24,24,0,0,0,24-24V64A24"
                   ",24,0,0,0,208,40Zm8,136a8,8,0,0,1-8,8H48a8,8,0,0,1-8-8V64a8,8,0,0,1,8-8H208a8,8"
                   ",0,0,1,8,8Zm-48,48a8,8,0,0,1-8,8H96a8,8,0,0,1,0-16h64A8,8,0,0,1,168,224Zm-3.56-110.66-48"
                   "-32A8,8,0,0,0,104,88v64a8,8,0,0,0,12.44,6.66l48-32a8,8,0,0,0,0-13.32ZM120,137.05V103l25.58,17Z")}]])

(defn- booster [_]
  (let [hover? (r/atom false)]
    (fn [{:keys [title desc color1 color2 image on-click db-name]
          :or {color1 "#8089f6"
               color2 "#3e448c"}}]
      (when-not @(subscribe [::subs/booster-active? db-name])
        (let [cg? @(subscribe [::bp/cg?])
              title-text-style {:style {:color "white"
                                        :letter-spacing "1px"
                                        :text-shadow "1px 1px 2px #000"
                                        :font-size "18px"}}
              desc-text-style {:style {:color "white"
                                       :letter-spacing "1px"
                                       :text-shadow "1px 1px 2px #000"
                                       :font-size "14px"
                                       :max-width "250px"
                                       :line-height "1.2em"}}
              width "320px"
              height "90px"]
          [:div {:class "shimmer-effect-2"
                 :style {:width width
                         :height height
                         :box-shadow (str "0 0 1px rgba(255, 255, 255, 0.6),
                                     0 0 2px rgba(255, 255, 255, 0.4),
                                     0 0 20px " color1)
                         :pointer-events "all"
                         :cursor "pointer"
                         :border-radius "20px"
                         :background-image (str "linear-gradient(180deg, " color1 ", " color2 ")")}
                 :on-click on-click
                 :on-mouse-over #(reset! hover? true)
                 :on-mouse-out #(reset! hover? false)}
           (when (and @hover? (not= db-name :booster_discord))
             [:div {:style {:position "absolute"
                            :background "rgba(0,0,0,0.3)"
                            :height "100%"
                            :width "100%"}}
              [:div
               {:style {:position "relative"
                        :top "50%"
                        :left "50%"
                        :transform "translate(0%,-50%)"}}
               play-icon]])
           [:div {:style {:display "flex"
                          :gap "12px"
                          :flex-direction "row"
                          :align-items "center"
                          :justify-content "center"
                          :height "100%"}}
            [:img {:src (str "img/" image)
                   :style {:width "50px"}}]
            [:div
             {:style {:display "flex"
                      :gap "4px"
                      :flex-direction "column"
                      :align-items "flex-start"
                      :justify-content "center"
                      :height "100%"}}
             [:span title-text-style
              title]
             [:span desc-text-style
              desc]]]])))))

(defn- menu-button [{:keys [text on-click logo class style disabled?]}]
  [:button
   {:class ["menu_button" class]
    :disabled disabled?
    :style (merge {:font-size (when @(subscribe [::bp/cg?]) "14px")
                   :letter-spacing "1px"}
                  style)
    :on-click on-click}
   text
   (when logo
     [:img {:src logo
            :style {:width (if @(subscribe [::bp/cg?]) "100px" "156px")}}])])

(defn- get-booster [type]
  (fn []
    (ads/request-rewarded
      (fn []
        (dispatch-pro-sync :get-booster {:booster type})))))

(defn all-boosters-pack []
  [:div {:style {:display "flex"
                 :flex-direction "column"
                 :width "100%"
                 :overflow-y "auto"
                 :box-sizing "border-box"
                 :background-color "bg-color"
                 :color "white"
                 :border-radius "3px"
                 :padding-top "10px"
                 :padding-left "15px"}}

   (if @(subscribe [::subs/all-boosters-active?])
     [:div {:style
            {:display "flex"
             :justify-content "center"
             :align-items "center"
             :height "100%"}}
      [:span
       {:style {:font-size "36px"}}
       "All boosters are activated!"]]
     [:div {:style {:margin-top "10px"}}
      [:div {:style {:display "flex"
                     :flex-direction "column"
                     :gap "12px"}}
       [:div {:style {:display "flex"
                      :align-items "flex-start"
                      :justify-content "center"
                      :box-sizing "border-box"}}
        [:div {:style {:display "grid"
                       :grid-template-columns "repeat(auto-fit, minmax(250px, 1fr))"
                       :gap "16px"
                       :width "100%"
                       :align-items "center"
                       :justify-content "center"}}
         [booster {:title "Coin Booster"
                   :desc "Doubles (2X) the amount of coins you earn"
                   :db-name :booster_coin
                   :color1 "#fbd413"
                   :color2 "#a28702"
                   :image "boost-coin.png"
                   :on-click (get-booster :coin)}]
         [booster {:title "Cooldown Booster"
                   :desc "Reduces ability cooldowns by 20%"
                   :db-name :booster_cooldown
                   :color1 "#246aff"
                   :color2 "#0435a3"
                   :image "boost-cooldown.png"
                   :on-click (get-booster :cooldown)}]
         [booster {:title "Stamina Regen Booster"
                   :desc "Boosts stamina regeneration by +25%"
                   :db-name :booster_regen_mana
                   :color1 "#13b5fb"
                   :color2 "#0472a1"
                   :image "boost-regen-stamina.png"
                   :on-click (get-booster :regen-mana)}]
         [booster {:title "Health Regen Booster"
                   :desc "Boosts health regeneration by +100%"
                   :db-name :booster_regen_hp
                   :color1 "#fb1313"
                   :color2 "#770000"
                   :image "boost-regen-health.png"
                   :on-click (get-booster :regen-hp)}]
         [booster {:title "Damage Booster"
                   :desc "Increases your attack damage by +10%"
                   :db-name :booster_damage
                   :color1 "#ff851f"
                   :color2 "#b3580a"
                   :image "boost-attack.png"
                   :on-click (get-booster :damage)}]
         [booster {:title "Defense Booster"
                   :desc "Enhances your defense by +20%"
                   :db-name :booster_defense
                   :color1 "#c3c3c3"
                   :color2 "#6e6e6e"
                   :image "boost-defense.png"
                   :on-click (get-booster :defense)}]
         [booster {:title "Stun Booster"
                   :desc "Increases your stun immunity by +15%"
                   :db-name :booster_stun
                   :color1 "#5ca139"
                   :color2 "#34631a"
                   :image "boost-stun.png"
                   :on-click (get-booster :stun)}]
         [booster {:title "Root Booster"
                   :desc "Increases your root immunity by +20%"
                   :db-name :booster_root
                   :color1 "#6329bc"
                   :color2 "#3d1875"
                   :image "boost-root.png"
                   :on-click (get-booster :root)}]
         [booster {:title "Discord Speed Booster"
                   :desc "Join Discord and get +5% speed booster for forever!"
                   :db-name :booster_discord
                   :image "discord.png"
                   :on-click (fn []
                               (dispatch-pro-sync :get-booster {:booster :discord})
                               (js/window.open "https://discord.gg/hyn2YcZfC2" "_blank"))}]]]]])])

(defn booster-panel []
  (let [mobile? @(subscribe [::subs/mobile?])
        height "30px"
        cg? @(subscribe [::bp/cg?])]
    (when @(subscribe [::subs/booster-panel-open?])
      [:div
       {:style {:display "flex"
                :user-select "none"
                :pointer-events "all"
                :position "absolute"
                :justify-content "center"
                :align-items "center"
                :width "720px"
                :height (if mobile?
                          (str "calc(100% - " height ")")
                          (if cg? "450px" "500px"))
                :top "50%"
                :left "50%"
                :z-index 998
                :transform "translate(-50%, -50%)"}}
       [:div {:style {:display "flex"
                      :flex-direction "row"
                      :width "100%"
                      :height "100%"
                      :background bg-color
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

          [:div
           {:style {:display "flex"
                    :gap "4px"
                    :flex-direction "column"
                    :position "absolute"
                    :justify-content "center"
                    :align-items "center"
                    :margin-top "5px"}}
           [:div
            {:style {:display "flex"
                     :flex-direction "row"
                     :gap "4px"
                     :justify-content "center"
                     :align-items "center"}}
            [:span {:style {:font-size "24px"
                            :letter-spacing "1px"
                            :color "white"}}
             "Get your Boosters"]
            play-icon]
           [:span {:style {:font-size "16px"
                           :color coin-color
                           :letter-spacing "1px"
                           :text-shadow "1px 1px 2px #000"}}
            "Boosters are active for 30 minutes!"]
           [:span {:style {:font-size "16px"
                           :color "white"
                           :letter-spacing "1px"
                           :text-shadow "1px 1px 2px #000"}}
            (str "Activate all boosters ang get  ⚡️ badge next to your username!")]]

          [:span {:on-click #(re/fire-rules :game/booster-panel? false)
                  :style {:position "fixed"
                          :left "calc(100% - 30px)"
                          :font-size "24px"
                          :color "white"
                          :cursor "pointer"}} "X"]]

         [:div {:style {:display "flex"
                        :flex-direction "row"
                        :overflow "hidden"
                        :width "100%"
                        :height (str "calc(100% - " header-height ")")}}
          [all-boosters-pack]]]]])))
