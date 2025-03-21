(ns main.ui.view.view
  (:require
    [applied-science.js-interop :as j]
    [breaking-point.core :as bp]
    [clojure.string :as str]
    [main.ads :as ads]
    [main.api.camera :as api.camera]
    [main.config :as config]
    [main.rule-engine :as re]
    [main.scene.mobile :as mobile]
    [main.scene.network :as network]
    [main.scene.network :refer [dispatch-pro]]
    [main.scene.settings :as settings]
    [main.ui.events :as events]
    [main.ui.styles :as styles]
    [main.ui.subs :as subs]
    [main.ui.svg :as svg]
    [main.ui.view.boosters :refer [booster-panel]]
    [main.ui.view.components :refer [modal]]
    [main.ui.view.create-room :refer [create-room-panel]]
    [main.ui.view.leaderboard :refer [leaderboard-panel]]
    [main.ui.view.login :refer [log-in-sign-up]]
    [main.ui.view.settings :refer [settings settings-open?]]
    [main.ui.view.shop :refer [shop-panel]]
    [main.ui.view.spells-info :refer [spells-info spells-open?]]
    [main.utils :as utils]
    [re-frame.core :as re-frame :refer [subscribe]]
    [reagent.core :as r]
    [reagent.dom :as rdom]))

(def death-match-kill-count
  (if config/dev?
    2
    40))

(def touching? (atom false))
(def last-touch-x (atom 0))
(def last-touch-y (atom 0))
(def selected-img-path (r/atom nil))

(defn- skill-slot [{:keys [enabled controller type mobile?]}]
  (let [[element kind] type
        [cooldown path mobile-click]
        (cond
          (= element :jump) [::subs/cooldown-spell
                             "img/texture/skill_jump.png"
                             :player/mobile-jump-click?]

          (and (= kind :spell)
               (= element :fire)) [::subs/cooldown-spell
                                   "img/texture/skill_fire.png"
                                   :player/mobile-fire-click?]

          (= element :rune) [::subs/cooldown-roll
                             "img/texture/skill_dash.png"
                             :player/mobile-dash-click?]

          (and (= kind :sorcery)
               (= element :fire)) [::subs/cooldown-spell-super-nova
                                   "img/texture/skill_super_nova.png"
                                   :player/mobile-fire-sorcery-click?]

          (and (= kind :spell)
               (= element :ice)) [::subs/cooldown-spell-ice-arrow
                                  "img/texture/skill_ice_arrow.png"
                                  :player/mobile-ice-click?]

          (and (= kind :sorcery)
               (= element :ice)) [::subs/cooldown-spell-ice-tornado
                                  "img/texture/skill_ice_tornado.png"
                                  :player/mobile-ice-sorcery-click?]

          (and (= kind :spell)
               (= element :wind)) [::subs/cooldown-spell-wind-slash
                                   "img/texture/skill_wind_slash.png"
                                   :player/mobile-wind-click?]

          (and (= kind :sorcery)
               (= element :wind)) [::subs/cooldown-spell-wind-tornado
                                   "img/texture/skill_wind_tornado.png"
                                   :player/mobile-wind-sorcery-click?]

          (and (= kind :spell)
               (= element :light)) [::subs/cooldown-spell-light-staff
                                    "img/texture/skill_light_staff.png"
                                    :player/mobile-light-click?]

          (and (= kind :sorcery)
               (= element :light)) [::subs/cooldown-spell-light-strike
                                    "img/texture/skill_light_strike.png"
                                    :player/mobile-light-sorcery-click?]

          (and (= kind :spell)
               (= element :toxic)) [::subs/cooldown-spell
                                    "img/texture/skill_toxic.png"
                                    :player/mobile-toxic-click?]

          (and (= kind :sorcery)
               (= element :toxic)) [::subs/cooldown-spell-toxic-cloud
                                    "img/texture/skill_toxic_cloud.png"
                                    :player/mobile-toxic-sorcery-click?]

          (and (= kind :spell)
               (= element :earth)) [::subs/cooldown-spell
                                    "img/texture/skill_rock.png"
                                    :player/mobile-earth-click?]

          (and (= kind :sorcery)
               (= element :earth)) [::subs/cooldown-spell-rock-wall
                                    "img/texture/skill_rock_wall.png"
                                    :player/mobile-earth-sorcery-click?])
        cooldown @(subscribe [cooldown])
        duration (:duration cooldown)
        duration (if @(subscribe [::subs/booster-active? :booster_cooldown])
                   (* duration 0.8)
                   duration)
        last-time-applied (:last-time-applied cooldown)
        current-time @(subscribe [::subs/current-time])
        left-time (Math/max 0 (- duration (- current-time last-time-applied)))
        cooldown-finished? (>= (- current-time last-time-applied) duration)
        cg? @(subscribe [::bp/cg?])
        skill-width (if mobile?
                      "60px"
                      (if cg? "40px" "60px"))
        font-size (if cg? "16px" "24px")
        top (if mobile?
              "unset"
              (if cg? "calc(50% + 15px)" "calc(50% + 13px)"))
        camera (re/query :camera)
        mouse-sensitivity (settings/get-setting :mouse-sensitivity)]
    [:div#mobile-skill
     {:style (cond-> {:display "flex"
                      :flex-direction "column"
                      :justify-content "center"
                      :align-items "center"}
               mobile? (assoc
                         :pointer-events "all"
                         :z-index 99))
      :on-touch-start (fn [e]
                        (when (and mobile? (not @touching?))
                          (when mobile-click
                            (reset! selected-img-path path)
                            (re/fire-rules mobile-click true))
                          (reset! touching? true)
                          (let [touch (some (fn [t] (when (= "IMG" (j/get-in t [:target :nodeName]))
                                                      t)) (j/get e :touches))]
                            (reset! last-touch-x (j/get touch :clientX))
                            (reset! last-touch-y (j/get touch :clientY)))))
      :on-touch-move (fn [e]
                       (when @touching?
                         (let [touch (some (fn [t] (when (= "IMG" (j/get-in t [:target :nodeName]))
                                                     t)) (j/get e :touches))
                               dx (- (j/get touch :clientX) @last-touch-x)
                               dy (- (j/get touch :clientY) @last-touch-y)
                               speed (/ mouse-sensitivity 100)]
                           (j/update! camera :alpha - (* dx speed))
                           (j/update! camera :beta - (* dy speed))
                           (reset! last-touch-x (j/get touch :clientX))
                           (reset! last-touch-y (j/get touch :clientY)))))
      :on-touch-end (fn [e]
                      (when (and mobile? @touching?)
                        (reset! touching? false)
                        (when mobile-click
                          (reset! selected-img-path nil)
                          (re/fire-rules mobile-click false))))}
     (when (not cooldown-finished?)
       [:span {:style {:position "absolute"
                       :top top
                       :font-size font-size
                       :color "white"
                       :z-index 2}}
        (j/call (/ left-time 1000) :toFixed 1)])
     (when controller
       [:img {:style {:width (when-not (= (first type) :rune) "20px")
                      :height (when (= (first type) :rune) "20px")
                      :filter (when (or (not cooldown-finished?)
                                        (and enabled (not @(subscribe [enabled]))))
                                "brightness(0.5)")}
              :src controller}])
     [:img {:style {:width skill-width
                    :filter (when (or (not cooldown-finished?)
                                      (and enabled (not @(subscribe [enabled])))
                                      (and @touching? (= path @selected-img-path)))
                              "brightness(0.5)")}
            :src path}]]))

