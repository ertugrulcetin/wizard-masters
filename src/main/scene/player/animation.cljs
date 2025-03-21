(ns main.scene.player.animation
  (:require
    [main.api.animation :as api.anim]
    [main.api.sound :as api.sound]
    [main.rule-engine :as re :refer [reg-rule reg-anim reg-anim-event]]))

(reg-anim
  :player
  {"idle" {:loop? true}
   "run" {:speed 1
          :loop? true}
   "run_backward" {:speed 1
                   :loop? true}
   "run_left" {:speed 1
               :loop? true}
   "run_right" {:speed 1
                :loop? true}
   "run_forward_d_l" {:speed 1
                      :loop? true}
   "run_forward_d_r" {:speed 1
                      :loop? true}
   "run_backward_d_r" {:speed 1
                       :loop? true}
   "run_backward_d_l" {:speed 1
                       :loop? true}
   "aim_bow" {:speed 1
              :loop? true}
   "levitate" {:speed 1
               :loop? true
               :blending-speed 0.05}
   "fall_lightx" {:speed 1
                  :loop? true}
   "dash" {:speed 1.5}
   "jump" {:speed 1.5
           :from 20
           :to 85}
   "jump_up_2" {:from 10
                :speed 2}
   "jump_light" {:speed 1
                 :from 5
                 :to 20}
   "spell_right" {:speed 2
                  :from 40
                  :to 102}
   "spell_light_strike" {:speed 1}
   "spell_fire_ball" {:speed 2
                      :to 125}
   "spell_wind_tornado" {:speed 1}
   "spell_ice" {:speed 2
                :to 100}
   "ice_ball" {:speed 1
               :to 30}
   "spell_wind_slash_right" {:speed 1.5}
   "spell_wind_slash_left" {:speed 1.5}
   "spell_light_staff" {:loop? true
                        :from 30
                        :to 40}
   "spell_toxic" {:speed 0.8
                  :from 20
                  :to 50}
   "spell_toxic_cloud" {}
   "spell_rock" {:speed 3}
   "spell_rock_wall" {:speed 3}
   "react" {}
   "die" {}})

(reg-anim-event
  :player
  {:anim "run"
   :frame 15
   :fn (fn []
         (when (re/query  :player/ground?)
           (api.sound/play :sound/step-3)))})

(reg-anim-event
  :player
  {:anim "run"
   :frame 37
   :fn (fn []
         (when (re/query  :player/ground?)
           (api.sound/play :sound/step-4)))})

(reg-anim-event
  :player
  {:anim "run_backward"
   :frame 17
   :fn (fn []
         (when (re/query  :player/ground?)
           (api.sound/play :sound/step-3)))})

(reg-anim-event
  :player
  {:anim "run_backward"
   :frame 36
   :fn (fn []
         (when (re/query  :player/ground?)
           (api.sound/play :sound/step-4)))})

(reg-anim-event
  :player
  {:anim "run_left"
   :frame 17
   :fn (fn []
         (when (re/query  :player/ground?)
           (api.sound/play :sound/step-3)))})

(reg-anim-event
  :player
  {:anim "run_left"
   :frame 40
   :fn (fn []
         (when (re/query  :player/ground?)
           (api.sound/play :sound/step-4)))})

(reg-anim-event
  :player
  {:anim "run_right"
   :frame 21
   :fn (fn []
         (when (re/query  :player/ground?)
           (api.sound/play :sound/step-3)))})

(reg-anim-event
  :player
  {:anim "run_right"
   :frame 44
   :fn (fn []
         (when (re/query  :player/ground?)
           (api.sound/play :sound/step-4)))})

(reg-anim-event
  :player
  {:anim "run_backward_d_l"
   :frame 23
   :fn (fn []
         (when (re/query  :player/ground?)
           (api.sound/play :sound/step-3)))})

(reg-anim-event
  :player
  {:anim "run_backward_d_l"
   :frame 44
   :fn (fn []
         (when (re/query  :player/ground?)
           (api.sound/play :sound/step-4)))})

(reg-anim-event
  :player
  {:anim "run_backward_d_r"
   :frame 23
   :fn (fn []
         (when (re/query  :player/ground?)
           (api.sound/play :sound/step-3)))})

