(ns main.ui.view.create-room
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

(def shop-bg-color "#454545")
(def header-height "40px")
(def coin-color "rgb(251, 212, 19)")

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

(defn create-room-join-game []
  (let [selected-game-mode (r/atom :solo-death-match)
        private? (r/atom true)
        entered-room-id (r/atom nil)]
    (fn []
      (let [cg? @(subscribe [::bp/cg?])
            style {:font-size (if cg? "14px" "18px")}]
        [:div {:style {:display "flex"
                       :flex-direction "column"
                       :width "100%"
                       :overflow-y "auto"
                       :padding "10px"
                       :box-sizing "border-box"
                       :background-color "#454545"
                       :color "white"
                       :border-radius "3px"}}

         [:div {:style {:margin-top "10px"}}

          [:div {:style {:display "flex"
                         :flex-direction "column"
                         :gap "12px"}}
           [:div {:style {:display "flex"
                          :align-items "center"
                          :justify-content "center"
                          :box-sizing "border-box"}}
            [:div {:style {:display "flex"
                           :flex-direction "row"
                           :gap "16px"
                           :align-items "center"
                           :justify-content "center"}}
             [:span {:style {:color "white"
                             :font-size (if cg? "16px" "22px")
                             :text-stroke "4px"
                             :text-shadow "2px 2px 4px #000"}}
              "Game Mode:"]
             [:div
              {:style {:display "flex"
                       :flex-direction "row"
                       :align-items "center"
                       :gap "12px"
                       :z-index 9
                       :pointer-events "all"}}
              [menu-button {:text "Solo Deathmatch"
                            :class (when (= @selected-game-mode :solo-death-match) "selected")
                            :style style
                            :on-click (fn []
                                        (reset! selected-game-mode :solo-death-match))}]
              [menu-button {:text "Team Deathmatch"
                            :class (when (= @selected-game-mode :team-death-match) "selected")
                            :style style
                            :on-click (fn []
                                        (reset! selected-game-mode :team-death-match))}]]]]
           ;; TODO enable this after multi server added
           #_[:div
            {:style {:display "flex"
                     :align-items "center"
                     :justify-content "center"
                     :box-sizing "border-box"}}
            [:span {:style {:color "white"
                            :font-size (if cg? "14px" "18px")
                            :text-stroke "4px"
                            :text-shadow "2px 2px 4px #000"}}
             (if (= :usa (re/query :server/selected))
               "Server: USA (Change on main page)"
               "Server: Germany (Change on main page)")]]
           [:div
            {:style {:display "flex"
                     :align-items "center"
                     :justify-content "center"
                     :box-sizing "border-box"}}
            [:span {:style {:color "white"
                            :font-size (if cg? "14px" "18px")
                            :text-stroke "4px"
                            :text-shadow "2px 2px 4px #000"}}
             "Private?:"]
            [:input {:type "checkbox"
                     :checked @private?
                     :on-change (fn [e]
                                  (reset! private? (j/get-in e [:target :checked])))}]]
           [:div
            {:style {:display "flex"
                     :align-items "center"
                     :justify-content "center"
                     :box-sizing "border-box"}}
            [:span
             {:style {:color "white"
                      :font-size "12px"
                      :font-family "sans-serif"
                      :text-stroke "4px"
                      :text-shadow "2px 2px 4px #000"}}
             "(If private is checked, then players with room id can join)"]]]
          [:div
           {:style {:width "100%"
                    :display "flex"
                    :justify-content "center"
                    :margin-top "15px"}}
           [menu-button {:text "Create Game!"
                         :style (assoc style :width "250px" :font-size "20px")
                         :disabled? @(subscribe [::subs/creating-room?])
                         :on-click (fn []
                                     (re/insert-with-ui :game/creating-room? true)
                                     (dispatch-pro-sync :create-room {:game-mode @selected-game-mode
                                                                      :private? @private?}))}]]]
         [:div
          {:style {:width "100%"
                   :display "flex"
                   :justify-content "center"
                   :align-items "center"
                   :flex-direction "column"
                   :gap "4px"
                   :margin-top "50px"}}
          [:span {:style {:font-size "20px"}} "Or Join a game"]
          [:div
           {:style {:width "100%"
                    :display "flex"
                    :justify-content "center"
                    :gap "6px"
                    :align-items "center"}}
           [:input {:type "text"
                    :placeholder "Enter room id"
                    :value @entered-room-id
                    :on-change (fn [e]
                                 (->> (j/get-in e [:target :value])
                                      str/trim
                                      str/upper-case
                                      (re-seq #"[A-Z0-9]")
                                      (apply str)
                                      (reset! entered-room-id)))
                    :maxLength "5"
                    :style {:outline "none"
                            :width "110px"
                            :height "32px"}}]
           [menu-button {:text "Join Game!"
                         :style (assoc style :width "150px")
                         :disabled? (or (not= 5 (count @entered-room-id))
                                        @(subscribe [::subs/checking-room?]))
                         :on-click (fn []
                                     (re/insert-with-ui :game/checking-room? true)
                                     (dispatch-pro-sync :check-room-available {:room-id @entered-room-id}))}]]]]))))

(defn create-room-panel []
  (let [mobile? @(subscribe [::subs/mobile?])
        height "40px"]
    [:<>
     (when @(subscribe [::subs/create-room-panel-open?])
       [:div
        {:style {:display "flex"
                 :user-select "none"
                 :pointer-events "all"
                 :position "absolute"
                 :justify-content "center"
                 :align-items "center"
                 :width "600px"
                 :height (if mobile? (str "calc(100% - " height ")") "370px")
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

           [:span {:style {:left "50%"
                           :font-size "24px"
                           :color "white"}}
            "Play with your Friends!"]

           [:span {:on-click #(re/fire-rules :game/create-room-panel? false)
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
           [create-room-join-game]]]]])
     (when-let [{:keys [success? room-not-available? room-full?]} @(subscribe [::subs/share-room-link-modal])]
       (let [link (ads/get-created-room-link)]
         [modal {:open? true
                 :header (if success? "Share link with your friends!" "Error!")
                 :content (cond
                            room-not-available?
                            "Room does not exist, please use valid room id."

                            room-full?
                            "Room is full, please join another room."

                            (not success?)
                            "Something went wrong please try again or refresh the page."

                            success?
                            [:div
                             {:style {:display "flex"
                                      :flex-direction "row"
                                      :align-items "center"
                                      :gap "4px"}}
                             [:input {:style {:width "250px"
                                              :height "30px"
                                              :outline "none"}
                                      :readOnly true
                                      :type "text"
                                      :value link}]
                             [button {:text "Copy link"
                                      :on-click (fn []
                                                  (utils/copy-to-clipboard (ads/get-created-room-link))
                                                  (js/alert "Link copied to the clipboard."))}]])
                 :close-button-text "Okay"
                 :close-fn #(re/insert :game/share-room-link-modal nil)}]))]))