(defn- fps-and-ping []
  (let [cg? @(subscribe [::bp/cg?])
        mobile? @(subscribe [::subs/mobile?])
        font-size (if cg? "12px" "15px")
        width (if cg? "120px" "150px")
        style {:style {:font-size font-size
                       :user-select "none"
                       :text-stroke "4px"
                       :text-shadow "2px 2px 4px #000"
                       :letter-spacing "1px"}}]
    [:<>
     (when mobile?
       [:div
        {:style {:position "absolute"
                 :width "35px"
                 :height "35px"
                 :right "130px"
                 :top "10px"
                 :pointer-events "all"
                 :z-index 99}
         :on-touch-start (fn []
                           (re/fire-rules :mobile/stop-game? true))}
        [:img {:src "img/exit.png"
               :style {:width "35px"}}]])
     [:div
      {:style {:display "flex"
               :flex-direction "column"
               :justify-content "center"
               :align-items "center"
               :user-select "none"
               :width width
               :height "65px"
               :position "absolute"
               :right "-5px"
               :top "-5px"
               :color "white"
               :padding "5px"
               :gap "4px"
               :border-radius "3px"}}
      [:span style "Fps: " @(subscribe [::subs/fps])]
      [:span style "Ping: " @(subscribe [::subs/network-ping])]
      [:span style "Room Id: " @(subscribe [::subs/player-room-id])]]]))

(defn- collectable-durations []
  (let [speed-remained @(subscribe [::subs/player-speed-booster-until])
        cg? @(subscribe [::bp/cg?])
        font-size (if cg? "13px" "16px")
        span-style {:text-stroke "4px"
                    :text-shadow "2px 2px 4px #000"}]
    (when speed-remained
      [:div
       {:style {:display "flex"
                :flex-direction "column"
                :font-size font-size
                :justify-content "center"
                :align-items "center"
                :width "200px"
                :height "40px"
                :position "absolute"
                :right "0px"
                :bottom "100px"
                :color "white"
                :padding "10px"
                :border-radius "3px"}}
       [:span {:style span-style} "Speed remained: " speed-remained " secs"]])))

(defn get-team-color [team]
  (if (= :red team)
    "#ff4c4c"
    "#61d5ff"))

(defn- player-killed [[killer victim]]
  (let [cg? @(subscribe [::bp/cg?])
        font-size (if cg? "12px" "16px")
        div-max-width (if cg? "200px" "400px")
        span-max-width (if cg? "70px" "140px")
        style {:font-size font-size
               :white-space "nowrap"
               :overflow "hidden"
               :text-overflow "ellipsis"
               :max-width span-max-width
               :text-stroke "2px"
               :text-shadow "1px 1px 2px #000"}]
    [:div {:style {:display "flex"
                   :flex-direction "row"
                   :gap "5px"
                   :max-width div-max-width}}
     [:span {:style (merge style {:color (get-team-color (:team killer))})} (:username killer)]
     [:span {:style style} "killed"]
     [:span {:style (merge style {:color (get-team-color (:team victim))})} (:username victim)]]))

(defn- who-killed-who []
  (when-let [who-killed-who @(subscribe [::subs/who-killed-who])]
    [:div
     {:style {:display "flex"
              :gap "4px"
              :flex-direction "column"
              :position "absolute"
              :right "10px"
              :top "90px"
              :color "white"
              :border-radius "5px"
              :letter-spacing ".1rem"
              :padding "10px"
              :font-size "18px"}}
     (for [[id who-killed-who] (map-indexed vector who-killed-who)]
       ^{:key id}
       [player-killed who-killed-who])]))

(defn- player-score [player-id current-player? username kills team]
  (let [cg? @(subscribe [::bp/cg?])
        mobile? @(subscribe [::subs/mobile?])
        game-mode @(subscribe [::subs/game-mode])
        font-size (if mobile?
                    "9px"
                    (if cg? "11px" "13px"))
        font-family "sans-serif"
        username-style {:style {:font-family font-family
                                :white-space "nowrap"
                                :overflow "hidden"
                                :font-size font-size
                                :text-overflow "ellipsis"}}]

    [:div
     {:style {:display "flex"
              :flex-direction "row"
              :align-items "center"
              :justify-content "center"}}
     [:div
      {:style {:display "flex"
               :width "150px"
               :height (if mobile?
                         "7px"
                         (if cg? "10px" "15px"))
               :align-items "center"
               :justify-content "space-between"
               :padding "5px"
               :opacity (if @(subscribe [::subs/player-focus? player-id]) 1 0.5)
               :background (if (= game-mode :team-death-match)
                             (if (= :red team)
                               (if current-player?
                                 "#ff4c4c"
                                 "#b62b2b")
                               (if current-player?
                                 "#4fadcf"
                                 "#2b91b6"))
                             (if current-player? "rgba(247,149,32,.8)" "rgba(0,0,0,.4)"))
               :border-radius "5px"
               :color "white"}}
      [:span username-style
       (if current-player?
         (str username " (You)")
         username)]
      [:span {:style {:font-family font-family
                      :font-size font-size}}
       (str " " kills)]]
     (when (or (and (not current-player?) @(subscribe [::subs/player-boosts? player-id]))
               (and current-player? @(subscribe [::subs/all-boosters-active?])))
       [:span
        {:style {:position "absolute"
                 :left "160px"
                 :font-size "22px"
                 :text-shadow "1px 1px 2px #000"}}
        "⚡\uFE0F"])]))

(defn- players-score-board []
  [:div
   {:style {:display "flex"
            :flex-direction "column"
            :gap "3px"
            :position "absolute"
            :left "10px"
            :top "15px"
            :letter-spacing ".1rem"}}
   (if (= :team-death-match @(subscribe [::subs/game-mode]))
     (let [stats (group-by :team @(subscribe [::subs/players-stats]))
           red-team (sort-by :kills > (:red stats))
           blue-team (sort-by :kills > (:blue stats))
           my-team @(subscribe [::subs/player-team])
           teams (if (= my-team :red)
                   [red-team blue-team]
                   [blue-team red-team])]
       [:<>
        (for [t teams]
          (for [{:keys [player-id current-player? username kills team]} t]
            ^{:key username}
            [player-score player-id current-player? username kills team]))])
     (for [{:keys [player-id current-player? username kills team]} (sort-by :kills > @(subscribe [::subs/players-stats]))]
       ^{:key username}
       [player-score player-id current-player? username kills team]))])

(defn- damage-effect []
  [:<>
   [:div#damage-effect-1
    {:style {:width "100%"
             :height "100%"
             :position "absolute"
             :box-sizing "border-box"
             :border "40px solid red"
             :filter "blur(50px)"
             :opacity 0
             :transition "opacity 0.5s ease-in-out"
             :z-index 9999}}]
   [:div#damage-effect-2
    {:style {:width "100%"
             :height "100%"
             :position "absolute"
             :box-sizing "border-box"
             :border "20px solid red"
             :filter "blur(30px)"
             :transition "opacity 0.5s ease-in-out"
             :opacity 0
             :z-index 9999}}]])