(reg-anim-event
  :player
  {:anim "run_backward_d_r"
   :frame 44
   :fn (fn []
         (when (re/query  :player/ground?)
           (api.sound/play :sound/step-4)))})

(reg-anim-event
  :player
  {:anim "run_forward_d_l"
   :frame 23
   :fn (fn []
         (when (re/query  :player/ground?)
           (api.sound/play :sound/step-3)))})

(reg-anim-event
  :player
  {:anim "run_forward_d_l"
   :frame 43
   :fn (fn []
         (when (re/query  :player/ground?)
           (api.sound/play :sound/step-4)))})

(reg-anim-event
  :player
  {:anim "run_forward_d_r"
   :frame 23
   :fn (fn []
         (when (re/query  :player/ground?)
           (api.sound/play :sound/step-3)))})

(reg-anim-event
  :player
  {:anim "run_forward_d_r"
   :frame 43
   :fn (fn []
         (when (re/query  :player/ground?)
           (api.sound/play :sound/step-4)))})

(def throw-light-strike)

(reg-anim-event
  :player
  {:anim "spell_light_strike"
   :frame 50
   :fn (fn []
         (when-let [pos (re/query  :player/light-strike-position)]
           (throw-light-strike pos)))})

(def throw-wind-tornado)

(reg-anim-event
  :player
  {:anim "spell_wind_tornado"
   :frame 55
   :fn (fn []
         (when-let [pos (re/query  :player/wind-tornado-position)]
           (throw-wind-tornado pos)))})

(defn- moving? []
  (let [{player-forward? :player/forward?
         player-backward? :player/backward?
         player-left? :player/left?
         player-right? :player/right?} (re/query-all)]
    (or player-forward? player-backward? player-left? player-right?)))

(defn- moving-only-backward? []
  (let [{player-forward? :player/forward?
         player-backward? :player/backward?
         player-left? :player/left?
         player-right? :player/right?} (re/query-all)]
    (and player-backward?
         (not player-forward?)
         (not player-left?)
         (not player-right?))))

(defn- moving-only-forward? []
  (let [{player-forward? :player/forward?
         player-backward? :player/backward?
         player-left? :player/left?
         player-right? :player/right?} (re/query-all)]
    (and player-forward?
         (not player-backward?)
         (not player-left?)
         (not player-right?))))

(defn- moving-only-left? []
  (let [{player-forward? :player/forward?
         player-backward? :player/backward?
         player-left? :player/left?
         player-right? :player/right?} (re/query-all)]
    (and player-left?
         (not player-forward?)
         (not player-backward?)
         (not player-right?))))

(defn- moving-only-right? []
  (let [{player-forward? :player/forward?
         player-backward? :player/backward?
         player-left? :player/left?
         player-right? :player/right?} (re/query-all)]
    (and player-right?
         (not player-forward?)
         (not player-backward?)
         (not player-left?))))

(defn- moving-only-forward-right? []
  (let [{player-forward? :player/forward?
         player-backward? :player/backward?
         player-left? :player/left?
         player-right? :player/right?} (re/query-all)]
    (and player-forward?
         player-right?
         (not player-backward?)
         (not player-left?))))

(defn- moving-only-forward-left? []
  (let [{player-forward? :player/forward?
         player-backward? :player/backward?
         player-left? :player/left?
         player-right? :player/right?} (re/query-all)]
    (and player-forward?
         player-left?
         (not player-backward?)
         (not player-right?))))

(defn- moving-only-backward-left? []
  (let [{player-forward? :player/forward?
         player-backward? :player/backward?
         player-left? :player/left?
         player-right? :player/right?} (re/query-all)]
    (and player-backward?
         player-left?
         (not player-forward?)
         (not player-right?))))

(defn- moving-only-backward-right? []
  (let [{player-forward? :player/forward?
         player-backward? :player/backward?
         player-left? :player/left?
         player-right? :player/right?} (re/query-all)]
    (and player-backward?
         player-right?
         (not player-forward?)
         (not player-left?))))

