(ns main.api.particle
  (:require
    ["@babylonjs/core/Particles/gpuParticleSystem" :refer [GPUParticleSystem]]
    ["@babylonjs/core/Particles/particleSystem" :refer [ParticleSystem]]
    [applied-science.js-interop :as j]
    [clojure.string :as str]
    [main.api.asset :as api.asset]
    [main.api.constant :as api.const]
    [main.api.core :as api.core :refer [v3]]
    [main.config :as config]
    [main.rule-engine :as re])
  (:require-macros
    [main.macros :as m]))

(defn- remove-size-gradients [particle-system]
  (let [g (j/call particle-system :getSizeGradients)]
    (doseq [g (map (j/get :gradient) g)]
      (j/call particle-system :removeSizeGradient g))))

(defn add-gradients [particle-system {:keys [size-gradients
                                             color-gradients
                                             velocity-gradients
                                             limit-velocity-gradients
                                             angular-speed-gradients
                                             emit-rate-gradients
                                             life-time-gradients
                                             start-size-gradients]}]
  (when (seq size-gradients)
    (remove-size-gradients particle-system))
  (doseq [args size-gradients]
    (apply j/call particle-system :addSizeGradient args))
  (doseq [args color-gradients]
    (apply j/call particle-system :addColorGradient args))
  (doseq [args velocity-gradients]
    (apply j/call particle-system :addVelocityGradient args))
  (doseq [args limit-velocity-gradients]
    (apply j/call particle-system :addLimitVelocityGradient args))
  (doseq [args angular-speed-gradients]
    (apply j/call particle-system :addAngularSpeedGradient args))
  (doseq [args emit-rate-gradients]
    (apply j/call particle-system :addEmitRateGradient args))
  (doseq [args life-time-gradients]
    (apply j/call particle-system :addLifeTimeGradient args))
  (doseq [args start-size-gradients]
    (apply j/call particle-system :addStartSizeGradient args)))

(defn create-particle-system [{:keys [name
                                      capacity
                                      gpu?
                                      delay
                                      billboard?
                                      billboard-mode
                                      position
                                      rotation
                                      particle-texture
                                      emitter
                                      min-emit-box
                                      max-emit-box
                                      color1
                                      color2
                                      color-dead
                                      min-size
                                      max-size
                                      min-life-time
                                      max-life-time
                                      emit-rate
                                      blend-mode
                                      gravity
                                      direction1
                                      direction2
                                      pre-warm-step-offset
                                      pre-warm-cycles
                                      min-angular-speed
                                      max-angular-speed
                                      min-emit-power
                                      max-emit-power
                                      min-initial-rotation
                                      max-initial-rotation
                                      min-scale-x
                                      max-scale-x
                                      min-scale-y
                                      max-scale-y
                                      local?
                                      target-stop-duration
                                      dispose-on-stop?
                                      update-speed
                                      noise-texture
                                      noise-strength
                                      rendering-group-id
                                      texture-mask
                                      limit-velocity-damping
                                      animation-sheet-enabled?
                                      sprite-cell-height
                                      sprite-cell-width
                                      start-sprite-cell-id
                                      end-sprite-cell-id
                                      sprite-cell-change-speed
                                      sprite-cell-loop?
                                      sprite-random-start-cell?
                                      force-depth-write?
                                      manual-emit-count
                                      world-offset]
                               :or {capacity 2000}
                               :as opts}]
  (let [particle-system (if (and gpu? (j/get GPUParticleSystem :IsSupported))
                          (GPUParticleSystem. name capacity (api.core/get-scene))
                          (ParticleSystem. name capacity (api.core/get-scene)))]
    (add-gradients particle-system opts)
    (m/cond-doto particle-system
      particle-texture (j/assoc! :particleTexture particle-texture)
      emitter (j/assoc! :emitter emitter)
      position (j/assoc! :position position)
      rotation (j/assoc! :rotation rotation)
      min-emit-box (j/assoc! :minEmitBox min-emit-box)
      max-emit-box (j/assoc! :maxEmitBox max-emit-box)
      color1 (j/assoc! :color1 color1)
      color2 (j/assoc! :color2 color2)
      delay (j/assoc! :delay delay)
      color-dead (j/assoc! :colorDead color-dead)
      min-size (j/assoc! :minSize min-size)
      max-size (j/assoc! :maxSize max-size)
      min-life-time (j/assoc! :minLifeTime min-life-time)
      max-life-time (j/assoc! :maxLifeTime max-life-time)
      emit-rate (j/assoc! :emitRate emit-rate)
      blend-mode (j/assoc! :blendMode blend-mode)
      gravity (j/assoc! :gravity gravity)
      direction1 (j/assoc! :direction1 direction1)
      direction2 (j/assoc! :direction2 direction2)
      min-angular-speed (j/assoc! :minAngularSpeed min-angular-speed)
      max-angular-speed (j/assoc! :maxAngularSpeed max-angular-speed)
      min-emit-power (j/assoc! :minEmitPower min-emit-power)
      max-emit-power (j/assoc! :maxEmitPower max-emit-power)
      min-scale-x (j/assoc! :minScaleX min-scale-x)
      max-scale-x (j/assoc! :maxScaleX max-scale-x)
      min-scale-y (j/assoc! :minScaleY min-scale-y)
      max-scale-y (j/assoc! :maxScaleY max-scale-y)
      update-speed (j/assoc! :updateSpeed update-speed)
      noise-texture (j/assoc! :noiseTexture noise-texture)
      noise-strength (j/assoc! :noiseStrength noise-strength)
      rendering-group-id (j/assoc! :renderingGroupId rendering-group-id)
      pre-warm-step-offset (j/assoc! :preWarmStepOffset pre-warm-step-offset)
      pre-warm-cycles (j/assoc! :preWarmCycles pre-warm-cycles)
      min-initial-rotation (j/assoc! :minInitialRotation min-initial-rotation)
      max-initial-rotation (j/assoc! :maxInitialRotation max-initial-rotation)
      sprite-cell-height (j/assoc! :spriteCellHeight sprite-cell-height)
      sprite-cell-width (j/assoc! :spriteCellWidth sprite-cell-width)
      start-sprite-cell-id (j/assoc! :startSpriteCellID start-sprite-cell-id)
      end-sprite-cell-id (j/assoc! :endSpriteCellID end-sprite-cell-id)
      sprite-cell-change-speed (j/assoc! :spriteCellChangeSpeed sprite-cell-change-speed)
      manual-emit-count (j/assoc! :manualEmitCount manual-emit-count
                                  :hard-code-emit-count manual-emit-count)
      (some? billboard?) (j/assoc! :isBillboardBased billboard?)
      (some? sprite-cell-loop?) (j/assoc! :spriteCellLoop sprite-cell-loop?)
      (some? sprite-random-start-cell?) (j/assoc! :spriteRandomStartCell sprite-random-start-cell?)
      (some? local?) (j/assoc! :isLocal local?)
      world-offset (j/assoc! :worldOffset world-offset)
      target-stop-duration (j/assoc! :targetStopDuration target-stop-duration)
      (some? animation-sheet-enabled?) (j/assoc! :isAnimationSheetEnabled animation-sheet-enabled?)
      (some? dispose-on-stop?) (j/assoc! :disposeOnStop dispose-on-stop?)
      (some? force-depth-write?) (j/assoc! :forceDepthWrite force-depth-write?)
      texture-mask (j/assoc! :textureMask texture-mask)
      billboard-mode (j/assoc! :billboardMode billboard-mode)
      limit-velocity-damping (j/assoc! :limitVelocityDamping limit-velocity-damping))))