(defn- heartbeat-effect []
  [:<>
   [:div#heartbeat-effect-1
    {:style {:width "100%"
             :height "100%"
             :position "absolute"
             :box-sizing "border-box"
             :border "40px solid red"
             :filter "blur(50px)"
             :opacity 0
             :transition "opacity 0.5s ease-in-out"
             :z-index 9999}}]
   [:div#heartbeat-effect-2
    {:style {:width "100%"
             :height "100%"
             :position "absolute"
             :box-sizing "border-box"
             :border "20px solid red"
             :filter "blur(30px)"
             :transition "opacity 0.5s ease-in-out"
             :opacity 0
             :z-index 9999}}]])

(defn- levitate []
  (let [lev (atom 0)]
    (r/create-class
      {:component-did-update (fn []
                               (some-> (js/document.getElementById "levitate")
                                       (j/call-in [:style :setProperty] "--p" (str @lev))))
       :reagent-render
       (fn []
         (let [mana @(subscribe [::subs/player-mana])]
           (reset! lev mana)
           [:div
            {:class "levitate_wrapper"}
            [:div#levitate.levitate {:class (when @(subscribe [::subs/player-levitate?])
                                              "shake shake-constant")
                                     :style {:opacity (if (= mana 100)
                                                        0
                                                        0.75)
                                             :animation (when (<= mana 20)
                                                          "low_mana 1s infinite")}}]]))})))

(defn get-health-percentage [current-health total-health]
  (* (/ current-health total-health) 100))

(defn- message-box []
  (let [cg? @(subscribe [::bp/cg?])
        chat-focus? @(subscribe [::subs/chat-focus?])
        messages (map-indexed vector @(subscribe [::subs/chat-messages]))
        messages? (seq messages)]
    [:div
     {:style {:display "flex"
              :max-width "270px"
              :border-radius "5px"
              :background (if (or chat-focus? messages?) "rgba(0, 0, 0, 0.4)" "unset")
              :font-size "15px"
              :font-family "sans-serif"
              :color "white"
              :word-wrap "break-word"
              :flex-direction "column"
              :padding "5px"
              :position "absolute"
              :left "15px"
              :bottom (if cg? "5px" "15px")}}
     [:div
      {:style {:display "flex"
               :flex-direction "column"}}
      (for [[idx {:keys [username message]}] messages]
        ^{:key idx}
        [:span {:style {:padding "3px"
                        :font-size (if cg? "12px" "15px")
                        :font-family "sans-serif"}} (str username ": " message)])]
     [:div
      [:input {:id "chat-input"
               :type "text"
               :class "chat"
               :placeholder (when (or chat-focus? messages?) "Press ENTER to chat!")
               :maxLength "100"
               :style {:font-size (if cg? "12px" "15px")
                       :width (if cg? "150px" "200px")
                       :height (if cg? "15px" "20px")
                       :opacity 1 #_(if chat-focus? 1 0)
                       :border "unset"
                       :outline "unset"
                       :color "white"
                       :background "transparent"}}]]
     (when-not (or chat-focus? messages?)
       [:span {:style
               {:color "white"
                :font-size (if cg? "15px" "20px")
                :text-stroke "4px"
                :text-shadow "2px 2px 4px #000"}}
        "Press ENTER to chat!"])]))

(defn- health-bar [width]
  [:div
   {:style {:display "flex"
            :align-items "center"}}
   (let [max-health-width (/ width 2)
         health-percentage (get-health-percentage @(subscribe [::subs/player-current-health])
                                                  @(subscribe [::subs/player-total-health]))
         health-width (/ (* max-health-width health-percentage) 100)]
     [:div {:style {:width (str (* health-width 1.94) "px")
                    :height "12px"
                    :position "absolute"
                    :left (if (> health-percentage 50) "6px" "9px")
                    :transition "width 0.5s ease-in-out"
                    :background-image "url('img/texture/hp_wrap.png')"
                    :background-size "cover"
                    :background-repeat "no-repeat"
                    :z-index 2}}])
   [:img {:style {:width "100%"
                  :height "20px"
                  :z-index 2}
          :src "img/texture/hp_indicator.png"}]])

(defn- mobile-skills []
  (let [width (if @(subscribe [::bp/cg?]) 250 350)
        primary-element (re/query :player/primary-element)
        secondary-element (re/query :player/secondary-element)]
    [:<>
     [:div
      {:style {:display "flex"
               :position "fixed"
               :bottom "5px"
               :left "50%"
               :transform "translate(-50%, 0%)"
               :justify-content "flex-end"
               :align-items "center"
               :box-sizing "border-box"}}
      [:div
       {:style {:display "flex"
                :flex-direction "column"
                :gap "10px"
                :width (str width "px")}}
       [health-bar width]]]

     [:div
      {:id "mobile-skills"
       :style {:display "flex"
               :flex-direction "column-reverse"
               :position "fixed"
               :bottom "30px"
               :right "180px"
               :transform "translate(50%, 0%)"
               :justify-content "flex-end"
               :gap "20px"
               :align-items "center"
               :box-sizing "border-box"}}
      [:div
       {:style {:width "200px"
                :display "flex"
                :justify-content "space-between"
                :align-items "center"}}
       [skill-slot {:type [:jump] :mobile? true}]
       [skill-slot {:type [:rune] :mobile? true}]]
      [:div
       {:style {:width "200px"
                :display "flex"
                :justify-content "space-between"
                :align-items "center"}}
       [skill-slot {:type [primary-element :spell] :mobile? true}]
       [skill-slot {:type [secondary-element :spell] :mobile? true}]]

      [:div
       {:style {:width "200px"
                :display "flex"
                :justify-content "space-between"
                :align-items "center"}}
       [skill-slot {:type [primary-element :sorcery] :mobile? true}]
       [skill-slot {:type [secondary-element :sorcery] :mobile? true}]]



      #_[:div {:style {:display "flex"
                       :flex-direction "row"
                       :gap "6px"
                       :margin-bottom "5px"
                       :width "100%"
                       :justify-content "space-between"}}
         [skill-slot {:type [primary-element :spell] :mobile? true}]
         [skill-slot {:type [primary-element :sorcery] :mobile? true}]
         [skill-slot {:type [:jump] :mobile? true}]
         [skill-slot {:type [:rune] :mobile? true}]
         [skill-slot {:type [secondary-element :sorcery] :mobile? true}]
         [skill-slot {:type [secondary-element :spell] :mobile? true}]]]]))

(defn- skills-panel []
  (if @(subscribe [::subs/mobile?])
    [mobile-skills]
    (let [width (if @(subscribe [::bp/cg?]) 250 350)
          primary-element (re/query :player/primary-element)
          secondary-element (re/query :player/secondary-element)]
      [:div#skills-panel
       {:style {:display "flex"
                :position "fixed"
                :bottom "0px"
                :left "50%"
                :transform "translate(-50%, 0%)"
                :justify-content "flex-end"
                :align-items "center"
                :box-sizing "border-box"}}
       [:div
        {:style {:display "flex"
                 :flex-direction "column"
                 :gap "10px"
                 :width (str width "px")}}
        [health-bar width]
        [:div {:style {:display "flex"
                       :flex-direction "row"
                       :margin-bottom "5px"
                       :width "100%"
                       :justify-content "space-between"}}
         [skill-slot {:type [primary-element :spell]
                      :controller "img/texture/mouse_left.png"}]
         [skill-slot {:type [primary-element :sorcery]
                      :controller "img/texture/key_q.png"}]
         [skill-slot {:type [:rune]
                      :controller "img/texture/key_shift.png"}]
         [skill-slot {:type [secondary-element :sorcery]
                      :controller "img/texture/key_e.png"}]
         [skill-slot {:type [secondary-element :spell]
                      :controller "img/texture/mouse_right.png"}]]]])))

