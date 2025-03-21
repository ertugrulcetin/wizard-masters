(ns main.ui.view.spells-info
  (:require
    [breaking-point.core :as bp]
    [main.ui.subs :as subs]
    [re-frame.core :refer [subscribe]]
    [reagent.core :as r]))

(def spells-open? (r/atom false))

(defn- skill-info [img text]
  [:div {:style {:display "flex"
                 :gap "12px"
                 :flex-direction "row"
                 :align-items "center"
                 :justify-content "center"
                 :max-width "300px"}}
   [:img {:src img
          :style {:width "50px"}}]
   [:span {:style {:color "white"
                   :font-family "sans-serif"}} text]])

(defn spells-info []
  (let [mobile? @(subscribe [::subs/mobile?])]
    (when @spells-open?
      [:div
       {:on-click #(reset! spells-open? false)
        :style {:display "flex"
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
       [:div {:style (cond-> {:width "700px"
                              :padding-bottom "10px"
                              :background "#454545"
                              :border-radius "5px"
                              :border "2px solid white"}
                       @(subscribe [::bp/cg?]) (merge {:width "650px"
                                                       :height "400px"
                                                       :overflow-y "scroll"
                                                       :overflow-x "hidden"})
                       mobile? (merge {:height "calc(100% - 30px)"}))}
        [:span {:on-click #(reset! spells-open? false)
                :style {:position "relative"
                        :top "5px"
                        :left "calc(100% - 25px)"
                        :font-size "24px"
                        :color "white"
                        :cursor "pointer"}} "X"]
        [:div {:style {:display "flex"
                       :gap "10px"
                       :flex-direction "column"
                       :align-items "center"
                       :justify-content "center"}}
         [:div {:style {:display "flex"
                        :flex-direction "row"
                        :padding "10px"
                        :gap "24px"}}
          [:div {:style {:display "flex"
                         :justify-content "center"
                         :width "300px"}}
           [:span {:style {:color "white"
                           :font-size "24px"
                           :font-family "sans-serif"}} "Spells"]]
          [:div {:style {:display "flex"
                         :justify-content "center"
                         :width "300px"}}
           [:span {:style {:color "white"
                           :font-size "24px"
                           :font-family "sans-serif"}} "Sorceries"]]]

         [:div {:style {:display "flex"
                        :flex-direction "row"
                        :padding "10px"
                        :gap "24px"}}
          [skill-info
           "img/texture/skill_fire.png"
           "Launches a fire projectile that deals area damage in a 5 meter radius on impact."]
          [skill-info
           "img/texture/skill_super_nova.png"
           "Summons a powerful supernova that deals heavy damage in a 10-meter radius."]]

         [:div {:style {:display "flex"
                        :flex-direction "row"
                        :padding "10px"
                        :gap "24px"}}
          [skill-info
           "img/texture/skill_ice_arrow.png"
           "Shoots a fast, long-range arrow. The longer the charge, the more damage it deals. Drains enemy mana and blocks mana regen for 1 second."]
          [skill-info
           "img/texture/skill_ice_tornado.png"
           "Summons an ice tornado that freezes enemies within a 10-meter radius for 2 seconds."]]

         [:div {:style {:display "flex"
                        :flex-direction "row"
                        :padding "10px"
                        :gap "24px"}}
          [skill-info
           "img/texture/skill_wind_slash.png"
           "Deals damage to enemies within medium range and pushes them back. Casting at the ground launches your character upward."]
          [skill-info
           "img/texture/skill_wind_tornado.png"
           "Summons a tornado that deals low damage in a 7.5-meter radius, rooting enemies for up to 5 seconds. Effective at close to medium range."]]

         [:div {:style {:display "flex"
                        :flex-direction "row"
                        :padding "10px"
                        :gap "24px"}}
          [skill-info
           "img/texture/skill_light_staff.png"
           "Delivers instant, precise damage to enemies at long range with hitscan accuracy."]
          [skill-info
           "img/texture/skill_light_strike.png"
           "Calls down a lightning strike that impacts a 7.5-meter radius, stunning enemies for up to 3 seconds. Has medium range."]]

         [:div {:style {:display "flex"
                        :flex-direction "row"
                        :padding "10px"
                        :gap "24px"}}
          [skill-info
           "img/texture/skill_toxic.png"
           "Launches 3 toxic projectiles that each deal low damage in a 3-meter radius and leave a puddle slowing enemies for 1 second."]
          [skill-info
           "img/texture/skill_toxic_cloud.png"
           "Summons toxic smoke that deals damage over time in a 10-meter radius."]]

         [:div {:style {:display "flex"
                        :flex-direction "row"
                        :padding "10px"
                        :gap "24px"}}
          [skill-info
           "img/texture/skill_rock.png"
           "Slams heavy rocks from both sides, crushing enemies at close range for significant damage."]
          [skill-info
           "img/texture/skill_rock_wall.png"
           "Spawns two rock walls that can block enemy spells; both walls can be placed in a row."]]]]])))
