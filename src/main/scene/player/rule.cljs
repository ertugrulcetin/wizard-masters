(ns main.scene.player.rule
  (:require
    [applied-science.js-interop :as j]
    [clojure.string :as str]
    [clojure.walk :as walk]
    [main.ads :as ads]
    [main.api.animation :as api.anim]
    [main.api.asset :as api.asset]
    [main.api.camera :as api.camera]
    [main.api.constant :as api.const]
    [main.api.core :as api.core :refer [v3]]
    [main.api.material :as api.material]
    [main.api.mesh :as api.mesh]
    [main.api.particle :as api.particle]
    [main.api.physics :as api.physics]
    [main.api.sound :as api.sound]
    [main.api.sound :refer [reg-sound]]
    [main.api.tween :as api.tween]
    [main.common :as common]
    [main.config :as config]
    [main.crisp :as crisp]
    [main.rule-engine :as re :refer [reg-rule reg-anim-event]]
    [main.scene.network :as network :refer [dispatch-pro dispatch-pro-sync dispatch-pro-response]]
    [main.scene.player.animation :as player.animation]
    [main.scene.player.map :as player.map]
    [main.scene.player.model :as player.model]
    [main.scene.player.shop :as player.shop]
    [main.scene.player.sound]
    [main.scene.utils :as scene.utils]
    [main.utils :as utils])
  (:require-macros
    [main.macros :as m]))

(def kill-info-duration 2500)
(def total-mana 100)

(def mana-costs
  {:spell 15
   :rock 15
   :toxic 15
   :ice-arrow 15
   :wind-slash 15
   :light-staff 50})

(def cooldowns
  {:cooldown/spell-toxic-cloud {:duration 15500}
   :cooldown/spell-super-nova {:duration 15500}
   :cooldown/spell-rock-wall {:duration 15500}
   :cooldown/spell-ice-tornado {:duration 15500}
   :cooldown/spell-wind-tornado {:duration 15500}
   :cooldown/spell-light-strike {:duration 15500}
   :cooldown/roll {:duration 4500}})

(defn- reset-cooldowns []
  (doseq [[k v] cooldowns]
    (re/insert (keyword (str "cooldown/" (name k))) v)))

(defn pressed-forward? []
  (let [{:keys [keys-pressed game/mobile? player/js-angle]} (re/query-all)]
    (if mobile?
      (or (boolean (and js-angle (<= 67.5 js-angle 112.5)))
          (boolean (and js-angle (<= 22.5 js-angle 67.49)))
          (boolean (and js-angle (<= 112.5 js-angle 157.49))))
      (or (keys-pressed "KeyW")
          (keys-pressed "ArrowUp")))))

(defn pressed-backward? []
  (let [{:keys [keys-pressed game/mobile? player/js-angle]} (re/query-all)]
    (if mobile?
      (or (boolean (and js-angle (<= 247.5 js-angle 292.49)))
          (boolean (and js-angle (<= 202.5 js-angle 247.49)))
          (boolean (and js-angle (<= 292.5 js-angle 337.49))))
      (or (keys-pressed "KeyS")
          (keys-pressed "ArrowDown")))))

(defn pressed-left? []
  (let [{:keys [keys-pressed game/mobile? player/js-angle]} (re/query-all)]
    (if mobile?
      (or (boolean (and js-angle (<= 157.5 js-angle 202.49)))
          (boolean (and js-angle (<= 112.5 js-angle 157.49)))
          (boolean (and js-angle (<= 202.5 js-angle 247.49))))
      (or (keys-pressed "KeyA")
          (keys-pressed "ArrowLeft")))))

(defn pressed-right? []
  (let [{:keys [keys-pressed game/mobile? player/js-angle]} (re/query-all)]
    (if mobile?
      (or (boolean (and js-angle (or (<= js-angle 22.29)
                                     (>= js-angle 337.5))))
          (boolean (and js-angle (<= 22.5 js-angle 67.49)))
          (boolean (and js-angle (<= 292.5 js-angle 337.49))))
      (or (keys-pressed "KeyD")
          (keys-pressed "ArrowRight")))))

(defn pressed-space? []
  (let [keys-pressed (re/query :keys-pressed)]
    (keys-pressed "Space")))

(defn- get-forward-dir [camera forward-temp]
  (let [forward (cond-> 0
                  (pressed-forward?) inc
                  (pressed-backward?) dec)
        forward-dir (j/call camera :getDirection api.const/v3-forward)]
    (api.core/set-v3 forward-temp (* forward (j/get forward-dir :x)) 0 (* forward (j/get forward-dir :z)))))

(defn- get-right-dir [camera right-temp]
  (let [right (cond-> 0
                (pressed-right?) inc
                (pressed-left?) dec)
        right-dir (j/call camera :getDirection api.const/v3-right)]
    (api.core/set-v3 right-temp (* right (j/get right-dir :x)) 0 (* right (j/get right-dir :z)))))

(defn- get-char-forward-dir-with-right [{:keys [dt camera forward-temp right-temp result-temp]}]
  (let [forward-dir (get-forward-dir camera forward-temp)
        right-dir (get-right-dir camera right-temp)
        x (+ (j/get forward-dir :x) (j/get right-dir :x))
        z (+ (j/get forward-dir :z) (j/get right-dir :z))
        result (api.core/normalize (api.core/set-v3 result-temp (* x dt) 0 (* z dt)))
        yaw (js/Math.atan2 (j/get result :x) (j/get result :z))
        offset (* 2 js/Math.PI)]
    [yaw offset result]))

(defn- get-camera-dir [camera result-temp]
  (let [forward-dir (j/call camera :getDirection api.const/v3-forward)
        x (j/get forward-dir :x)
        z (j/get forward-dir :z)
        result (api.core/normalize (api.core/set-v3 result-temp x 0 z))
        yaw (js/Math.atan2 (j/get result :x) (j/get result :z))
        offset (* 2 js/Math.PI)]
    [yaw offset result]))

(defn- get-y-deceleration [v-ref-y]
  (let [air-time (re/query :player/air-time)
        gravity (re/query :player/gravity)
        dragging? (re/query :player/dragging?)
        deceleration (* gravity (* (api.core/get-anim-ratio) 1.5) (/ air-time 4))
        deceleration (Math/min 0.8 deceleration)]
    (if dragging?
      0
      deceleration)))

(defn freezing? []
  (< (js/Date.now) (re/query :player/freeze-end-time)))

(defn wind-stunned? []
  (< (js/Date.now) (re/query :player/wind-tornado-stunned-end-time)))

(reg-rule
  :player/click-to-connect
  {:what {:pointer-locked? {}
          :mouse/left-click? {}}
   :when (fn [{{:keys [network/connected?]} :session}]
           (not connected?))
   :then (fn []
           (network/connect))})