(defn- damage-notify []
  (when-let [spells (seq @(subscribe [::subs/player-incoming-enemy-spells]))]
    (let [width (if @(subscribe [::bp/cg?]) "70px" "90px")]
      [:div#damage-notify
       {:style {:display "flex"
                :flex-direction "column"
                :gap "12px"
                :position "absolute"
                :top "30%"
                :left "50%"
                :transform "translate(-50%, -50%)"
                :justify-content "center"
                :align-items "center"
                :box-sizing "border-box"
                :animation "glowing 0.5s ease-in-out infinite"}}
       [:span
        {:style {:font-size "36px"
                 :color "#ff0024"
                 :text-shadow "1px 1px 2px #fff"}}
        "Danger!"]
       [:div
        {:style {:display "flex"
                 :flex-direction "row"
                 :gap "12px"}}
        (for [[idx spell] (map-indexed vector spells)]
          ^{:key idx}
          [:img {:style {:width width}
                 :src (case spell
                        :fire "img/texture/skill_super_nova.png"
                        :ice "img/texture/skill_ice_tornado.png"
                        :light "img/texture/skill_light_strike.png"
                        :wind "img/texture/skill_wind_tornado.png"
                        :toxic "img/texture/skill_toxic_cloud.png")}])]])))

(defn- set-primary-element [element]
  (let [secondary-element (re/query :player/secondary-element)]
    (when (= element secondary-element)
      (re/insert :player/secondary-element nil))
    (re/insert :player/primary-element element)))

(defn- set-secondary-element [element]
  (let [primary-element (re/query :player/primary-element)]
    (when (= element primary-element)
      (re/insert :player/primary-element nil))
    (re/insert :player/secondary-element element)))

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

(defn- select-element [type element]
  (if (= type :primary)
    (set-primary-element element)
    (set-secondary-element element)))

(defn- element [{:keys [element path type unlocked?]}]
  (let [selected-element (if (= type :primary)
                           @(subscribe [::subs/player-primary-element])
                           @(subscribe [::subs/player-secondary-element]))
        selected-in-other? (= element
                              (if (= type :primary)
                                @(subscribe [::subs/player-secondary-element])
                                @(subscribe [::subs/player-primary-element])))
        cg? @(subscribe [::bp/cg?])]
    [:div {:style {:display "flex"}}
     (when-not unlocked?
       [:div
        {:style {:position "absolute"
                 :width (if cg? "40px" "60px")
                 :height (if cg? "40px" "60px")
                 :z-index 2
                 :pointer-events "none"}}
        [:div {:style {:position "relative"
                       :top "50%"
                       :left "50%"
                       :width "32px"
                       :transform "translate(-50%,-50%)"}}
         play-icon]])
     [:img {:class (when-not selected-in-other? "element-selection")
            :style (cond-> {:width (if cg? "40px" "60px")
                            :cursor :pointer
                            :box-sizing "border-box"}
                     (and selected-element (= selected-element element))
                     (merge {:border "1px solid white"
                             :border-radius "5px"})

                     (or (and selected-element (not= selected-element element))
                         selected-in-other?
                         (not unlocked?))
                     (merge {:filter "brightness(0.5)"}))
            :on-click (fn []
                        (if unlocked?
                          (select-element type element)
                          (ads/request-rewarded (fn []
                                                  (let [kw (case element
                                                             :wind :player/unlocked-wind-element?
                                                             :light :player/unlocked-light-element?
                                                             :toxic :player/unlocked-toxic-element?
                                                             :earth :player/unlocked-earth-element?)]
                                                    (re/insert kw true)
                                                    (select-element type element))))))
            :src path}]]))

(defn- elements [{:keys [type selected]}]
  [:div {:style {:display "flex"
                 :user-select "none"
                 :flex-direction "column"
                 :justify-content "center"
                 :align-items "center"
                 :gap "10px"
                 :padding "5px"}}
   (if selected
     [:span {:style {:font-size "20px"
                     :margin-top "5px"
                     :color "white"
                     :text-shadow "2px 2px 4px #000"}}
      (-> selected name str/capitalize)]
     [:span
      {:style {:animation "blink_little 2s ease-in-out infinite"
               :font-size (if @(subscribe [::bp/cg?]) "16px" "20px")
               :margin-top "5px"
               :color "white"
               :text-shadow "2px 2px 4px #000"}}
      (if (= type :primary)
        "Choose primary element"
        "Choose secondary element")])
   [:div {:style {:display "flex"
                  :gap "10px"}}
    [element {:element :fire
              :type type
              :path "img/texture/skill_super_nova.png"
              :unlocked? true}]
    [element {:element :ice
              :type type
              :path "img/texture/skill_ice_tornado.png"
              :unlocked? true}]
    [element {:element :wind
              :type type
              :path "img/texture/skill_wind_tornado.png"
              :unlocked? true}]
    [element {:element :light
              :type type
              :path "img/texture/skill_light_strike.png"
              :unlocked? true}]
    [element {:element :toxic
              :type type
              :path "img/texture/skill_toxic_cloud.png"
              :unlocked? true}]
    [element {:element :earth
              :type type
              :path "img/texture/skill_rock_wall.png"
              :unlocked? true}]]])

(defn- choose-primary-secondary-elements []
  [:div {:style {:display "flex"
                 :position "fixed"
                 :bottom "10px"
                 :left "50%"
                 :transform "translate(-50%, 0%)"
                 :align-items "center"
                 :box-sizing "border-box"
                 :z-index 99
                 :pointer-events "all"}}
   [:div {:style {:display "flex"
                  :gap "20px"
                  :align-items "flex-end"}}
    [elements {:type :primary
               :selected @(subscribe [::subs/player-primary-element])}]
    [elements {:type :secondary
               :selected @(subscribe [::subs/player-secondary-element])}]]])

(defn- menu-button [{:keys [text on-click logo class style disabled?]}]
  [:button
   {:class ["menu_button" class]
    :disabled disabled?
    :style (cond-> (merge {:font-size (when @(subscribe [::bp/cg?]) "14px")
                           :letter-spacing "1px"}
                          style)
             @(subscribe [::subs/mobile?]) (assoc :padding "5px"
                                                  :padding-top "5px"))
    :on-click on-click}
   text
   (when logo
     [:img {:src logo
            :style {:width (if @(subscribe [::bp/cg?]) "100px" "156px")}}])])