(defn update-speed [ps speed]
  (j/assoc! ps :updateSpeed speed))

(defn set-target-stop-duration [ps target-stop-duration]
  (j/assoc! ps :targetStopDuration (/ target-stop-duration (/ 0.01 (j/get ps :updateSpeed)))))

(defn set-emit-rate [ps emit-rate]
  (j/assoc! ps :emitRate emit-rate))

(defn reset [ps]
  (if (vector? ps)
    (doseq [p ps]
      (j/assoc! p "_actualFrame" 0)
      (j/call p :reset))
    (do
      (j/assoc! ps "_actualFrame" 0)
      (j/call ps :reset))))

(defn start
  ([ps]
   (reset ps)
   (if (vector? ps)
     (doseq [p ps]
       (j/call p :start))
     (j/call ps :start)))
  ([ps delay]
   (reset ps)
   (if (vector? ps)
     (doseq [p ps]
       (j/call p :start))
     (j/call ps :start delay))))

(defn clear-emitters [ps]
  (if (vector? ps)
    (doseq [p ps]
      (j/assoc! p :emitter nil))
    (j/assoc! ps :emitter nil)))

(defn stop [ps]
  (if (vector? ps)
    (doseq [p ps]
      (j/call p :stop))
    (j/call ps :stop)))

(defn started? [ps]
  (j/call ps :isStarted))

(defn stopped? [ps]
  (j/call ps :isAlive))

(defn dispose [ps]
  (if (vector? ps)
    (doseq [p ps]
      (j/call p :dispose))
    (j/call ps :dispose)))

(defn dispose-on-stop [ps]
  (if (vector? ps)
    (doseq [p ps]
      (j/assoc! p :disposeOnStop true))
    (j/assoc! ps :disposeOnStop true)))

(defn gpu-with-target-stop-dur? [ps]
  (if (vector? ps)
    (some (fn [p] (and (j/get p :isGPU)
                       (> (j/get p :targetStopDuration) 0))) ps)
    (and (j/get ps :isGPU)
         (> (j/get ps :targetStopDuration) 0))))

(defn push-to-pool [pool ps]
  (js/setTimeout
    (fn []
      (stop ps)
      (reset ps)
      (clear-emitters ps)
      (re/push-to-pool pool ps))
    1000))

(defn- clear-all-ps-and-pools []
  (when config/dev?
    (doseq [pool (filter
                   (fn [pool] (str/starts-with? (name pool) "particle-"))
                   (keys (re/query :pool-item-creation)))]
      (let [items (re/query pool)]
        (doseq [ps items]
          (stop ps)
          (reset ps)
          (clear-emitters ps))
        (re/insert pool [])))))