(defn- init-elements-if-not-available []
  (let [primary-element (re/query :player/primary-element)
        secondary-element (re/query :player/secondary-element)
        elements #{:fire :ice :wind :light :toxic :earth}]
    (cond
      (and (nil? primary-element)
           (nil? secondary-element))
      (do
        (re/insert {:player/primary-element :fire
                    :player/secondary-element :ice}))

      (nil? primary-element)
      (re/insert :player/primary-element (first (disj elements secondary-element :wind :light :toxic :earth)))

      (nil? secondary-element)
      (re/insert :player/secondary-element (first (disj elements primary-element :wind :light :toxic :earth))))))

(reg-rule
  :player/join-game-queue
  {:what {:pointer-locked? {}
          :mouse/left-click? {}}
   :then (fn [{{:keys [pointer-locked?
                       game/shop?
                       network/connected?
                       player/requested-to-join?
                       player/room-id
                       player/requested-room-id
                       game/started?
                       game/end-time
                       settings]} :session}]
           (when (and pointer-locked?
                      connected?
                      (not shop?)
                      (not started?)
                      (not room-id)
                      (or (nil? end-time)
                          (>= (- (js/Date.now) end-time) common/new-game-after-milli-secs))
                      (not requested-to-join?))
             (println "Requested to join!")
             (init-elements-if-not-available)
             (re/insert {:player/requested-to-join? true
                         :game/join-failed nil})
             (dispatch-pro :join-game-queue {:requested-room-id requested-room-id
                                             :create-room? (ads/instant-join?)
                                             :mode (:game-mode settings :solo-death-match)})))})

(defn- has-time-passed? [action-time duration]
  (>= (js/Date.now) (+ action-time duration)))

(defn- detach-camera-from-target []
  (let [{:keys [camera player/offset-box player/temp-offset-box]} (re/query-all)]
    (j/assoc! temp-offset-box :position (api.core/get-pos offset-box true))
    (j/call camera :setTarget temp-offset-box)))

(defn- attach-camera-to-target []
  (let [{:keys [camera player/offset-box]} (re/query-all)]
    (j/call camera :setTarget offset-box)
    (j/assoc! camera
              :radius api.camera/default-radius
              :alpha api.camera/init-beta-alpha
              :beta api.camera/init-beta-alpha)))

(defn- stop-light-staff-effects [particle-light-staff]
  (re/insert {:player/spell-light-staff? false})
  (when (api.sound/playing? :sound/electric-light-staff-player)
    (api.sound/stop :sound/electric-light-staff-player))
  (some-> particle-light-staff api.particle/stop))

(defn- clear-keys []
  (re/fire-rules {:keys-pressed #{}
                  :mouse/right-click? false
                  :mouse/left-click? false
                  :player/mobile-jump-click? false
                  :player/mobile-fire-click? false
                  :player/mobile-fire-sorcery-click? false
                  :player/mobile-ice-click? false
                  :player/mobile-ice-sorcery-click? false
                  :player/mobile-wind-click? false
                  :player/mobile-wind-sorcery-click? false
                  :player/mobile-light-click? false
                  :player/mobile-light-sorcery-click? false
                  :player/mobile-toxic-click? false
                  :player/mobile-toxic-sorcery-click? false
                  :player/mobile-earth-click? false
                  :player/mobile-earth-sorcery-click? false
                  :player/fire-prev-key-sorcery-pressed? false
                  :player/ice-prev-key-sorcery-pressed? false
                  :player/air-prev-key-sorcery-pressed? false
                  :player/light-prev-key-sorcery-pressed? false}))

(defn- apply-things-when-game-stop []
  (let [{:keys [particle/speed-line image/crosshair]} (re/query-all)]
    (when-not (j/get speed-line :stopped?)
      (api.particle/stop speed-line))
    (j/assoc! crosshair :isVisible false)
    (js/setTimeout #(ads/request-midgame) 1500)
    (crisp/close)
    (crisp/show)
    (stop-light-staff-effects (re/query :player/particle-light-staff))
    (clear-keys)))

(defn- apply-things-when-game-start []
  (let [{:keys [image/crosshair]} (re/query-all)]
    (j/assoc! crosshair :isVisible true)
    (crisp/close)
    (crisp/hide)
    (ads/clear-all-banners)
    (player.shop/update-equipped (re/query :player/model))
    (ads/show-invite-button)))

(reg-rule
  :ad/refresh-banner-ads
  {:what {:ad/banner-ready? {}
          :ad/init? {}}
   :then (fn []
           (js/setTimeout
             (fn []
               (let [{game-started? :player/game-started?
                      video-running? :ad/video-running?
                      banner-ready? :ad/banner-ready?
                      ad-init? :ad/init?
                      last-time-banner-requested :ad/last-time-banner-requested} (re/query-all)]
                 (when (and ad-init?
                            (not game-started?)
                            (not video-running?)
                            banner-ready?
                            (or (nil? last-time-banner-requested)
                                (> (- (js/Date.now) last-time-banner-requested) (* 60 1000))))
                   (ads/request-banners))))
             500))})

(reg-rule
  :ad/refresh-banner-ads-after-video-ad
  {:what {:ad/video-running? {}}
   :then (fn [{{game-started? :player/game-started?
                video-running? :ad/video-running?
                ad-init? :ad/init?
                banner-ready? :ad/banner-ready?
                last-time-banner-requested :ad/last-time-banner-requested} :session}]
           (when (and ad-init?
                      (not game-started?)
                      (not video-running?)
                      banner-ready?
                      (or (nil? last-time-banner-requested)
                          (> (- (js/Date.now) last-time-banner-requested) (* 60 1000))))
             (ads/request-banners)))})

(defn- stop-gameplay []
  (println "Game stop")
  (re/fire-rules {:player/game-started? false
                  :player/paused? true
                  :player/paused-time (js/Date.now)})
  (ads/gameplay-stop)
  (apply-things-when-game-stop))

(reg-rule
  :player/mobile-stop-game
  {:what {:mobile/stop-game? {}}
   :then (fn [{{stop-game? :mobile/stop-game?} :session}]
           (when stop-game?
             (stop-gameplay)))})

(reg-rule
  :player/game-focus
  {:what {:pointer-locked? {}
          :window/focus? {}
          :tab/focus? {}
          :player/current-health {}}
   :then (fn [{{pointer-locked? :pointer-locked?
                window-focused? :window/focus?
                tab-focused? :tab/focus?
                current-health :player/current-health
                shop? :game/shop?
                game-started? :player/game-started?
                player-room-id :player/room-id
                player-died? :player/died?
                player-died-time :player/died-time
                player-paused? :player/paused?
                player-paused-time :player/paused-time
                player-respawn-duration :player/respawn-duration
                started? :game/started?} :session}]
           (when (and pointer-locked?
                      window-focused?
                      tab-focused?
                      started?
                      player-room-id
                      (> current-health 0)
                      (not shop?)
                      (not player-paused?)
                      (not player-died?)
                      (or (nil? player-died-time)
                          (has-time-passed? player-died-time player-respawn-duration))
                      (or (nil? player-paused-time)
                          (has-time-passed? player-paused-time player-respawn-duration))
                      (not game-started?))
             (do
               (println "Game start")
               (re/fire-rules {:player/game-started? true})
               (ads/gameplay-start)
               (apply-things-when-game-start)))

           (when (and (or shop?
                          (not pointer-locked?)
                          (not window-focused?)
                          (not tab-focused?)
                          (not started?)
                          (not player-room-id)
                          (not (> current-health 0)))
                      game-started?)
             (stop-gameplay)))})

(reg-rule
  :player/set-game-focus
  {:what {:player/game-started? {:when-value-change? true}}
   :then (fn [{{game-started? :player/game-started?
                player-model :player/model
                player-respawn-duration :player/respawn-duration} :session}]
           (if false #_config/dev?
               (do
                 (re/insert {:player/focus? true})
                 (dispatch-pro :set-game-focus {:focus? true}))
               (if game-started?
                 (do
                   (re/insert {:player/focus? true})
                   (dispatch-pro :set-game-focus {:focus? true}))
                 (do
                   (js/setTimeout
                     #(do
                        (re/insert {:player/focus? false})
                        (api.core/set-enabled player-model false)
                        (dispatch-pro :set-game-focus {:focus? false}))
                     (* player-respawn-duration 0.6))))))})

(reg-rule
  :player/wind-tornado-stun-check
  {:what {:dt {}
          :player/capsule {:then false}
          :player/wind-tornado-stunned-end-time {:then false}}
   :when (fn [{{game-started? :player/game-started?
                wind-tornado-stunned-end-time :player/wind-tornado-stunned-end-time} :session}]
           (and game-started? wind-tornado-stunned-end-time))
   :then (fn [{{capsule :player/capsule
                wind-tornado-stunned-end-time :player/wind-tornado-stunned-end-time} :session}]
           (cond
             (and (< (js/Date.now) wind-tornado-stunned-end-time)
                  (not= 0 (api.physics/get-gravity-factor capsule)))
             (api.physics/set-gravity-factor capsule 0)

             (> (js/Date.now) wind-tornado-stunned-end-time)
             (do
               (api.physics/set-gravity-factor capsule player.model/gravity-factor)
               (re/insert {:player/wind-tornado-stunned-end-time nil}))))})

(defn casting-spell? [& {:keys [except]}]
  (let [{:keys [player/spell?
                player/spell-toxic?
                player/spell-toxic-cloud?
                player/spell-rock?
                player/spell-rock-wall?
                player/spell-ice-tornado?
                player/spell-super-nova?
                player/spell-ice-arrow?
                player/spell-wind-slash?
                player/spell-wind-tornado?
                player/spell-light-strike?
                player/spell-light-staff?]} (dissoc (or (re/query-all)
                                                        (re/query-all)) except)]
    (or spell?
        spell-super-nova?
        spell-rock?
        spell-rock-wall?
        spell-toxic?
        spell-toxic-cloud?
        spell-ice-arrow?
        spell-ice-tornado?
        spell-wind-slash?
        spell-wind-tornado?
        spell-light-strike?
        spell-light-staff?)))

(reg-rule
  :player/move
  {:disabled? false
   :locals {:forward-temp (v3)
            :right-temp (v3)
            :result-temp (v3)
            :speed-temp (v3)
            :v-ref (v3)}
   :what {:dt {}
          :pointer-locked? {}
          :player/model {}
          :player/capsule {}
          :player/anim-groups {}
          :camera {}
          :keys-pressed {:then false}}
   :when (fn [{{:keys [player/current-health
                       player/dash?
                       player/game-started?
                       player/last-time-got-wind-slash-hit
                       player/dash-finished-time]} :session}]
           (and game-started?
                (not (freezing?))
                (not (wind-stunned?))
                (not (and last-time-got-wind-slash-hit
                          (<= (js/Date.now) (+ last-time-got-wind-slash-hit 750))))
                (or (nil? dash-finished-time)
                    (> (js/Date.now) dash-finished-time))
                (> current-health 0)
                (not dash?)))
   :then (fn [{{:keys [dt camera]
                current-time :current-time
                mobile? :game/mobile?
                js-distance :player/js-distance
                player-capsule :player/capsule
                player-dragging? :player/dragging?
                puddle-end-time :player/puddle-end-time
                player-fast-forward-speed :player/fast-forward-speed
                player-normal-speed :player/normal-speed} :session
               {:keys [forward-temp right-temp result-temp speed-temp v-ref]} :locals}]
           (if (or (pressed-forward?)
                   (pressed-left?)
                   (pressed-backward?)
                   (pressed-right?))
             (let [speed (if (< current-time puddle-end-time)
                           5
                           (cond
                             (player.animation/moving-only-forward?)
                             player-fast-forward-speed

                             (or (player.animation/moving-only-forward-left?)
                                 (player.animation/moving-only-forward-right?))
                             (dec player-fast-forward-speed)

                             :else player-normal-speed))
                   speed (if player-dragging? (* 0.5 speed) speed)
                   speed (if mobile?
                           (* speed (/ (or js-distance 0) 50))
                           speed)
                   _ (api.physics/get-linear-velocity-to-ref player-capsule v-ref)
                   v-ref-y (j/get v-ref :y)
                   y-deceleration (get-y-deceleration v-ref-y)
                   [yaw offset result] (get-char-forward-dir-with-right {:dt dt
                                                                         :camera camera
                                                                         :forward-temp forward-temp
                                                                         :right-temp right-temp
                                                                         :result-temp result-temp})]
               (api.physics/set-linear-velocity player-capsule
                                                (api.core/set-v3 speed-temp
                                                                 (* speed (m/get result :x))
                                                                 (- v-ref-y y-deceleration)
                                                                 (* speed (m/get result :z)))))
             (let [_ (api.physics/get-linear-velocity-to-ref player-capsule v-ref)
                   v-ref-y (j/get v-ref :y)
                   y-deceleration (get-y-deceleration v-ref-y)]
               (api.physics/set-linear-velocity player-capsule (api.core/set-v3 speed-temp 0 (- v-ref-y y-deceleration) 0)))))})

(reg-rule
  :player/rotation
  {:locals {:forward-temp (v3)
            :result-temp (v3)
            :speed-temp (v3)
            :v-ref (v3)}
   :what {:dt {}
          :camera {}
          :player/model {}
          :player/spell? {}}
   :when (fn [{{:keys [player/game-started?]} :session}]
           game-started?)
   :then (fn [{{dt :dt
                camera :camera
                player-model :player/model} :session
               {:keys [forward-temp result-temp]} :locals}]
           (let [[yaw offset] (api.camera/get-char-forward-dir {:camera camera
                                                                :forward-temp forward-temp
                                                                :result-temp result-temp})]
             (m/assoc! player-model :rotation.y (+ yaw offset))))})

(reg-rule
  :player/key-press-check-for-movement
  {:what {:keys-pressed {}}
   :then (fn []
           (re/fire-rules {:player/forward? (boolean (pressed-forward?))
                           :player/backward? (boolean (pressed-backward?))
                           :player/left? (boolean (pressed-left?))
                           :player/right? (boolean (pressed-right?))
                           :player/jumping? (boolean (pressed-space?))}))})

(reg-rule
  :player/mobile-key-press-check-for-movement
  {:what {:dt {}}
   :then (fn [{{mobile? :game/mobile?} :session}]
           (when mobile?
             (re/fire-rules {:player/forward? (boolean (pressed-forward?))
                             :player/backward? (boolean (pressed-backward?))
                             :player/left? (boolean (pressed-left?))
                             :player/right? (boolean (pressed-right?))
                             :player/jumping? (boolean (pressed-space?))})))})

(reg-rule
  :player/jump
  {:locals {:jump-vec (v3)}
   :what {:pointer-locked? {:then false}
          :player/ground? {:then false}
          :player/spell? {:then false}
          :player/spell-super-nova? {:then false}
          :player/spell-ice-tornado? {:then false}
          :player/capsule {}
          :player/anim-groups {}
          :keys-pressed {}
          :player/mobile-jump-click? {}
          :keys-was-pressed {:then false}
          :player/jump-up? {:then false}}
   :when (fn [{{:keys [player/game-started?
                       player/mobile-jump-click?
                       player/ground?
                       player/dash?
                       player/jump-up?
                       player/current-running-anim-group
                       player/current-health]} :session}]
           (and game-started?
                (nil? current-running-anim-group)
                (or (re/key-is-pressed? "Space") mobile-jump-click?)
                ground?
                (not (freezing?))
                (not (wind-stunned?))
                (not jump-up?)
                (not dash?)
                (> current-health 0)))
   :then (fn [{{player-capsule :player/capsule
                player-jump-force :player/jump-force} :session
               {:keys [jump-vec]} :locals}]
           (j/assoc! jump-vec :y player-jump-force)
           (when-not (casting-spell?)
             (re/fire-rules {:player/jump-up? true}))
           (let [player-pos (api.core/get-pos player-capsule)]
             (api.physics/apply-impulse player-capsule jump-vec player-pos)))})

(defn- apply-linear-for-to-ball [ball-mesh direction-wit-scale]
  (api.anim/run-dur-fn {:duration 5
                        :f (fn [_]
                             (api.physics/set-linear-velocity ball-mesh direction-wit-scale))}))

(defn- set-ball-thrown-pos [capsule camera ball-mesh]
  (let [char-pos (api.core/clone (api.core/get-pos capsule))
        right-dir (api.core/clone (j/call camera :getDirection api.const/v3-right))
        forward-dir (api.core/clone (j/call camera :getDirection api.const/v3-forward))
        _ (j/call char-pos :addInPlace (j/call right-dir :scaleInPlace 0.5))
        _ (j/call char-pos :addInPlace forward-dir #_(j/call forward-dir :scaleInPlace 1))
        _ (j/update! char-pos :y (partial + 1))]
    (m/assoc! ball-mesh :position char-pos (api.core/clone (api.core/get-pos capsule)) #_char-pos)))

(defn- create-ball-agg [ball-mesh & {:keys [filter-group]}]
  (api.physics/physics-agg {:mesh ball-mesh
                            :disable-pre-step? false
                            :filter-group filter-group
                            :type :PhysicsShapeType/SPHERE
                            :mass 1
                            :friction 1
                            :motion-type :PhysicsMotionType/DYNAMIC}))

(defn- make-fire-ball-explode [ball-mesh]
  (let [fire-ball-explode-ps (j/get ball-mesh :fire-ball-explode-ps)
        [ps1 ps2] fire-ball-explode-ps
        n-of-ps (count fire-ball-explode-ps)
        ps-done-count (atom 0)
        on-stop (fn []
                  (swap! ps-done-count inc)
                  (when (= @ps-done-count n-of-ps)
                    (api.particle/push-to-pool :pool/particle-fire-ball-explode fire-ball-explode-ps)))
        pos (api.core/clone (api.core/get-pos ball-mesh))]
    (api.particle/start-ps fire-ball-explode-ps {:emitter pos})
    (api.sound/play :sound/fire-projectile-hit {:position pos})
    (j/call-in ps1 [:onStoppedObservable :addOnce] on-stop)
    (j/call-in ps2 [:onStoppedObservable :addOnce] on-stop)))

(defn- send-fire-projectile-hit [ball-mesh]
  (dispatch-pro :fire-projectile {:pos (api.core/v3->v (api.core/get-pos ball-mesh))}))

(defn- reset-ball-props [ball-mesh]
  (let [body (j/get ball-mesh :physicsBody)
        fire-projectile-ps (j/get ball-mesh :fire-projectile-ps)
        collided? (j/get body :collided?)]
    (when-not collided?
      (j/assoc! body :collided? true)
      (when (j/get ball-mesh :current-player-throwing?)
        (send-fire-projectile-hit ball-mesh))
      (api.physics/make-physics-body-static body)
      (make-fire-ball-explode ball-mesh)
      (m/assoc! ball-mesh :position (v3 0 -2 0))
      (api.particle/stop fire-projectile-ps)
      (api.particle/push-to-pool :pool/particle-fire-projectile fire-projectile-ps)
      (re/push-to-pool :pool/fire-projectile-ball ball-mesh))))

(defn- enemy? [player-id]
  (let [players (re/query :players)
        team (get-in players [player-id :team])]
    (not= (re/query :player/team) team)))

(defn create-rock-projectile []
  (let [m (api.core/get-node-by-name "SM_Env_StoneWall_02")
        _ (j/assoc! m
                    :alwaysSelectAsActiveMesh true
                    :doNotSyncBoundingInfo true
                    :isPickable false
                    :parent (re/query :camera)
                    :scaling (v3 0.03))
        rock (api.core/clone m :name "rock")
        convex (api.core/find-child-mesh rock #(and % (str/includes? % "SM_Env_StoneWall_02_convex")))
        _ (api.core/set-enabled convex false)
        p-shape (api.physics/physics-shape {:mesh convex
                                            :trigger? true})
        _ (j/assoc! p-shape :filterCollideMask api.const/collision-group-other-players)
        body (api.physics/physics-body {:tn convex
                                        :motion-type :PhysicsMotionType/ANIMATED
                                        :disable-pre-step? false
                                        :shape p-shape})]
    (j/assoc! rock :body body)
    (api.core/set-enabled rock false)
    (j/assoc! body :on-trigger
              (fn [type name collided]
                (when-let [player-id (and (= type "TRIGGER_ENTERED")
                                          (= "trigger_player" name)
                                          (j/get-in collided [:transformNode :parent :player-id]))]
                  (let [collided-player-ids (j/get body :collided-player-ids #{})]
                    (when (and (j/get body :current-player-throwing?)
                               (not (collided-player-ids player-id)))
                      (j/update! body :collided-player-ids (fnil conj #{}) player-id)
                      (when (enemy? player-id)
                        (dispatch-pro :rock-projectile {:player-id player-id
                                                        :pos (api.core/v3->v (api.core/get-pos rock))})))))))
    rock))

(defn init-create-rock-projectiles []
  (re/register-item-creation :pool/rock-projectile #(create-rock-projectile)))

(defn create-fire-projectile-ball []
  (let [ball-mesh (api.mesh/sphere {:name "fire-projectile-ball"
                                    :visible? false
                                    :pickable? false
                                    :diameter 0.01})
        {:keys [body]} (j/lookup (create-ball-agg ball-mesh))]
    (j/assoc! body :on-trigger
              (fn [type _ _]
                (when (= type "TRIGGER_ENTERED")
                  (reset-ball-props ball-mesh))))
    (api.physics/add-collision-observable body (fn []
                                                 (reset-ball-props ball-mesh)))
    ball-mesh))

(defn init-fire-projectile-balls []
  (re/register-item-creation :pool/fire-projectile-ball create-fire-projectile-ball))

(defn- make-toxic-ball-explode [ball-mesh]
  (let [toxic-ball-explode-ps (j/get ball-mesh :toxic-projectile-explode-ps)
        on-stop (fn []
                  (api.particle/push-to-pool :pool/particle-toxic-projectile-explode toxic-ball-explode-ps))
        pos (api.core/clone (api.core/get-pos ball-mesh))]
    (api.particle/start-ps toxic-ball-explode-ps {:emitter pos})
    (api.sound/play :sound/toxic-explode {:position pos})
    (j/call-in toxic-ball-explode-ps [:onStoppedObservable :addOnce] on-stop)))

(defn- send-toxic-projectile-hit [ball-mesh]
  (dispatch-pro :toxic-projectile {:pos (api.core/v3->v (api.core/get-pos ball-mesh))}))

(defn- reset-toxic-ball-props [ball-mesh]
  (let [body (j/get ball-mesh :physicsBody)
        toxic-projectile-ps (j/get ball-mesh :toxic-projectile-ps)
        collided? (j/get body :collided?)]
    (when-not collided?
      (j/assoc! body :collided? true)
      (when (j/get ball-mesh :current-player-throwing?)
        (send-toxic-projectile-hit ball-mesh))
      (api.physics/make-physics-body-static body)
      (make-toxic-ball-explode ball-mesh)
      (m/assoc! ball-mesh :position (v3 0 -20 0))
      (api.particle/stop toxic-projectile-ps)
      (api.particle/push-to-pool :pool/particle-toxic-projectile toxic-projectile-ps)
      (re/push-to-pool :pool/toxic-projectile-ball ball-mesh))))

(defn create-puddle-trigger [position]
  (js/setTimeout
    (fn []
      (let [trigger-sphere (api.mesh/sphere {:name "puddle_trigger"
                                             :diameter 3
                                             :segments 1
                                             :position position})
            p-shape (api.physics/physics-shape {:mesh trigger-sphere
                                                :trigger? true})
            body (api.physics/physics-body {:tn trigger-sphere
                                            :motion-type :PhysicsMotionType/STATIC
                                            :shape p-shape})
            ps (re/pop-from-pool :pool/particle-toxic-puddle)]
        (api.particle/start-ps ps {:emitter position})
        (j/call-in ps [:onStoppedObservable :addOnce] #(api.particle/push-to-pool :pool/particle-toxic-puddle ps))

        (api.core/set-enabled trigger-sphere false)
        (js/setTimeout #(api.core/dispose trigger-sphere) 5000)
        (j/assoc! body :on-trigger
                  (fn [type name _]
                    (when (and (not (j/get trigger-sphere :entered?))
                               (= "player-capsule" name)
                               (= type "TRIGGER_ENTERED"))
                      (j/assoc! trigger-sphere :entered? true)
                      (re/insert :player/puddle-end-time (+ (js/Date.now) 1000)))))))
    250))

(defn create-toxic-projectile-ball []
  (let [ball-mesh (api.mesh/sphere {:name "toxic-projectile-ball"
                                    :visible? false
                                    :pickable? false
                                    :diameter 0.01})
        {:keys [body]} (j/lookup (create-ball-agg ball-mesh))]
    (j/assoc! body :on-trigger
              (fn [type _ _]
                (when (= type "TRIGGER_ENTERED")
                  (reset-toxic-ball-props ball-mesh))))
    (api.physics/add-collision-observable body (fn []
                                                 (create-puddle-trigger (api.core/get-pos ball-mesh true))
                                                 (reset-toxic-ball-props ball-mesh)))
    ball-mesh))

(defn init-toxic-projectile-balls []
  (re/register-item-creation :pool/toxic-projectile-ball create-toxic-projectile-ball))

(defn create-kill-splash-ball []
  (let [ball-mesh (api.mesh/sphere {:name "kill-splash-ball"
                                    :visible? false
                                    :pickable? false
                                    :diameter 0.1})
        {:keys [body shape]} (j/lookup (create-ball-agg ball-mesh {:filter-group api.const/collision-group-kill-splash}))]
    (j/assoc! shape :filterCollideMask api.const/collision-group-environment)
    (api.physics/make-physics-body-static body)
    (api.physics/add-collision-observable
      body
      (fn [e]
        (when (= api.const/collision-group-environment (j/get-in e [:collidedAgainst :_shape :filterMembershipMask]))
          (api.physics/set-linear-velocity ball-mesh (v3))
          (api.physics/make-physics-body-static body))))
    ball-mesh))

(defn create-kill-splash-surface-ball []
  (let [ball-mesh (api.mesh/sphere {:name "kill-splash-surface-ball"
                                    :visible? false
                                    :pickable? false
                                    :diameter 0.1})
        {:keys [body shape]} (j/lookup (create-ball-agg ball-mesh {:filter-group api.const/collision-group-kill-splash-surface}))]
    (j/assoc! shape :filterCollideMask api.const/collision-group-environment)
    (api.physics/make-physics-body-static body)
    (api.physics/add-collision-observable
      body
      (fn [e]
        (when (= api.const/collision-group-environment (j/get-in e [:collidedAgainst :_shape :filterMembershipMask]))
          (api.physics/set-linear-velocity ball-mesh (v3))
          (api.physics/make-physics-body-static body))))
    ball-mesh))

(defn init-kill-splash-balls []
  (re/register-item-creation :pool/kill-splash-ball create-kill-splash-ball)
  (re/register-item-creation :pool/kill-splash-surface-ball create-kill-splash-surface-ball)
  (dotimes [_ 1]
    (re/push-to-pool :pool/kill-splash-ball (create-kill-splash-ball)))
  (dotimes [_ 1]
    (re/push-to-pool :pool/kill-splash-surface-ball (create-kill-splash-surface-ball))))

(defn- show-surface-explode [pos]
  (dotimes [_ 10]
    (let [surface-splash-mesh (re/pop-from-pool :pool/kill-splash-surface-ball)
          pos (api.core/clone pos)
          splash-surface-ps (re/pop-from-pool :pool/particle-kill-splash-surface)
          velocity (-> (v3 (* (- (Math/random) 0.5) 1.5)
                           (- (Math/random) 0.2)
                           (* (- (Math/random) 0.5) 1.5))
                       api.core/normalize
                       (j/call :scaleInPlace 15))
          body (j/get surface-splash-mesh :physicsBody)]
      (j/assoc! surface-splash-mesh :position pos)
      (api.particle/start-ps splash-surface-ps {:emitter surface-splash-mesh})
      (api.physics/make-physics-body-dynamic (j/get surface-splash-mesh :physicsBody) 1)
      (api.physics/set-linear-velocity surface-splash-mesh velocity)
      (j/call-in splash-surface-ps [:onStoppedObservable :addOnce]
                 (fn []
                   (api.physics/set-linear-velocity surface-splash-mesh (v3))
                   (api.physics/make-physics-body-static body)
                   (re/push-to-pool :pool/kill-splash-surface-ball surface-splash-mesh)
                   (api.particle/push-to-pool :pool/particle-kill-splash-surface splash-surface-ps))))))

(defn- show-blood-explode [pos]
  (dotimes [_ 10]
    (let [kill-splash-mesh (re/pop-from-pool :pool/kill-splash-ball)
          splash-blood-ps (re/pop-from-pool :pool/particle-kill-splash-blood)
          pos (api.core/clone pos)
          velocity (-> (v3 (* (- (Math/random) 0.5) 1)
                           (* (Math/random) 0.5)
                           (* (- (Math/random) 0.5) 1))
                       api.core/normalize
                       (j/call :scaleInPlace 5))
          body (j/get kill-splash-mesh :physicsBody)]
      (j/assoc! kill-splash-mesh :position pos)
      (api.particle/start-ps splash-blood-ps {:emitter kill-splash-mesh})
      (api.physics/make-physics-body-dynamic body 1)
      (api.physics/set-linear-velocity kill-splash-mesh velocity)
      (j/call-in splash-blood-ps [:onStoppedObservable :addOnce]
                 (fn []
                   (api.physics/set-linear-velocity kill-splash-mesh (v3))
                   (api.physics/make-physics-body-static body)
                   (re/push-to-pool :pool/kill-splash-ball kill-splash-mesh)
                   (api.particle/push-to-pool :pool/particle-kill-splash-blood splash-blood-ps))))))

(defn show-kill-explode [pos]
  (show-surface-explode pos)
  (show-blood-explode pos))

(defn- send-fire-projectile [ball-mesh direction-with-scaled]
  (let [pos (api.core/get-pos ball-mesh)]
    (dispatch-pro :throw-fire-projectile {:pos (api.core/v3->v pos)
                                          :dir (api.core/v3->v direction-with-scaled)})))

(defn throw-fire-projectile-ball
  ([]
   (throw-fire-projectile-ball nil nil))
  ([pos dir]
   (let [other-player-threw? (boolean (and pos dir))
         {:keys [camera player/capsule]} (re/query-all)
         ball-mesh (re/pop-from-pool :pool/fire-projectile-ball)
         {:keys [physicsBody]} (j/lookup ball-mesh)
         fire-projectile-ps (api.particle/start-ps :pool/particle-fire-projectile {:emitter ball-mesh})
         fire-ball-explode-ps (re/pop-from-pool :pool/particle-fire-ball-explode)
         direction-with-scaled (or dir (api.camera/get-direction-scaled camera 50))]
     (api.sound/play :sound/air-whoosh {:time 0 :offset 0.1})
     (j/assoc! ball-mesh
               :fire-projectile-ps fire-projectile-ps
               :fire-ball-explode-ps fire-ball-explode-ps
               :current-player-throwing? (not other-player-threw?))
     (if pos
       (m/assoc! ball-mesh :position pos)
       (set-ball-thrown-pos capsule camera ball-mesh))
     (when-not other-player-threw?
       (send-fire-projectile ball-mesh direction-with-scaled))
     (api.physics/make-physics-body-dynamic physicsBody 1)
     (j/assoc! physicsBody :collided? false)
     (apply-linear-for-to-ball ball-mesh direction-with-scaled))))

(defn apply-shockwave-effect [& {:keys [intensity]
                                 :or {intensity 1.0}}]
  (let [intensity (atom intensity)
        shockwave (re/query :post-process/shockwave)
        noise (re/query :texture/noise)]
    (j/assoc!
      shockwave
      :onApply (fn [effect]
                 (j/call effect :setFloat "time" (* (j/call js/performance :now) 0.001))
                 (j/call effect :setFloat "intensity" @intensity)
                 (j/call effect :setTexture "noiseTexture" noise)
                 (swap! intensity #(Math/max 0 (- % (* 0.03 (api.core/get-anim-ratio)))))
                 (when (<= @intensity 0)
                   (j/call effect :setFloat "intensity" 0)
                   (j/assoc! shockwave :onApply nil))))))

(defn- add-cooldown
  ([skill]
   (add-cooldown skill nil))
  ([skill {:keys [session duration]}]
   (let [cooldown-kw (keyword (str "cooldown/" (name skill)))
         f #(cond-> %
              true (assoc :last-time-applied (js/Date.now))
              duration (assoc :duration duration))]
     (if session
       (re/upsert session cooldown-kw f)
       (re/upsert cooldown-kw f)))))

(defn- booster-active? [booster]
  (let [data (re/query-all)
        booster-time (-> data :player/data booster)
        server-time (:game/server-time data)]
    (and booster-time (< server-time booster-time))))

(defn- cooldown-finished? [skill]
  (let [cooldown-kw (keyword (str "cooldown/" (name skill)))
        {:keys [duration last-time-applied]} (re/query cooldown-kw)
        duration (if (booster-active? :booster_cooldown)
                   (* duration 0.8)
                   duration)]
    (>= (- (js/Date.now) last-time-applied) duration)))

(def super-nova-scale 15)

(defn throw-super-nova [pos]
  (let [super-nova-ball (re/pop-from-pool :pool/super-nova-ball)
        super-nova-shockwave-ps (re/pop-from-pool :pool/particle-super-nova-shockwave)
        _ (api.core/set-enabled super-nova-ball true)
        mat (j/get super-nova-ball :material)
        scale super-nova-scale
        initial-pos (j/update! (api.core/clone pos) :y - 10)]
    (j/call-in super-nova-shockwave-ps [:onStoppedObservable :addOnce]
               (fn []
                 (api.particle/push-to-pool :pool/particle-super-nova-shockwave super-nova-shockwave-ps)))
    (j/assoc! (j/call mat :getBlockByName "transparency") :value 1)
    (j/assoc! mat :backFaceCulling false)
    (api.sound/play :sound/nova-impact {:position pos})
    (api.tween/tween {:target [super-nova-ball :position]
                      :duration 500
                      :from initial-pos
                      :to pos
                      :on-end (fn []
                                (api.particle/start-ps super-nova-shockwave-ps {:emitter pos}))})
    (api.tween/tween {:target [super-nova-ball :scaling]
                      :duration 1000
                      :easing :bounce/in
                      :from {:x 0 :y 0 :z 0}
                      :to {:x scale :y scale :z scale}})
    (api.tween/tween {:from {:alpha 1}
                      :to {:alpha 0}
                      :delay 1000
                      :duration 500
                      :on-update (fn [x]
                                   (j/assoc! (j/call mat :getBlockByName "transparency") :value (j/get x :alpha)))
                      :on-end (fn []
                                (api.core/set-enabled super-nova-ball false)
                                (re/push-to-pool :pool/super-nova-ball super-nova-ball))})))

(defn consume-mana [cost]
  (re/upsert :player/mana #(Math/max 0 (- % cost)))
  (re/insert {:player/mana-regen-unfreeze-time (+ (js/Date.now) 500)}))

(reg-rule
  :player/spell
  {:what {:player/spell? {:then false}
          :mouse/left-click? {}
          :mouse/right-click? {}
          :player/mobile-fire-click? {}
          :player/anim-groups {:then false}
          :player/capsule {:then false}
          :player/current-health {:then false}}
   :when (fn [{{mobile? :game/mobile?
                mobile-fire-click? :player/mobile-fire-click?
                game-started? :player/game-started?
                mouse-left-click? :mouse/left-click?
                mouse-right-click? :mouse/right-click?
                primary-element :player/primary-element
                secondary-element :player/secondary-element
                mana :player/mana
                dash? :player/dash?} :session}]
           (and game-started?
                (or (= primary-element :fire)
                    (= secondary-element :fire))
                (or (and (not mobile?)
                         (or (and mouse-left-click? (= primary-element :fire))
                             (and mouse-right-click? (= secondary-element :fire))))
                    (and mobile? mobile-fire-click?))
                (not dash?)
                (not (casting-spell?))
                (not (freezing?))
                (> mana (:spell mana-costs))))
   :then (fn []
           (throw-fire-projectile-ball)
           (re/fire-rules {:player/spell? true
                           :player/air-time 0})
           (consume-mana (:spell mana-costs))
           (apply-shockwave-effect :intensity 0.3))})

(defn- send-toxic-projectile [projectiles]
  (dispatch-pro :throw-toxic-projectile projectiles))

(defn throw-toxic-ball
  ([]
   (throw-toxic-ball nil))
  ([balls]
   (let [other-player-threw? (boolean (seq balls))
         [[pos-1 dir-1] [pos-2 dir-2] [pos-3 dir-3]] balls
         {:keys [camera player/capsule]} (re/query-all)
         ball-mesh (re/pop-from-pool :pool/toxic-projectile-ball)
         ball-mesh2 (re/pop-from-pool :pool/toxic-projectile-ball)
         ball-mesh3 (re/pop-from-pool :pool/toxic-projectile-ball)
         ball-mesh-1-physics-body (j/get ball-mesh :physicsBody)
         ball-mesh-2-physics-body (j/get ball-mesh2 :physicsBody)
         ball-mesh-3-physics-body (j/get ball-mesh3 :physicsBody)
         fire-projectile-ps (api.particle/start-ps :pool/particle-toxic-projectile {:emitter ball-mesh})
         fire-ball-explode-ps (re/pop-from-pool :pool/particle-toxic-projectile-explode)

         fire-projectile-ps-2 (api.particle/start-ps :pool/particle-toxic-projectile {:emitter ball-mesh2})
         fire-ball-explode-ps-2 (re/pop-from-pool :pool/particle-toxic-projectile-explode)

         fire-projectile-ps-3 (api.particle/start-ps :pool/particle-toxic-projectile {:emitter ball-mesh3})
         fire-ball-explode-ps-3 (re/pop-from-pool :pool/particle-toxic-projectile-explode)

         direction-with-scaled (or dir-1 (api.camera/get-direction-scaled camera 30))
         r (v3)
         quat-rot (api.core/quat-rot-axis (v3 0 1 0) (api.core/to-rad -5))
         _ (j/call direction-with-scaled :rotateByQuaternionToRef quat-rot r)
         direction-with-scaled-left (or dir-2 r)

         r2 (v3)
         quat-rot2 (api.core/quat-rot-axis (v3 0 1 0) (api.core/to-rad 5))
         _ (j/call direction-with-scaled :rotateByQuaternionToRef quat-rot2 r2)
         direction-with-scaled-right (or dir-3 r2)]
     (api.sound/play :sound/toxic-whoosh {:position (if other-player-threw?
                                                      pos-1
                                                      (api.core/get-pos (re/query :player/model) true))
                                          :time 0
                                          :offset 0.1})
     (j/assoc! ball-mesh
               :toxic-projectile-ps fire-projectile-ps
               :toxic-projectile-explode-ps fire-ball-explode-ps
               :current-player-throwing? (not other-player-threw?))
     (j/assoc! ball-mesh2
               :toxic-projectile-ps fire-projectile-ps-2
               :toxic-projectile-explode-ps fire-ball-explode-ps-2
               :current-player-throwing? (not other-player-threw?))
     (j/assoc! ball-mesh3
               :toxic-projectile-ps fire-projectile-ps-3
               :toxic-projectile-explode-ps fire-ball-explode-ps-3
               :current-player-throwing? (not other-player-threw?))

     (set-ball-thrown-pos capsule camera ball-mesh)
     (set-ball-thrown-pos capsule camera ball-mesh2)
     (set-ball-thrown-pos capsule camera ball-mesh3)

     (when pos-1
       (m/assoc! ball-mesh :position pos-1))

     (if pos-2
       (m/assoc! ball-mesh2 :position pos-2)
       (j/update! ball-mesh2 :position (fn [pos]
                                         (-> pos
                                             (j/call :addInPlace (-> (j/call camera :getDirection api.const/v3-left)
                                                                     (j/call :scaleInPlace 0.1)))))))
     (if pos-3
       (m/assoc! ball-mesh3 :position pos-3)
       (j/update! ball-mesh3 :position (fn [pos]
                                         (-> pos
                                             (j/call :addInPlace (-> (j/call camera :getDirection api.const/v3-right)
                                                                     (j/call :scaleInPlace 0.1)))))))

     (when-not other-player-threw?
       (send-toxic-projectile [{:pos (-> ball-mesh api.core/get-pos api.core/v3->v)
                                :dir (api.core/v3->v direction-with-scaled)}
                               {:pos (-> ball-mesh2 api.core/get-pos api.core/v3->v)
                                :dir (api.core/v3->v direction-with-scaled-left)}
                               {:pos (-> ball-mesh3 api.core/get-pos api.core/v3->v)
                                :dir (api.core/v3->v direction-with-scaled-right)}]))

     (api.physics/make-physics-body-dynamic ball-mesh-1-physics-body 1)
     (j/assoc! ball-mesh-1-physics-body :collided? false)
     (api.physics/apply-impulse ball-mesh direction-with-scaled (api.core/get-pos ball-mesh))

     (api.physics/make-physics-body-dynamic ball-mesh-2-physics-body 1)
     (j/assoc! ball-mesh-2-physics-body :collided? false)
     (api.physics/apply-impulse ball-mesh2 direction-with-scaled-left (api.core/get-pos ball-mesh2))

     (api.physics/make-physics-body-dynamic ball-mesh-3-physics-body 1)
     (j/assoc! ball-mesh-3-physics-body :collided? false)
     (api.physics/apply-impulse ball-mesh3 direction-with-scaled-right (api.core/get-pos ball-mesh3)))))

(reg-rule
  :player/toxic-spell
  {:what {:player/spell-toxic? {:then false}
          :mouse/left-click? {}
          :mouse/right-click? {}
          :player/mobile-toxic-click? {}
          :player/anim-groups {:then false}
          :player/capsule {:then false}
          :player/current-health {:then false}}
   :when (fn [{{game-started? :player/game-started?
                mouse-left-click? :mouse/left-click?
                mouse-right-click? :mouse/right-click?
                primary-element :player/primary-element
                secondary-element :player/secondary-element
                mobile? :game/mobile?
                mobile-toxic-click? :player/mobile-toxic-click?
                mana :player/mana
                dash? :player/dash?} :session}]
           (and game-started?
                (or (and (not mobile?)
                         (or (and mouse-left-click? (= primary-element :toxic))
                             (and mouse-right-click? (= secondary-element :toxic))))
                    (and mobile? mobile-toxic-click?))
                (not dash?)
                (not (casting-spell?))
                (not (freezing?))
                (> mana (:toxic mana-costs))))
   :then (fn []
           (throw-toxic-ball)
           (re/fire-rules {:player/spell-toxic? true
                           :player/air-time 0})
           (consume-mana (:toxic mana-costs)))})

(defn- send-rock [rocks]
  (dispatch-pro :throw-rocks rocks))

(defn throw-rock-ball
  ([]
   (let [camera (re/query :camera)
         ball-mesh (re/pop-from-pool :pool/rock-projectile)
         ball-mesh-2 (re/pop-from-pool :pool/rock-projectile)
         body (j/get ball-mesh :body)
         body-2 (j/get ball-mesh-2 :body)
         _ (j/assoc! body
                     :collided-player-ids #{}
                     :current-player-throwing? true)
         _ (j/assoc! body-2
                     :collided-player-ids #{}
                     :current-player-throwing? true)
         right-dir (j/call camera :getDirection api.const/v3-right)
         left-dir (j/call camera :getDirection api.const/v3-left)]
     (j/assoc! ball-mesh
               :parent camera
               :position (v3 -2.5 -0.1 15)
               :rotation (v3 (api.core/to-rad 79) (api.core/to-rad -36) (api.core/to-rad 58)))

     (j/assoc! ball-mesh-2
               :parent camera
               :position (v3 2.5 -0.1 15)
               :rotation (v3 (api.core/to-rad 79) (api.core/to-rad -36) (api.core/to-rad 58)))
     (api.core/set-enabled ball-mesh true)
     (api.core/set-enabled ball-mesh-2 true)
     (js/setTimeout
       (fn []
         (api.sound/play :sound/rock-hit {:position (api.core/get-pos ball-mesh)})
         (let [{:keys [position rotation]} (api.core/get-world-transforms ball-mesh)]
           (j/assoc! ball-mesh
                     :parent nil
                     :position position
                     :rotation rotation))
         (let [{:keys [position rotation]} (api.core/get-world-transforms ball-mesh-2)]
           (j/assoc! ball-mesh-2
                     :parent nil
                     :position position
                     :rotation rotation))

         (let [ball-mesh-end-pos (-> (api.core/get-pos ball-mesh true)
                                     (j/call :addInPlace (j/call right-dir :scale 2.5)))
               ball-mesh-2-end-pos (-> (api.core/get-pos ball-mesh-2 true)
                                       (j/call :addInPlace (j/call left-dir :scale 2.5)))]
           (api.tween/tween {:target [ball-mesh :position]
                             :easing :back/out
                             :duration 500
                             :from (j/get ball-mesh :position)
                             :to ball-mesh-end-pos})
           (api.tween/tween {:target [ball-mesh-2 :position]
                             :easing :back/out
                             :duration 500
                             :from (j/get ball-mesh-2 :position)
                             :to ball-mesh-2-end-pos})

           (js/setTimeout (fn []
                            (api.core/set-enabled ball-mesh false)
                            (j/assoc! ball-mesh :position (v3 0 -20 0))
                            (j/assoc! body :current-player-throwing? false)
                            (re/push-to-pool :pool/rock-projectile ball-mesh)) 750)
           (js/setTimeout (fn []
                            (api.core/set-enabled ball-mesh-2 false)
                            (j/assoc! ball-mesh-2 :position (v3 0 -20 0))
                            (j/assoc! body-2 :current-player-throwing? false)
                            (re/push-to-pool :pool/rock-projectile ball-mesh-2)) 750)

           (send-rock [{:pos-s (-> ball-mesh api.core/get-pos api.core/v3->v)
                        :pos-e (api.core/v3->v ball-mesh-end-pos)
                        :rot (api.core/v3->v (j/get ball-mesh :rotation))}
                       {:pos-s (-> ball-mesh-2 api.core/get-pos api.core/v3->v)
                        :pos-e (api.core/v3->v ball-mesh-2-end-pos)
                        :rot (api.core/v3->v (j/get ball-mesh-2 :rotation))}])))
       34)))
  ([pos-s pos-e rot]
   (let [ball-mesh (re/pop-from-pool :pool/rock-projectile)]
     (j/assoc! ball-mesh
               :parent nil
               :position pos-s
               :rotation rot)
     (api.core/set-enabled ball-mesh true)
     (api.sound/play :sound/rock-hit {:position (api.core/get-pos ball-mesh)})
     (api.tween/tween {:target [ball-mesh :position]
                       :easing :back/out
                       :duration 500
                       :from (j/get ball-mesh :position)
                       :to pos-e})
     (js/setTimeout (fn []
                      (api.core/set-enabled ball-mesh false)
                      (j/assoc! ball-mesh :position (v3 0 -20 0))
                      (re/push-to-pool :pool/rock-projectile ball-mesh)) 750))))

(reg-rule
  :player/rock-spell
  {:what {:player/spell-rock? {:then false}
          :player/mobile-earth-click? {}
          :mouse/left-click? {}
          :mouse/right-click? {}
          :player/anim-groups {:then false}
          :player/capsule {:then false}
          :player/current-health {:then false}}
   :when (fn [{{game-started? :player/game-started?
                mouse-left-click? :mouse/left-click?
                mouse-right-click? :mouse/right-click?
                primary-element :player/primary-element
                secondary-element :player/secondary-element
                mobile? :game/mobile?
                mobile-earth-click? :player/mobile-earth-click?
                mana :player/mana
                dash? :player/dash?} :session}]
           (and game-started?
                (or (and (not mobile?)
                         (or (and mouse-left-click? (= primary-element :earth))
                             (and mouse-right-click? (= secondary-element :earth))))
                    (and mobile? mobile-earth-click?))
                (not dash?)
                (not (casting-spell?))
                (not (freezing?))
                (> mana (:rock mana-costs))))
   :then (fn []
           (re/fire-rules {:player/spell-rock? true
                           :player/air-time 0})
           (throw-rock-ball)
           (consume-mana (:rock mana-costs)))})

(defn- send-super-nova [pos]
  (dispatch-pro :super-nova {:pos (api.core/v3->v pos)}))

(defn- send-light-strike [pos]
  (dispatch-pro :light-strike {:pos (api.core/v3->v pos)}))

(defn- notify-enemies [pos spell]
  (dispatch-pro :notify-enemies {:pos (api.core/v3->v pos)
                                 :spell spell}))

(reg-rule
  :player/spell-super-nova
  {:what {:keys-pressed {}
          :player/mobile-fire-sorcery-click? {}
          :player/current-health {:then false}
          :player/spell? {:then false}
          :player/spell-super-nova? {:then false}}
   :when (fn [{{game-started? :player/game-started?
                primary-element :player/primary-element
                secondary-element :player/secondary-element
                dash? :player/dash?} :session}]
           (and game-started?
                (or (= primary-element :fire)
                    (= secondary-element :fire))
                (not dash?)
                (not (casting-spell?))
                (not (freezing?))
                (cooldown-finished? :spell-super-nova)))
   :then (fn [{{mobile? :game/mobile?
                fire-prev-key-sorcery-pressed? :player/fire-prev-key-sorcery-pressed?
                mobile-fire-sorcery-click? :player/mobile-fire-sorcery-click?
                keys-pressed :keys-pressed
                temp-super-nova :player/temp-super-nova
                player-super-nova-position :player/super-nova-position
                primary-element :player/primary-element
                secondary-element :player/secondary-element} :session}]
           (let [key-code (cond
                            (= primary-element :fire) "KeyQ"
                            (= secondary-element :fire) "KeyE")
                 not-pressed? (if mobile?
                                (not mobile-fire-sorcery-click?)
                                (not (keys-pressed key-code)))]
             (some-> temp-super-nova (api.core/set-enabled false))
             (when (and fire-prev-key-sorcery-pressed? not-pressed?)
               (when player-super-nova-position
                 (notify-enemies player-super-nova-position :fire)
                 (re/fire-rules {:player/spell-super-nova? true})
                 (js/setTimeout
                   (fn []
                     (throw-super-nova player-super-nova-position)
                     (js/setTimeout #(send-super-nova player-super-nova-position) 500)
                     (add-cooldown :spell-super-nova)
                     (js/setTimeout apply-shockwave-effect 500))
                   500)))
             (re/insert {:player/fire-prev-key-sorcery-pressed? (if mobile?
                                                                  mobile-fire-sorcery-click?
                                                                  (keys-pressed key-code))})))})

(reg-rule
  :player/position-super-nova
  {:locals {:temp-super-nova-pos (v3)
            :super-nova-pos (v3)
            :start (v3)
            :end (v3)
            :collide-with #js {:collideWith api.const/collision-group-environment}
            :result (api.physics/raycast-result)}
   :what {:dt {}
          :keys-pressed {:then false}
          :player/mobile-fire-sorcery-click? {:then false}
          :player/spell-super-nova? {:then false}
          :player/current-health {:then false}
          :player/fire-ball-sphere {:then false}
          :camera {:then false}}
   :when (fn [{{game-started? :player/game-started?
                mobile? :game/mobile?
                primary-element :player/primary-element
                secondary-element :player/secondary-element
                dash? :player/dash?} :session}]
           (and game-started?
                (or (= primary-element :fire)
                    (= secondary-element :fire))
                (not (freezing?))
                (not dash?)
                (not (casting-spell?))
                (cooldown-finished? :spell-super-nova)))
   :then (fn [{{camera :camera
                keys-pressed :keys-pressed
                mobile-fire-sorcery-click? :player/mobile-fire-sorcery-click?
                temp-super-nova :player/temp-super-nova
                primary-element :player/primary-element
                secondary-element :player/secondary-element} :session
               {:keys [temp-super-nova-pos super-nova-pos start end result collide-with]} :locals}]
           (let [temp-super-nova (if temp-super-nova
                                   temp-super-nova
                                   (let [temp-super-nova (re/pop-from-pool :pool/super-nova-ball)]
                                     (j/assoc! temp-super-nova :name "temp-super-nova-ball")
                                     (re/insert {:player/temp-super-nova temp-super-nova})
                                     temp-super-nova))
                 key-code (cond
                            (= primary-element :fire) "KeyQ"
                            (= secondary-element :fire) "KeyE")]
             (if (or (keys-pressed key-code) mobile-fire-sorcery-click?)
               (let [mat-super-nova (api.asset/get-asset :material/fire-nova)
                     _ (j/assoc! (j/call mat-super-nova :getBlockByName "transparency") :value 0.5)
                     mat-super-nova-disallowed (api.asset/get-asset :material/fire-nova-disallowed)
                     r (api.core/create-picking-ray camera)
                     start (api.core/set-v3 start (j/get r :origin))
                     dir (j/get r :direction)
                     length 150
                     end (j/call (api.core/set-v3 end start) :addInPlace (j/call dir :scaleInPlace length))
                     _ (api.physics/raycast-to-ref start end result collide-with)]
                 (api.core/set-enabled temp-super-nova true)
                 (if (j/get result :hasHit)
                   (let [point (j/get result :hitPoint)
                         distance (j/get result :hitDistance)
                         normal (j/get result :hitNormal)
                         normal-y (j/get normal :y)
                         super-nova-pos (api.core/set-v3 super-nova-pos point)
                         temp-super-nova-pos (api.core/set-v3 temp-super-nova-pos point)]
                     (j/assoc! temp-super-nova :position temp-super-nova-pos)
                     (if (or (< normal-y 0.6) (> distance 70))
                       (do
                         (re/insert {:player/super-nova-position nil})
                         (j/assoc! temp-super-nova :material mat-super-nova-disallowed))
                       (do
                         (j/assoc! temp-super-nova :material mat-super-nova)
                         (re/insert {:player/super-nova-position super-nova-pos}))))
                   (re/insert {:player/super-nova-position nil})))
               (api.core/set-enabled temp-super-nova false))))})

(defn create-toxic-cloud-ball []
  (let [s (api.mesh/sphere {:name "toxic-cloud-ball"
                            :material (api.core/clone (api.asset/get-asset :material/toxic-cloud))
                            :enabled? false
                            :diameter 1})]
    (j/assoc! s :scaling (v3 super-nova-scale))
    s))

(defn init-toxic-cloud-balls []
  (re/register-item-creation :pool/toxic-cloud-ball create-toxic-cloud-ball))

(def toxic-cloud-duration 7500)

(defn- create-toxic-cloud-trigger [position player-id toxic-cloud-id]
  (let [trigger-sphere (api.mesh/sphere {:name "toxic_cloud_trigger"
                                         :diameter 20
                                         :segments 2
                                         :position position})
        p-shape (api.physics/physics-shape {:mesh trigger-sphere
                                            :trigger? true})
        body (api.physics/physics-body {:tn trigger-sphere
                                        :motion-type :PhysicsMotionType/STATIC
                                        :shape p-shape})]
    (api.core/set-enabled trigger-sphere false)
    (js/setTimeout #(api.core/dispose trigger-sphere) toxic-cloud-duration)
    (j/assoc! body :on-trigger
              (fn [type name _]
                (when (and (not (j/get trigger-sphere :entered?))
                           (= "player-capsule" name)
                           (= type "TRIGGER_ENTERED"))
                  (j/assoc! trigger-sphere :entered? true)
                  (when (and player-id (enemy? player-id))
                    (dispatch-pro :entered-toxic-cloud {:toxic-cloud-id toxic-cloud-id})))))))

(defn throw-toxic-cloud [{:keys [position player-id toxic-cloud-id]}]
  (let [toxic-cloud-ball (re/pop-from-pool :pool/toxic-cloud-ball)
        toxic-cloud-ps (re/pop-from-pool :pool/particle-toxic-cloud-smoke)
        _ (api.core/set-enabled toxic-cloud-ball true)
        mat (j/get toxic-cloud-ball :material)
        scale super-nova-scale
        alpha 0.25]
    (j/call-in toxic-cloud-ps [:onStoppedObservable :addOnce]
               (fn []
                 (api.particle/push-to-pool :pool/particle-toxic-cloud-smoke toxic-cloud-ps)))
    (j/assoc! (j/call mat :getBlockByName "transparency") :value alpha)
    (j/assoc! mat :backFaceCulling false)
    (j/assoc! toxic-cloud-ball :position position)
    (api.sound/play :sound/toxic-cloud-explode {:position position})
    (create-toxic-cloud-trigger (api.core/clone position) player-id toxic-cloud-id)
    (api.tween/tween {:target [toxic-cloud-ball :scaling]
                      :duration 500
                      :from {:x 0 :y 0 :z 0}
                      :to {:x scale :y scale :z scale}
                      :on-end (fn []
                                (api.particle/start-ps toxic-cloud-ps {:emitter position}))})
    (js/setTimeout
      (fn []
        (api.tween/tween {:from {:alpha alpha}
                          :to {:alpha 0}
                          :duration 250
                          :on-update (fn [x]
                                       (j/assoc! (j/call mat :getBlockByName "transparency") :value (j/get x :alpha)))
                          :on-end (fn []
                                    (api.core/set-enabled toxic-cloud-ball false)
                                    (re/push-to-pool :pool/toxic-cloud-ball toxic-cloud-ball))}))
      toxic-cloud-duration)))

(defn- send-toxic-cloud [pos]
  (dispatch-pro :toxic-cloud {:pos (api.core/v3->v pos)}))

(reg-rule
  :player/spell-toxic-cloud
  {:what {:keys-pressed {}
          :player/mobile-toxic-sorcery-click? {}
          :player/current-health {:then false}
          :player/spell? {:then false}
          :player/spell-toxic-cloud? {:then false}}
   :when (fn [{{game-started? :player/game-started?
                primary-element :player/primary-element
                secondary-element :player/secondary-element
                dash? :player/dash?} :session}]
           (and game-started?
                (or (= primary-element :toxic)
                    (= secondary-element :toxic))
                (not dash?)
                (not (casting-spell?))
                (not (freezing?))
                (cooldown-finished? :spell-toxic-cloud)))
   :then (fn [{{toxic-prev-key-sorcery-pressed? :player/toxic-prev-key-sorcery-pressed?
                keys-pressed :keys-pressed
                temp-toxic-cloud :player/temp-toxic-cloud
                player-toxic-cloud-position :player/toxic-cloud-position
                primary-element :player/primary-element
                secondary-element :player/secondary-element
                mobile? :game/mobile?
                mobile-toxic-sorcery-click? :player/mobile-toxic-sorcery-click?} :session}]
           (let [key-code (cond
                            (= primary-element :toxic) "KeyQ"
                            (= secondary-element :toxic) "KeyE")
                 not-pressed? (if mobile?
                                (not mobile-toxic-sorcery-click?)
                                (not (keys-pressed key-code)))]
             (some-> temp-toxic-cloud (api.core/set-enabled false))
             (when (and toxic-prev-key-sorcery-pressed? not-pressed?)
               (when player-toxic-cloud-position
                 (notify-enemies player-toxic-cloud-position :toxic)
                 (re/fire-rules {:player/spell-toxic-cloud? true})
                 (js/setTimeout
                   #(do
                      (throw-toxic-cloud {:position player-toxic-cloud-position})
                      (send-toxic-cloud player-toxic-cloud-position)
                      (add-cooldown :spell-toxic-cloud))
                   750)))
             (re/insert {:player/toxic-prev-key-sorcery-pressed? (if mobile?
                                                                   mobile-toxic-sorcery-click?
                                                                   (keys-pressed key-code))})))})

(reg-rule
  :player/position-toxic-cloud
  {:locals {:temp-toxic-cloud-pos (v3)
            :toxic-cloud-pos (v3)
            :start (v3)
            :end (v3)
            :collide-with #js {:collideWith api.const/collision-group-environment}
            :result (api.physics/raycast-result)}
   :what {:dt {}
          :keys-pressed {:then false}
          :player/spell-toxic-cloud? {:then false}
          :player/current-health {:then false}
          :player/fire-ball-sphere {:then false}
          :camera {:then false}}
   :when (fn [{{game-started? :player/game-started?
                primary-element :player/primary-element
                secondary-element :player/secondary-element
                dash? :player/dash?} :session}]
           (and game-started?
                (or (= primary-element :toxic)
                    (= secondary-element :toxic))
                (not (freezing?))
                (not dash?)
                (not (casting-spell?))
                (cooldown-finished? :spell-toxic-cloud)))
   :then (fn [{{camera :camera
                keys-pressed :keys-pressed
                mobile-toxic-sorcery-click? :player/mobile-toxic-sorcery-click?
                temp-toxic-cloud :player/temp-toxic-cloud
                primary-element :player/primary-element
                secondary-element :player/secondary-element} :session
               {:keys [temp-toxic-cloud-pos toxic-cloud-pos start end result collide-with]} :locals}]
           (let [temp-toxic-cloud (if temp-toxic-cloud
                                    temp-toxic-cloud
                                    (let [temp-toxic-cloud (re/pop-from-pool :pool/toxic-cloud-ball)]
                                      (j/assoc! temp-toxic-cloud :name "temp-toxic-cloud-ball")
                                      (re/insert {:player/temp-toxic-cloud temp-toxic-cloud})
                                      temp-toxic-cloud))
                 key-code (cond
                            (= primary-element :toxic) "KeyQ"
                            (= secondary-element :toxic) "KeyE")]
             (if (or (keys-pressed key-code) mobile-toxic-sorcery-click?)
               (let [mat-toxic-cloud (api.asset/get-asset :material/toxic-cloud)
                     _ (j/assoc! (j/call mat-toxic-cloud :getBlockByName "transparency") :value 0.5)
                     mat-toxic-cloud-disallowed (api.asset/get-asset :material/fire-nova-disallowed)
                     r (api.core/create-picking-ray camera)
                     start (api.core/set-v3 start (j/get r :origin))
                     dir (j/get r :direction)
                     length 150
                     end (j/call (api.core/set-v3 end start) :addInPlace (j/call dir :scaleInPlace length))
                     _ (api.physics/raycast-to-ref start end result collide-with)]
                 (api.core/set-enabled temp-toxic-cloud true)
                 (if (j/get result :hasHit)
                   (let [point (j/get result :hitPoint)
                         distance (j/get result :hitDistance)
                         normal (j/get result :hitNormal)
                         normal-y (j/get normal :y)
                         toxic-cloud-pos (api.core/set-v3 toxic-cloud-pos point)
                         temp-toxic-cloud-pos (api.core/set-v3 temp-toxic-cloud-pos point)]
                     (j/assoc! temp-toxic-cloud :position temp-toxic-cloud-pos)
                     (if (or (< normal-y 0.6) (> distance 70))
                       (do
                         (re/insert {:player/toxic-cloud-position nil})
                         (j/assoc! temp-toxic-cloud :material mat-toxic-cloud-disallowed))
                       (do
                         (j/assoc! temp-toxic-cloud :material mat-toxic-cloud)
                         (re/insert {:player/toxic-cloud-position toxic-cloud-pos}))))
                   (re/insert {:player/toxic-cloud-position nil})))
               (api.core/set-enabled temp-toxic-cloud false))))})

(defn throw-light-strike [pos]
  (let [light-strike-ps (re/pop-from-pool :pool/particle-light-strike)
        [ps1 ps2 ps3] light-strike-ps
        n-of-ps (count light-strike-ps)
        ps-done-count (atom 0)
        on-stop (fn []
                  (swap! ps-done-count inc)
                  (when (= @ps-done-count n-of-ps)
                    (api.particle/push-to-pool :pool/particle-light-strike light-strike-ps)))]
    (api.particle/start-ps light-strike-ps {:emitter pos})
    (api.sound/play :sound/electric-light-strike-thunder)
    (j/call-in ps1 [:onStoppedObservable :addOnce] on-stop)
    (j/call-in ps2 [:onStoppedObservable :addOnce] on-stop)
    (j/call-in ps3 [:onStoppedObservable :addOnce] on-stop)))

(set! player.animation/throw-light-strike throw-light-strike)

(reg-rule
  :player/spell-light-strike
  {:what {:keys-pressed {}
          :player/mobile-light-sorcery-click? {}
          :player/current-health {:then false}
          :player/spell? {:then false}
          :player/spell-light-strike? {:then false}}
   :when (fn [{{game-started? :player/game-started?
                primary-element :player/primary-element
                secondary-element :player/secondary-element
                dash? :player/dash?} :session}]
           (and game-started?
                (or (= primary-element :light)
                    (= secondary-element :light))
                (not dash?)
                (not (casting-spell?))
                (not (freezing?))
                (cooldown-finished? :spell-light-strike)))
   :then (fn [{{light-prev-key-sorcery-pressed? :player/light-prev-key-sorcery-pressed?
                player-model :player/model
                keys-pressed :keys-pressed
                light-strike-cylinder :player/light-strike-cylinder
                light-strike-position :player/light-strike-position
                primary-element :player/primary-element
                secondary-element :player/secondary-element
                mobile? :game/mobile?
                mobile-light-sorcery-click? :player/mobile-light-sorcery-click?} :session}]
           (let [key-code (cond
                            (= primary-element :light) "KeyQ"
                            (= secondary-element :light) "KeyE")
                 not-pressed? (if mobile?
                                (not mobile-light-sorcery-click?)
                                (not (keys-pressed key-code)))]
             (some-> light-strike-cylinder (api.core/set-enabled false))
             (when (and light-prev-key-sorcery-pressed? not-pressed?)
               (when light-strike-position
                 (notify-enemies light-strike-position :light)
                 (re/fire-rules {:player/spell-light-strike? true})
                 (js/setTimeout
                   (fn []
                     (send-light-strike light-strike-position)
                     (api.sound/play :sound/electric-light-strike {:position (api.core/get-pos player-model)})
                     (add-cooldown :spell-light-strike))
                   1000)))
             (re/insert {:player/light-prev-key-sorcery-pressed? (if mobile?
                                                                   mobile-light-sorcery-click?
                                                                   (keys-pressed key-code))})))})

(reg-rule
  :player/position-light-strike
  {:locals {:temp-light-strike-pos (v3)
            :light-strike-pos (v3)
            :start (v3)
            :end (v3)
            :collide-with #js {:collideWith api.const/collision-group-environment}
            :result (api.physics/raycast-result)}
   :what {:dt {}
          :keys-pressed {:then false}
          :player/current-health {:then false}
          :player/fire-ball-sphere {:then false}
          :camera {:then false}}
   :when (fn [{{game-started? :player/game-started?
                primary-element :player/primary-element
                secondary-element :player/secondary-element
                dash? :player/dash?} :session}]
           (and game-started?
                (or (= primary-element :light)
                    (= secondary-element :light))
                (not dash?)
                (not (casting-spell?))
                (not (freezing?))
                (cooldown-finished? :spell-light-strike)))
   :then (fn [{{camera :camera
                keys-pressed :keys-pressed
                light-strike-cylinder :player/light-strike-cylinder
                light-strike-height :player/light-strike-height
                primary-element :player/primary-element
                secondary-element :player/secondary-element
                mobile-light-sorcery-click? :player/mobile-light-sorcery-click?} :session
               {:keys [temp-light-strike-pos light-strike-pos start end result collide-with]} :locals}]
           (let [key-code (cond
                            (= primary-element :light) "KeyQ"
                            (= secondary-element :light) "KeyE")]
             (if (or (keys-pressed key-code) mobile-light-sorcery-click?)
               (let [mat-light-strike-cylinder (api.asset/get-asset :material/light-strike-cylinder)
                     mat-light-strike-cylinder-disallowed (api.asset/get-asset :material/light-strike-cylinder-disallowed)
                     r (api.core/create-picking-ray camera)
                     start (api.core/set-v3 start (j/get r :origin))
                     dir (j/get r :direction)
                     length 150
                     end (j/call (api.core/set-v3 end start) :addInPlace (j/call dir :scaleInPlace length))
                     _ (api.physics/raycast-to-ref start end result collide-with)]
                 (api.core/set-enabled light-strike-cylinder true)
                 (if (j/get result :hasHit)
                   (let [point (j/get result :hitPoint)
                         distance (j/get result :hitDistance)
                         normal (j/get result :hitNormal)
                         normal-y (j/get normal :y)
                         light-strike-pos (api.core/set-v3 light-strike-pos point)
                         temp-light-strike-pos (api.core/set-v3 temp-light-strike-pos point)]
                     (j/assoc! light-strike-cylinder :position temp-light-strike-pos)
                     (j/update-in! light-strike-cylinder [:position :y] + (/ light-strike-height 2))
                     (if (or (< normal-y 0.6) (not (< 8 distance 40)))
                       (do
                         (re/insert {:player/light-strike-position nil})
                         (j/assoc! light-strike-cylinder :material mat-light-strike-cylinder-disallowed))
                       (do
                         (j/assoc! light-strike-cylinder :material mat-light-strike-cylinder)
                         (re/insert {:player/light-strike-position light-strike-pos}))))
                   (re/insert {:player/light-strike-position nil})))
               (api.core/set-enabled light-strike-cylinder false))))})

(defn- create-wind-tornado []
  (let [circle (api.mesh/sphere {:name "wind-tornado-circle"
                                 :diameter-x 1
                                 :diameter-y 0.2
                                 :diameter-z 1
                                 :segments 16
                                 :material (api.core/clone (api.asset/get-asset :material/wind-tornado-mat))})
        tornado (api.core/clone (api.core/get-mesh-by-name "Tornado"))
        _ (m/assoc! tornado
                    :material (api.core/clone (j/get tornado :material))
                    :circle circle
                    :alpha-value 0.5
                    :position.y 1
                    :scaling (v3 4))
        _ (m/assoc! circle
                    :parent tornado
                    :alpha-value 0.6
                    :scaling (v3 3)
                    :position.y -1.35)]
    (api.core/set-enabled tornado false)
    tornado))

(defn init-create-wind-tornados []
  (re/register-item-creation :pool/wind-tornado create-wind-tornado))

(defn throw-wind-tornado [pos]
  (let [wind-tornado (re/pop-from-pool :pool/wind-tornado)
        wind-tornado-circle (j/get wind-tornado :circle)
        _ (api.core/set-enabled wind-tornado true)
        wind-cloud-ps (api.particle/wind-tornado-clouds)
        scale 5]
    (api.sound/play :sound/wind-tornado-blow {:position pos})
    (j/call wind-tornado :setPivotPoint (v3 0 -2 0))
    (j/assoc! wind-tornado :position pos)
    (j/assoc-in! wind-tornado [:material :alpha] (j/get wind-tornado :alpha-value))
    (j/assoc-in! wind-tornado-circle [:material :alpha] (j/get wind-tornado-circle :alpha-value))
    (api.particle/start-ps wind-cloud-ps {:emitter wind-tornado})
    (api.anim/run-dur-fn {:duration 5
                          :f (fn [_]
                               (j/update-in! wind-tornado [:rotation :y] + 0.1))
                          :on-end (fn []
                                    (api.core/set-enabled wind-tornado false)
                                    (re/push-to-pool :pool/wind-tornado wind-tornado)
                                    (api.particle/push-to-pool :pool/particle-wind-tornado-cloud wind-cloud-ps))})
    (api.tween/tween {:target [wind-tornado :scaling]
                      :duration 1000
                      :easing :elastic/out
                      :from {:x 0 :y 0 :z 0}
                      :to {:x scale :y scale :z scale}})
    (api.tween/tween {:from {:alpha 1}
                      :to {:alpha 0}
                      :delay 4500
                      :duration 500
                      :on-update (fn [x]
                                   (j/assoc-in! wind-tornado [:material :alpha] (j/get x :alpha))
                                   (j/assoc-in! wind-tornado-circle [:material :alpha] (j/get x :alpha)))})))

(set! player.animation/throw-wind-tornado throw-wind-tornado)

(defn send-wind-tornado [pos]
  (dispatch-pro :wind-tornado {:pos (api.core/v3->v pos)}))

(defn- move-temp-wind-tornado-wrapper [temp-wind-tornado temp-wind-tornado-disabled-pos]
  (let [tn (j/get temp-wind-tornado :parent)]
    (j/assoc! tn :position (api.core/set-v3 temp-wind-tornado-disabled-pos 0 -10 0))))

(reg-rule
  :player/spell-wind-tornado
  {:locals {:temp-wind-tornado-disabled-pos (v3)}
   :what {:keys-pressed {}
          :player/mobile-wind-sorcery-click? {}
          :player/capsule {:then false}
          :player/current-health {:then false}}
   :when (fn [{{game-started? :player/game-started?
                primary-element :player/primary-element
                secondary-element :player/secondary-element
                dash? :player/dash?} :session}]
           (and game-started?
                (or (= primary-element :wind)
                    (= secondary-element :wind))
                (not dash?)
                (not (casting-spell?))
                (not (freezing?))
                (cooldown-finished? :spell-wind-tornado)))
   :then (fn [{{air-prev-key-sorcery-pressed? :player/air-prev-key-sorcery-pressed?
                player-model :player/model
                keys-pressed :keys-pressed
                temp-wind-tornado :player/temp-wind-tornado
                wind-tornado-position :player/wind-tornado-position
                primary-element :player/primary-element
                secondary-element :player/secondary-element
                mobile? :game/mobile?
                mobile-wind-sorcery-click? :player/mobile-wind-sorcery-click?} :session
               {:keys [temp-wind-tornado-disabled-pos]} :locals}]
           (let [key-code (cond
                            (= primary-element :wind) "KeyQ"
                            (= secondary-element :wind) "KeyE")
                 not-pressed? (if mobile?
                                (not mobile-wind-sorcery-click?)
                                (not (keys-pressed key-code)))]
             (some-> temp-wind-tornado (api.core/set-enabled false))
             (when (and air-prev-key-sorcery-pressed? not-pressed?)
               (when wind-tornado-position
                 (notify-enemies wind-tornado-position :wind)
                 (re/fire-rules {:player/spell-wind-tornado? true})
                 (api.sound/play :sound/wind-tornado-cast {:position (api.core/get-pos player-model)})
                 (js/setTimeout
                   (fn []
                     (send-wind-tornado wind-tornado-position)
                     (add-cooldown :spell-wind-tornado))
                   1000))
               (when air-prev-key-sorcery-pressed?
                 (move-temp-wind-tornado-wrapper temp-wind-tornado temp-wind-tornado-disabled-pos)))
             (re/insert {:player/air-prev-key-sorcery-pressed? (if mobile?
                                                                 mobile-wind-sorcery-click?
                                                                 (keys-pressed key-code))})))})

(reg-rule
  :player/position-wind-tornado
  {:locals {:temp-wind-tornado-pos (v3)
            :start (v3)
            :end (v3)
            :collide-with #js {:collideWith api.const/collision-group-environment}
            :result (api.physics/raycast-result)}
   :what {:dt {}
          :keys-pressed {:then false}
          :player/current-health {:then false}
          :player/temp-wind-tornado {:then false}
          :camera {:then false}}
   :when (fn [{{game-started? :player/game-started?
                primary-element :player/primary-element
                secondary-element :player/secondary-element
                dash? :player/dash?} :session}]
           (and game-started?
                (or (= primary-element :wind)
                    (= secondary-element :wind))
                (not dash?)
                (not (casting-spell?))
                (not (freezing?))
                (cooldown-finished? :spell-wind-tornado)))
   :then (fn [{{camera :camera
                keys-pressed :keys-pressed
                temp-wind-tornado :player/temp-wind-tornado
                mobile-wind-sorcery-click? :player/mobile-wind-sorcery-click?
                primary-element :player/primary-element
                secondary-element :player/secondary-element} :session
               {:keys [temp-wind-tornado-pos start end result collide-with]} :locals}]
           (let [key-code (cond
                            (= primary-element :wind) "KeyQ"
                            (= secondary-element :wind) "KeyE")]
             (if (or (keys-pressed key-code) mobile-wind-sorcery-click?)
               (let [mat-tornado (api.asset/get-asset :material/tornado)
                     mat-temp-tornado-disallowed (api.asset/get-asset :material/tornado-disallowed)
                     mat-temp-tornado-cylinder (api.asset/get-asset :material/wind-tornado-mat)
                     r (api.core/create-picking-ray camera)
                     start (api.core/set-v3 start (j/get r :origin))
                     dir (j/get r :direction)
                     length 100
                     end (j/call (api.core/set-v3 end start) :addInPlace (j/call dir :scaleInPlace length))
                     _ (api.physics/raycast-to-ref start end result collide-with)]
                 (api.core/set-enabled temp-wind-tornado true)
                 (if (j/get result :hasHit)
                   (let [point (j/get result :hitPoint)
                         distance (j/get result :hitDistance)
                         normal (j/get result :hitNormal)
                         normal-y (j/get normal :y)
                         temp-wind-tornado-pos (api.core/set-v3 temp-wind-tornado-pos point)
                         temp-tornado-cylinder (j/get (api.core/get-child-meshes temp-wind-tornado) 0)
                         body (j/get temp-wind-tornado :body)
                         _ (j/assoc! body :disablePreStep false)
                         collided? (j/get body :collided?)
                         tn (j/get temp-wind-tornado :parent)]
                     (j/assoc! tn :position (j/update! temp-wind-tornado-pos :y + 7))
                     (if (or (< normal-y 0.4) (not (< 6 distance 40)) collided?)
                       (do
                         (re/insert {:player/wind-tornado-position nil})
                         (j/assoc! temp-wind-tornado :material mat-temp-tornado-disallowed)
                         (j/assoc! temp-tornado-cylinder :material mat-temp-tornado-disallowed))
                       (do
                         (j/assoc! temp-wind-tornado :material mat-tornado)
                         (j/assoc! temp-tornado-cylinder :material mat-temp-tornado-cylinder)
                         (re/insert {:player/wind-tornado-position (api.core/clone point)}))))
                   (re/insert {:player/wind-tornado-position nil})))
               (let [body (j/get temp-wind-tornado :body)]
                 (j/assoc! body :disablePreStep true)
                 (api.core/set-enabled temp-wind-tornado false)))))})

(defn- send-ice-tornado [pos]
  (dispatch-pro :ice-tornado {:pos (api.core/v3->v pos)}))

(defn- throw-ice-tornado [pos]
  (let [tornado (re/pop-from-pool :pool/ice-tornado)
        sparkle-ps (re/pop-from-pool :pool/particle-snowflake)]
    (api.sound/play :sound/ice-tornado-wind {:position pos})
    (api.particle/start-ps sparkle-ps {:emitter tornado})
    (api.core/set-enabled tornado true)
    (j/assoc! tornado :position pos)
    (re/upsert :player/tornados-to-spin (fnil conj #{}) tornado)
    (api.tween/tween {:from {:alpha 0}
                      :to {:alpha 1}
                      :duration 200
                      :on-update (fn [x]
                                   (j/assoc! tornado :visibility (j/get x :alpha)))})
    (api.tween/tween {:from {:alpha 1}
                      :to {:alpha 0}
                      :delay 2000
                      :duration 200
                      :on-update (fn [x]
                                   (j/assoc! tornado :visibility (j/get x :alpha)))
                      :on-end (fn []
                                (re/upsert :player/tornados-to-spin disj tornado)
                                (api.core/set-enabled tornado false)
                                (api.particle/push-to-pool :pool/particle-snowflake sparkle-ps)
                                (re/push-to-pool :pool/ice-tornado tornado))})))

(reg-rule
  :player/spell-ice-tornado
  {:locals {:temp-pos (v3)}
   :what {:keys-pressed {}
          :player/mobile-ice-sorcery-click? {}
          :player/spell? {:then false}
          :player/capsule {:then false}
          :player/current-health {:then false}
          :player/spell-super-nova? {:then false}}
   :when (fn [{{game-started? :player/game-started?
                primary-element :player/primary-element
                secondary-element :player/secondary-element
                dash? :player/dash?} :session}]
           (and game-started?
                (or (= primary-element :ice)
                    (= secondary-element :ice))
                (not dash?)
                (not (casting-spell?))
                (not (freezing?))
                (cooldown-finished? :spell-ice-tornado)))
   :then (fn [{{mobile? :game/mobile?
                mobile-ice-sorcery-click? :player/mobile-ice-sorcery-click?
                ice-prev-key-sorcery-pressed? :player/ice-prev-key-sorcery-pressed?
                keys-pressed :keys-pressed
                player-capsule :player/capsule
                primary-element :player/primary-element
                secondary-element :player/secondary-element} :session
               {:keys [temp-pos]} :locals}]
           (let [key-code (cond
                            (= primary-element :ice) "KeyQ"
                            (= secondary-element :ice) "KeyE")
                 pressed? (if mobile?
                            mobile-ice-sorcery-click?
                            (keys-pressed key-code))]
             (when (and ice-prev-key-sorcery-pressed? (not pressed?))
               (let [pos (j/update! (api.core/set-v3 temp-pos (api.core/get-pos player-capsule)) :y + 1.5)]
                 (notify-enemies pos :ice)
                 (re/fire-rules {:player/spell-ice-tornado? true})
                 (js/setTimeout
                   (fn []
                     (let [final-pos (j/update! (api.core/set-v3 temp-pos (api.core/get-pos player-capsule)) :y + 1.5)]
                       (throw-ice-tornado final-pos)
                       (send-ice-tornado final-pos))
                     (add-cooldown :spell-ice-tornado))
                   1000)))
             (re/insert {:player/ice-prev-key-sorcery-pressed? pressed?})))})

(let [start (v3)
      end (v3)
      result-ray (api.physics/raycast-result)
      query #js {:shouldHitTriggers true}]
  (defn- throw-ice-arrow-and-create-trail [camera dir result]
    (let [player-pos (api.core/clone (api.core/get-pos (re/query :player/capsule)))
          player-pos (j/update! player-pos :y + 0.3)
          right (-> (j/call camera :getDirection api.const/v3-right)
                    (j/call :scaleInPlace 0.1))
          player-pos (j/call player-pos :addInPlace right)
          ray-length (if (> (j/get result :distance) 0)
                       (j/get result :distance)
                       (let [start (api.core/set-v3 start player-pos)
                             dir (api.camera/get-direction-scaled camera 100)
                             end (j/call (api.core/set-v3 end player-pos) :addInPlace dir)
                             _ (api.physics/raycast-to-ref start end result-ray query)]
                         (or (j/get result-ray :hitDistance) 100)))
          dir (j/call (api.core/normalize (api.core/clone dir)) :scale ray-length)
          end (j/call player-pos :add dir)
          arrow-trail-ball (re/pop-from-pool :pool/arrow-trail-ball)
          arrow-trail-ball (j/assoc! arrow-trail-ball :position player-pos)
          _ (api.core/compute-world-matrix arrow-trail-ball)
          trail-mesh (j/get arrow-trail-ball :trail-mesh)
          anim (api.anim/animation {:anim-name "arrow-trail"
                                    :from player-pos
                                    :to end
                                    :target-prop "position"
                                    :data-type api.const/animation-type-v3
                                    :loop-mode api.const/animation-loop-cons})]
      (j/call trail-mesh :reset)
      (j/call trail-mesh :stop)
      (j/call trail-mesh :start)
      (api.anim/begin-direct-animation
        {:target arrow-trail-ball
         :animations anim
         :speed-ratio 2
         :to 60
         :on-animation-end (fn []
                             (j/call trail-mesh :reset)
                             (j/call trail-mesh :stop)
                             (re/push-to-pool :pool/arrow-trail-ball arrow-trail-ball))}))))

(let [start (v3)
      end (v3)
      query #js {:shouldHitTriggers true}
      result (api.physics/raycast-result)]
  (defn- send-ice-arrow [camera drag-factor]
    (let [player-offset-box (re/query :player/offset-box)
          new-pos (api.core/get-pos player-offset-box)
          start (api.core/set-v3 start new-pos)
          dir (api.camera/get-direction-scaled camera 1000)
          end (j/call (api.core/set-v3 end new-pos) :addInPlace dir)
          _ (api.physics/raycast-to-ref start end result query)]
      (if-let [player-id (m/get result :body :transformNode :parent :player-id)]
        (dispatch-pro :ice-arrow {:id player-id
                                  :pos (api.core/v3->v (j/get result :hitPoint))
                                  :drag-factor drag-factor})
        (dispatch-pro :ice-arrow))
      (throw-ice-arrow-and-create-trail camera dir result))))

(defn create-arrow-trail-ball []
  (let [ball (api.mesh/sphere {:name "arrow-trail-ball"
                               :diameter 0.1
                               :segments 2
                               :visible? false
                               :pickable? false})
        arrow-trail (api.core/trail-mesh {:name "arrow-trail-mesh"
                                          :pickable? false
                                          :diameter 0.2
                                          :length 30
                                          :auto-start? false
                                          :generator ball})
        arrow-trail-mat (api.asset/get-asset :material/arrow-trail)]
    (j/assoc! arrow-trail :material arrow-trail-mat)
    (j/assoc! ball :trail-mesh arrow-trail)))

(defn init-arrow-trail-balls []
  (re/register-item-creation :pool/arrow-trail-ball create-arrow-trail-ball))

(reg-rule
  :player/spell-ice-arrow
  {:what {:player/ice-arrow-fov {}
          :camera {:then false}
          :mouse/right-click? {:then false}
          :mouse/left-click? {:then false}
          :player/capsule {:then false}
          :player/current-health {:then false}}
   :when (fn [{{mobile? :game/mobile?
                game-started? :player/game-started?
                right-click? :mouse/right-click?
                left-click? :mouse/left-click?
                dash? :player/dash?
                primary-element :player/primary-element
                secondary-element :player/secondary-element
                mana :player/mana} :session}]
           (and game-started?
                (or (and (not mobile?)
                         (not (or (and left-click? (= primary-element :ice))
                                  (and right-click? (= secondary-element :ice)))))
                    mobile?)
                (not dash?)
                (not (casting-spell?))
                (not (freezing?))
                (> mana (:ice-arrow mana-costs))))
   :then (fn [{{player-ice-arrow-fov :player/ice-arrow-fov
                player-offset-box :player/offset-box
                camera :camera} :session}]
           (let [drag-factor (* (- api.camera/fov player-ice-arrow-fov)
                                (/ 100 (- api.camera/fov api.camera/zoom-fov)))]
             (send-ice-arrow camera drag-factor)
             (re/fire-rules {:player/spell-ice-arrow? true
                             :player/air-time 0})
             (let [arrow-sparkle-ps (re/pop-from-pool :pool/particle-arrow-sparkles)]
               (j/call-in arrow-sparkle-ps [:onStoppedObservable :addOnce]
                          (fn []
                            (api.particle/push-to-pool :pool/particle-arrow-sparkles arrow-sparkle-ps)))
               (api.particle/start-ps arrow-sparkle-ps {:emitter player-offset-box
                                                        :delay 100}))
             (api.sound/play :sound/ice-whoosh {:position (api.core/get-pos player-offset-box)})
             (consume-mana (* (:ice-arrow mana-costs) (+ 1 (/ drag-factor 100))))))})

(defn init-spell-wind-emitter []
  (re/register-item-creation :pool/spell-wind-emitter (fn []
                                                        (api.mesh/sphere {:name "spell-wind-emitter"
                                                                          :visible? false
                                                                          :diameter 0.1}))))

(defn throw-wind-slash [{:keys [start end on-end duration]}]
  (let [wind-slash-ps (re/pop-from-pool :pool/particle-wind-slash)
        ball (re/pop-from-pool :pool/spell-wind-emitter)]
    (api.particle/start-ps wind-slash-ps {:emitter ball})
    (api.sound/play :sound/wind-slash {:position (api.core/get-pos ball)})
    (api.tween/tween {:duration (or duration 100)
                      :from {:x (j/get start :x)
                             :y (j/get start :y)
                             :z (j/get start :z)}
                      :to {:x (j/get end :x)
                           :y (j/get end :y)
                           :z (j/get end :z)}
                      :on-update (fn [v]
                                   (m/assoc! ball
                                             :position.x (j/get v :x)
                                             :position.y (j/get v :y)
                                             :position.z (j/get v :z)))
                      :on-end (fn []
                                (api.particle/stop wind-slash-ps)
                                (api.particle/push-to-pool :pool/particle-wind-slash wind-slash-ps)
                                (re/push-to-pool :pool/spell-wind-emitter ball)
                                (when on-end (on-end)))})))

(reg-rule
  :player/spell-wind-slash
  {:locals {:start (v3)
            :end (v3)
            :end-for-vfx (v3)
            :query #js {:shouldHitTriggers true}
            :result (api.physics/raycast-result)
            :wind-jump-vec (v3 0 1000 0)}
   :what {:mouse/right-click? {}
          :mouse/left-click? {}
          :player/mobile-wind-click? {}
          :camera {:then false}
          :player/capsule {:then false}
          :player/current-health {:then false}}
   :when (fn [{{game-started? :player/game-started?
                mobile? :game/mobile?
                mobile-wind-click? :player/mobile-wind-click?
                mouse-right-click? :mouse/right-click?
                mouse-left-click? :mouse/left-click?
                dash? :player/dash?
                primary-element :player/primary-element
                secondary-element :player/secondary-element
                mana :player/mana} :session}]
           (and game-started?
                (or (and (not mobile?)
                         (or (and mouse-left-click? (= primary-element :wind))
                             (and mouse-right-click? (= secondary-element :wind))))
                    (and mobile? mobile-wind-click?))
                (not dash?)
                (not (casting-spell?))
                (not (freezing?))
                (> mana (:wind-slash mana-costs))))
   :then (fn [{{player-offset-box :player/offset-box
                last-time-spell-wind-slash :player/last-time-spell-wind-slash
                spell-wind-slash-count :player/spell-wind-slash-count
                camera :camera} :session
               {:keys [start end end-for-vfx result query wind-jump-vec]} :locals}]
           (let [new-pos (api.core/get-pos player-offset-box)
                 start (api.core/set-v3 start new-pos)
                 dir-for-vfx (api.camera/get-direction-scaled camera 50)
                 end-for-vfx (j/call (api.core/set-v3 end-for-vfx new-pos) :addInPlace dir-for-vfx)
                 dir (api.camera/get-direction-scaled camera 500)
                 end (j/call (api.core/set-v3 end new-pos) :addInPlace dir)
                 _ (api.physics/raycast-to-ref start end result query)
                 player-pos (api.core/clone (api.core/get-pos (re/query :player/capsule)))
                 player-pos (j/update! player-pos :y + 0.3)
                 right (-> (j/call camera :getDirection api.const/v3-right)
                           (j/call :scaleInPlace 0.1))
                 player-pos (j/call player-pos :addInPlace right)
                 normalize-dir (api.camera/get-direction-scaled camera 1)
                 hit-point (atom nil)]
             (re/fire-rules {:player/spell-wind-slash? true
                             :player/last-time-spell-wind-slash (js/Date.now)
                             :player/spell-wind-slash-count (if (> (- (js/Date.now) last-time-spell-wind-slash) 1000)
                                                              0
                                                              spell-wind-slash-count)
                             :player/air-time 0})
             (re/upsert :player/spell-wind-slash-count inc)
             (throw-wind-slash {:start player-pos
                                :end end-for-vfx
                                :duration 500})
             (consume-mana (:wind-slash mana-costs))
             (if (j/get result :hasHit)
               (let [point (j/get result :hitPoint)]
                 (if-let [player-id (m/get result :body :transformNode :parent :player-id)]
                   (dispatch-pro :wind-slash {:player-id player-id
                                              :dir (api.core/v3->v normalize-dir)
                                              :distance (api.core/distance point player-pos)})
                   (do
                     (dispatch-pro :wind-slash {:dir (api.core/v3->v normalize-dir)})
                     (reset! hit-point point)))
                 (let [wind-hit-ps (re/pop-from-pool :pool/particle-wind-hit)]
                   (api.particle/start-ps wind-hit-ps {:emitter point
                                                       :auto-pool :pool/particle-wind-hit})
                   (api.sound/play :sound/wind-hit {:position point}))
                 (when (< (j/get normalize-dir :y) -0.8)
                   (let [player-capsule (re/query :player/capsule)
                         player-pos (api.core/get-pos player-capsule)]
                     (api.physics/apply-impulse player-capsule wind-jump-vec player-pos))))
               (dispatch-pro :wind-slash {:dir (api.core/v3->v normalize-dir)}))))})

(reg-rule
  :player/spell-light-staff
  {:locals {:start (v3)
            :end (v3)
            :query #js {:shouldHitTriggers true}
            :result (api.physics/raycast-result)}
   :what {:dt {}
          :mouse/left-click? {:then false}
          :mouse/right-click? {:then false}
          :camera {:then false}
          :player/capsule {:then false}
          :player/current-health {:then false}
          :player/particle-light-staff-emitter {:then false}}
   :when (fn [{{game-started? :player/game-started?
                dash? :player/dash?} :session}]
           (and game-started?
                (not dash?)
                (not (casting-spell? {:except :player/spell-light-staff?}))
                (not (freezing?))))
   :then (fn [{{dt :dt
                player-model :player/model
                mouse-left-click? :mouse/left-click?
                mouse-right-click? :mouse/right-click?
                player-offset-box :player/offset-box
                particle-light-staff :player/particle-light-staff
                emitter-light-staff :player/particle-light-staff-emitter
                primary-element :player/primary-element
                secondary-element :player/secondary-element
                spell-light-staff? :player/spell-light-staff?
                mobile? :game/mobile?
                mobile-light-click? :player/mobile-light-click?
                mana :player/mana
                camera :camera} :session
               {:keys [start end result query]} :locals}]
           (if (and
                 (> mana (* dt (:light-staff mana-costs)))
                 (or (and (not mobile?)
                          (or (and mouse-left-click? (= primary-element :light))
                              (and mouse-right-click? (= secondary-element :light))))
                     (and mobile? mobile-light-click?)))
             (do
               (when-not (api.sound/playing? :sound/electric-light-staff-player)
                 (api.sound/play :sound/electric-light-staff-player {:position (api.core/get-pos player-model)}))
               (when-not (api.particle/started? particle-light-staff)
                 (api.particle/start-ps particle-light-staff {:emitter emitter-light-staff}))
               (let [right-hand-tn (api.core/find-bone-tn player-model "mixamorig:RightHand")
                     new-pos (api.core/get-pos player-offset-box)
                     start (api.core/set-v3 start new-pos)
                     dir (api.camera/get-direction-scaled camera 1000)
                     end (j/call (api.core/set-v3 end new-pos) :addInPlace dir)
                     _ (api.physics/raycast-to-ref start end result query)]
                 (if (j/get result :hasHit)
                   (let [start (api.core/set-v3 start (api.core/get-pos right-hand-tn))
                         point (j/get result :hitPoint)
                         distance (api.core/distance start point)
                         midpoint (-> start
                                      (j/call :add point)
                                      (j/call :scale 0.5))]
                     (j/assoc! emitter-light-staff :position midpoint)
                     (j/call emitter-light-staff :lookAt point)
                     (j/assoc! particle-light-staff
                               :minScaleY distance
                               :maxScaleY distance)
                     (if-let [player-id (m/get result :body :transformNode :parent :player-id)]
                       (re/upsert :player/light-staff-hits (fn [light-staff-hits]
                                                             (conj (or light-staff-hits []) {:player-id player-id
                                                                                             :point (api.core/v3->v point)
                                                                                             :dt dt})))
                       (re/upsert :player/light-staff-hits (fn [light-staff-hits]
                                                             (conj (or light-staff-hits []) {:point (api.core/v3->v point)}))))
                     (consume-mana (* 0.6 dt (:light-staff mana-costs))))))
               (re/fire-rules {:player/spell-light-staff? true
                               :player/air-time 0}))
             (when spell-light-staff?
               (stop-light-staff-effects particle-light-staff))))})

(reg-rule
  :player/drag-ice-arrow
  {:what {:dt {}
          :mouse/right-click? {:then false}
          :mouse/left-click? {:then false}
          :camera {:then false}
          :player/capsule {:then false}
          :player/current-health {:then false}}
   :when (fn [{{game-started? :player/game-started?} :session}]
           game-started?)
   :then (fn [{{dt :dt
                camera :camera
                mobile? :game/mobile?
                mobile-ice-click? :player/mobile-ice-click?
                right-click? :mouse/right-click?
                left-click? :mouse/left-click?
                primary-element :player/primary-element
                secondary-element :player/secondary-element
                first-time-right-click :player/first-time-right-click
                started-bow-draw? :player/started-bow-draw?} :session}]
           (if (and (or (and (not mobile?)
                             (or (and left-click? (= primary-element :ice))
                                 (and right-click? (= secondary-element :ice))))
                        (and mobile? mobile-ice-click?))
                    (not (casting-spell?))
                    (not (freezing?))
                    (cooldown-finished? :spell-ice-arrow))
             (do
               (when-not first-time-right-click
                 (re/insert :player/first-time-right-click (js/Date.now)))
               (when (and first-time-right-click (> (- (js/Date.now) first-time-right-click) 200))
                 (j/update! camera :fov #(Math/max api.camera/zoom-fov (- % dt)))
                 (re/insert :player/dragging? true)
                 (when (= (j/get camera :fov) api.camera/zoom-fov)
                   (api.camera/smooth-zoom-sensibility camera))
                 (when (and (not started-bow-draw?)
                            (not (api.sound/playing? :sound/bow-draw)))
                   (re/insert {:player/started-bow-draw? true})
                   (api.sound/play :sound/bow-draw))))
             (do
               (when first-time-right-click
                 (re/fire-rules :player/ice-arrow-fov (j/get camera :fov))
                 (api.camera/reset-camera-sensibility camera))
               (re/insert {:player/first-time-right-click nil
                           :player/dragging? false
                           :player/started-bow-draw? false})
               (api.sound/stop :sound/bow-draw)
               (when-not (= (j/get camera :fov) api.camera/fov)
                 (j/update! camera :fov #(Math/min api.camera/fov (+ % (* dt 3))))))))})

(defn- throw-rock-wall [position rotation]
  (let [rock-wall (re/pop-from-pool :pool/rock-wall)
        pos (api.core/clone position)
        init-pos (j/update! pos :y - 5)]
    (api.sound/play :sound/rock-wall {:position pos})
    (j/assoc! rock-wall :rotation rotation)
    (api.core/set-enabled rock-wall true)
    (api.tween/tween {:target [rock-wall :position]
                      :duration 500
                      :easing :back/out
                      :from init-pos
                      :to (api.core/clone position)})
    (js/setTimeout
      (fn []
        (api.core/set-enabled rock-wall false)
        (j/assoc! rock-wall :position (v3 0 -20 0))
        (re/push-to-pool :pool/rock-wall rock-wall))
      7500)))

(reg-rule
  :player/spell-rock-wall
  {:what {:keys-pressed {}
          :player/mobile-earth-sorcery-click? {}
          :player/current-health {:then false}
          :player/spell-rock-wall? {:then false}}
   :when (fn [{{game-started? :player/game-started?
                primary-element :player/primary-element
                secondary-element :player/secondary-element
                dash? :player/dash?} :session}]
           (and game-started?
                (or (= primary-element :earth)
                    (= secondary-element :earth))
                (not dash?)
                (not (casting-spell?))
                (not (freezing?))
                (cooldown-finished? :spell-rock-wall)))
   :then (fn [{{rock-prev-key-sorcery-pressed? :player/rock-prev-key-sorcery-pressed?
                keys-pressed :keys-pressed
                temp-rock-wall :player/temp-rock-wall
                rock-wall-position :player/rock-wall-position
                rock-wall-rotation :player/rock-wall-rotation
                primary-element :player/primary-element
                secondary-element :player/secondary-element
                last-time-spell-rock-wall :player/last-time-spell-rock-wall
                mobile? :game/mobile?
                mobile-earth-sorcery-click? :player/mobile-earth-sorcery-click?} :session}]
           (let [key-code (cond
                            (= primary-element :earth) "KeyQ"
                            (= secondary-element :earth) "KeyE")
                 not-pressed? (if mobile?
                                (not mobile-earth-sorcery-click?)
                                (not (keys-pressed key-code)))]
             (some-> temp-rock-wall (api.core/set-enabled false))
             (when (and rock-prev-key-sorcery-pressed? not-pressed?)
               (when rock-wall-position
                 (re/fire-rules {:player/spell-rock-wall? true})
                 (dispatch-pro :rock-wall {:pos (api.core/v3->v rock-wall-position)
                                           :rot (api.core/v3->v rock-wall-rotation)})
                 (throw-rock-wall rock-wall-position rock-wall-rotation)
                 (re/upsert :player/spell-rock-wall-count (fnil inc 0))
                 (re/insert :player/last-time-spell-rock-wall (js/Date.now))
                 (cond
                   (and last-time-spell-rock-wall
                        (> (- (js/Date.now) last-time-spell-rock-wall)
                           (get-in cooldowns [:cooldown/spell-rock-wall :duration]))
                        (= 2 (re/query :player/spell-rock-wall-count)))
                   (re/insert :player/spell-rock-wall-count 1)

                   (= 2 (re/query :player/spell-rock-wall-count))
                   (do
                     (add-cooldown :spell-rock-wall)
                     (re/insert :player/spell-rock-wall-count 0)))))
             (re/insert {:player/rock-prev-key-sorcery-pressed? (if mobile?
                                                                  mobile-earth-sorcery-click?
                                                                  (keys-pressed key-code))})))})

(reg-rule
  :player/position-rock-wall
  {:locals {:temp-rock-wall-pos (v3)
            :rock-wall-pos (v3)
            :start (v3)
            :end (v3)
            :collide-with #js {:collideWith api.const/collision-group-environment}
            :result (api.physics/raycast-result)}
   :what {:dt {}
          :keys-pressed {:then false}
          :player/spell-rock-wall? {:then false}
          :player/current-health {:then false}
          :player/fire-ball-sphere {:then false}
          :camera {:then false}}
   :when (fn [{{game-started? :player/game-started?
                primary-element :player/primary-element
                secondary-element :player/secondary-element
                dash? :player/dash?} :session}]
           (and game-started?
                (or (= primary-element :earth)
                    (= secondary-element :earth))
                (not (freezing?))
                (not dash?)
                (not (casting-spell?))
                (cooldown-finished? :spell-rock-wall)))
   :then (fn [{{camera :camera
                keys-pressed :keys-pressed
                temp-rock-wall :player/temp-rock-wall
                primary-element :player/primary-element
                secondary-element :player/secondary-element
                mobile-earth-sorcery-click? :player/mobile-earth-sorcery-click?} :session
               {:keys [temp-rock-wall-pos start end result collide-with]} :locals}]
           (let [temp-rock-wall (if temp-rock-wall
                                  temp-rock-wall
                                  (let [temp-rock-wall (re/pop-from-pool :pool/rock-wall)
                                        material (api.core/clone (j/get temp-rock-wall :material))]
                                    (api.core/dispose (j/get temp-rock-wall :agg))
                                    (j/assoc! temp-rock-wall :visibility 0.5
                                              :name "temp-rock-wall")
                                    (j/assoc! temp-rock-wall :temp-material material)
                                    (j/assoc! temp-rock-wall)
                                    (re/insert {:player/temp-rock-wall temp-rock-wall})
                                    temp-rock-wall))
                 key-code (cond
                            (= primary-element :earth) "KeyQ"
                            (= secondary-element :earth) "KeyE")]
             (if (or (keys-pressed key-code) mobile-earth-sorcery-click?)
               (let [mat-rock-wall (j/get temp-rock-wall :temp-material)
                     mat-rock-wall-not-allowed (api.asset/get-asset :material/fire-nova-disallowed)
                     r (api.core/create-picking-ray camera)
                     start (api.core/set-v3 start (j/get r :origin))
                     dir (j/get r :direction)
                     length 50
                     end (j/call (api.core/set-v3 end start) :addInPlace (j/call dir :scaleInPlace length))
                     _ (api.physics/raycast-to-ref start end result collide-with)
                     forward-dir (j/call camera :getDirection api.const/v3-forward)
                     right-dir (j/call camera :getDirection api.const/v3-right)
                     angle (Math/atan2 (.-x forward-dir)
                                       (.-z forward-dir))]
                 (api.core/set-enabled temp-rock-wall true)
                 (if (j/get result :hasHit)
                   (let [point (j/get result :hitPoint)
                         distance (j/get result :hitDistance)
                         normal (j/get result :hitNormal)
                         normal-y (j/get normal :y)
                         temp-rock-wall-pos (api.core/set-v3 temp-rock-wall-pos point)
                         temp-rock-wall-pos (j/call temp-rock-wall-pos :addInPlace (j/call right-dir :scale 5))
                         rot (v3 (api.core/to-rad 90) angle 0)]
                     (println distance)
                     (j/assoc! temp-rock-wall
                               :position temp-rock-wall-pos
                               :rotation rot)
                     (if (or (< normal-y 0.6)
                             (<= distance 5)
                             (> distance 30))
                       (do
                         (re/insert {:player/rock-wall-position nil})
                         (j/assoc! temp-rock-wall :material mat-rock-wall-not-allowed))
                       (do
                         (j/assoc! temp-rock-wall :material mat-rock-wall)
                         (re/insert {:player/rock-wall-position temp-rock-wall-pos
                                     :player/rock-wall-rotation rot}))))
                   (re/insert {:player/rock-wall-position nil})))
               (api.core/set-enabled temp-rock-wall false))))})

(reg-rule
  :player/apply-offscreen-damage-arrow
  {:what {:player/show-offscreen-damage-arrow {}
          :image/offscreen-damage {:then false}}
   :then (fn [{{:keys [image/offscreen-damage player/show-offscreen-damage-tween]} :session}]
           (j/assoc! offscreen-damage
                     :isVisible true
                     :alpha 1)
           (some-> show-offscreen-damage-tween api.tween/stop)
           (re/insert {:player/show-offscreen-damage-tween
                       (api.tween/tween {:from {:alpha 1}
                                         :to {:alpha 0}
                                         :duration 1500
                                         :on-update (fn [x]
                                                      (j/assoc! offscreen-damage :alpha (j/get x :alpha)))
                                         :on-end (fn []
                                                   (j/assoc! offscreen-damage
                                                             :isVisible false
                                                             :alpha 0))})}))})

(reg-rule
  :player/update-offscreen-damage-arrow
  {:locals {:temp-pos (v3)}
   :what {:dt {}
          :camera {}
          :player/offset-box {:then false}
          :player/show-offscreen-damage-arrow {:then false}
          :image/offscreen-damage {:then false}}
   :when (fn [{{:keys [image/offscreen-damage]} :session}]
           (j/get offscreen-damage :isVisible))
   :then (fn [{{:keys [camera player/offset-box player/show-offscreen-damage-arrow image/offscreen-damage]} :session
               {:keys [temp-pos]} :locals}]
           (let [enemy-pos (:enemy-position show-offscreen-damage-arrow)
                 offset-box-pos (api.core/set-v3 temp-pos (api.core/get-pos offset-box))
                 player-pos (j/assoc! offset-box-pos :y 0)
                 direction (api.core/normalize (j/call enemy-pos :subtract player-pos))
                 camera-forward (-> camera
                                    (j/call :getForwardRay)
                                    (j/get :direction))
                 angle (Math/acos (api.core/dot camera-forward direction))
                 cross (api.core/cross camera-forward direction)
                 angle (if (< (j/get cross :y) 0)
                         (- angle)
                         angle)]
             (j/assoc! offscreen-damage :rotation angle)))})

(defn add-mana [mana]
  (re/upsert :player/mana #(Math/min total-mana (if (booster-active? :booster_regen_mana)
                                                  (+ % (* mana 1.25))
                                                  (+ % mana)))))

(reg-rule
  :player/mana-regen
  {:what {:dt {}
          :player/capsule {:then false}
          :player/mana {:then false}}
   :when (fn [{{:keys [player/game-started?
                       player/levitate?
                       player/inside-map?
                       player/mana]} :session}]
           (and game-started?
                inside-map?
                (not (freezing?))
                (not levitate?)
                (not (casting-spell?))
                (< mana total-mana)))
   :then (fn [{{:keys [player/levitate-last-off-time
                       player/ground?
                       player/mana-regen-unfreeze-time]} :session}]
           (let [factor (if ground? 2.0 1.0)
                 regen (* factor (api.core/get-anim-ratio))]
             (when (and (> (- (js/Date.now) levitate-last-off-time) 150)
                        (or (nil? mana-regen-unfreeze-time)
                            (> (js/Date.now) mana-regen-unfreeze-time)))
               (add-mana regen))))})

(reg-rule
  :player/levitate
  {:disabled? false
   :locals {:temp-velocity (v3)
            :shut-down-levitate? (atom false)}
   :what {:dt {}
          :player/capsule {:then false}
          :keys-pressed {:then false}
          :player/ground? {:then false}
          :player/mana {:then false}
          :player/levitate? {:then false}
          :particle/levitate {:then false}}
   :when (fn [{{:keys [player/game-started?
                       player/dash?
                       player/dragging?]} :session}]
           (and game-started?
                (not (freezing?))
                (not (wind-stunned?))
                (not dash?)
                (not dragging?)
                (not (casting-spell?))))
   :then (fn [{{:keys [dt
                       keys-pressed
                       player/capsule
                       player/jump-up?
                       player/levitate?
                       player/mobile-jump-click?
                       player/mana]
                particle-levitate :particle/levitate} :session
               {:keys [temp-velocity shut-down-levitate?]} :locals}]
           (let [y-up-force (* dt 50)
                 current-velocity (api.physics/get-linear-velocity capsule)
                 mana-cost (api.core/get-anim-ratio)]
             (cond
               (and (not jump-up?)
                    (or (keys-pressed "Space") mobile-jump-click?)
                    (> mana mana-cost))
               (let [new-velocity (api.core/set-v3 temp-velocity current-velocity)
                     new-velocity (j/update! new-velocity :y (partial + y-up-force))]
                 (when-not (api.sound/playing? :sound/levitate)
                   (api.sound/set-volume :sound/levitate 1)
                   (api.sound/play :sound/levitate {:time 0 :offset 0.2}))
                 (when (or (re/key-was-pressed? "Space") mobile-jump-click?)
                   (re/insert {:player/air-time 0
                               :player/levitate? true}))
                 (consume-mana mana-cost)
                 (api.physics/set-linear-velocity capsule new-velocity))

               (or (not (keys-pressed "Space"))
                   (not mobile-jump-click?))
               (do
                 (when (api.sound/playing? :sound/levitate)
                   (when-not @shut-down-levitate?
                     (reset! shut-down-levitate? true)
                     (api.tween/tween {:from {:volume 1}
                                       :to {:volume 0}
                                       :duration 300
                                       :on-update (fn [x]
                                                    (api.sound/set-volume :sound/levitate (j/get x :volume)))
                                       :on-end (fn []
                                                 (reset! shut-down-levitate? false)
                                                 (api.sound/stop :sound/levitate))})))
                 (when (or (re/key-was-pressed? "Space") mobile-jump-click?)
                   (re/insert {:player/levitate-last-off-time (js/Date.now)}))
                 (re/insert {:player/levitate? false}))

               :else (re/insert {:player/levitate? false}))

             (when (not= levitate? (re/query :player/levitate?))
               (if (re/query :player/levitate?)
                 (api.particle/start particle-levitate)
                 (api.particle/stop particle-levitate)))))})

(reg-rule
  :player/dash
  {:what {:camera {}
          :keys-pressed {}
          :player/mobile-dash-click? {}
          :pointer-locked? {:then false}
          :player/capsule {:then false}
          :player/model {:then false}
          :player/anim-groups {:then false}
          :player/ground? {:then false}
          :keys-was-pressed {:then false}
          :particle/dash {:then false}
          :particle/speed-line {:then false}
          :player/dash-trail {:then false}}
   :when (fn [{{:keys [player/game-started? player/mobile-dash-click?]} :session}]
           (and game-started?
                ;; (not (casting-spell?))
                (not (freezing?))
                (not (wind-stunned?))
                (cooldown-finished? :roll)
                (or (re/key-is-pressed? "ShiftLeft")
                    (re/key-is-pressed? "ShiftRight")
                    mobile-dash-click?)))
   :then (fn [{{camera :camera
                player-capsule :player/capsule
                particle-dash :particle/dash
                dash-trail :player/dash-trail
                player-backward? :player/backward?
                particle-speed-line :particle/speed-line} :session}]
           (let [speed-left-right 2000
                 speed-forward-backward 250
                 only-right? (player.animation/moving-only-right?)
                 only-left? (player.animation/moving-only-left?)
                 [speed normalize-dir] (cond
                                         only-right?
                                         [speed-left-right (j/call camera :getDirection api.const/v3-right)]

                                         only-left?
                                         [speed-left-right (j/call camera :getDirection api.const/v3-left)]


                                         :else
                                         [speed-forward-backward (if player-backward?
                                                                   (j/call (api.camera/get-direction-scaled camera 1) :negate)
                                                                   (api.camera/get-direction-scaled camera 1))])
                 _ (re/fire-rules {:player/dash? true
                                   :player/move-only-left-or-right? (or only-right? only-left?)
                                   :player/dash-dir normalize-dir})
                 player-pos (api.core/get-pos player-capsule)
                 impulse-speed (j/call normalize-dir :scaleInPlace speed)]
             (when (j/get particle-speed-line :stopped?)
               (j/assoc! particle-speed-line :stopped? false)
               (api.particle/start particle-speed-line))
             (j/call dash-trail :start)
             (j/assoc-in! dash-trail [:material :alpha] 0.5)
             (api.core/set-enabled dash-trail true)
             (api.particle/start particle-dash)
             (api.sound/play :sound/dash-sound)
             (api.physics/apply-impulse player-capsule impulse-speed player-pos)
             (add-cooldown :roll)
             (js/setTimeout (fn []
                              ;; (re/fire-rules  {:player/dash? false})
                              (j/call dash-trail :stop)
                              (api.tween/tween {:from {:alpha 0.5}
                                                :to {:alpha 0}
                                                :duration 1500
                                                :on-update (fn [x]
                                                             (j/assoc-in! dash-trail [:material :alpha] (j/get x :alpha)))
                                                :on-end (fn []
                                                          (j/call dash-trail :reset)
                                                          (api.core/set-enabled dash-trail false))})
                              (api.particle/stop particle-speed-line)) 500)))})

(reg-rule
  :player/dash-direction
  {:locals {:forward-temp (v3)
            :right-temp (v3)
            :result-temp (v3)}
   :what {:dt {}
          :player/dash? {:then false}
          :player/move-only-left-or-right? {:then false}
          :player/dash-dir {:then false}
          :camera {:then false}
          :player/capsule {:then false}
          :player/model {:then false}}
   :when (fn [{{:keys [player/game-started? player/dash? player/move-only-left-or-right?]} :session}]
           (and dash?
                game-started?
                (not move-only-left-or-right?)
                (not (freezing?))
                (not (wind-stunned?))))
   :then (fn [{{camera :camera
                player-capsule :player/capsule
                player-backward? :player/backward?} :session}]
           (let [normalize-dir (if player-backward?
                                 (j/call (api.camera/get-direction-scaled camera 1) :negate)
                                 (api.camera/get-direction-scaled camera 1))
                 player-pos (api.core/get-pos player-capsule)
                 speed (* (api.core/get-anim-ratio) 75)
                 impulse-speed (j/call normalize-dir :scaleInPlace speed)]
             (api.physics/apply-impulse player-capsule impulse-speed player-pos)))})

(reg-rule
  :camera/collision-check
  {:disabled? false
   :locals {:temp-pos (v3)
            :start (v3)
            :end (v3)
            :collide-with #js {:collideWith api.const/collision-group-environment}
            :result (api.physics/raycast-result)}
   :what {:dt {}
          :camera {:then false}
          :player/capsule {:then false}
          :player/current-health {:then false}}
   :then (fn [{{:keys [camera player/offset-box camera/vertical-shaking? player/current-health]} :session
               {:keys [temp-pos start end result collide-with]} :locals}]
           (when (> current-health 0)
             (let [right-dir (j/call camera :getDirection api.const/v3-right)
                   _ (api.core/set-v3 temp-pos 0 0 0)
                   new-pos (j/call temp-pos :addInPlace (j/call right-dir :scaleInPlace 0.5))
                   _ (m/assoc! offset-box :position new-pos)
                   _ (j/update-in! offset-box [:position :y] inc)
                   new-pos (api.core/get-pos offset-box)
                   dir (-> camera
                           (j/call :getForwardRay)
                           (j/get :direction)
                           (j/call :negate))
                   start (api.core/set-v3 start new-pos)
                   end (j/call (api.core/set-v3 end start) :addInPlace (j/call dir :scaleInPlace 2))
                   _ (api.physics/raycast-to-ref start end result collide-with)]
               (if (j/get result :hasHit)
                 (let [point (j/get result :hitPoint)]
                   (j/assoc! camera :position point))
                 (when-not vertical-shaking?
                   (j/assoc! camera :radius api.camera/default-radius)))))
           (re/fire-rules {:player/need-camera-detach? (= current-health 0)}))})

(reg-rule
  :player/check-camera-detach
  {:what {:player/need-camera-detach? {:when-value-change? true}}
   :then (fn [{{:keys [player/need-camera-detach?]} :session}]
           (if need-camera-detach?
             (detach-camera-from-target)
             (attach-camera-to-target)))})

(reg-rule
  :player/room-id-update
  {:what {:player/room-id {:allow-nil? true}}
   :then (fn [{{:keys [player/room-id]} :session}]
           (if room-id
             (do
               (api.sound/stop-audio-element "background-music"))
             (do
               (ads/hide-invite-button)
               (api.sound/play-audio-element "background-music"))))})

(reg-rule
  :player/calculate-ping
  {:what {:player/calculate-ping {}}
   :then (fn []
           (dispatch-pro :ping {:timestamp (js/Date.now)}))})

(reg-rule
  :player/calculate-ping
  {:what {:player/calculate-ping {}}
   :then (fn []
           (dispatch-pro :ping {:timestamp (js/Date.now)}))})

(reg-rule
  :player/fps
  {:what {:dt {}}
   :run-every 250
   :then (fn []
           (re/insert {:fps (some-> (api.core/get-engine)
                                    (j/call :getFps)
                                    (j/call :toFixed))}))})

(defn- show-damage-effect [effect-color]
  (let [d1 (js/document.getElementById "damage-effect-1")
        d2 (js/document.getElementById "damage-effect-2")
        color (case effect-color
                :white "white"
                :red "red"
                :blue "#4a9ad3")
        b1 (str "40px solid " color)
        b2 (str "20px solid " color)]
    (when d1
      (j/call-in d1 [:classList :add] "glow-effect")
      (j/assoc-in! d1 [:style :border] b1))
    (when d2
      (j/call-in d2 [:classList :add] "glow-effect")
      (j/assoc-in! d2 [:style :border] b2))
    (js/setTimeout (fn []
                     (when-let [d1 (js/document.getElementById "damage-effect-1")]
                       (j/call-in d1 [:classList :remove] "glow-effect"))
                     (when-let [d2 (js/document.getElementById "damage-effect-2")]
                       (j/call-in d2 [:classList :remove] "glow-effect"))) 1000)))

(defn show-heartbeat-effect []
  (let [d1 (js/document.getElementById "heartbeat-effect-1")
        d2 (js/document.getElementById "heartbeat-effect-2")
        color "red"
        b1 (str "40px solid " color)
        b2 (str "20px solid " color)]
    (when d1
      (j/call-in d1 [:classList :add] "glow-effect")
      (j/assoc-in! d1 [:style :border] b1))
    (when d2
      (j/call-in d2 [:classList :add] "glow-effect")
      (j/assoc-in! d2 [:style :border] b2))))

(defn cancel-heartbeat-effect []
  (let [d1 (js/document.getElementById "heartbeat-effect-1")
        d2 (js/document.getElementById "heartbeat-effect-2")]
    (when d1
      (j/call-in d1 [:classList :remove] "glow-effect"))
    (when d2
      (j/call-in d2 [:classList :remove] "glow-effect"))))

(defn- cancel-heartbeat []
  (cancel-heartbeat-effect)
  (re/insert {:player/stopping-heartbeat-song? true})
  (api.tween/tween {:from {:volume 1}
                    :to {:volume 0}
                    :duration 1000
                    :on-update (fn [x]
                                 (api.sound/set-volume :sound/heartbeat (j/get x :volume)))
                    :on-end (fn []
                              (api.sound/stop :sound/heartbeat))}))

(reg-rule
  :player/start-in-danger-effect
  {:what {:dt {}
          :game/started? {:then false}
          :pointer-locked? {:then false}
          :player/current-health {:then false}}
   :when (fn [{{:keys [game/started?]} :session}]
           started?)
   :then (fn [{{:keys [player/current-health
                       player/stopping-heartbeat-song?]} :session}]
           (if (< 0 current-health 350)
             (do
               (when-not (api.sound/playing? :sound/heartbeat)
                 (show-heartbeat-effect)
                 (api.sound/set-volume :sound/heartbeat 1)
                 (api.sound/play :sound/heartbeat)
                 (re/insert {:player/stopping-heartbeat-song? false})))
             (when (and (not stopping-heartbeat-song?)
                        (api.sound/playing? :sound/heartbeat))
               (cancel-heartbeat))))})

(reg-rule
  :player/ground-checker
  {:disabled? false
   ;; :run-every 100
   :locals {:start (v3)
            :end (v3)
            :collide-with #js {:collideWith api.const/collision-group-environment}
            :result (api.physics/raycast-result)
            :y-down (v3 0 -1 0)
            :ray (api.core/ray (v3) (v3) 2)}
   :what {:dt {}
          :player/capsule {}}
   :when (fn [{{game-started? :player/game-started?
                player-focus? :player/focus?} :session}]
           (and game-started?
                player-focus?))
   :then (fn [{{player-capsule :player/capsule
                particle-shockwave :particle/shockwave
                particle-speed-line :particle/speed-line
                player-air-time :player/air-time
                player-dash? :player/dash?
                dt :dt} :session
               {:keys [start end result collide-with]} :locals}]
           (let [start (j/update! (api.core/set-v3 start (api.core/get-pos player-capsule)) :y + 1)
                 end (j/update! (api.core/set-v3 end start) :y - 3)
                 _ (api.physics/raycast-to-ref start end result collide-with)
                 ground? (j/get result :hasHit)]
             (re/fire-rules {:player/ground? ground?})
             (if ground?
               ;; Reset air time when on the ground
               (do
                 (re/insert {:player/air-time 0})
                 (cond
                   (> player-air-time 1.2)
                   (do
                     (api.sound/play :sound/landing {:time 0 :offset 0.2 :volume 1})
                     (api.particle/start particle-shockwave)
                     (api.camera/shake-vertical 0.5 0.75))

                   (> player-air-time 0.6)
                   (do
                     (api.sound/play :sound/landing {:time 0 :offset 0.2 :volume 1})
                     (api.camera/shake-vertical 0.5 0.4))

                   (> player-air-time 0.2)
                   (do
                     (api.sound/set-volume :sound/landing 0.5)
                     (api.sound/play :sound/landing {:time 0 :offset 0.2 :volume 0.5})
                     (api.camera/shake-vertical 0.5 0.3)))
                 (when (and (not (j/get particle-speed-line :stopped?))
                            (not player-dash?))
                   (api.particle/stop particle-speed-line)))
               ;; Increment air time when in the air
               (do
                 (cond
                   (= (re/query :player/air-time) 0)
                   (when (and (not (j/get particle-speed-line :stopped?))
                              (not player-dash?))
                     (api.particle/stop particle-speed-line))

                   (> (re/query :player/air-time) 1.2)
                   (when (j/get particle-speed-line :stopped?)
                     (j/assoc! particle-speed-line :stopped? false)
                     (api.particle/start particle-speed-line)))
                 (re/upsert :player/air-time #(+ % dt))))))})

(def sending-states-to-server-tick-rate (/ 1000 30))

(defn- diff-maps [old new]
  (reduce
    (fn [acc [k v]]
      (if (not= (get old k) v)
        (assoc acc k v)
        acc))
    {}
    new))

(reg-rule
  :player/send-state-to-server
  {:what {:dt {}
          :player/capsule {}
          :player/model {}
          :player/prev-state {}}
   :run-every sending-states-to-server-tick-rate
   :then (fn [{{player-model :player/model
                player-focus? :player/focus?
                player-anim-groups :player/anim-groups
                player-prev-state :player/prev-state} :session}]
           (let [pos (api.core/get-pos player-model)
                 rot (j/get player-model :rotation)
                 light-staff-hits (re/query :player/light-staff-hits)
                 _ (re/insert :player/light-staff-hits [])
                 new-state {:px (j/get pos :x)
                            :py (j/get pos :y)
                            :pz (j/get pos :z)
                            :rx (j/get rot :x)
                            :ry (j/get rot :y)
                            :rz (j/get rot :z)
                            :focus? (boolean player-focus?)}
                 st (j/get (first (api.anim/get-playing-anim-groups player-anim-groups)) :name)
                 new-state (cond-> new-state
                             (and st (not= "die" st)) (assoc :st st))
                 diff (diff-maps player-prev-state new-state)
                 diff (if (seq light-staff-hits)
                        (assoc diff :light-staff-hits light-staff-hits)
                        diff)]
             (when (seq diff)
               (dispatch-pro :set-state diff))
             (re/insert {:player/prev-state new-state})))})

(reg-rule
  :map/upload
  {:what {:map/upload {}}
   :then (fn [{{{:keys [name blob]} :map/upload
                assets-manager :assets-manager} :session}]
           (api.core/set-files-to-load name blob)
           (let [mt (j/call assets-manager :addMeshTask "upload-map" "" "file:" name)]
             (j/assoc! mt :onSuccess (fn [task]
                                       (let [mesh (j/get-in task [:loadedMeshes 0])]
                                         (api.mesh/upload-map mesh)))))
           (j/call assets-manager :load))})

(defn add-user-name [mesh player-id username same-team?]
  (when-let [t (j/get mesh :username)]
    (api.core/dispose t))
  (when (str/blank? username)
    (println "Username is blank!"))
  (let [diffuse-color (if same-team?
                        (api.core/color 0)
                        (api.core/color 1 0 0))
        emissive-color (if same-team?
                         (api.core/color 1)
                         (api.core/color 1 0 0))
        mat (api.material/standard-mat {:name "material_username"
                                        :disable-lighting? true
                                        :diffuse-color diffuse-color
                                        :emissive-color emissive-color})
        username (api.mesh/text {:name (str "username_" player-id)
                                 :text (if (str/blank? username)
                                         ""
                                         username)
                                 :font-data (re/query :font/droid)
                                 :face-to-screen? true
                                 :size 0.2
                                 :mat mat})]
    (j/assoc! username
              :parent mesh
              :position (v3 0 -2 0)
              :scaling (v3 1 -1 1)
              :renderingGroupId (if same-team? 1 0))
    (j/assoc! mesh :username username)))

(defn update-health-bar-color [mesh same-team?]
  (let [health-bar (j/get mesh :health-bar)
        color (if same-team?
                "white"
                "red")]
    (j/assoc! health-bar
              :color color
              :background color)))

(defn make-enemies-pickable [mesh team my-team]
  (doseq [m (api.core/get-child-meshes mesh)]
    (j/assoc! m :isPickable (not= team my-team))))

(defn- init-players [players my-team mode]
  (doseq [[id {:keys [px py pz rx ry rz health username team equipped]}] players]
    (let [mesh (re/pop-from-pool :player/character-pool)
          team (if (= mode :solo-death-match) :red team)
          same-team? (= team my-team)]
      (j/assoc! mesh
                :name (str "Player_" id)
                :player-id id
                :position (v3 px py pz)
                :rotation (v3 rx ry rz))
      (re/upsert :players #(-> %
                               (assoc-in [id :mesh] mesh)
                               (assoc-in [id :username] username)
                               (assoc-in [id :team] team)
                               (assoc-in [id :health] health)))
      (player.shop/update-equipped-other-players mesh equipped)
      (player.model/apply-hero-material mesh team)
      (api.core/set-enabled mesh true)
      (add-user-name mesh id username same-team?)
      (update-health-bar-color mesh same-team?)
      (api.mesh/update-health-bar (j/get mesh :health-bar) (/ health 1000))
      (make-enemies-pickable mesh team my-team))))

(defmethod dispatch-pro-response :init [{{:keys [id pos]} :init}]
  (println "init started...")
  (when-not (re/query :player/capsule)
    (println "init player model data..")
    (player.model/init-player cooldowns))
  (println "init player model finished")
  (api.physics/set-pos (re/query :player/capsule) pos)

  (doseq [[_ {:keys [mesh]}] (re/query :players)]
    (when mesh
      (api.core/set-enabled mesh false)
      (re/push-to-pool :player/character-pool mesh)))

  (re/insert {:player/id id
              :player/prev-state {}})
  (api.core/resize)
  (re/fire-rules :player/calculate-ping true)
  (re/insert :player/data-store (utils/get-item :user))

  (re/fire-rules {:network/connected? true
                  :network/connecting? false
                  :network/error nil}))

(defmethod dispatch-pro-response :auth [{{:keys [data auth]} :auth}]
  (utils/set-item :auth-token auth)
  (when-not (empty? data)
    (re/insert :player/data (walk/keywordize-keys data))))

(defmethod dispatch-pro-response :auth-completed? [_]
  (println "Auth completed")
  (re/insert :player/auth-completed? true)
  (crisp/show)
  (dispatch-pro :init (network/add-cg-username {}))
  (dispatch-pro-sync :get-server-time {})
  (network/add-requested-room-id))

(defn- reset-player []
  (let [player-model (re/query :player/model)
        player-capsule (re/query :player/capsule)]
    (init-elements-if-not-available)
    (api.core/set-enabled player-model true)
    (api.physics/set-gravity-factor player-capsule player.model/gravity-factor)
    (re/fire-rules {:player/died? false
                    :player/paused? false
                    ;; This is bad naming, it's for ice-arrow leftover state
                    :player/first-time-right-click nil
                    :player/mana total-mana
                    :player/speed-booster-until nil})))

(defn- set-collectable-trigger [id body]
  (j/assoc! body :collided? false)
  (j/assoc! body :on-trigger
            (fn [type _ _]
              (when (and (= type "TRIGGER_ENTERED")
                         (not (j/get body :collided?))
                         (-> (re/query :player/collectables)
                             (get id)
                             (j/get :isVisible)))
                (j/assoc! body :collided? true)
                (dispatch-pro :get-collectable {:id id})))))

(defn- get-collectable-pool [collectable-type]
  (case collectable-type
    :hp :pool/collectable-hp-potion
    :mp :pool/collectable-mp-potion
    :speed :pool/collectable-speed-potion))

(defn- purge-collectables []
  (let [collectables (re/query :player/collectables)
        triggers (re/query :trigger/collectable-box)]
    (doseq [[_ c] collectables]
      (j/assoc! c :parent nil)
      (api.core/dispose c))
    (doseq [t (vals triggers)]
      (api.core/dispose t))
    (re/insert {:player/collectables {}
                :trigger/collectable-box {}})))

(defn- init-collectables [collectables]
  (purge-collectables)
  (let [collectables-node (or (api.core/get-node-by-name "collectables-node")
                              (api.core/transform-node {:name "collectables-node"}))]
    (j/assoc! collectables-node :parent (api.core/get-node-by-name "wrapper_map"))
    (doseq [{:keys [id active? current-collectable position]} collectables]
      (let [collectable (re/pop-from-pool (get-collectable-pool current-collectable))
            trigger-box (player.model/create-trigger-box collectables-node id collectable (api.core/v->v3 position))
            body (j/get trigger-box :body)
            shape (j/get trigger-box :shape)]
        (set-collectable-trigger id body)
        (j/assoc! shape
                  :filterMembershipMask api.const/collision-group-collectables
                  :filterCollideMask api.const/collision-group-player)
        (j/assoc! collectable
                  :parent trigger-box
                  :collectable-type current-collectable)
        (api.core/set-enabled collectable active?)
        (re/upsert :player/collectables #(assoc % id collectable))
        (re/upsert :trigger/collectable-box #(assoc % id trigger-box))))))

(defmethod dispatch-pro-response :start-game [{{:keys [room-id world pos username team collectables map map-change-time mode]} :start-game}]
  (player.map/load-map map)
  (let [current-player-id (re/query :player/id)
        players (dissoc world current-player-id)
        team (if (= mode :solo-death-match)
               :blue
               team)
        _ (init-players players team mode)
        players (re/query :players)]
    (reset-player)
    (player.model/apply-hero-material (re/query :player/model) team)
    (init-collectables collectables)
    (dispatch-pro :stats)
    (ads/show-invite-button)
    (api.physics/set-pos (re/query :player/capsule) pos)
    (re/fire-rules {:players players
                    :player/room-id room-id
                    :player/username username
                    :player/team team
                    :player/requested-to-join? false
                    :game/map-change-time map-change-time
                    :game/started? true
                    :game/ended? false
                    :game/end-time nil
                    :game/mode mode
                    :game/win? nil
                    :game/join-failed nil
                    :game/ack-game-queue-time nil})))

(defmethod dispatch-pro-response :solo-death-match-map-change [{{:keys [map map-change-time respawn-pos collectables]} :solo-death-match-map-change}]
  (player.map/load-map map)
  (init-collectables collectables)
  (re/fire-rules {:game/map-change-time map-change-time})
  (api.physics/set-pos (re/query :player/capsule) respawn-pos))

(defmethod dispatch-pro-response :join-game-fail [{{:keys [reason room-id]} :join-game-fail}]
  (println "Failed to join the room")
  (re/insert {:game/join-failed {:reason reason
                                 :room-id room-id}})
  (when (= :not-available reason)
    (re/fire-rules {:player/requested-room-id nil}))
  (re/insert {:player/requested-to-join? false})
  (scene.utils/exit-pointer-lock))

(defn push-hero-materials [mesh team]
  (doseq [m (api.core/get-child-meshes mesh)
          :let [name (j/get m :name)]
          :when (and name (str/includes? name "Chr_Hips"))]
    (cond
      (and (= :red team) (= "hero-red" (j/get-in m [:material :name])))
      (re/push-to-pool :pool/material-hero-red (j/get m :material))

      (and (= :blue team) (= "hero-blue" (j/get-in m [:material :name])))
      (re/push-to-pool :pool/material-hero-blue (j/get m :material)))))

(defn- reset-all-players []
  (doseq [[player-id {:keys [mesh team]}] (re/query :players)]
    (when (nil? mesh)
      (println "Mesh nil reset-all-players"))
    (re/push-to-pool :player/character-pool mesh)
    (j/assoc! mesh :position (v3 0 -2 0))
    (api.core/set-enabled mesh false)
    (re/upsert :players #(dissoc % player-id))
    (push-hero-materials mesh team)))

(defn- reset-current-player-material []
  (let [model (re/query :player/model)
        team (re/query :player/team)]
    (push-hero-materials model team)))

(defn end-game []
  (reset-all-players)
  (reset-current-player-material)
  (cancel-heartbeat)
  (purge-collectables)
  (scene.utils/exit-pointer-lock)
  (ads/hide-invite-button))

(set! network/end-game end-game)

(defmethod dispatch-pro-response :end-game [{{:keys [win?]} :end-game}]
  (println "End game called!")
  (re/fire-rules {:player/room-id nil
                  :player/requested-room-id nil
                  :game/started? false
                  :game/ended? true
                  :game/end-time (js/Date.now)
                  :game/win? win?})
  (if win?
    (api.sound/play :sound/victory)
    (api.sound/play :sound/defeat))
  (end-game))

(defmethod dispatch-pro-response :get-collectable [{{:keys [current-collectable]} :get-collectable}]
  (let [offset-box (re/query :player/offset-box)
        speed-cap (* 90 1000)
        speed-duration 30000]
    (case current-collectable
      :hp (do
            (api.sound/play :sound/collectable-hp)
            (common/show-hit-number {:value "+500 HP"
                                     :color "#3ad74e"
                                     :link-mesh offset-box}))
      :mp (do
            (api.sound/play :sound/collectable-mp)
            (common/show-hit-number {:value "+1000 MP"
                                     :color "#3dc3fd"
                                     :link-mesh offset-box})
            (add-mana total-mana)
            (re/insert :player/last-time-mp-potion-collected (js/Date.now)))
      :speed (do
               (api.sound/play :sound/collectable-speed)
               (common/show-hit-number {:value "Speed Booster!"
                                        :color "yellow"
                                        :link-mesh offset-box})
               (re/upsert :player/speed-booster-until
                          (fn [speed-booster-until]
                            (Math/min (+ (js/Date.now) speed-cap)
                                      (Math/max (+ speed-booster-until speed-duration)
                                                (+ (js/Date.now) speed-duration)))))))))

(defmethod dispatch-pro-response :join-game-queue [_]
  (re/insert {:game/ack-game-queue-time (js/Date.now)})
  (println "Ack game queue"))

(defmethod dispatch-pro-response :stats [params]
  (let [{:keys [stats red-team-kills blue-team-kills]} (:stats params)
        current-player-id (re/query :player/id)
        current-player-username (re/query :player/username)
        players (re/query :players)
        stats (reduce
                (fn [acc [player-id {:keys [kills team]}]]
                  (conj acc {:player-id player-id
                             :current-player? (= player-id current-player-id)
                             :kills (or kills 0)
                             :team team
                             :username (if (= player-id current-player-id)
                                         current-player-username
                                         (get-in players [player-id :username]))}))
                []
                stats)]
    (re/insert {:players/stats {:stats stats
                                :red-team-kills red-team-kills
                                :blue-team-kills blue-team-kills}})))

(defmethod dispatch-pro-response :who-killed-who [params]
  (let [[killer-id victim-id] (:who-killed-who params)
        current-player-id (re/query :player/id)
        current-player-username (re/query :player/username)
        current-player-team (re/query :player/team)
        players (re/query :players)
        killer-username (if (= current-player-id killer-id)
                          current-player-username
                          (get-in players [killer-id :username]))
        victim-username (if (= current-player-id victim-id)
                          current-player-username
                          (get-in players [victim-id :username]))
        killer-team (if (= current-player-id killer-id)
                      current-player-team
                      (get-in players [killer-id :team]))
        victim-team (if (= current-player-id victim-id)
                      current-player-team
                      (get-in players [victim-id :team]))]
    (re/upsert :players/who-killed-who
               (fn [killings]
                 (->> (conj (or killings []) [(js/Date.now)
                                              {:username killer-username :team killer-team}
                                              {:username victim-username :team victim-team}])
                      (take-last 5)
                      vec)))))

(defn create-super-nova-ball []
  (let [s (api.mesh/sphere {:name "super-nova-ball"
                            :material (api.core/clone (api.asset/get-asset :material/fire-nova))
                            :enabled? false
                            :diameter 1})]
    (j/assoc! s :scaling (v3 super-nova-scale))
    s))

(defn init-super-nova-balls []
  (re/register-item-creation :pool/super-nova-ball create-super-nova-ball))

(defn create-rock-wall []
  (let [m (api.core/get-node-by-name "SM_Env_StoneWall_01")
        rock-wall (api.core/clone m :name "rock_wall")
        _ (j/assoc! rock-wall
                    :alwaysSelectAsActiveMesh true
                    :doNotSyncBoundingInfo true
                    :isPickable false
                    :parent nil
                    :rotation (v3 (api.core/to-rad 90) 0 0)
                    :scaling (v3 0.02 0.06 0.05))
        agg (api.physics/physics-agg {:mesh rock-wall
                                      :disable-pre-step? false
                                      :type :PhysicsShapeType/BOX
                                      :mass 0
                                      :motion-type :PhysicsMotionType/STATIC})]
    (api.core/set-enabled rock-wall true)
    (j/assoc! rock-wall :agg agg)
    rock-wall))

(defn init-rock-walls []
  (re/register-item-creation :pool/rock-wall create-rock-wall))

(defn create-material-hero-blue []
  (-> (api.asset/get-asset :material/hero-blue)
      (api.core/clone)
      (j/assoc! :name "hero-blue")))

(defn create-material-hero-red []
  (-> (api.asset/get-asset :material/hero-red)
      (api.core/clone)
      (j/assoc! :name "hero-red")))

(defn init-hero-materials []
  (re/register-item-creation :pool/material-hero-blue create-material-hero-blue)
  (re/register-item-creation :pool/material-hero-red create-material-hero-red))

(defn create-ice-tornado []
  (api.mesh/sphere {:name "ice-tornado"
                    :diameter-x 1
                    :diameter-y 0.3
                    :diameter-z 1
                    :enabled? false
                    :scale (v3 20)
                    :material (api.asset/get-asset :material/ice-tornado)}))

(defn init-ice-tornado []
  (re/register-item-creation :pool/ice-tornado create-ice-tornado))

(def scream-sounds
  [:sound/scream-1 :sound/scream-2
   :sound/scream-3 :sound/scream-4
   :sound/scream-5 :sound/scream-6
   :sound/scream-7 :sound/scream-8])

(defn- play-death-scream [pos]
  (api.sound/play (first (shuffle scream-sounds)) {:position pos}))

(defn- process-anims [player-id st]
  (let [players (re/query :players)
        mesh (get-in players [player-id :mesh])
        anim-groups (j/get mesh :anim-groups)
        prev-anim-name (get-in players [player-id :prev-anim-name])
        anim-map (re/get-anim-map :player)]
    (when (and st (not= prev-anim-name st))
      (let [anim-to-run (api.anim/find-animation-group st anim-groups)
            opts (get anim-map st)]
        (api.anim/stop-all anim-groups)
        (api.anim/start (assoc opts :anim-group anim-to-run)))
      (when (and (= st "die") prev-anim-name)
        (let [enemy-pos (api.core/clone (j/get mesh :position))
              enemy-pos (j/update! enemy-pos :y #(+ % 1.5))]
          (api.sound/play :death {:position enemy-pos})
          (api.core/set-enabled mesh false)
          (show-kill-explode enemy-pos)
          (play-death-scream enemy-pos)))
      (when (and (not= st "die")
                 (not (api.core/enabled? mesh)))
        (api.core/set-enabled mesh true)))
    (re/upsert :players #(assoc-in % [player-id :prev-anim-name] st))))

(defn- process-fire-projectiles [fire-projectiles]
  (doseq [{:keys [pos dir]} fire-projectiles
          :let [pos (api.core/v->v3 pos)
                dir (api.core/v->v3 dir)]]
    (throw-fire-projectile-ball pos dir)))

(defn- process-super-novas [super-novas]
  (doseq [{:keys [pos]} super-novas
          :let [pos (api.core/v->v3 pos)]]
    (throw-super-nova pos)))

(defn- process-ice-tornados [ice-tornados]
  (doseq [{:keys [pos]} ice-tornados
          :let [pos (api.core/v->v3 pos)]]
    (throw-ice-tornado pos)))

(defn- show-ice-arrow-hit [player-id enemy-id]
  (when-let [player-model (get-in (re/query :players) [player-id :mesh])]
    (let [arrow-hit-emitter (some (fn [m]
                                    (when (= (j/get m :name) "ice_arrow_point")
                                      m)) (api.core/get-child-meshes player-model))
          arrow-sparkle-ps (re/pop-from-pool :pool/particle-arrow-sparkles)]
      (j/call-in arrow-sparkle-ps [:onStoppedObservable :addOnce]
                 (fn []
                   (api.particle/push-to-pool :pool/particle-arrow-sparkles arrow-sparkle-ps)))
      (api.particle/start-ps arrow-sparkle-ps {:emitter arrow-hit-emitter})
      (api.sound/play :sound/ice-whoosh {:position (api.core/get-pos arrow-hit-emitter)})))
  (when enemy-id
    (let [current-player-id (re/query :player/id)
          current-player-model (re/query :player/model)
          me-the-enemy? (= current-player-id enemy-id)
          players (re/query :players)
          player-model (if me-the-enemy?
                         current-player-model
                         (get-in players [enemy-id :mesh]))
          ice-arrow-hit-ps (re/pop-from-pool :pool/particle-ice-arrow-hit)]
      (j/call-in ice-arrow-hit-ps [:onStoppedObservable :addOnce]
                 (fn []
                   (api.particle/push-to-pool :pool/particle-ice-arrow-hit ice-arrow-hit-ps)))
      (api.particle/start-ps ice-arrow-hit-ps {:emitter player-model})
      (api.sound/play :sound/ice-hit {:position (api.core/get-pos player-model)}))))

(defn- process-ice-arrows [ice-arrows]
  (doseq [{:keys [player-id enemy-id]} ice-arrows]
    (show-ice-arrow-hit player-id enemy-id)))

(defn- update-players-health [player-id health]
  (let [player (get (re/query :players) player-id)
        current-health (:health player)
        health-bar (j/get (:mesh player) :health-bar)]
    (when-not (= current-health health)
      (api.mesh/update-health-bar health-bar (/ health 1000)))
    (re/upsert :players #(-> % (assoc-in [player-id :health] health)))))

(defn- process-damage-effects [damage-effects]
  (doseq [{:keys [player-id type]} damage-effects]
    (let [current-player-id (re/query :player/id)
          current-player-model (re/query :player/model)
          me-the-enemy? (= current-player-id player-id)
          players (re/query :players)
          player-model (if me-the-enemy?
                         current-player-model
                         (get-in players [player-id :mesh]))
          material (some
                     (fn [m]
                       (when (and (j/get m :name)
                                  (str/includes? (j/get m :name) "Chr_Hips_Male_0"))
                         (j/get m :material)))
                     (api.core/get-child-meshes player-model))
          to (if (= type :ice)
               {:r 0 :g 0 :b 162}
               {:r 162 :g 0 :b 0})]
      (api.tween/tween
        {:from {:r 0 :g 0 :b 0}
         :to to
         :duration 250
         :on-update (fn [c]
                      (j/assoc! material :emissiveColor (api.core/color-rgb (j/get c :r)
                                                                            (j/get c :g)
                                                                            (j/get c :b)
                                                                            255)))
         :on-end (fn []
                   (api.tween/tween
                     {:from to
                      :to {:r 0 :g 0 :b 0}
                      :duration 250
                      :on-update (fn [c]
                                   (j/assoc! material :emissiveColor (api.core/color-rgb (j/get c :r)
                                                                                         (j/get c :g)
                                                                                         (j/get c :b)
                                                                                         255)))}))}))))

(defn- process-collectables [collectable-updates]
  (doseq [{:keys [collectable-id current-collectable active?]} collectable-updates]
    (let [collectables (re/query :player/collectables)
          collectable (get collectables collectable-id)]
      (if active?
        (let [pool (get-collectable-pool current-collectable)
              collectable (re/pop-from-pool pool)
              trigger-box (get (re/query :trigger/collectable-box) collectable-id)
              body (j/get trigger-box :body)]
          (j/assoc! collectable
                    :collectable-type current-collectable
                    :parent trigger-box
                    :position (v3))
          (api.core/set-enabled collectable true)
          (set-collectable-trigger collectable-id body)
          (re/upsert :player/collectables #(assoc % collectable-id collectable)))
        (let [pool (get-collectable-pool (j/get collectable :collectable-type))]
          (j/assoc! collectable :parent nil)
          (api.core/set-enabled collectable false)
          (re/push-to-pool pool collectable))))))

(defn- show-light-staff [player-id point]
  (when-not (= player-id (re/query :player/id))
    (let [players (re/query :players)
          player-model (get-in players [player-id :mesh])
          player-hand (api.core/find-bone-tn player-model "mixamorig:RightHand")
          start (api.core/get-pos player-hand true)
          point (api.core/v->v3 point)
          distance (api.core/distance start point)
          midpoint (-> start
                       (j/call :add point)
                       (j/call :scale 0.5))
          emitter-light-staff (j/get player-model :particle-light-staff-emitter)
          particle-light-staff (j/get player-model :particle-light-staff)]
      (j/assoc! emitter-light-staff :position midpoint)
      (j/call emitter-light-staff :lookAt point)
      (j/assoc! particle-light-staff
                :minScaleY distance
                :maxScaleY distance)
      (when-not (api.particle/started? particle-light-staff)
        (api.particle/start-ps particle-light-staff {:emitter emitter-light-staff}))
      (when-not (api.sound/playing? :sound/electric-light-staff)
        (api.sound/play :sound/electric-light-staff {:position (api.core/get-pos player-model)
                                                     :length 1.2})))))

(defn- show-enemy-got-light-staff [enemy-id]
  (let [current-player-id (re/query :player/id)
        current-player-model (re/query :player/model)
        me-the-enemy? (= current-player-id enemy-id)
        players (re/query :players)
        player-model (if me-the-enemy?
                       current-player-model
                       (get-in players [enemy-id :mesh]))
        light-staff-hit-ps (re/pop-from-pool :pool/particle-light-burst)]
    (j/call-in light-staff-hit-ps [:onStoppedObservable :addOnce]
               (fn []
                 (api.particle/push-to-pool :pool/particle-light-burst light-staff-hit-ps)))
    (api.particle/start-ps light-staff-hit-ps {:emitter player-model})))

(defn- process-light-staffs [light-staffs]
  (doseq [{:keys [player-id point enemy-id]} light-staffs]
    (show-light-staff player-id point)
    (show-enemy-got-light-staff enemy-id)))

(defn- process-light-strikes [light-strikes]
  (doseq [{:keys [pos]} light-strikes
          :let [pos (api.core/v->v3 pos)]]
    (throw-light-strike pos)))

(defn- process-wind-slashes [wind-slashes]
  (doseq [{:keys [player-id enemy-id dir]} wind-slashes
          :let [projectile-duration 250]]
    (if (and player-id enemy-id)
      (let [players (re/query :players)
            player-model (get-in players [player-id :mesh])
            me-the-enemy? (= enemy-id (re/query :player/id))
            enemy-model (if me-the-enemy?
                          (re/query :player/model)
                          (get-in players [enemy-id :mesh]))]
        (throw-wind-slash {:start (j/update! (api.core/get-pos player-model) :y inc)
                           :end (j/update! (api.core/get-pos enemy-model) :y inc)
                           :duration (when-not me-the-enemy? projectile-duration)
                           :on-end (fn []
                                     (let [wind-hit-ps (re/pop-from-pool :pool/particle-wind-hit)]
                                       (api.particle/start-ps wind-hit-ps {:emitter enemy-model
                                                                           :auto-pool :pool/particle-wind-hit})))}))
      (let [players (re/query :players)
            player-model (get-in players [player-id :mesh])
            start (j/update! (api.core/get-pos player-model true) :y inc)
            dir (j/call (api.core/v->v3 dir) :scaleInPlace 50)]
        (throw-wind-slash {:start start
                           :end (j/call start :add dir)
                           :duration projectile-duration})))))

(defn- process-wind-tornados [wind-tornados]
  (doseq [{:keys [pos]} wind-tornados
          :let [pos (api.core/v->v3 pos)]]
    (throw-wind-tornado pos)))

(defn- process-update-equips [update-equips]
  (doseq [{:keys [player-id equipped]} update-equips]
    (let [players (re/query :players)
          player-model (get-in players [player-id :mesh])]
      (player.shop/update-equipped-other-players player-model equipped))))

(defn- process-toxic-clouds [toxic-clouds]
  (doseq [{:keys [player-id pos toxic-cloud-id]} toxic-clouds
          :let [pos (api.core/v->v3 pos)]]
    (throw-toxic-cloud {:position pos
                        :player-id player-id
                        :toxic-cloud-id toxic-cloud-id})))

(defn- process-toxic-projectiles [toxic-projectiles]
  (when (seq toxic-projectiles)
    (throw-toxic-ball (map (fn [{:keys [pos dir]}]
                             [(api.core/v->v3 pos) (api.core/v->v3 dir)])
                           toxic-projectiles))))

(defn- process-rock-balls [rock-balls]
  (doseq [{:keys [pos-s pos-e rot]} rock-balls
          :let [pos-s (api.core/v->v3 pos-s)
                pos-e (api.core/v->v3 pos-e)
                rot (api.core/v->v3 rot)]]
    (throw-rock-ball pos-s pos-e rot)))

(defn- process-rock-walls [rock-walls]
  (doseq [{:keys [pos rot]} rock-walls
          :let [pos (api.core/v->v3 pos)
                rot (api.core/v->v3 rot)]]
    (throw-rock-wall pos rot)))

(defmethod dispatch-pro-response :world-snapshot [params]
  (let [world* (:world-snapshot params)
        fire-projectiles (get world* :fire-projectiles)
        toxic-projectiles (get world* :toxic-projectiles)
        rock-balls (get world* :rock-balls)
        rock-walls (get world* :rock-walls)
        super-novas (get world* :super-novas)
        ice-tornados (get world* :ice-tornados)
        ice-arrows (get world* :ice-arrows)
        light-staffs (get world* :light-staffs)
        light-strikes (get world* :light-strikes)
        wind-slashes (get world* :wind-slashes)
        wind-tornados (get world* :wind-tornados)
        damage-effects (get world* :damage-effects)
        collectables (get world* :collectables)
        update-equips (get world* :update-equips)
        toxic-clouds (get world* :toxic-clouds)
        server-time (get world* :server-time)
        world* (dissoc world*
                       :fire-projectiles
                       :toxic-projectiles
                       :super-novas
                       :ice-tornados
                       :ice-arrows
                       :damage-effects
                       :collectables
                       :light-staffs
                       :light-strikes
                       :wind-slashes
                       :wind-tornados
                       :update-equips
                       :toxic-clouds
                       :rock-balls
                       :rock-walls
                       :server-time)
        current-player-id (re/query :player/id)
        world (dissoc world* current-player-id)
        shop-open? (re/query :game/shop?)]
    (re/insert :game/server-time server-time)
    (re/fire-rules :player/current-health (get-in world* [current-player-id :health]))
    (when (and (= (get-in world* [current-player-id :health]) 0)
               (not (re/query :player/died?)))
      (re/fire-rules {:player/died? true
                      :player/died-time (js/Date.now)})
      (let [pos (api.core/clone (j/get (re/query :player/model) :position))]
        (play-death-scream pos)
        (api.sound/play :death {:position pos}))
      (reset-cooldowns))
    (process-fire-projectiles fire-projectiles)
    (process-toxic-projectiles toxic-projectiles)
    (process-rock-balls rock-balls)
    (process-rock-walls rock-walls)
    (process-super-novas super-novas)
    (process-light-strikes light-strikes)
    (process-ice-tornados ice-tornados)
    (process-toxic-clouds toxic-clouds)
    (process-wind-tornados wind-tornados)
    (process-ice-arrows ice-arrows)
    (process-light-staffs light-staffs)
    (process-wind-slashes wind-slashes)
    (process-damage-effects damage-effects)
    (process-collectables collectables)
    (process-update-equips update-equips)
    (doseq [[player-id {:keys [px py pz rx ry rz health st ping focus? boost?]}] world]
      (when-let [mesh (get-in (re/query :players) [player-id :mesh])]
        (when (not config/dev?)
          (if (and (not= "die" st)
                   focus?
                   (not shop-open?))
            (api.core/set-enabled mesh true)
            (api.core/set-enabled mesh false)))
        (let [position (j/get mesh :position)
              rotation (j/get mesh :rotation)
              ping (or ping 50)
              tween-duration (Math/min (Math/max 50 (* ping 2)) 2000)]
          (some-> (j/get mesh :tween-position) api.tween/stop)
          (some-> (j/get mesh :tween-rotation) api.tween/stop)
          (j/assoc! mesh :tween-position (api.tween/tween
                                           {:target [mesh :position]
                                            :duration tween-duration
                                            :from {:x (j/get position :x) :y (j/get position :y) :z (j/get position :z)}
                                            :to {:x px :y py :z pz}}))
          (j/assoc! mesh :tween-rotation (api.tween/tween
                                           {:target [mesh :rotation]
                                            :duration tween-duration
                                            :from {:x (j/get rotation :x) :y (j/get rotation :y) :z (j/get rotation :z)}
                                            :to {:x rx :y ry :z rz}}))
          (update-players-health player-id health))
        (process-anims player-id st)
        (re/upsert :players #(-> %
                                 (assoc-in [player-id :focus?] focus?)
                                 (assoc-in [player-id :boost?] boost?)))))))

(defmethod dispatch-pro-response :new-player-join [params]
  (let [{:keys [id pos health username team equipped]} (:new-player-join params)
        [px py pz] pos
        mesh (re/pop-from-pool :player/character-pool)
        my-team (re/query :player/team)
        game-mode (re/query :game/mode)
        team (if (= game-mode :solo-death-match)
               :red
               team)
        same-team? (= team my-team)]
    (j/assoc! mesh
              :name (str "Player_" id)
              :player-id id
              :position (v3 px py pz))
    (m/assoc! mesh
              :trigger-box.player-id id)
    (add-user-name mesh id username same-team?)
    (player.shop/update-equipped-other-players mesh equipped)
    (player.model/apply-hero-material mesh team)
    (make-enemies-pickable mesh team my-team)
    (api.core/set-enabled mesh true)
    (update-health-bar-color mesh same-team?)
    (re/upsert :players #(-> %
                             (assoc-in [id :mesh] mesh)
                             (assoc-in [id :username] username)
                             (assoc-in [id :team] team)
                             (assoc-in [id :health] health)))
    (dispatch-pro :stats)))

(defmethod dispatch-pro-response :player-exit [params]
  (let [player-id (:player-exit params)
        players (re/query :players)
        mesh (get-in players [player-id :mesh])
        team (get-in players [player-id :team])]
    (when (nil? mesh)
      (println "Mesh nil player-exit"))
    (re/push-to-pool :player/character-pool mesh)
    (j/assoc! mesh :position (v3 0 -2 0))
    (push-hero-materials mesh team)
    (api.core/set-enabled mesh false)
    (re/upsert :players #(dissoc % player-id))
    (dispatch-pro :stats)))

(defn- show-range-damages [damage-and-positions timer]
  (let [players (re/query :players)]
    (doseq [[player-id damage died?] damage-and-positions]
      (let [player-model (get-in players [player-id :mesh])]
        (when died?
          (when @timer
            (js/clearTimeout @timer)
            (reset! timer nil))
          (re/insert {:player/kill-info {:killed? true
                                         :username (j/get-in player-model [:username :text])}})
          (reset! timer (js/setTimeout #(re/insert {:player/kill-info {}}) kill-info-duration)))
        (common/show-hit-number {:value damage
                                 :pos (j/update! (api.core/clone (api.core/get-pos player-model)) :y + 0.5)})))))

(let [timer (atom nil)]
  (defmethod dispatch-pro-response :ice-arrow [params]
    (let [data (:ice-arrow params)
          player-id (:player-id data)
          damage (:damage data)
          died? (:died? data)
          players (re/query :players)
          player-model (get-in players [player-id :mesh])
          ice-arrow-hit-ps (re/pop-from-pool :pool/particle-ice-arrow-hit)]
      (when player-model
        (j/call-in ice-arrow-hit-ps [:onStoppedObservable :addOnce]
                   (fn []
                     (api.particle/push-to-pool :pool/particle-ice-arrow-hit ice-arrow-hit-ps)))
        (api.particle/start-ps ice-arrow-hit-ps {:emitter player-model})
        (api.sound/play :sound/ice-hit {:position (api.core/get-pos player-model)})
        (when died?
          (when @timer
            (js/clearTimeout @timer)
            (reset! timer nil))
          (re/insert {:player/kill-info {:killed? true
                                         :username (j/get-in player-model [:username :text])}})
          (reset! timer (js/setTimeout #(re/insert {:player/kill-info {}}) kill-info-duration)))
        (common/show-hit-number {:value damage
                                 :pos (j/update! (api.core/clone (api.core/get-pos player-model)) :y + 0.5)})))))

(defn- add-auto-pool [pool])

(let [timer (atom nil)]
  (defmethod dispatch-pro-response :wind-slash [params]
    (let [data (:wind-slash params)
          player-id (:player-id data)
          damage (:damage data)
          died? (:died? data)
          players (re/query :players)
          player-model (get-in players [player-id :mesh])
          wind-hit-ps (re/pop-from-pool :pool/particle-wind-hit)]
      (when player-model
        (api.particle/start-ps wind-hit-ps {:emitter player-model
                                            :auto-pool :pool/particle-wind-hit})
        #_(api.sound/play :sound/ice-hit {:position (api.core/get-pos player-model)})
        (when died?
          (when @timer
            (js/clearTimeout @timer)
            (reset! timer nil))
          (re/insert {:player/kill-info {:killed? true
                                         :username (j/get-in player-model [:username :text])}})
          (reset! timer (js/setTimeout #(re/insert {:player/kill-info {}}) kill-info-duration)))
        (common/show-hit-number {:value damage
                                 :pos (j/update! (api.core/clone (api.core/get-pos player-model)) :y + 0.5)})))))

(let [timer (atom nil)]
  (defmethod dispatch-pro-response :light-staff-hits [params]
    (let [data (:light-staff-hits params)
          player-id (:player-id data)
          damage (:damage data)
          died? (:died? data)
          players (re/query :players)
          player-model (get-in players [player-id :mesh])]
      (when player-model
        ;; (j/call-in ice-arrow-hit-ps [:onStoppedObservable :addOnce]
        ;;           (fn []
        ;;             (api.particle/push-to-pool :pool/particle-ice-arrow-hit ice-arrow-hit-ps)))
        ;; (api.particle/start-ps ice-arrow-hit-ps {:emitter player-model})
        #_(api.sound/play :sound/ice-hit {:position (api.core/get-pos player-model)})
        (when died?
          (when @timer
            (js/clearTimeout @timer)
            (reset! timer nil))
          (re/insert {:player/kill-info {:killed? true
                                         :username (j/get-in player-model [:username :text])}})
          (reset! timer (js/setTimeout #(re/insert {:player/kill-info {}}) kill-info-duration)))
        (common/show-hit-number {:value damage
                                 :duration-factor 1
                                 :pos (j/update! (api.core/clone (api.core/get-pos player-model)) :y + 0.5)})))))

(let [timer (atom nil)]
  (defmethod dispatch-pro-response :fire-projectile [params]
    (let [damage-and-positions (-> params :fire-projectile :damage-and-positions)]
      (show-range-damages damage-and-positions timer))))

(let [timer (atom nil)]
  (defmethod dispatch-pro-response :rock-projectile [params]
    (let [damage-and-positions (-> params :rock-projectile :damage-and-positions)]
      (show-range-damages damage-and-positions timer))))

(let [timer (atom nil)]
  (defmethod dispatch-pro-response :toxic-projectile [params]
    (let [damage-and-positions (-> params :toxic-projectile :damage-and-positions)]
      (show-range-damages damage-and-positions timer))))

(let [timer (atom nil)]
  (defmethod dispatch-pro-response :super-nova [params]
    (let [damage-and-positions (-> params :super-nova :damage-and-positions)]
      (show-range-damages damage-and-positions timer))))

(let [timer (atom nil)]
  (defmethod dispatch-pro-response :toxic-cloud [params]
    (let [damage-and-positions (-> params :toxic-cloud :damage-and-positions)]
      (show-range-damages damage-and-positions timer))))

(let [timer (atom nil)]
  (defmethod dispatch-pro-response :light-strike [params]
    (let [damage-and-positions (-> params :light-strike :damage-and-positions)]
      (show-range-damages damage-and-positions timer))))

(let [timer (atom nil)]
  (defmethod dispatch-pro-response :ice-tornado [params]
    (let [damage-and-positions (-> params :ice-tornado :damage-and-positions)]
      (show-range-damages damage-and-positions timer))))

(let [timer (atom nil)]
  (defmethod dispatch-pro-response :wind-tornado [params]
    (let [damage-and-positions (-> params :wind-tornado :damage-and-positions)]
      (show-range-damages damage-and-positions timer))))

(defmethod dispatch-pro-response :respawn [params]
  (let [data (:respawn params)
        new-pos (:pos data)
        player-capsule (re/query :player/capsule)]
    (when true #_(not config/dev?)
          (api.physics/set-pos player-capsule new-pos))
    (reset-player)
    (attach-camera-to-target)
    (scene.utils/lock-pointer)))

(comment
  (-> (re/query :player/capsule) api.core/get-pos api.core/v3->v))

(defmethod dispatch-pro-response :entered-toxic-cloud [params])

(defmethod dispatch-pro-response :ping [params]
  (let [ping (:ping params)
        timestamp (:timestamp ping)
        rtt (int (/ (- (js/Date.now) timestamp) 2))]
    (re/insert :network/ping rtt)
    (dispatch-pro :set-ping {:ping rtt})))

(defmethod dispatch-pro-response :set-ping [_]
  (js/setTimeout #(re/fire-rules :player/calculate-ping true) 1000))

(let [timer (atom nil)]
  (defmethod dispatch-pro-response :hit-fire-ball [params]
    (let [damage-and-positions (-> params :hit-fire-ball :damage-and-positions)
          players (re/query :players)]
      (doseq [[player-id damage died?] damage-and-positions]
        (let [player-model (get-in players [player-id :mesh])]
          (when died?
            (when @timer
              (js/clearTimeout @timer)
              (reset! timer nil))
            (re/insert {:player/kill-info {:killed? true
                                           :username (j/get-in player-model [:username :text])}})
            (reset! timer (js/setTimeout #(re/insert {:player/kill-info {}}) kill-info-duration)))
          (common/show-hit-number {:value damage
                                   :pos (j/update! (api.core/clone (api.core/get-pos player-model)) :y + 0.5)}))))))

(defn- got-hit [{:keys [data timer effect-color f me? duration-factor]
                 :or {effect-color :red}}]
  (let [damage (:damage data)
        died? (:died? data)
        player-id (:player-id data)
        player-model (re/query :player/model)
        players (re/query :players)
        enemy-player-mesh (get-in players [player-id :mesh])
        pos (j/update! (api.core/clone (api.core/get-pos player-model)) :y + 1.5)]
    (common/show-hit-number {:value damage
                             :pos pos
                             :color "red"
                             :duration-factor duration-factor})
    (show-damage-effect effect-color)
    (when-not me?
      (re/fire-rules
        :player/show-offscreen-damage-arrow
        {:enemy-position (api.core/get-pos enemy-player-mesh)}))
    (when f (f))
    (when died?
      (when @timer
        (js/clearTimeout @timer)
        (reset! timer nil))
      (show-kill-explode pos)
      (api.core/set-enabled player-model false)
      (re/insert {:player/kill-info {:killed-by? true
                                     :me? me?
                                     :username (if me?
                                                 (re/query :player/username)
                                                 (j/get-in enemy-player-mesh [:username :text]))}})
      (reset! timer (js/setTimeout #(re/insert {:player/kill-info {}}) kill-info-duration))
      (scene.utils/exit-pointer-lock))))

(let [timer (atom nil)]
  (defmethod dispatch-pro-response :got-fire-projectile-hit [params]
    (got-hit {:data (:got-fire-projectile-hit params)
              :timer timer})))

(let [timer (atom nil)]
  (defmethod dispatch-pro-response :got-rock-projectile-hit [params]
    (got-hit {:data (:got-rock-projectile-hit params)
              :timer timer})))

(let [timer (atom nil)]
  (defmethod dispatch-pro-response :got-toxic-projectile-hit [params]
    (got-hit {:data (:got-toxic-projectile-hit params)
              :timer timer})))

(let [timer (atom nil)]
  (defmethod dispatch-pro-response :got-toxic-cloud-damage-over-time [params]
    (got-hit {:data (:got-toxic-cloud-damage-over-time params)
              :timer timer})))

(let [timer (atom nil)]
  (defmethod dispatch-pro-response :got-super-nova-hit [params]
    (got-hit {:data (:got-super-nova-hit params)
              :timer timer
              :f api.camera/shake-camera})))

(let [timer (atom nil)]
  (defmethod dispatch-pro-response :got-toxic-cloud-hit [params]
    (got-hit {:data (:got-toxic-cloud-hit params)
              :timer timer})))

(let [timer (atom nil)]
  (defmethod dispatch-pro-response :got-light-strike-hit [params]
    (got-hit {:data (:got-light-strike-hit params)
              :timer timer
              :f (fn []
                   (re/insert :player/freeze-end-time (+ (js/Date.now) 1000)))})))

(let [timer (atom nil)]
  (defmethod dispatch-pro-response :got-ice-tornado-hit [params]
    (got-hit {:data (:got-ice-tornado-hit params)
              :timer timer
              :effect-color :blue
              :f (fn []
                   (re/insert :player/freeze-end-time (+ (js/Date.now) 1500)))})))

(let [timer (atom nil)]
  (defmethod dispatch-pro-response :got-wind-tornado-hit [params]
    (got-hit {:data (:got-wind-tornado-hit params)
              :timer timer
              :effect-color :white
              :f (fn []
                   (let [end-time (+ (js/Date.now) 2500)]
                     (re/insert {:player/wind-tornado-stunned-end-time end-time}))
                   (let [player-capsule (re/query :player/capsule)
                         [x y z] (api.core/v3->v (api.core/get-pos player-capsule))
                         [x2 y2 z2] (-> params :got-wind-tornado-hit :tornado-pos)
                         x2 (+ x2 (utils/rand-between -1.5 1.5))
                         z2 (+ z2 (utils/rand-between -1.5 1.5))
                         y2 (+ y2 5 (utils/rand-between -0.5 1))]
                     (api.tween/tween {:duration 1000
                                       :from {:x x :y y :z z}
                                       :to {:x x2 :y y2 :z z2}
                                       :on-update (fn [v]
                                                    (api.physics/set-pos player-capsule [(j/get v :x)
                                                                                         (j/get v :y)
                                                                                         (j/get v :z)]))})))})))

(let [timer (atom nil)]
  (defmethod dispatch-pro-response :got-light-staff-hit [params]
    (got-hit {:data (:got-light-staff-hit params)
              :duration-factor 1
              :timer timer})))

(let [timer (atom nil)]
  (defmethod dispatch-pro-response :got-ice-arrow-hit [params]
    (got-hit {:data (:got-ice-arrow-hit params)
              :timer timer
              :effect-color :blue
              :f (fn []
                   (re/upsert :player/mana #(Math/max 0 (- % (-> params :got-ice-arrow-hit :consumed-mana))))
                   (re/insert :player/mana-regen-unfreeze-time (+ (js/Date.now) 1000)))})))

(let [timer (atom nil)]
  (defmethod dispatch-pro-response :got-wind-slash-hit [params]
    (got-hit {:data (:got-wind-slash-hit params)
              :timer timer
              :effect-color :white
              :f (fn []
                   (let [dir (-> params :got-wind-slash-hit :dir api.core/v->v3 (j/call :scaleInPlace 1000))
                         player-capsule (re/query :player/capsule)
                         player-pos (api.core/get-pos player-capsule)]
                     (re/insert :player/last-time-got-wind-slash-hit (js/Date.now))
                     (api.physics/apply-impulse player-capsule dir player-pos)))})))

(let [timer (atom nil)]
  (defmethod dispatch-pro-response :fell [_]
    (got-hit {:data {:damage 1000
                     :died? true}
              :me? true
              :timer timer})))

(defmethod dispatch-pro-response :got-message [params]
  (let [data (:got-message params)
        message (:message data)
        player-id (:id data)
        players (re/query :players)
        current-player-id (re/query :player/id)
        current-player-username (re/query :player/username)
        username (if (= player-id current-player-id)
                   current-player-username
                   (get-in players [player-id :username]))]
    (re/upsert :chat/messages (fn [messages]
                                (if (seq messages)
                                  (->> {:username username
                                        :message message
                                        :time (js/Date.now)}
                                       (conj messages)
                                       (take-last 5)
                                       vec)
                                  [{:username username
                                    :message message
                                    :time (js/Date.now)}])))))

(reg-rule
  :chat/renew-times
  {:what {:player/chat-focus? {}}
   :then (fn [_]
           (re/upsert
             :chat/messages
             (fn [messages]
               (mapv #(assoc % :time (js/Date.now)) messages))))})

(reg-rule
  :network/online
  {:what {:dt {}}
   :run-every 1000
   :then (fn [_]
           (dispatch-pro :online))})

(reg-rule
  :rotate-vfx-ball
  {:what {:dt {}
          :player/tornados-to-spin {:then false}}
   :then (fn [{{:keys [dt player/tornados-to-spin]} :session}]
           (doseq [t tornados-to-spin]
             (j/update-in! t [:material :diffuseTexture :uOffset] + (* dt 2))))})

(reg-rule
  :player/scale-team-member-username
  {:run-every 50
   :what {:dt {}
          :player/capsule {:then false}
          :player/room-id {:then false}
          :player/team {:then false}
          :players {:then false}}
   :then (fn [{{player-team :player/team
                player-capsule :player/capsule
                players :players} :session}]
           (doseq [[_ {:keys [mesh team]}] players
                   :when (= team player-team)]
             (let [my-pos (api.core/get-pos player-capsule)
                   player-pos (api.core/get-pos mesh)
                   distance (api.core/distance my-pos player-pos)
                   final-scaling (if (<= distance 10)
                                   1
                                   (/ distance 10))
                   final-scaling (utils/clamp final-scaling 1 4)
                   username (j/get mesh :username)]
               (api.core/set-v3 (j/get username :scaling) final-scaling (- final-scaling) final-scaling))))})

(reg-rule
  :player/respawn
  {:what {:player/died? {:then false}
          :pointer-locked? {}
          :mouse/left-click? {}
          :player/current-health {:then false}}
   :when (fn [{{:keys [pointer-locked?
                       player/died?
                       player/died-time
                       player/paused?
                       player/paused-time
                       player/respawn-duration
                       player/respawn-requested-time
                       player/focus?
                       player/room-id]} :session}]
           (and pointer-locked?
                room-id
                (or (nil? focus?)
                    (false? focus?))
                (or (nil? respawn-requested-time)
                    (> (- (js/Date.now) respawn-requested-time) 1000))
                (or died? paused?)
                (or (has-time-passed? died-time respawn-duration)
                    (has-time-passed? paused-time respawn-duration))))
   :then (fn []
           (re/insert {:player/respawn-requested-time (js/Date.now)})
           (network/dispatch-pro :respawn))})

(reg-rule
  :player/check-inside-map?
  {:run-every 100
   :what {:dt {}
          :player/game-started? {}
          :player/capsule {:then false}
          :map/regen-box {:then false}}
   :when (fn [{{:keys [player/game-started?]} :session}]
           game-started?)
   :then (fn [{{:keys [player/capsule map/regen-box]} :session}]
           (re/insert {:player/inside-map? (j/call regen-box :intersectsPoint (api.core/get-pos capsule))}))})

(reg-rule
  :scene/move-collectables
  {:disabled? true
   :locals {:elapsed (atom 0)}
   :what {:dt {}
          :player/collectables {:then false}}
   :then (fn [{{:keys [player/collectables]} :session
               {:keys [elapsed]} :locals}]
           (let [elapsed (swap! elapsed + (api.core/get-delta-time))
                 y (/ (Math/sin elapsed) 10)]
             (doseq [[_ c] collectables
                     :when (j/get c :isEnabled)]
               (j/assoc-in! c [:position :y] y)
               (j/update-in! c [:rotation :y] + 0.01))))})

#_(reg-rule
    :player/let-user-know-collectable-ended
    {:run-every 250
     :what {:dt {}}
     :then (fn [{{:keys [player/speed-booster-until
                         player/prev-speed-booster-until]} :session}]
             (when (and (not= speed-booster-until prev-speed-booster-until)
                        (> (js/Date.now) speed-booster-until))
               (re/insert {:player/prev-speed-booster-until speed-booster-until})
               (api.sound/play :sound/collectable-finish)))})

(defmethod dispatch-pro-response :online [params]
  (re/insert :network/online (-> params :online :count)))

(defmethod dispatch-pro-response :notify-damage [{{:keys [spell]} :notify-damage}]
  (re/upsert :player/incoming-enemy-spells (fnil conj []) {:spell spell
                                                           :time (js/Date.now)})
  (re/upsert :player/incoming-enemy-spells (fn [spells]
                                             (filterv (fn [{:keys [time]}]
                                                        (> (+ time 1500) (js/Date.now))) spells))))

(reg-rule
  :game/play-map-change-time-counter-sound
  {:what {:game/map-change-time-counter {:when-value-change? true}}
   :then (fn [{{map-change-time-counter :game/map-change-time-counter} :session}]
           (when map-change-time-counter
             (api.sound/play :sound/map-change-countdown)))})

(reg-rule
  :game/map-change-remained-time
  {:run-every 250
   :what {:dt {}
          :game/server-time {:then false}
          :game/map-change-time {:then false}}
   :when (fn [{{:keys [game/mode]} :session}]
           (= mode :solo-death-match))
   :then (fn [{{:keys [game/server-time
                       game/map-change-time]} :session}]
           (let [remaining-ms (- map-change-time server-time)]
             (re/fire-rules {:game/map-change-time-counter (<= remaining-ms 5999)})))})

(comment
  (let [m (api.core/get-node-by-name "SM_Env_Rock_Cliff_02")
        rock (j/call m :createInstance "rock")]
    (j/assoc! rock
              :alwaysSelectAsActiveMesh true
              :doNotSyncBoundingInfo true
              :isPickable false
              :parent nil
              :position (v3 0 3 0)
              :rotation (v3 (api.core/to-rad 0) 90 0)
              :scaling (v3 0.001))
    (api.core/set-enabled rock true))

  (re/query :player/collectables)
  (j/call (api.core/get-engine) :setHardwareScalingLevel 1)

  )