(defn- game-mode []
  (let [cg? @(subscribe [::bp/cg?])
        style {:font-size (if cg? "14px" "18px")}
        game-mode @(subscribe [::subs/settings-game-mode])]
    [:div {:style {:display "flex"
                   :position "fixed"
                   :bottom (if cg? "100px" "130px")
                   :left "50%"
                   :transform "translate(-50%, 0%)"
                   :align-items "center"
                   :box-sizing "border-box"}}
     [:div {:style {:display "flex"
                    :flex-direction "column"
                    :gap "6px"
                    :align-items "center"
                    :justify-content "center"}}
      [:span {:style {:color "white"
                      :font-size (if cg? "16px" "22px")
                      :text-stroke "4px"
                      :text-shadow "2px 2px 4px #000"}}
       "Game Mode"]
      [:div
       {:style {:display "flex"
                :flex-direction "row"
                :align-items "center"
                :gap "12px"
                :z-index 9
                :pointer-events "all"}}
       [menu-button {:text "Solo Deathmatch"
                     :class (when (= game-mode :solo-death-match) "selected")
                     :style style
                     :disabled? (or (not @(subscribe [::subs/player-auth-completed?]))
                                    @(subscribe [::subs/network-connecting?]))
                     :on-click (fn []
                                 (when-not (= game-mode :solo-death-match)
                                   (network/disconnect)
                                   (js/setTimeout network/connect 250)
                                   (settings/apply-setting :game-mode :solo-death-match)))}]
       [menu-button {:text "Team Deathmatch"
                     :class (when (= game-mode :team-death-match) "selected")
                     :style style
                     :disabled? (or (not @(subscribe [::subs/player-auth-completed?]))
                                    @(subscribe [::subs/network-connecting?]))
                     :on-click (fn []
                                 (when-not (= game-mode :team-death-match)
                                   (network/disconnect)
                                   (js/setTimeout network/connect 250)
                                   (settings/apply-setting :game-mode :team-death-match)))}]]]]))

(defn- kill-info []
  (let [{:keys [killed? killed-by? me? username]} @(subscribe [::subs/player-kill-info])
        cg? @(subscribe [::bp/cg?])]
    [:div#kill-info
     {:style {:display "flex"
              :position "fixed"
              :flex-direction "column"
              :gap "6px"
              :bottom (if cg? "100px" "125px")
              :left "50%"
              :transform "translate(-50%, 0%)"
              :width "500px"
              :height "100px"
              :justify-content "center"
              :align-items "center"
              :box-sizing "border-box"
              :opacity (if (or killed? killed-by?) 1 0)
              :transition "opacity 0.5s ease-in-out"}}
     (when killed?
       [:span {:style
               {:color "rgb(251, 212, 19)"
                :font-size "20px"
                :text-shadow "1px 1px 2px #000"}}
        (if @(subscribe [::subs/booster-active? :booster_coin])
          "+20 Coins"
          "+10 Coins")])
     [:div {:style {:position "relative"
                    :animation (when (or killed? killed-by?)
                                 "grow-shrink 0.5s ease-in-out")}}
      [:img {:style {:width (if cg? "250px" "300px")
                     :height "auto"}
             :src "img/texture/kill-info.png"}]
      [:div
       {:style {:position "absolute"
                :display "flex"
                :justify-content "center"
                :width "220px"
                :transform "translate(-50%, 0%)"
                :left "50%"
                :bottom "12px"}}
       [:span {:style {:font-size (if cg? "16px" "18px")
                       :font-weight "bold"
                       :font-family "sans-serif"
                       :white-space "nowrap"
                       :overflow "hidden"
                       :text-overflow "ellipsis"
                       :color (cond
                                me? "#ff5959"
                                killed? "white"
                                killed-by? "orange")}}
        (cond
          me? "Killed yourself!"
          killed? (str "Killed " username)
          killed-by? (str "Killed by " username))]]]]))