(defn- stop-all-and-start-anim-group [anim-name player-anim-groups]
  (let [anim-map (re/get-anim-map :player)
        anim-group (api.anim/find-animation-group anim-name player-anim-groups)]
    (api.anim/stop-all player-anim-groups)
    (api.anim/start (assoc (get anim-map anim-name) :anim-group anim-group))))

(reg-rule
  :player/animation-process
  {:what {:pointer-locked? {}
          :player/anim-groups {:then false}
          :player/ground? {}
          :player/jump-up? {}
          :player/died? {}
          :player/spell? {}
          :player/forward? {}
          :player/backward? {}
          :player/left? {}
          :player/right? {}
          :player/levitate? {}
          :player/current-health {}}
   :then (fn [{{player-anim-groups :player/anim-groups
                pointer-locked? :pointer-locked?
                player-died? :player/died?
                player-spell? :player/spell?
                player-spell-toxic? :player/spell-toxic?
                player-spell-rock? :player/spell-rock?
                player-spell-rock-wall? :player/spell-rock-wall?
                player-spell-toxic-cloud? :player/spell-toxic-cloud?
                player-spell-super-nova? :player/spell-super-nova?
                player-spell-ice-arrow? :player/spell-ice-arrow?
                player-ground? :player/ground?
                player-jump-up? :player/jump-up?
                player-levitate? :player/levitate?
                player-dash? :player/dash?
                player-dragging? :player/dragging?
                player-spell-ice-tornado? :player/spell-ice-tornado?
                player-spell-light-staff? :player/spell-light-staff?
                player-spell-light-strike? :player/spell-light-strike?
                player-spell-wind-slash? :player/spell-wind-slash?
                player-spell-wind-slash-count :player/spell-wind-slash-count
                player-spell-wind-tornado? :player/spell-wind-tornado?
                player-health :player/current-health
                player-freeze-end-time :player/freeze-end-time} :session}]
           (let [current-running-anim-group-name (api.anim/get-first-get-playing-anim-group-name player-anim-groups)]
             (if-not pointer-locked?
               (do
                 (cond
                   (and (= player-health 0)
                        (not (nil? current-running-anim-group-name))
                        (not= current-running-anim-group-name "die"))
                   (stop-all-and-start-anim-group "die" player-anim-groups)

                   (and (> player-health 0)
                        (not= current-running-anim-group-name "idle"))
                   (stop-all-and-start-anim-group "idle" player-anim-groups)))
               (cond
                 (and (= player-health 0)
                      (not (nil? current-running-anim-group-name))
                      (not= current-running-anim-group-name "die"))
                 (stop-all-and-start-anim-group "die" player-anim-groups)

                 (< (js/Date.now) player-freeze-end-time)
                 (api.anim/stop-all player-anim-groups)

                 (and player-ground?
                      player-jump-up?
                      (not= current-running-anim-group-name "jump_light"))
                 (stop-all-and-start-anim-group "jump_light" player-anim-groups)

                 (and (not player-levitate?)
                      (not player-ground?)
                      (not player-spell?)
                      (not player-dragging?)
                      (not player-spell-toxic?)
                      (not player-spell-rock?)
                      (not player-spell-rock-wall?)
                      (not player-spell-toxic-cloud?)
                      (not player-spell-ice-arrow?)
                      (not player-spell-super-nova?)
                      (not player-spell-ice-tornado?)
                      (not player-spell-light-staff?)
                      (not player-spell-light-strike?)
                      (not player-spell-wind-slash?)
                      (not player-spell-wind-tornado?)
                      (not player-jump-up?)
                      (not player-dash?)
                      (not= current-running-anim-group-name "fall_light"))
                 (stop-all-and-start-anim-group "fall_light" player-anim-groups)

                 (and player-levitate?
                      (not player-dash?)
                      (not player-ground?)
                      (not player-dragging?)
                      (not player-spell?)
                      (not player-spell-toxic?)
                      (not player-spell-rock?)
                      (not player-spell-rock-wall?)
                      (not player-spell-toxic-cloud?)
                      (not player-spell-ice-arrow?)
                      (not player-spell-super-nova?)
                      (not player-spell-ice-tornado?)
                      (not player-spell-light-staff?)
                      (not player-spell-light-strike?)
                      (not player-spell-wind-slash?)
                      (not player-spell-wind-tornado?)
                      (not player-jump-up?)
                      (not= current-running-anim-group-name "levitate"))
                 (stop-all-and-start-anim-group "levitate" player-anim-groups)

                 (and player-dash?
                      (not= current-running-anim-group-name "dash"))
                 (stop-all-and-start-anim-group "dash" player-anim-groups)

                 (and player-spell?
                      (not player-dragging?)
                      (not player-spell-toxic?)
                      (not player-spell-rock?)
                      (not player-spell-rock-wall?)
                      (not player-spell-toxic-cloud?)
                      (not player-spell-ice-arrow?)
                      (not player-spell-super-nova?)
                      (not player-spell-ice-tornado?)
                      (not player-spell-light-staff?)
                      (not player-spell-light-strike?)
                      (not player-spell-wind-slash?)
                      (not player-spell-wind-tornado?)
                      (not player-dash?)
                      (not= current-running-anim-group-name "spell_right"))
                 (stop-all-and-start-anim-group "spell_right" player-anim-groups)

                 (and player-spell-super-nova?
                      (not player-dragging?)
                      (not player-spell-toxic?)
                      (not player-spell-rock?)
                      (not player-spell-rock-wall?)
                      (not player-spell-toxic-cloud?)
                      (not player-spell-ice-arrow?)
                      (not player-spell?)
                      (not player-spell-ice-tornado?)
                      (not player-spell-light-staff?)
                      (not player-spell-light-strike?)
                      (not player-spell-wind-slash?)
                      (not player-spell-wind-tornado?)
                      (not player-dash?)
                      (not= current-running-anim-group-name "spell_fire_ball"))
                 (stop-all-and-start-anim-group "spell_fire_ball" player-anim-groups)

                 (and player-spell-ice-tornado?
                      (not player-dragging?)
                      (not player-spell-toxic?)
                      (not player-spell-rock?)
                      (not player-spell-rock-wall?)
                      (not player-spell-toxic-cloud?)
                      (not player-spell-ice-arrow?)
                      (not player-spell?)
                      (not player-spell-super-nova?)
                      (not player-spell-light-staff?)
                      (not player-spell-light-strike?)
                      (not player-spell-wind-slash?)
                      (not player-spell-wind-tornado?)
                      (not player-dash?)
                      (not= current-running-anim-group-name "spell_ice"))
                 (stop-all-and-start-anim-group "spell_ice" player-anim-groups)

                 (and player-spell-light-staff?
                      (not player-spell-light-strike?)
                      (not player-spell-wind-slash?)
                      (not player-spell-wind-tornado?)
                      (not player-spell-ice-tornado?)
                      (not player-dragging?)
                      (not player-spell-ice-arrow?)
                      (not player-spell-toxic?)
                      (not player-spell-rock?)
                      (not player-spell-rock-wall?)
                      (not player-spell-toxic-cloud?)
                      (not player-spell?)
                      (not player-spell-super-nova?)
                      (not player-dash?)
                      (not= current-running-anim-group-name "spell_light_staff"))
                 (stop-all-and-start-anim-group "spell_light_staff" player-anim-groups)

                 (and player-spell-light-strike?
                      (not player-spell-wind-slash?)
                      (not player-spell-wind-tornado?)
                      (not player-spell-light-staff?)
                      (not player-spell-ice-tornado?)
                      (not player-dragging?)
                      (not player-spell-ice-arrow?)
                      (not player-spell-toxic?)
                      (not player-spell-rock?)
                      (not player-spell-rock-wall?)
                      (not player-spell-toxic-cloud?)
                      (not player-spell?)
                      (not player-spell-super-nova?)
                      (not player-dash?)
                      (not= current-running-anim-group-name "spell_light_strike"))
                 (stop-all-and-start-anim-group "spell_light_strike" player-anim-groups)

                 (and player-spell-toxic?
                      (not player-spell-rock?)
                      (not player-spell-rock-wall?)
                      (not player-spell-toxic-cloud?)
                      (not player-spell-light-strike?)
                      (not player-spell-wind-slash?)
                      (not player-spell-wind-tornado?)
                      (not player-spell-light-staff?)
                      (not player-spell-ice-tornado?)
                      (not player-dragging?)
                      (not player-spell-ice-arrow?)
                      (not player-spell?)
                      (not player-spell-super-nova?)
                      (not player-dash?)
                      (not= current-running-anim-group-name "spell_toxic"))
                 (stop-all-and-start-anim-group "spell_toxic" player-anim-groups)

                 (and player-spell-rock?
                      (not player-spell-rock-wall?)
                      (not player-spell-toxic?)
                      (not player-spell-toxic-cloud?)
                      (not player-spell-light-strike?)
                      (not player-spell-wind-slash?)
                      (not player-spell-wind-tornado?)
                      (not player-spell-light-staff?)
                      (not player-spell-ice-tornado?)
                      (not player-dragging?)
                      (not player-spell-ice-arrow?)
                      (not player-spell?)
                      (not player-spell-super-nova?)
                      (not player-dash?)
                      (not= current-running-anim-group-name "spell_rock"))
                 (stop-all-and-start-anim-group "spell_rock" player-anim-groups)

                 (and player-spell-rock-wall?
                      (not player-spell-rock?)
                      (not player-spell-toxic?)
                      (not player-spell-toxic-cloud?)
                      (not player-spell-light-strike?)
                      (not player-spell-wind-slash?)
                      (not player-spell-wind-tornado?)
                      (not player-spell-light-staff?)
                      (not player-spell-ice-tornado?)
                      (not player-dragging?)
                      (not player-spell-ice-arrow?)
                      (not player-spell?)
                      (not player-spell-super-nova?)
                      (not player-dash?)
                      (not= current-running-anim-group-name "spell_rock_wall"))
                 (stop-all-and-start-anim-group "spell_rock_wall" player-anim-groups)

                 (and player-spell-toxic-cloud?
                      (not player-spell-toxic?)
                      (not player-spell-rock?)
                      (not player-spell-rock-wall?)
                      (not player-spell-light-strike?)
                      (not player-spell-wind-slash?)
                      (not player-spell-wind-tornado?)
                      (not player-spell-light-staff?)
                      (not player-spell-ice-tornado?)
                      (not player-dragging?)
                      (not player-spell-ice-arrow?)
                      (not player-spell?)
                      (not player-spell-super-nova?)
                      (not player-dash?)
                      (not= current-running-anim-group-name "spell_toxic_cloud"))
                 (stop-all-and-start-anim-group "spell_toxic_cloud" player-anim-groups)

                 (and player-spell-ice-arrow?
                      (not player-spell-toxic?)
                      (not player-spell-rock?)
                      (not player-spell-rock-wall?)
                      (not player-spell-toxic-cloud?)
                      (not player-dragging?)
                      (not player-spell-ice-tornado?)
                      (not player-spell?)
                      (not player-spell-super-nova?)
                      (not player-spell-light-staff?)
                      (not player-spell-light-strike?)
                      (not player-spell-wind-slash?)
                      (not player-spell-wind-tornado?)
                      (not player-dash?)
                      (not= current-running-anim-group-name "ice_ball"))
                 (stop-all-and-start-anim-group "ice_ball" player-anim-groups)

                 (and player-spell-wind-slash?
                      (not player-spell-wind-tornado?)
                      (not player-spell-ice-arrow?)
                      (not player-spell-toxic?)
                      (not player-spell-rock?)
                      (not player-spell-rock-wall?)
                      (not player-spell-toxic-cloud?)
                      (not player-dragging?)
                      (not player-spell-ice-tornado?)
                      (not player-spell?)
                      (not player-spell-super-nova?)
                      (not player-spell-light-staff?)
                      (not player-spell-light-strike?)
                      (not player-dash?)
                      (and (not= current-running-anim-group-name "spell_wind_slash_right")
                           (not= current-running-anim-group-name "spell_wind_slash_left")))
                 (if (even? player-spell-wind-slash-count)
                   (stop-all-and-start-anim-group "spell_wind_slash_left" player-anim-groups)
                   (stop-all-and-start-anim-group "spell_wind_slash_right" player-anim-groups))

                 (and player-spell-wind-tornado?
                      (not player-spell-wind-slash?)
                      (not player-spell-ice-arrow?)
                      (not player-spell-toxic?)
                      (not player-spell-rock?)
                      (not player-spell-rock-wall?)
                      (not player-spell-toxic-cloud?)
                      (not player-dragging?)
                      (not player-spell-ice-tornado?)
                      (not player-spell?)
                      (not player-spell-super-nova?)
                      (not player-spell-light-staff?)
                      (not player-spell-light-strike?)
                      (not player-dash?)
                      (not= current-running-anim-group-name "spell_wind_tornado"))
                 (stop-all-and-start-anim-group "spell_wind_tornado" player-anim-groups)

                 (and player-dragging?
                      (not player-spell-ice-arrow?)
                      (not player-spell-toxic?)
                      (not player-spell-rock?)
                      (not player-spell-rock-wall?)
                      (not player-spell-toxic-cloud?)
                      (not player-spell-ice-tornado?)
                      (not player-spell?)
                      (not player-spell-super-nova?)
                      (not player-spell-light-staff?)
                      (not player-spell-light-strike?)
                      (not player-spell-wind-slash?)
                      (not player-spell-wind-tornado?)
                      (not player-dash?)
                      (not= current-running-anim-group-name "aim_bow"))
                 (stop-all-and-start-anim-group "aim_bow" player-anim-groups)

                 (and (moving?)
                      (moving-only-forward?)
                      (not= current-running-anim-group-name "run")
                      (not player-jump-up?)
                      (not player-dash?)
                      (not player-spell?)
                      (not player-dragging?)
                      (not player-spell-ice-arrow?)
                      (not player-spell-toxic?)
                      (not player-spell-rock?)
                      (not player-spell-rock-wall?)
                      (not player-spell-toxic-cloud?)
                      (not player-spell-super-nova?)
                      (not player-spell-ice-tornado?)
                      (not player-spell-light-staff?)
                      (not player-spell-light-strike?)
                      (not player-spell-wind-slash?)
                      (not player-spell-wind-tornado?)
                      player-ground?)
                 (stop-all-and-start-anim-group "run" player-anim-groups)

                 (and (moving?)
                      (moving-only-backward?)
                      (not= current-running-anim-group-name "run_backward")
                      (not player-jump-up?)
                      (not player-dash?)
                      (not player-spell?)
                      (not player-dragging?)
                      (not player-spell-ice-arrow?)
                      (not player-spell-toxic?)
                      (not player-spell-rock?)
                      (not player-spell-rock-wall?)
                      (not player-spell-toxic-cloud?)
                      (not player-spell-super-nova?)
                      (not player-spell-ice-tornado?)
                      (not player-spell-light-staff?)
                      (not player-spell-light-strike?)
                      (not player-spell-wind-slash?)
                      (not player-spell-wind-tornado?)
                      player-ground?)
                 (stop-all-and-start-anim-group "run_backward" player-anim-groups)

                 (and (moving?)
                      (moving-only-left?)
                      (not= current-running-anim-group-name "run_left")
                      (not player-jump-up?)
                      (not player-dash?)
                      (not player-spell?)
                      (not player-dragging?)
                      (not player-spell-ice-arrow?)
                      (not player-spell-toxic?)
                      (not player-spell-rock?)
                      (not player-spell-rock-wall?)
                      (not player-spell-toxic-cloud?)
                      (not player-spell-super-nova?)
                      (not player-spell-ice-tornado?)
                      (not player-spell-light-staff?)
                      (not player-spell-light-strike?)
                      (not player-spell-wind-slash?)
                      (not player-spell-wind-tornado?)
                      player-ground?)
                 (stop-all-and-start-anim-group "run_left" player-anim-groups)

                 (and (moving?)
                      (moving-only-right?)
                      (not= current-running-anim-group-name "run_right")
                      (not player-jump-up?)
                      (not player-dash?)
                      (not player-spell?)
                      (not player-dragging?)
                      (not player-spell-ice-arrow?)
                      (not player-spell-toxic?)
                      (not player-spell-rock?)
                      (not player-spell-rock-wall?)
                      (not player-spell-toxic-cloud?)
                      (not player-spell-super-nova?)
                      (not player-spell-ice-tornado?)
                      (not player-spell-light-staff?)
                      (not player-spell-light-strike?)
                      (not player-spell-wind-slash?)
                      (not player-spell-wind-tornado?)
                      player-ground?)
                 (stop-all-and-start-anim-group "run_right" player-anim-groups)

                 (and (moving?)
                      (moving-only-forward-left?)
                      (not= current-running-anim-group-name "run_forward_d_l")
                      (not player-jump-up?)
                      (not player-dash?)
                      (not player-spell?)
                      (not player-dragging?)
                      (not player-spell-ice-arrow?)
                      (not player-spell-toxic?)
                      (not player-spell-rock?)
                      (not player-spell-rock-wall?)
                      (not player-spell-toxic-cloud?)
                      (not player-spell-super-nova?)
                      (not player-spell-ice-tornado?)
                      (not player-spell-light-staff?)
                      (not player-spell-light-strike?)
                      (not player-spell-wind-slash?)
                      (not player-spell-wind-tornado?)
                      player-ground?)
                 (stop-all-and-start-anim-group "run_forward_d_l" player-anim-groups)

                 (and (moving?)
                      (moving-only-forward-right?)
                      (not= current-running-anim-group-name "run_forward_d_r")
                      (not player-jump-up?)
                      (not player-dash?)
                      (not player-spell?)
                      (not player-dragging?)
                      (not player-spell-ice-arrow?)
                      (not player-spell-toxic?)
                      (not player-spell-rock?)
                      (not player-spell-rock-wall?)
                      (not player-spell-toxic-cloud?)
                      (not player-spell-super-nova?)
                      (not player-spell-ice-tornado?)
                      (not player-spell-light-staff?)
                      (not player-spell-light-strike?)
                      (not player-spell-wind-slash?)
                      (not player-spell-wind-tornado?)
                      player-ground?)
                 (stop-all-and-start-anim-group "run_forward_d_r" player-anim-groups)

                 (and (moving?)
                      (moving-only-backward-left?)
                      (not= current-running-anim-group-name "run_backward_d_l")
                      (not player-jump-up?)
                      (not player-dash?)
                      (not player-spell?)
                      (not player-dragging?)
                      (not player-spell-ice-arrow?)
                      (not player-spell-toxic?)
                      (not player-spell-rock?)
                      (not player-spell-rock-wall?)
                      (not player-spell-toxic-cloud?)
                      (not player-spell-super-nova?)
                      (not player-spell-ice-tornado?)
                      (not player-spell-light-staff?)
                      (not player-spell-light-strike?)
                      (not player-spell-wind-slash?)
                      (not player-spell-wind-tornado?)
                      player-ground?)
                 (stop-all-and-start-anim-group "run_backward_d_l" player-anim-groups)

                 (and (moving?)
                      (moving-only-backward-right?)
                      (not= current-running-anim-group-name "run_backward_d_r")
                      (not player-jump-up?)
                      (not player-dash?)
                      (not player-spell?)
                      (not player-dragging?)
                      (not player-spell-ice-arrow?)
                      (not player-spell-toxic?)
                      (not player-spell-rock?)
                      (not player-spell-rock-wall?)
                      (not player-spell-toxic-cloud?)
                      (not player-spell-super-nova?)
                      (not player-spell-ice-tornado?)
                      (not player-spell-light-staff?)
                      (not player-spell-light-strike?)
                      (not player-spell-wind-slash?)
                      (not player-spell-wind-tornado?)
                      player-ground?)
                 (stop-all-and-start-anim-group "run_backward_d_r" player-anim-groups)

                 (and player-ground?
                      (not (moving?))
                      (not player-dash?)
                      (not player-spell?)
                      (not player-spell-ice-arrow?)
                      (not player-spell-toxic?)
                      (not player-spell-rock?)
                      (not player-spell-rock-wall?)
                      (not player-spell-toxic-cloud?)
                      (not player-dragging?)
                      (not player-spell-super-nova?)
                      (not player-spell-ice-tornado?)
                      (not player-spell-light-staff?)
                      (not player-spell-light-strike?)
                      (not player-spell-wind-slash?)
                      (not player-spell-wind-tornado?)
                      (not player-died?)
                      (not player-jump-up?)
                      (not= current-running-anim-group-name "idle"))
                 (stop-all-and-start-anim-group "idle" player-anim-groups)))))})