(defn- create-auto-pool [ps pool]
  (if (vector? ps)
    (let [n-of-ps (count ps)
          ps-done-count (atom 0)
          on-stop (fn []
                    (swap! ps-done-count inc)
                    (when (= @ps-done-count n-of-ps)
                      (push-to-pool pool ps)))]
      (doseq [p ps]
        (j/call-in p [:onStoppedObservable :addOnce] on-stop)))
    (j/call-in ps [:onStoppedObservable :addOnce] #(push-to-pool pool ps))))

(defn start-ps [particle {:keys [delay emitter auto-pool]}]
  #_(clear-all-ps-and-pools)
  (let [ps (if (keyword? particle)
             (re/pop-from-pool particle)
             particle)]
    (some->> auto-pool (create-auto-pool ps))
    (if (vector? ps)
      (doseq [p ps]
        (when-let [ec (j/get p :hard-code-emit-count)]
          (j/assoc! p :manualEmitCount ec))
        (j/assoc! p :emitter emitter)
        (j/call p :start (or delay (j/get p :delay))))
      (do
        (when-let [ec (j/get ps :hard-code-emit-count)]
          (j/assoc! ps :manualEmitCount ec))
        (j/assoc! ps :emitter emitter)
        (j/call ps :start (or delay (j/get ps :delay)))))
    ps))

(defn fire-projectile []
  (let [smoke-color (api.core/color-rgb 125 125 125 255)
        smoke (create-particle-system {:name "fire-projectile-smoke"
                                       :gpu? true
                                       :emitter nil
                                       :particle-texture (api.asset/get-asset :texture/smoke-6)
                                       :capacity 50
                                       :min-life-time 1
                                       :max-life-time 1
                                       :color1 smoke-color
                                       :color2 smoke-color
                                       :color-dead smoke-color
                                       :blend-mode api.const/particle-blend-mode-standard
                                       :animation-sheet-enabled? true
                                       :sprite-cell-width 102
                                       :sprite-cell-height 66
                                       :sprite-cell-loop? true
                                       :start-sprite-cell-id 0
                                       :end-sprite-cell-id 6
                                       :sprite-cell-change-speed 1
                                       :min-size 0.1
                                       :max-size 0.3
                                       :pre-warm-cycles 100
                                       :min-angular-speed 0
                                       :max-angular-speed (* Math/PI 4)
                                       :min-emit-power 0
                                       :max-emit-power 0
                                       :update-speed 0.02
                                       :emit-rate 50
                                       :size-gradients [[0 0.5]
                                                        [0.33 0.2]
                                                        [1 0]]})
        ay-trail (create-particle-system {:name "fire-projectile-moon"
                                          :gpu? true
                                          :emitter nil
                                          :particle-texture (api.asset/get-asset :texture/ay)
                                          :capacity 50
                                          :min-life-time 1
                                          :max-life-time 1
                                          :color1 (api.core/color 1 0 0 1)
                                          :color2 (api.core/color-rgb 255 94 0 1)
                                          :color-dead (api.core/color 0 0 0 1)
                                          :pre-warm-cycles 100
                                          :min-angular-speed 0
                                          :max-angular-speed (* Math/PI 4)
                                          :min-emit-power 0
                                          :max-emit-power 0
                                          :update-speed 0.02
                                          :emit-rate 35
                                          :size-gradients [[0 1]
                                                           [0.33 0.5]
                                                           [1 0]]})]
    (j/call smoke :createSphereEmitter 0.1 0)
    (j/call ay-trail :createSphereEmitter 0.1 0)
    [smoke ay-trail]))

(defn wind-slash []
  [(create-particle-system {:name "wind-slash"
                            :emitter nil
                            :particle-texture (api.asset/get-asset :texture/wind-sprites)
                            :capacity 50
                            :min-life-time 1
                            :max-life-time 1
                            :min-emit-box (v3)
                            :max-emit-box (v3)
                            :color1 (api.core/color-rgb 255 255 255 255)
                            :color2 (api.core/color-rgb 255 255 255 255)
                            :color-dead (api.core/color-rgb 0 0 0 255)
                            :blend-mode api.const/particle-blend-mode-add
                            :animation-sheet-enabled? true
                            :sprite-cell-width 90
                            :sprite-cell-height 90
                            :start-sprite-cell-id 0
                            :end-sprite-cell-id 9
                            :sprite-cell-change-speed 1
                            :limit-velocity-gradients [[0 15]
                                                       [0.15 10]
                                                       [0.25 5]
                                                       [1 0]]
                            :size-gradients [[0 0]
                                             [0.3 1.5]
                                             [0.5 2]
                                             [0.9 1.8]
                                             [1 0]]
                            :billboard? false
                            :min-angular-speed 0
                            :max-angular-speed 20
                            :min-initial-rotation 0
                            :max-initial-rotation Math/PI
                            :min-emit-power 1
                            :max-emit-power 2
                            :update-speed 0.031
                            :emit-rate 10})
   (create-particle-system {:name "wind-slash-2"
                            :emitter nil
                            :particle-texture (api.asset/get-asset :texture/wind-sprites)
                            :capacity 50
                            :min-life-time 1
                            :max-life-time 1
                            :min-emit-box (v3)
                            :max-emit-box (v3)
                            :color1 (api.core/color-rgb 255 255 255 255)
                            :color2 (api.core/color-rgb 255 255 255 255)
                            :color-dead (api.core/color-rgb 0 0 0 255)
                            :blend-mode api.const/particle-blend-mode-add
                            :animation-sheet-enabled? true
                            :sprite-cell-width 90
                            :sprite-cell-height 90
                            :start-sprite-cell-id 0
                            :end-sprite-cell-id 9
                            :sprite-cell-change-speed 1
                            :limit-velocity-gradients [[0 15]
                                                       [0.15 10]
                                                       [0.25 5]
                                                       [1 0]]
                            :size-gradients [[0 0]
                                             [0.3 1.5]
                                             [0.5 2]
                                             [0.9 1.8]
                                             [1 0]]
                            :billboard? true
                            :min-angular-speed 0
                            :max-angular-speed 20
                            :min-initial-rotation 0
                            :max-initial-rotation Math/PI
                            :min-emit-power 1
                            :max-emit-power 2
                            :update-speed 0.031
                            :emit-rate 10})])

(defn wind-hit []
  (let [ps (create-particle-system {:name "wind-hit-1"
                                    :emitter nil
                                    :particle-texture (api.asset/get-asset :texture/wind-hit-sprites)
                                    :capacity 1
                                    :min-life-time 1
                                    :max-life-time 1
                                    :color1 (api.core/color-rgb 255 255 255 255)
                                    :color2 (api.core/color-rgb 255 255 255 255)
                                    :color-dead (api.core/color-rgb 0 0 0 255)
                                    :blend-mode api.const/particle-blend-mode-add
                                    :animation-sheet-enabled? true
                                    :sprite-cell-width 80
                                    :sprite-cell-height 80
                                    :start-sprite-cell-id 0
                                    :end-sprite-cell-id 9
                                    :sprite-cell-change-speed 0.8
                                    :min-scale-x 2
                                    :max-scale-x 2
                                    :min-scale-y 2
                                    :max-scale-y 2
                                    :world-offset (v3 0 1 0)
                                    :billboard? true
                                    :billboard-mode api.const/particle-billboard-mode-all
                                    :min-emit-power 1
                                    :max-emit-power 1
                                    :update-speed 0.028
                                    :size-gradients [[0 0.075]
                                                     [1 3]]
                                    :target-stop-duration 1})
        ps2 (create-particle-system {:name "wind-hit-2"
                                     :emitter nil
                                     :particle-texture (api.asset/get-asset :texture/wind-hit-sprites)
                                     :capacity 1
                                     :min-life-time 1
                                     :max-life-time 1
                                     :color1 (api.core/color-rgb 255 255 255 255)
                                     :color2 (api.core/color-rgb 255 255 255 255)
                                     :color-dead (api.core/color-rgb 0 0 0 255)
                                     :blend-mode api.const/particle-blend-mode-add
                                     :animation-sheet-enabled? true
                                     :sprite-cell-width 80
                                     :sprite-cell-height 80
                                     :start-sprite-cell-id 0
                                     :end-sprite-cell-id 9
                                     :sprite-cell-change-speed 0.8
                                     :min-scale-x 2
                                     :max-scale-x 2
                                     :min-scale-y 2
                                     :max-scale-y 2
                                     :world-offset (v3 0 1 0)
                                     :billboard? true
                                     :billboard-mode api.const/particle-billboard-mode-all
                                     :min-emit-power 1
                                     :max-emit-power 1
                                     :update-speed 0.04
                                     :size-gradients [[0 0.1]
                                                      [1 4]]
                                     :target-stop-duration 1})]
    (j/call ps :createPointEmitter (v3) (v3))
    (j/call ps2 :createPointEmitter (v3) (v3))
    [ps ps2]))

(defn fire-ball-explode []
  (let [ps (create-particle-system {:name "fire-ball-explode"
                                    :gpu? true
                                    :particle-texture (api.asset/get-asset :texture/smoke-sprite)
                                    :capacity 50
                                    :emitter nil
                                    :min-life-time 3
                                    :max-life-time 3
                                    :target-stop-duration 2
                                    :emit-rate 50
                                    :min-emit-power 5
                                    :max-emit-power 5
                                    :blend-mode api.const/particle-blend-mode-multiply-add
                                    :limit-velocity-gradients [[0 15]
                                                               [0.15 10]
                                                               [0.25 5]
                                                               [1 0]]
                                    :color-gradients [[0
                                                       (api.core/color-rgb 255 255 0 255)
                                                       (api.core/color-rgb 0 0 0 255)]
                                                      [0.7
                                                       (api.core/color-rgb 255 25 25 255)
                                                       (api.core/color-rgb 244 40 40 255)]
                                                      [1
                                                       (api.core/color-rgb 0 0 0 255)
                                                       (api.core/color-rgb 0 0 0 255)]]
                                    :animation-sheet-enabled? true
                                    :sprite-cell-width 128
                                    :sprite-cell-height 128
                                    :sprite-cell-loop? true
                                    :sprite-random-start-cell? true
                                    :start-sprite-cell-id 0
                                    :end-sprite-cell-id 63
                                    :sprite-cell-change-speed 1
                                    :update-speed 0.1
                                    :size-gradients [[0 2]
                                                     [0.5 1]
                                                     [1 0]]
                                    :local? true})
        circle (create-particle-system
                 {:name "fire-ball-circle-explode"
                  :gpu? true
                  :particle-texture (api.asset/get-asset :texture/circle)
                  :capacity 1
                  :emitter nil
                  :blend-mode api.const/particle-blend-mode-add
                  :billboard? true
                  :min-size 0.1
                  :max-size 0.5
                  :min-scale-x 4
                  :max-scale-x 4
                  :min-scale-y 4
                  :max-scale-y 4
                  :min-emit-power 1
                  :max-emit-power 1
                  :min-life-time 1
                  :max-life-time 1
                  :update-speed 0.04
                  :size-gradients [[0 0]
                                   [1 4]]
                  :color-gradients [[0
                                     (api.core/color-rgb 255 25 25 255)
                                     (api.core/color-rgb 244 40 40 255)]
                                    [0.5
                                     (api.core/color-rgb 255 255 0 255)
                                     (api.core/color-rgb 234 234 37 255)]
                                    [1
                                     (api.core/color-rgb 255 0 0 0)
                                     (api.core/color-rgb 255 0 37 0)]]
                  :target-stop-duration 1})]
    (j/call circle :createPointEmitter (v3) (v3))
    (j/call ps :createCylinderEmitter 0.1 0.1)
    [ps circle]))

(defn super-nova-shockwave []
  (let [ps (create-particle-system {:name "super-nova-shockwave"
                                    ;; :gpu? true
                                    :particle-texture (api.asset/get-asset :texture/smoke-sprite)
                                    :capacity 100
                                    :emitter nil
                                    :min-life-time 4
                                    :max-life-time 4
                                    :target-stop-duration 3
                                    :emit-rate 100
                                    :min-emit-power 8
                                    :max-emit-power 8
                                    :blend-mode api.const/particle-blend-mode-multiply-add
                                    :limit-velocity-gradients [[0 15]
                                                               [0.15 10]
                                                               [0.25 5]
                                                               [1 0]]
                                    :color-gradients [[0
                                                       (api.core/color-rgb 255 255 0 255)
                                                       (api.core/color-rgb 0 0 0 255)]
                                                      [0.7
                                                       (api.core/color-rgb 255 25 25 255)
                                                       (api.core/color-rgb 244 40 40 255)]
                                                      [1
                                                       (api.core/color-rgb 0 0 0 255)
                                                       (api.core/color-rgb 0 0 0 255)]]
                                    :animation-sheet-enabled? true
                                    :sprite-cell-width 128
                                    :sprite-cell-height 128
                                    :sprite-cell-loop? true
                                    :sprite-random-start-cell? true
                                    :start-sprite-cell-id 0
                                    :end-sprite-cell-id 63
                                    :sprite-cell-change-speed 1
                                    :update-speed 0.1
                                    :world-offset (v3 0 1 0)
                                    :size-gradients [[0 3]
                                                     [0.5 2]
                                                     [1 0]]
                                    :local? true})]
    (j/call ps :createCylinderEmitter 0.1 0.1)
    ps))

(defn snowflake []
  (create-particle-system {:name "snowflake"
                           :gpu? true
                           :particle-texture (api.asset/get-asset :texture/ice-particle)
                           :capacity 100
                           :emitter nil
                           :direction1 (v3 1 0.1 1)
                           :direction2 (v3 -1 -0.1 -1)
                           :min-emit-box (v3 -0.2)
                           :max-emit-box (v3 0.2)
                           :min-life-time 1
                           :max-life-time 3
                           :blend-mode api.const/particle-blend-mode-add
                           :min-size 0.1
                           :max-size 0.3
                           :min-angular-speed 0
                           :max-angular-speed (/ Math/PI 2)
                           :min-emit-power 1
                           :max-emit-power 5
                           :update-speed 0.005
                           :emit-rate 50
                           :target-stop-duration 1
                           :size-gradients [[0 0.3]
                                            [0.33 0.2]
                                            [1 0]]}))

(defn levitate-trail [{:keys [name emitter]}]
  (let [ps (create-particle-system {:name name
                                    ;; :gpu? true
                                    :particle-texture (api.asset/get-asset :texture/star)
                                    :capacity 100
                                    :min-life-time 0.1
                                    :max-life-time 0.3
                                    :color-gradients [[0.33 (api.core/color-rgb 195 41 32 255)]
                                                      [0.66 (api.core/color-rgb 255 156 67 255)]
                                                      [1 (api.core/color-rgb 215 195 97 255)]]
                                    :min-size 0.15
                                    :max-size 0.25
                                    :gravity (v3 0 -9.81 0)
                                    :min-angular-speed 0
                                    :max-angular-speed Math/PI
                                    :min-emit-power 1
                                    :max-emit-power 3
                                    :update-speed 0.005
                                    :emit-rate 100
                                    :emitter emitter
                                    :world-offset (v3 0 -1 0)})]
    ps))

(defn dash [{:keys [name emitter]}]
  (let [duration 1.5
        ps (create-particle-system {:name name
                                    :particle-texture (api.asset/get-asset :texture/dash)
                                    :capacity 1
                                    :emitter emitter
                                    :blend-mode api.const/particle-blend-mode-add
                                    :animation-sheet-enabled? true
                                    :sprite-cell-width 135
                                    :sprite-cell-height 133
                                    :sprite-cell-loop? false
                                    :start-sprite-cell-id 0
                                    :end-sprite-cell-id 6
                                    :sprite-cell-change-speed 10
                                    :min-life-time duration
                                    :max-life-time duration
                                    :direction1 (v3)
                                    :direction2 (v3)
                                    :min-emit-power 1
                                    :max-emit-power 1
                                    :target-stop-duration duration
                                    :local? true
                                    :emit-rate 0
                                    :manual-emit-count 1})]
    (j/assoc! ps :renderingGroupId 1)
    (j/assoc! emitter :renderingGroupId 2)
    (j/call ps :createPointEmitter (v3) (v3))
    (j/call-in ps [:onStoppedObservable :add] (fn []
                                                (j/call ps :reset)
                                                (j/assoc! ps :manualEmitCount 1)))
    ps))

(defn shockwave [{:keys [name emitter]}]
  (let [ps (create-particle-system {:name name
                                    ;; :gpu? true
                                    :particle-texture (api.asset/get-asset :texture/smoke-sprite)
                                    :capacity 100
                                    :emitter emitter
                                    :min-life-time 3
                                    :max-life-time 3
                                    :target-stop-duration 0.5
                                    :emit-rate 100
                                    :min-emit-power 1
                                    :max-emit-power 1
                                    :color1 (api.core/color-rgb 255 255 255 255)
                                    :color2 (api.core/color-rgb 255 255 255 255)
                                    :color-dead (api.core/color-rgb 255 255 255 255)
                                    :blend-mode api.const/particle-blend-mode-multiply-add
                                    :limit-velocity-gradients [[0 0.5]
                                                               [0.15 1]
                                                               [0.25 2]
                                                               [1 0]]
                                    :animation-sheet-enabled? true
                                    :sprite-cell-width 128
                                    :sprite-cell-height 128
                                    :sprite-cell-loop? true
                                    :sprite-random-start-cell? true
                                    :start-sprite-cell-id 0
                                    :end-sprite-cell-id 63
                                    :sprite-cell-change-speed 1
                                    :world-offset (v3 0 -0.8 0)
                                    :update-speed 0.1
                                    :size-gradients [[0 1]
                                                     [0.5 0.5]
                                                     [1 0]]})]
    (j/call ps :createCylinderEmitter 0.1 0.1)
    ps))

(defn speed-line [{:keys [name emitter]}]
  (let [ps (create-particle-system {:name name
                                    :particle-texture (api.asset/get-asset :texture/flare)
                                    ;; :pre-warm-cycles 100
                                    ;; :pre-warm-step-offset 10
                                    :capacity 500
                                    :emitter emitter
                                    :min-size 0.1
                                    :max-size 0.5
                                    :min-life-time 0
                                    :max-life-time 0
                                    :emit-rate 200
                                    :update-speed 0.05
                                    :min-emit-power 0.01
                                    :max-emit-power 0.01
                                    :min-scale-x 0.3
                                    :max-scale-x 0.3
                                    :min-scale-y 20
                                    :max-scale-y 20
                                    :billboard? true
                                    :billboard-mode api.const/particle-billboard-mode-stretched})]
    (j/assoc! ps :renderingGroupId 1)
    (j/call ps :createCylinderEmitter 8 1 0)
    (j/call-in ps [:onStoppedObservable :add] #(j/assoc! ps :stopped? true))
    ps))

(defn arrow-sparkles []
  (let [ps (create-particle-system {:name "arrow-sparkles"
                                    :gpu? true
                                    :particle-texture (api.asset/get-asset :texture/ice-arrow)
                                    :capacity 50
                                    :emitter nil
                                    :min-angular-speed 0
                                    :max-angular-speed Math/PI
                                    :min-life-time 0.3
                                    :max-life-time 1
                                    :target-stop-duration 0.2
                                    :emit-rate 50
                                    :min-emit-power 1
                                    :max-emit-power 10
                                    :color1 (api.core/color-rgb 179 204 255 255)
                                    :color2 (api.core/color-rgb 51 128 255 255)
                                    :color-dead (api.core/color-rgb 0 0 50 255)
                                    :blend-mode api.const/particle-blend-mode-add
                                    :billboard-mode api.const/particle-billboard-mode-stretched
                                    :limit-velocity-gradients [[0 10]
                                                               [0.55 1]
                                                               [1 0]]
                                    :min-scale-x 0.5
                                    :max-scale-x 0.5
                                    :min-scale-y 0.5
                                    :max-scale-y 0.5
                                    :update-speed 0.02
                                    :size-gradients [[0 1]
                                                     [0.75 0.1]
                                                     [1 0]]})]
    (j/call ps :createSphereEmitter 0.5 1)
    ps))

(defn ice-arrow-hit []
  (let [ps (create-particle-system {:name "ice-hit"
                                    :gpu? true
                                    :particle-texture (api.asset/get-asset :texture/smoke-4)
                                    :capacity 100
                                    :min-life-time 0.3
                                    :max-life-time 0.3
                                    :color-gradients [[0 (api.core/color-rgb 179 204 255 255)]
                                                      [0.5 (api.core/color-rgb 51 128 255 255)]
                                                      [1 (api.core/color 0 0 0.2 0.0)]]
                                    :min-size 0.1
                                    :max-size 1
                                    :size-gradients [[0 1]
                                                     [0.75 2]
                                                     [1 0]]
                                    :gravity (v3 0 -9.81 0)
                                    :min-angular-speed 0
                                    :max-angular-speed Math/PI
                                    :min-emit-power 1
                                    :max-emit-power 3
                                    :emit-rate 100
                                    :local? true
                                    :emitter nil
                                    :update-speed 0.02
                                    :target-stop-duration 0.1
                                    :world-offset (v3 0 1 0)})]
    (j/call ps :createSphereEmitter 0.1)
    ps))

(defn kill-splash-blood []
  (let [duration 1.5
        ps (create-particle-system {:name "kill-splash-blood"
                                    :particle-texture (api.asset/get-asset :texture/kill-splash)
                                    :capacity 1
                                    :blend-mode api.const/particle-blend-mode-standard
                                    :min-life-time (- duration 1)
                                    :max-life-time duration
                                    :color-gradients [[0 (api.core/color 1 0 0 1)]
                                                      [0.9 (api.core/color 0.85 0.35 0.35 1)]
                                                      [1 (api.core/color 1 1 1 1)]]
                                    :size-gradients [[0 1]
                                                     [0.3 0.8]
                                                     [0.4 0.5]
                                                     [0.5 0.2]
                                                     [0.6 0.1]
                                                     [1 0]]
                                    :update-speed 0.007
                                    :manual-emit-count 1
                                    :local? true
                                    :emitter nil
                                    :target-stop-duration (/ duration 2)})]
    (j/call ps :createPointEmitter (v3) (v3))
    ps))

(defn kill-splash-surface []
  (let [duration 1
        ps (create-particle-system {:name "kill-splash-surface"
                                    :particle-texture (api.asset/get-asset :texture/kill-surface)
                                    :capacity 20
                                    :blend-mode api.const/particle-blend-mode-standard
                                    :min-life-time (- duration 1)
                                    :max-life-time duration
                                    :color1 (api.core/color-rgb 41 88 130 255)
                                    :color2 (api.core/color-rgb 125 155 180 255)
                                    :color-dead (api.core/color 1 1 1 1)
                                    :size-gradients [[0 2]
                                                     [0.3 0.2]
                                                     [0.5 0.1]
                                                     [0.6 0.1]
                                                     [0.8 0]
                                                     [1 0]]
                                    :limit-velocity-gradients [[0 5]
                                                               [0.15 3]
                                                               [0.25 2]
                                                               [0.5 1]
                                                               [0.6 0]
                                                               [1 0]]
                                    :min-angular-speed 0
                                    :max-angular-speed (/ Math/PI 2)
                                    :update-speed 0.008
                                    :manual-emit-count 20
                                    :local? true
                                    :emitter nil
                                    :target-stop-duration (/ duration 2)})]
    (j/call ps :createSphereEmitter 1 1)
    ps))

(defn light-staff [& {:keys [target-stop-duration
                             render-group-id]}]
  (let [ps (create-particle-system {:name "light-staff"
                                    :particle-texture (api.asset/get-asset :texture/light-staff)
                                    :billboard? false
                                    :capacity 100
                                    :emitter nil
                                    :min-life-time 0.1
                                    :max-life-time 0.3
                                    :emit-rate 5
                                    :min-emit-power 0
                                    :max-emit-power 0
                                    :min-size 1
                                    :max-size 1
                                    :min-scale-x 0.8
                                    :max-scale-x 1
                                    :blend-mode api.const/particle-blend-mode-one-one
                                    :color-gradients [[0
                                                       (api.core/color-rgb 0 0 0 255)
                                                       (api.core/color-rgb 0 0 0 255)]
                                                      [1
                                                       (api.core/color-rgb 45 132 239 255)
                                                       (api.core/color-rgb 42 192 253 255)]]
                                    :animation-sheet-enabled? true
                                    :sprite-cell-width 64
                                    :sprite-cell-height 256
                                    :sprite-cell-loop? true
                                    :sprite-random-start-cell? true
                                    :start-sprite-cell-id 0
                                    :end-sprite-cell-id 7
                                    :sprite-cell-change-speed 0.9
                                    :update-speed 0.017
                                    :target-stop-duration (or target-stop-duration 0)
                                    :local? true})]
    (j/assoc! ps :renderingGroupId (or render-group-id 0))
    (j/call ps :createPointEmitter (v3 0 1 0) (v3 0 1 0))
    ps))

(defn light-burst []
  (let [ps (create-particle-system {:name "light-burst"
                                    :gpu? true
                                    :particle-texture (api.asset/get-asset :texture/light-burst)
                                    :billboard? true
                                    :local? true
                                    :capacity 100
                                    :emitter nil
                                    :min-emit-power 0
                                    :max-emit-power 0
                                    :min-life-time 0.2
                                    :max-life-time 0.2
                                    :target-stop-duration 0.2
                                    :emit-rate 10
                                    :blend-mode api.const/particle-blend-mode-one-one
                                    :world-offset (v3 0 1 0)
                                    :color-gradients [[0
                                                       (api.core/color-rgb 0 0 0 255)
                                                       (api.core/color-rgb 0 0 0 255)]
                                                      [0.25
                                                       (api.core/color-rgb 174 183 187 255)
                                                       (api.core/color-rgb 57 141 180 255)]
                                                      [0.58
                                                       (api.core/color-rgb 109 118 122 255)
                                                       (api.core/color-rgb 29 111 136 255)]
                                                      [1
                                                       (api.core/color-rgb 0 0 0 255)
                                                       (api.core/color-rgb 0 0 0 255)]]
                                    :size-gradients [[0 0.8 1]
                                                     [1 2 3]]
                                    :update-speed 0.017})]
    (j/call ps :createSphereEmitter 0.2 0)
    ps))

(defn light-strike []
  (let [duration 5
        ps (create-particle-system {:name "light-strike"
                                    :particle-texture (api.asset/get-asset :texture/light-staff)
                                    :billboard? true
                                    :billboard-mode api.const/mesh-billboard-mode-y
                                    :capacity 50
                                    :emitter nil
                                    :min-life-time 0.8
                                    :max-life-time 0.8
                                    :emit-rate 30
                                    :min-emit-power 0
                                    :max-emit-power 0
                                    :min-emit-box (v3 -5 0 -5)
                                    :max-emit-box (v3 5 0 5)
                                    :min-size 1
                                    :max-size 1
                                    :min-scale-x 0.8
                                    :max-scale-x 1
                                    :min-scale-y 10
                                    :max-scale-y 10
                                    :world-offset (v3 0 5 0)
                                    :blend-mode api.const/particle-blend-mode-one-one
                                    :color-gradients [[0
                                                       (api.core/color-rgb 0 0 0 255)
                                                       (api.core/color-rgb 0 0 0 255)]
                                                      [1
                                                       (api.core/color-rgb 45 132 239 255)
                                                       (api.core/color-rgb 42 192 253 255)]]
                                    :animation-sheet-enabled? true
                                    :sprite-cell-width 64
                                    :sprite-cell-height 256
                                    :sprite-cell-loop? true
                                    :sprite-random-start-cell? true
                                    :start-sprite-cell-id 0
                                    :end-sprite-cell-id 7
                                    :sprite-cell-change-speed 0.9
                                    :update-speed 0.017
                                    :target-stop-duration duration})
        ps2 (create-particle-system {:name "light-ground-strike"
                                     :particle-texture (api.asset/get-asset :texture/light-burst)
                                     :billboard? false
                                     :capacity 30
                                     :world-offset (v3 0 0.1 0)
                                     :emitter nil
                                     :min-life-time 0.4
                                     :max-life-time 0.9
                                     :emit-rate 8
                                     :min-emit-power 0
                                     :max-emit-power 0
                                     :min-size 1
                                     :max-size 1
                                     :min-scale-x 1
                                     :max-scale-x 1
                                     :min-scale-y 1
                                     :max-scale-y 1
                                     :min-angular-speed -0.5
                                     :max-angular-speed 0.5
                                     :min-emit-box (v3)
                                     :max-emit-box (v3)
                                     :min-initial-rotation -3.14
                                     :max-initial-rotation 3.14
                                     :size-gradients [[0 0 0]
                                                      [0.3 4 4]
                                                      [1 15 15]]
                                     :color-gradients [[0
                                                        (api.core/color-rgb 0 0 0 255)
                                                        (api.core/color-rgb 0 0 0 255)]
                                                       [1
                                                        (api.core/color-rgb 45 132 239 255)
                                                        (api.core/color-rgb 42 192 253 255)]]
                                     :update-speed 0.017
                                     :target-stop-duration duration})
        p3 (create-particle-system {:name "light-cloud"
                                    :particle-texture (api.asset/get-asset :texture/cloud)
                                    :billboard? true
                                    :billboard-mode api.const/mesh-billboard-mode-y
                                    :capacity 50
                                    :emitter nil
                                    :min-life-time 1
                                    :max-life-time 5
                                    :emit-rate 25
                                    :min-emit-power 0
                                    :max-emit-power 0
                                    :min-emit-box (v3 -5 0 -5)
                                    :max-emit-box (v3 5 0 5)
                                    :min-size 1
                                    :max-size 1
                                    :min-scale-x 1
                                    :max-scale-x 1
                                    :min-scale-y 1
                                    :max-scale-y 1
                                    :world-offset (v3 0 11 0)
                                    :blend-mode api.const/particle-blend-mode-standard
                                    :size-gradients [[0 0 0]
                                                     [0.3 4 4]
                                                     [0.75 5 5]
                                                     [1 0 0]]
                                    :color-gradients [[0
                                                       (api.core/color-rgb 255 255 255 255)
                                                       (api.core/color-rgb 255 255 255 255)]
                                                      [1
                                                       (api.core/color-rgb 45 132 239 255)
                                                       (api.core/color-rgb 42 192 253 255)]]
                                    :update-speed 0.017
                                    :target-stop-duration (/ duration 2)})]
    [ps ps2 p3]))

(defn wind-tornado-clouds []
  (let [ps (create-particle-system {:name "wind-tornado-clouds"
                                    :emitter nil
                                    :local? true
                                    :particle-texture (api.asset/get-asset :texture/smoke-sprite)
                                    :capacity 50
                                    :min-life-time 3
                                    :max-life-time 3
                                    :emit-rate 25
                                    :min-emit-power 0
                                    :max-emit-power 1
                                    :min-scale-x 1.5
                                    :max-scale-x 1.5
                                    :min-scale-y 1.5
                                    :max-scale-y 1.5
                                    :color1 (api.core/color-rgb 255 255 255 255)
                                    :color2 (api.core/color-rgb 255 255 255 255)
                                    :color-dead (api.core/color-rgb 255 255 255 255)
                                    :blend-mode api.const/particle-blend-mode-multiply-add
                                    :limit-velocity-gradients [[0 0]
                                                               [0.15 2]
                                                               [0.25 0]
                                                               [1 0]]
                                    :animation-sheet-enabled? true
                                    :sprite-cell-width 128
                                    :sprite-cell-height 128
                                    :sprite-cell-loop? true
                                    :sprite-random-start-cell? true
                                    :start-sprite-cell-id 0
                                    :end-sprite-cell-id 63
                                    :sprite-cell-change-speed 1
                                    :world-offset (v3 0 -8 0)
                                    :update-speed 0.06
                                    :size-gradients [[0 0]
                                                     [0.5 3]
                                                     [1 0]]})]
    (j/call ps :createPointEmitter (v3 1) (v3 -1 1 -1))
    ps))

(defn toxic-projectile []
  (let [smoke (create-particle-system {:name "toxic-projectile"
                                       :gpu? true
                                       :emitter nil
                                       :particle-texture (api.asset/get-asset :texture/smoke-sprite)
                                       :capacity 100
                                       :gravity (v3 0 -0.1 0)
                                       :min-life-time 0
                                       :max-life-time 3
                                       :emit-rate 50
                                       :min-emit-power 0
                                       :max-emit-power 1
                                       :color-gradients [[0 (api.core/color-rgb 255 255 255 255)]
                                                         [0.3 (api.core/color-rgb 0 180 0 255)]
                                                         [0.5 (api.core/color-rgb 0 150 0 255)]
                                                         [1 (api.core/color-rgb 0 110 0 255)]]
                                       :blend-mode api.const/particle-blend-mode-multiply-add
                                       :animation-sheet-enabled? true
                                       :sprite-cell-width 128
                                       :sprite-cell-height 128
                                       :sprite-cell-loop? true
                                       :sprite-random-start-cell? true
                                       :start-sprite-cell-id 0
                                       :end-sprite-cell-id 63
                                       :sprite-cell-change-speed 1
                                       :update-speed 0.06
                                       :size-gradients [[0 0]
                                                        [0.5 0.5]
                                                        [1 0]
                                                        #_[1 0]]})]
    (j/call smoke :createPointEmitter (v3) (v3))
    smoke))

(defn toxic-projectile-explode []
  (let [ps (create-particle-system {:name "toxic-ball-explode"
                                    :gpu? true
                                    :particle-texture (api.asset/get-asset :texture/smoke-sprite)
                                    :capacity 50
                                    :emitter nil
                                    :min-life-time 0
                                    :max-life-time 3
                                    :target-stop-duration 0.3
                                    :emit-rate 25
                                    :min-emit-power 0
                                    :max-emit-power 2
                                    :blend-mode api.const/particle-blend-mode-multiply-add
                                    :color1 (api.core/color-rgb 255 255 255 255)
                                    :color2 (api.core/color-rgb 0 120 0 255)
                                    :color-dead (api.core/color-rgb 0 180 0 255)
                                    :animation-sheet-enabled? true
                                    :sprite-cell-width 128
                                    :sprite-cell-height 128
                                    :sprite-cell-loop? true
                                    :sprite-random-start-cell? true
                                    :start-sprite-cell-id 0
                                    :end-sprite-cell-id 63
                                    :sprite-cell-change-speed 0.5
                                    :update-speed 0.05
                                    :size-gradients [[0 5]
                                                     [0.5 3]
                                                     [1 0]]
                                    :local? true})]
    (j/call ps :createPointEmitter (v3) (v3 0.05 1.2 0.05))
    ps))

(defn toxic-puddle []
  (let [puddle (create-particle-system {:name "toxic-puddle"
                                        :gpu? true
                                        :emitter nil
                                        :delay 300
                                        :particle-texture (api.asset/get-asset :texture/smoke-6)
                                        :capacity 50
                                        :emit-rate 25
                                        :min-life-time 1
                                        :max-life-time 6
                                        :target-stop-duration 6
                                        :color1 (api.core/color-rgb 255 255 255 255)
                                        :color2 (api.core/color-rgb 0 120 0 255)
                                        :color-dead (api.core/color-rgb 0 255 0 255)
                                        :billboard? true
                                        :billboard-mode api.const/particle-billboard-mode-stretched
                                        :limit-velocity-gradients [[0 0.5]
                                                                   [0.15 1]
                                                                   [0.25 2]
                                                                   [1 0]]
                                        :blend-mode api.const/particle-blend-mode-standard
                                        :animation-sheet-enabled? true
                                        :sprite-cell-width 102
                                        :sprite-cell-height 66
                                        :sprite-cell-loop? true
                                        :start-sprite-cell-id 0
                                        :end-sprite-cell-id 6
                                        :sprite-cell-change-speed 1
                                        :min-size 0.1
                                        :max-size 0.3
                                        :pre-warm-cycles 100
                                        :min-angular-speed 0
                                        :max-angular-speed (* Math/PI 1)
                                        :min-emit-power 1
                                        :max-emit-power 1
                                        :update-speed 0.02
                                        :size-gradients [[0 0.2]
                                                         [0.5 0.6]
                                                         [1 0]]})]
    (j/call puddle :createSphereEmitter 0.2 0.2)
    puddle))

(defn toxic-cloud-smoke []
  (let [ps (create-particle-system {:name "toxic-cloud-smoke"
                                    :gpu? true
                                    :particle-texture (api.asset/get-asset :texture/smoke-sprite)
                                    :capacity 100
                                    :emit-rate 50
                                    :emitter nil
                                    :min-life-time 5
                                    :max-life-time 10
                                    :target-stop-duration 18
                                    :min-emit-power 0
                                    :max-emit-power 1
                                    :min-scale-x 2
                                    :max-scale-x 2
                                    :min-scale-y 2
                                    :max-scale-y 2
                                    :blend-mode api.const/particle-blend-mode-multiply-add
                                    :color1 (api.core/color-rgb 0 0 0 255)
                                    :color2 (api.core/color-rgb 0 100 0 255)
                                    :color-dead (api.core/color-rgb 0 139 31 255)
                                    :animation-sheet-enabled? true
                                    :sprite-cell-width 128
                                    :sprite-cell-height 128
                                    :sprite-cell-loop? true
                                    :sprite-random-start-cell? true
                                    :start-sprite-cell-id 0
                                    :end-sprite-cell-id 63
                                    :sprite-cell-change-speed 0.5
                                    :update-speed 0.05
                                    :min-emit-box (v3 -8 -1 -8)
                                    :max-emit-box (v3 8 1 8)
                                    :size-gradients [[0 3]
                                                     [0.5 6]
                                                     [0.8 3]
                                                     [1 0]]
                                    :local? true})]
    (j/call ps :createCylinderEmitter 5 15 1 0)
    ps))

(comment
  (clear-all-ps-and-pools)
  )

(defn init-particle-pool []
  (re/register-item-creation :pool/particle-fire-projectile #(fire-projectile))
  (re/register-item-creation :pool/particle-fire-ball-explode #(fire-ball-explode))
  (re/register-item-creation :pool/particle-super-nova-shockwave #(super-nova-shockwave))
  (re/register-item-creation :pool/particle-snowflake #(snowflake))
  (re/register-item-creation :pool/particle-arrow-sparkles #(arrow-sparkles))
  (re/register-item-creation :pool/particle-ice-arrow-hit #(ice-arrow-hit))
  (re/register-item-creation :pool/particle-kill-splash-blood #(kill-splash-blood))
  (re/register-item-creation :pool/particle-kill-splash-surface #(kill-splash-surface))
  (re/register-item-creation :pool/particle-light-burst #(light-burst))
  (re/register-item-creation :pool/particle-light-strike #(light-strike))
  (re/register-item-creation :pool/particle-wind-slash #(wind-slash))
  (re/register-item-creation :pool/particle-wind-hit #(wind-hit))
  (re/register-item-creation :pool/particle-wind-tornado-cloud #(wind-tornado-clouds))
  (re/register-item-creation :pool/particle-toxic-projectile #(toxic-projectile))
  (re/register-item-creation :pool/particle-toxic-projectile-explode #(toxic-projectile-explode))
  (re/register-item-creation :pool/particle-toxic-puddle #(toxic-puddle))
  (re/register-item-creation :pool/particle-toxic-cloud-smoke #(toxic-cloud-smoke))
  (dotimes [_ 1]
    (re/push-to-pool :pool/particle-fire-projectile (fire-projectile)))
  (dotimes [_ 1]
    (re/push-to-pool :pool/particle-fire-ball-explode (fire-ball-explode)))
  (dotimes [_ 1]
    (re/push-to-pool :pool/particle-super-nova-shockwave (super-nova-shockwave)))
  (dotimes [_ 1]
    (re/push-to-pool :pool/particle-snowflake (snowflake)))
  (dotimes [_ 1]
    (re/push-to-pool :pool/particle-arrow-sparkles (arrow-sparkles)))
  (dotimes [_ 1]
    (re/push-to-pool :pool/particle-ice-arrow-hit (ice-arrow-hit)))
  (dotimes [_ 1]
    (re/push-to-pool :pool/particle-kill-splash-blood (kill-splash-blood)))
  (dotimes [_ 1]
    (re/push-to-pool :pool/particle-kill-splash-surface (kill-splash-surface)))
  (dotimes [_ 1]
    (re/push-to-pool :pool/particle-light-burst (light-burst)))
  (dotimes [_ 1]
    (re/push-to-pool :pool/particle-light-strike (light-strike)))
  (dotimes [_ 1]
    (re/push-to-pool :pool/particle-toxic-projectile (toxic-projectile)))
  (dotimes [_ 1]
    (re/push-to-pool :pool/particle-toxic-projectile-explode (toxic-projectile-explode)))
  (dotimes [_ 1]
    (re/push-to-pool :pool/particle-toxic-puddle (toxic-puddle)))
  (dotimes [_ 1]
    (re/push-to-pool :pool/particle-toxic-cloud-smoke (toxic-cloud-smoke))))

(comment
  (js/setTimeout
    #(start-ps :pool/particle-ice-arrow-hit {:emitter (re/query :player/capsule)})
    3000)

  (mapv
    (fn [v]
      (parse-double (.toFixed v 2)))
    (api.core/v3->v (api.core/get-pos (re/query :player/capsule))))

  (println (api.core/v3->v (api.core/get-pos (re/query :player/capsule))))

  (js/setTimeout
    (fn []
      (start-ps (light-strike) {:emitter (v3)}))
    2000)

  )