(defn- booster-main []
  (let [cg? @(subscribe [::bp/cg?])
        mobile? (or @(subscribe [::subs/mobile?]) cg?)
        text-style {:style {:color "white"
                            :letter-spacing "1px"
                            :text-shadow "1px 1px 2px #000"
                            :font-size (if mobile?
                                         "11px"
                                         (if cg? "15px" "20px"))}}
        width (if mobile?
                "140px"
                (if cg? "180px" "250px"))
        height (if mobile?
                 "80px"
                 (if cg? "90px" "120px"))]
    [:div
     {:style {:position "absolute"
              :z-index 997
              :top (if mobile? "10px" "30px")
              :right "15px"}}
     [:div {:class "shimmer-effect"
            :style {:width width
                    :height height}
            :on-click #(re/fire-rules :game/booster-panel? true)}
      [:div {:style {:display "flex"
                     :gap "4px"
                     :flex-direction "column"
                     :align-items "center"
                     :justify-content "center"
                     :height "100%"}}
       [:img {:src "img/boost-main.png"
              :style {:width (if mobile?
                               "25px"
                               (if cg? "40px" "50px"))}}]
       [:span text-style
        "GET BOOSTERS"]
       [:span text-style
        "30 MINUTES ACTIVE!"]]]
     [:div {:style {:position "relative"
                    :bottom (if mobile?
                              "85px"
                              "130px")
                    :right "5px"
                    :animation "blink_little 2s ease-in-out infinite"}}
      [:div {:style {:position "absolute"
                     :background "red"
                     :width "24px"
                     :height "24px"
                     :border-radius "100%"}}]
      [:span {:style {:position "relative"
                      :font-family "sans-serif"
                      :font-weight "bold"
                      :top "2px"
                      :left "7px"
                      :color "white"}} @(subscribe [::subs/non-active-boosters-count])]]]))

(defn servers []
  (let [cg? @(subscribe [::bp/cg?])
        mobile? (or @(subscribe [::subs/mobile?]) cg?)
        style {:font-size (if mobile?
                            "10px"
                            "14px")}
        selected-server (re/query :server/selected)]
    [:div
     {:style {:position "absolute"
              :top (if mobile? "100px" "170px")
              :right "15px"
              :display "flex"
              :flex-direction "row"
              :align-items "center"
              :justify-content "center"
              :gap "12px"
              :z-index 9
              :pointer-events "all"}}
     [:span {:style {:color "white"
                     :font-size (if mobile?
                                  "12px"
                                  "15px")
                     :text-shadow "1px 1px 2px #000"}} "Servers:"]
     [:div
      {:style {:display "flex"
               :flex-direction "row"
               :align-items "center"
               :gap "6px"
               :z-index 9
               :pointer-events "all"}}
      [menu-button {:text "USA"
                    :class (when (= selected-server :usa) "selected")
                    :style style
                    :disabled? @(subscribe [::subs/network-connecting?])
                    :on-click (fn []
                                (when-not (= selected-server :usa)
                                  (re/insert :server/selected :usa)
                                  (network/disconnect)
                                  (js/setTimeout network/connect 250)))}]
      [menu-button {:text "Germany"
                    :class (when (= selected-server :germany) "selected")
                    :style style
                    :disabled? @(subscribe [::subs/network-connecting?])
                    :on-click (fn []
                                (when-not (= selected-server :germany)
                                  (re/insert :server/selected :germany)
                                  (network/disconnect)
                                  (js/setTimeout network/connect 250)))}]]]))

(defn- join-queue-waiting-next-game-text []
  (let [click-to-play? @(subscribe [::subs/click-to-play?])
        click-to-join? @(subscribe [::subs/click-to-join?])
        requested-to-join? @(subscribe [::subs/requested-to-join?])
        time-left-to-ready-for-nex-game @(subscribe [::subs/next-game-ready-in-secs])
        time-left-to-ready-for-respawn @(subscribe [::subs/respawn-left-in-secs])
        game-loading? @(subscribe [::subs/game-loading?])
        connected? @(subscribe [::subs/network-connected?])
        connecting? @(subscribe [::subs/network-connecting?])
        error? @(subscribe [::subs/network-error?])
        player-died? @(subscribe [::subs/player-died?])
        cg? @(subscribe [::bp/cg?])
        {:keys [reason room-id]} @(subscribe [::subs/game-join-failed])]
    (when (or click-to-play?
              click-to-join?
              requested-to-join?
              time-left-to-ready-for-nex-game
              time-left-to-ready-for-respawn
              player-died?
              reason)
      [:<>
       [:div {:style (cond-> {:display "flex"
                              :position "absolute"
                              :justify-content "center"
                              :align-items "center"
                              :left "50%"
                              :transform "translate(-50%, -50%)"}

                       (not @(subscribe [::subs/game-ended?]))
                       (assoc :top "calc(50% - 50px)")

                       @(subscribe [::subs/game-ended?])
                       (assoc :bottom "150px"))}
        [:div {:style {:display "flex"
                       :flex-direction "column"
                       :gap "8px"
                       :animation (when-not (or time-left-to-ready-for-nex-game
                                                time-left-to-ready-for-respawn
                                                error?
                                                reason)
                                    "blink 1s infinite")}}
         [:span {:style {:font-size (if cg? "28px" "36px")
                         :color "white"
                         :text-stroke "4px"
                         :text-shadow "2px 2px 4px #000"}}
          (cond
            game-loading?
            "Loading..."

            connecting?
            "Connecting..."

            error?
            "Couldn't connect to the server, try another server or refresh the page"

            (not connected?)
            "Click to connect"

            (= reason :no-room)
            (str "Room id: " room-id " does not exist, please enter valid room id.")

            (= reason :not-available)
            (str "Room id: " room-id " is not available anymore. Click to join a random room.")

            (= reason :full)
            (str "Room id: " room-id " is full now. Click to try again.")

            time-left-to-ready-for-nex-game
            (str "Next game ready in " time-left-to-ready-for-nex-game " seconds")

            click-to-join?
            "Click to join"

            time-left-to-ready-for-respawn
            (str "Respawn in " time-left-to-ready-for-respawn " seconds")

            player-died?
            "Click to respawn"

            click-to-play?
            "Click to play"

            requested-to-join?
            "Waiting for next game...")]
         (when (or click-to-join? click-to-play? (not connected?))
           (when-let [room-id @(subscribe [::subs/requested-room-id])]
             [:span
              {:style {:display "flex"
                       :justify-content "center"
                       :font-size (if cg? "14px" "18px")
                       :color "white"
                       :letter-spacing "1px"
                       :text-stroke "4px"
                       :text-shadow "2px 2px 4px #000"}}
              (str "Room id: " room-id)]))

         (when (or (= reason :full)
                   (= reason :no-room)
                   (= reason :not-available))
           [:button {:on-click (fn []
                                 (re/fire-rules {:player/requested-room-id nil
                                                 :player/requested-to-join? true
                                                 :game/join-failed nil})
                                 (dispatch-pro :join-game-queue))
                     :style {:font-size "24px"
                             :padding "5px"
                             :cursor "pointer"
                             :color "white"
                             :border "2px solid white"
                             :border-radius "5px"
                             :background "#008000d1"
                             :pointer-events "all"
                             :z-index 996}}
            "or click here for random available room"])]]])))

(defn- victory-defeat-text []
  (when @(subscribe [::subs/game-ended?])
    (let [victory? @(subscribe [::subs/win?])
          until-large? (< @(subscribe [::bp/screen-width]) 1440)]
      [:div {:style {:display "flex"
                     :position "absolute"
                     :justify-content "center"
                     :align-items "center"
                     :top "calc(50% - 20px)"
                     :left "50%"
                     :transform "translate(-50%, -50%)"}}
       [:div
        {:style {:animation "fadeInScale 1s ease-in-out"}}
        [:span {:style {:font-size (if until-large? "128px" "256px")
                        :color (if victory? "white" "red")
                        :text-stroke "4px"
                        :text-shadow "2px 2px 4px #000"}}
         (if victory?
           "Victory"
           "Defeat")]]])))

(def htp-open? (r/atom false))

(defn- how-to-play []
  (when @htp-open?
    [:div
     {:on-click #(reset! htp-open? false)
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
              :z-index 999
              :transform "translate(-50%, -50%)"}}
     [:img {:class "how-to-play"
            :src "img/texture/htp.png"
            :style {:border-radius "5px"
                    :width (when @(subscribe [::bp/cg?]) "500px")
                    :background "#4982a2"
                    :user-select "none"
                    :-webkit-user-drag "none"
                    :-moz-user-drag "none"
                    :user-drag "none"
                    :height "auto"
                    :cursor "pointer"}}]]))

(defn- team-kills []
  (when (and (= :team-death-match @(subscribe [::subs/game-mode])) @(subscribe [::subs/game-started?]))
    (let [{:keys [red-team-kills blue-team-kills]} @(subscribe [::subs/team-kills])
          my-team @(subscribe [::subs/player-team])
          cg? @(subscribe [::bp/cg?])
          font-size-team (if cg? "15px" "20px")
          font-size-win (if cg? "17px" "24px")
          width-size (if cg? 75 100)
          width (if cg? (str width-size "px") (str width-size "px"))]
      [:div#team-kills
       {:style {:position "absolute"
                :display "flex"
                :justify-content "center"
                :align-items "center"
                :transform "translate(-50%, 50%)"
                :left "50%"
                :top "-10px"}}
       [:div {:style {:display "flex"
                      :align-items "center"
                      :gap "2px"}}
        [:div {:style {:width width
                       :display "flex"
                       :justify-content "flex-end"
                       :align-items "center"
                       :height "20px"
                       :padding "5px 3px"
                       :background "#b62b2b"
                       :border (when (= :red my-team) "2px solid white")}}
         [:div {:style {:width (str (/ (* red-team-kills width-size) death-match-kill-count) "px")
                        :transition "width 0.5s ease-in-out"
                        :display "flex"
                        :justify-content "flex-end"
                        :align-items "center"
                        :height "20px"
                        :padding "2px"
                        :background "#ff4c4c"}}
          [:span
           {:style {:font-size font-size-team
                    :margin-top "5px"
                    :color "white"}}
           red-team-kills]]]]
       [:span
        {:style {:font-size font-size-win
                 :color "white"
                 :padding "5px"}}
        "40 to win"]
       [:div
        [:div {:style {:width width
                       :display "flex"
                       :justify-content "flex-start"
                       :align-items "center"
                       :height "20px"
                       :padding "5px 3px"
                       :background "#2b91b6"
                       :border (when (= :blue my-team) "2px solid white")}}
         [:div {:style {:width (str (/ (* blue-team-kills width-size) death-match-kill-count) "px")
                        :transition "width 0.5s ease-in-out"
                        :display "flex"
                        :justify-content "flex-start"
                        :align-items "center"
                        :height "20px"
                        :padding "2px"
                        :background "#4fadcf"}}
          [:span
           {:style {:font-size font-size-win
                    :margin-top "5px"
                    :color "white"}}
           blue-team-kills]]]]])))

(defn format-remaining-time [remaining-ms]
  (let [total-seconds (quot remaining-ms 1000)
        minutes (quot total-seconds 60)
        seconds (mod total-seconds 60)]
    (str (utils/format "%02d" minutes) ":" (utils/format "%02d" seconds))))

(defn- solo-death-match-map-time-left []
  (when (and (= :solo-death-match @(subscribe [::subs/game-mode])) @(subscribe [::subs/game-started?]))
    (let [cg? @(subscribe [::bp/cg?])
          font-size-win (if cg? "17px" "24px")
          remaining-ms @(subscribe [::subs/map-change-remained])]
      [:div
       {:style {:position "absolute"
                :display "flex"
                :justify-content "center"
                :align-items "center"
                :transform "translate(-50%, 50%)"
                :left "50%"
                :top "-10px"}}
       [:span
        {:style {:font-size font-size-win
                 :color (if (<= remaining-ms 59999)
                          "#ff4c4c"
                          "white")
                 :padding "5px"
                 :text-shadow "2px 2px 4px #000"}}
        (str "Next Map in " (format-remaining-time remaining-ms))]])))

(defn- loading-progress-bar []
  (let [progress (atom 0)]
    (r/create-class
      {:component-did-update (fn []
                               (some-> (js/document.getElementById "loading-progress")
                                       (j/call-in [:style :setProperty] "--p" (str @progress))))
       :reagent-render
       (fn []
         (let [mana @(subscribe [::subs/loading-progress])]
           (reset! progress mana)
           [:div
            {:style {:display "flex"
                     :width "100%"
                     :align-items "center"
                     :justify-content "center"}}
            [:div
             {:class "loading_progress_wrapper"}
             [:div#loading-progress.loading_progress]]]))})))

(defn loading-screen []
  [:div {:style {:position "absolute"
                 :top "0"
                 :left "0"
                 :width "100%"
                 :height "100%"
                 :background-color "black"
                 :z-index "9999"}}
   [:div {:style {:position "relative"
                  :display "flex"
                  :flex-direction "column"
                  :align-items "center"
                  :justify-content "center"
                  :gap "15px"
                  :top "50%"
                  :left "50%"
                  :transform "translate(-50%, -50%)"}}
    [:img {:src "img/loading-screen.png"
           :style {:width "70%"}}]
    [loading-progress-bar]
    [:span {:style {:color "white"
                    :font-size "24px"}} "Beta v0.3.010"]]])

(defn request-fullscreen []
  (let [element (j/get js/document :documentElement)]
    (cond
      (.-requestFullscreen element) (.requestFullscreen element)
      (.-mozRequestFullScreen element) (.mozRequestFullScreen element)
      (.-webkitRequestFullscreen element) (.webkitRequestFullscreen element)
      (.-msRequestFullscreen element) (.msRequestFullscreen element))))

(defn- player-username-and-coins [cg?]
  [:div
   {:style {:display "flex"
            :flex-direction "row"
            :gap "4px"
            :align-items "center"}}
   [menu-button {:text @(subscribe [::subs/player-username])
                 :class "username"
                 :on-click (if cg?
                             (fn [])
                             #(re/fire-rules :game/login? true))}]
   [:span
    {:style {:color "rgb(251, 212, 19)"
             :text-shadow "2px 2px 4px #000"
             :font-size (if @(subscribe [::bp/cg?]) "14px" "20px")}}
    @(subscribe [::subs/player-coins])]])

(defn- menu []
  [:div
   {:class "menu_button_wrapper"
    :style (cond-> {:display "flex"
                    :top "50%"
                    :left "15px"
                    :transform "translate(0%,-50%)"
                    :flex-direction "column"
                    :gap (if @(subscribe [::bp/cg?]) "12px" "16px")
                    :position "absolute"
                    :justify-content "center"
                    :align-items "center"
                    :z-index 99
                    :pointer-events "all"}
             @(subscribe [::subs/mobile?]) (assoc :height "calc(100% - 100px)"
                                                  :overflow-y "scroll"
                                                  :overflow-x "hidden"
                                                  :left "30px"
                                                  :top "calc(50% - 30px)"))}
   (cond
     (and @(subscribe [::subs/sdk-cg?])
          (not @(subscribe [::subs/player-cg-user-id?])))
     [menu-button {:text "Login"
                   :on-click (fn []
                               (network/disconnect)
                               (ads/show-auth-prompt
                                 (fn []
                                   (println "CG auth prompt success!")
                                   (j/call-in js/window [:location :reload]))))
                   :disabled? (not @(subscribe [::subs/player-auth-completed?]))}]

     (and (not (str/blank? @(subscribe [::subs/player-username])))
          @(subscribe [::subs/player-auth-completed?]))
     [player-username-and-coins @(subscribe [::subs/sdk-cg?])]

     (not @(subscribe [::subs/signed-up?]))
     [menu-button {:text "Login"
                   :on-click #(re/fire-rules :game/login? true)
                   :disabled? (not @(subscribe [::subs/player-auth-completed?]))}])
   [menu-button {:text "Shop \uD83D\uDD2E"
                 :on-click #(re/fire-rules :game/shop? true)
                 :disabled? (not @(subscribe [::subs/player-auth-completed?]))}]
   [menu-button {:text "Leaderboard 👑"
                 :on-click #(re/fire-rules :game/leaderboard? true)}]
   [menu-button {:text "Play with Friends!"
                 :on-click #(re/fire-rules :game/create-room-panel? true)}]
   (when (or @(subscribe [::subs/player-room-id])
             @(subscribe [::subs/requested-room-id]))
     [menu-button {:text "Exit room"
                   :on-click (fn []
                               (re/fire-rules {:player/requested-room-id nil})
                               (network/disconnect))}])
   [menu-button {:text "Settings" :on-click #(reset! settings-open? true)}]
   #_(when (utils/wm-domain?)
       [menu-button {:text "Full screen" :on-click request-fullscreen}])
   #_[menu-button {:text "How to play?" :on-click #(reset! htp-open? true)}]
   [menu-button {:text "Spells info" :on-click #(reset! spells-open? true)}]
   (when-not @(subscribe [::subs/mobile?])
     [menu-button {:logo "img/texture/dc-logo.png"
                   :class "discord"
                   :on-click #(js/window.open "https://discord.gg/hyn2YcZfC2" "_blank")}])])

(defn- send-feedback []
  [:div
   {:style {:position "absolute"
            :right "8px"
            :bottom "75px"}}
   #_{:on-click #(js/window.open "https://discord.gg/nJx9ZCrC" "_blank")
      :style {:position "absolute"
              :display "flex"
              :cursor "pointer"
              :pointer-events "all"
              :z-index 993
              :justify-content "center"
              :align-items "center"
              :gap "8px"
              :flex-direction "column"
              :animation "blink_little 2s ease-in-out infinite"
              :right "8px"
              :bottom "25px"}}
   [:span {:style {:color "white"}} "Send feedback!"]
   #_[:img {:style {:width "50px"}
            :src "img/texture/discord.png"}]])

(defn- stunned-info []
  (let [time-left-root @(subscribe [::subs/wind-tornado-stunned-time-left])
        time-left-stun @(subscribe [::subs/stun-time-left])
        time-left-puddle @(subscribe [::subs/puddle-time-left])
        cg? @(subscribe [::bp/cg?])
        style {:style {:color "white"
                       :font-size (if cg? "24px" "36px")
                       :text-stroke "4px"
                       :text-shadow "2px 2px 4px #000"}}]
    (when (or (> time-left-root 0)
              (> time-left-stun 0)
              (> time-left-puddle 0))
      [:div {:style {:position "absolute"
                     :top "calc(50% - 100px)"
                     :left "50%"
                     :transform "translate(-50%,-50%)"
                     :display "flex"
                     :flex-direction "column"
                     :justify-content "center"
                     :align-items "center"
                     :gap "12px"}}
       (when (> time-left-root 0)
         [:span style
          (str "Rooted " (j/call (/ time-left-root 1000) :toFixed 1))])
       (when (> time-left-stun 0)
         [:span style
          (str "Stunned " (j/call (/ time-left-stun 1000) :toFixed 1))])
       (when (> time-left-puddle 0)
         [:span style
          (str "Slowed " (j/call (/ time-left-puddle 1000) :toFixed 1))])])))

(defn- logo []
  (if @(subscribe [::bp/cg?])
    [:div {:style {:position "absolute"
                   :top "70px"
                   :left "50%"
                   :transform "translate(-50%,-50%)"}}
     [:img {:src "img/logo.png"
            :style {:width "256px"}}]]
    [:div {:style {:position "absolute"
                   :top "120px"
                   :left "50%"
                   :transform "translate(-50%,-50%)"}}
     [:img {:src "img/logo.png"
            :style {:width "456px"}}]]))

(defn- close-ad-block-info []
  (re/insert :ad/adblock-modal-open? false))

(comment
  (re/insert :ad/adblock-modal-open? true))

(defn- ad-block []
  [modal {:open? @(subscribe [::subs/adblock-modal-open?])
          :header "Ad Block Detected"
          :content "You need to disable Ad Block in order to get the reward."
          :close-button-text "OK"
          :close-fn close-ad-block-info}])

(defn- banner-ads []
  (r/create-class
    {:component-did-mount (fn []
                            (re/fire-rules :ad/banner-ready? true))
     :component-will-unmount (fn []
                               (re/fire-rules :ad/banner-ready? false))
     :reagent-render
     (fn []
       (let [cg? @(subscribe [::bp/cg?])]
         [:div
          [:div
           {:id "banner-container-300-250"
            :style (cond-> {:position "absolute"
                            :background "rgba(0, 0, 0, 0.25)"
                            :pointer-events "all"
                            :z-index 4
                            :transform "translate(0%,-50%)"
                            :right "10px"
                            :top "50%"
                            :width "300px"
                            :height "250px"}
                     cg?
                     (merge {:transform "translate(0%,-50%) scale(0.8)"
                             :right "-25px"})

                     @(subscribe [::subs/mobile?])
                     (merge {:transform "translate(0%,-50%) scale(0.4)"
                             :top "calc(50% + 15px)"
                             :right "-80px"}))}]]))}))

(defn- privacy-link []
  [:div {:on-click #(js/window.open "privacy.txt" "_blank")
         :style {:position "absolute"
                 :pointer-events "all"
                 :bottom "10px"
                 :cursor "pointer"
                 :left "10px"
                 :z-index 5}}
   [:span {:style {:color "white"
                   :font-size (when @(subscribe [::subs/mobile?]) "10px")
                   :font-family "sans-serif"}}
    "Privacy Terms"]])

(defn- mobile-controller-area []
  (r/create-class
    {:component-did-mount (fn []
                            (mobile/create-controller))
     :reagent-render
     (fn []
       [:div
        {:id "mobile-controller-area"
         :style {:width "35%"
                 :height "100%"
                 :user-select "none"
                 :pointer-events "all"}}])}))

(defn- rotate-screen []
  [:div
   {:style {:display "flex"
            :justify-content "center"
            :align-items "center"
            :width "100%"
            :height "100%"
            :background "black"
            :gap "12px"}}
   (svg/rotate 50 "rotate_device")
   [:span {:style {:color "White"
                   :font-size "20px"
                   :letter-spacing "1px"}}
    "Rotate your screen"]])

(defn- game-components []
  (if (and @(subscribe [::subs/mobile?])
           @(subscribe [::subs/orientation-portrait?]))
    [rotate-screen]
    [:<>
     (if @(subscribe [::subs/current-player-focus?])
       [:<>
        (when @(subscribe [::subs/mobile?])
          [mobile-controller-area])
        [team-kills]
        [solo-death-match-map-time-left]
        [levitate]
        [players-score-board]
        (when-not @(subscribe [::subs/mobile?])
          [message-box])
        [collectable-durations]
        [who-killed-who]
        [skills-panel]
        [damage-notify]
        [fps-and-ping]
        [stunned-info]]
       [:<>
        [logo]
        [how-to-play]
        [victory-defeat-text]
        [spells-info]
        [settings]
        [game-mode]
        [choose-primary-secondary-elements]
        [menu]
        [send-feedback]
        [banner-ads]
        [privacy-link]
        (when @(subscribe [::subs/leaderboard?])
          [leaderboard-panel])
        (when @(subscribe [::subs/login-panel-open?])
          [log-in-sign-up])
        [create-room-panel]
        [booster-panel]
        (when (and @(subscribe [::subs/player-auth-completed?])
                   (not @(subscribe [::subs/all-boosters-active?])))
          [booster-main])
        #_[servers]])
     [kill-info]
     [damage-effect]
     [heartbeat-effect]
     [join-queue-waiting-next-game-text]]))

(defn- main-panel []
  (r/create-class
    {:component-did-mount
     (fn []
       (utils/register-event-listener
         js/window "keydown"
         (fn [e]
           (when (= 27 (.-keyCode e))
             (re/fire-rules {:game/shop? false
                             :game/leaderboard? false
                             :game/login? false
                             :game/create-room-panel? false
                             :game/booster-panel? false
                             :game/share-room-link-modal nil}))
           (when (= 13 (.-keyCode e))
             (let [input (js/document.getElementById "chat-input")
                   focus? (= js/document.activeElement input)
                   canvas (js/document.getElementById "renderCanvas")]
               (if focus?
                 (let [message (-> input .-value str/trim)]
                   (when-not (str/blank? message)
                     (dispatch-pro :send-message {:message (utils/clean message)}))
                   (set! (.-value input) "")
                   (.focus canvas))
                 (.focus input))
               (re/fire-rules :player/chat-focus? (= js/document.activeElement input)))))))
     :reagent-render
     (fn []
       [:div (styles/app-container (or @(subscribe [::subs/ready-to-play?])
                                       @(subscribe [::subs/shop-opened?])))
        (cond
          @(subscribe [::subs/show-loading-progress?])
          [loading-screen]

          @(subscribe [::subs/shop-opened?])
          [shop-panel]

          :else [game-components])
        [:<>
         [ad-block]]])}))

(defn dev-setup []
  (when config/dev?
    (println "dev mode")))

(defn ^:dev/before-load before-load [])

(defn ^:dev/after-load mount-root []
  (re-frame/clear-subscription-cache!)
  (let [root-el (.getElementById js/document "app")]
    (rdom/unmount-component-at-node root-el)
    (rdom/render [main-panel] root-el)))

(defn init [mobile?]
  (re-frame/dispatch-sync [::events/initialize-db mobile?])
  (re-frame/dispatch-sync [::bp/set-breakpoints
                           {:breakpoints [:cg 1130
                                          :large-monitor]
                            :debounce-ms 150}])
  (dev-setup)
  (mount-root))

(comment
  (format-remaining-time 897059)
  (mount-root)
  )
