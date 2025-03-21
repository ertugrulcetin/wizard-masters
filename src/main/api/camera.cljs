(ns main.api.camera
  (:require
    ["@babylonjs/core/Animations/animation" :refer [Animation setKeys]]
    ["@babylonjs/core/Cameras/Inputs/arcRotateCameraPointersInput" :refer [ArcRotateCameraPointersInput]]
    ["@babylonjs/core/Cameras/arcRotateCamera" :refer [ArcRotateCamera]]
    ["@babylonjs/core/Cameras/camera" :refer [getDirection]]
    ["@babylonjs/core/Cameras/freeCamera" :refer [FreeCamera]]
    ["@babylonjs/core/Maths/math.vector" :refer [Quaternion toEulerAngles RotationAxis Forward]]
    [applied-science.js-interop :as j]
    [main.api.animation :as api.anim]
    [main.api.constant :as api.const]
    [main.api.core :as api.core :refer [db v3]]
    [main.api.tween :as api.tween]
    [main.rule-engine :as re]
    [main.scene.settings :as settings])
  (:require-macros
    [main.macros :as m]))

(def default-radius 2)
(def fov 1.5)
(def zoom-fov 1.1)

(def beta-lower-limit 0.2)
(def beta-upper-limit (- js/Math.PI 0.5))
(def init-beta-alpha 1.4)

(defn active-camera []
  (j/get-in db [:scene :activeCamera]))

(defn update-active-camera []
  (j/assoc-in! db [:nodes :camera :obj] (active-camera)))

(defn attach-control [camera]
  (j/call camera :attachControl (api.core/get-canvas) true))

(defn detach-control [camera]
  (j/call camera :detachControl))

(defn set-pos [pos]
  (j/call-in (active-camera) [:position :copyFrom] pos))

(defn create-free-camera [name & {:keys [position speed min-z]
                                  :or {position (v3 0 2 -10)
                                       speed 0.1
                                       min-z 0.1}
                                  :as opts}]
  (let [camera (FreeCamera. name position)
        init-rotation (api.core/clone (j/get camera :rotation))
        init-position (api.core/clone (j/get camera :position))]
    (j/call-in camera [:keysUpward :push] 69)
    (j/call-in camera [:keysDownward :push] 81)
    (j/call-in camera [:keysUp :push] 87)
    (j/call-in camera [:keysDown :push] 83)
    (j/call-in camera [:keysLeft :push] 65)
    (j/call-in camera [:keysRight :push] 68)
    (j/assoc! camera
              :speed speed
              :type :free
              :minZ min-z
              :init-rotation init-rotation
              :init-position init-position)
    camera))

(defn export-fns []
  [toEulerAngles
   getDirection
   RotationAxis
   Forward])

(defn disable-touch []
  (j/call-in (active-camera) [:inputs :removeByType] "ArcRotateCameraPointersInput"))

(defn enable-touch []
  (j/call-in (active-camera) [:inputs :add] (ArcRotateCameraPointersInput.)))

(defn create-arc-camera [{:keys [name
                                 canvas
                                 target
                                 position
                                 alpha
                                 beta
                                 lower-beta-limit
                                 upper-beta-limit
                                 radius
                                 target-screen-offset
                                 use-bouncing-behavior?
                                 use-framing-behavior?
                                 check-collisions?
                                 apply-gravity?
                                 collision-radius
                                 lower-radius-limit
                                 upper-radius-limit
                                 angular-sensibility-x
                                 angular-sensibility-y
                                 wheel-precision
                                 inertia
                                 speed
                                 min-z]
                          :or {min-z 0.1
                               inertia 0
                               alpha (/ Math/PI 2)
                               beta (/ Math/PI 2)
                               lower-radius-limit 0.1
                               wheel-precision 10
                               speed 0.1
                               target (v3)}}]
  (let [camera (ArcRotateCamera. name alpha beta radius target)]
    (when canvas (j/call camera :attachControl canvas true))
    (j/assoc! camera
              :keysUp #js[]
              :keysDown #js[]
              :keysLeft #js[]
              :keysRight #js[])
    (m/cond-doto camera
      camera (j/call :setPosition position)
      target-screen-offset (j/assoc! :targetScreenOffset target-screen-offset)
      (some? use-bouncing-behavior?) (j/assoc! :useBouncingBehavior use-bouncing-behavior?)
      (some? use-framing-behavior?) (j/assoc! :useFramingBehavior use-framing-behavior?)
      (some? apply-gravity?) (j/assoc! :applyGravity apply-gravity?)
      (some? check-collisions?) (j/assoc! :checkCollisions check-collisions?)
      radius (j/assoc! :radius lower-beta-limit)
      lower-beta-limit (j/assoc! :lowerBetaLimit lower-beta-limit)
      upper-beta-limit (j/assoc! :upperBetaLimit upper-beta-limit)
      collision-radius (j/assoc! :collisionRadius collision-radius)
      lower-radius-limit (j/assoc! :lowerRadiusLimit lower-radius-limit)
      upper-radius-limit (j/assoc! :upperRadiusLimit upper-radius-limit)
      inertia (j/assoc! :inertia inertia)
      min-z (j/assoc! :minZ min-z)
      speed (j/assoc! :speed speed)
      wheel-precision (j/assoc! :wheelPrecision wheel-precision)
      angular-sensibility-x (j/assoc! :angularSensibilityX angular-sensibility-x)
      angular-sensibility-y (j/assoc! :angularSensibilityY angular-sensibility-y)
      true (j/assoc! :type :arc))))

(defn shake-vertical [duration intensity]
  (when-not (re/query  :camera/vertical-shaking?)
    (re/insert  :camera/vertical-shaking? true)
    (let [camera (active-camera)
          scene (api.core/get-scene)
          on-end (fn []
                   (re/insert  :camera/vertical-shaking? false))
          start-time (j/call js/performance :now)
          duration-in-millis (* 1000 duration)
          original-beta (j/get camera :beta)
          shake (fn shake-f []
                  (let [current-time (j/call js/performance :now)
                        elapsed (- current-time start-time)
                        _ (when (> (- (js/Date.now) (j/get camera :lastMouseMoveTime)) 25)
                            (j/assoc! camera :movementY 0))
                        delta-y (j/get camera :movementY)
                        blocked? (> delta-y 1)]
                    (if (or (>= elapsed duration-in-millis) blocked?)
                      (do
                        (j/call-in scene [:onBeforeRenderObservable :removeCallback] shake-f)
                        (on-end))
                      (let [time-in-secs (/ elapsed 1000)
                            damping-factor (js/Math.exp (* time-in-secs -10))
                            beta-offset (* (js/Math.sin (* time-in-secs 2 js/Math.PI)) intensity damping-factor -1)]
                        (j/assoc! camera :beta (+ original-beta beta-offset))))))]
      (j/call-in scene [:onBeforeRenderObservable :add] shake))))

(defn shake-camera [& {:keys [speed
                              amplitude
                              frequency
                              influence
                              duration
                              rotation
                              rotation-frequency
                              frequency-offset
                              rotation-offset]
                       :or {speed 2
                            frequency-offset (Math/random)
                            rotation-offset (Math/random)
                            amplitude (v3 1)
                            frequency (v3 3 9 0)
                            rotation (v3 0.01 0 0)
                            rotation-frequency (v3 0 1 0)
                            influence 2
                            duration 0.75}}]
  (let [{:keys [camera camera-shake]} (re/query-all)]
    (j/assoc! camera-shake
              :influence influence
              :speed speed
              :amplitude amplitude
              :frequency frequency
              :rotation rotation
              :rotation-frequency rotation-frequency
              :frequency-offset frequency-offset
              :rotation-offset rotation-offset)
    (api.anim/run-dur-fn {:duration duration
                          :f #(j/call camera-shake :shake camera)
                          :on-end (fn []
                                    (when-let [p (j/get camera :parent)]
                                      (api.tween/tween {:target [p :position]
                                                        :duration 100
                                                        :from (j/get p :position)
                                                        :to (v3)})
                                      (api.tween/tween {:target [p :rotation]
                                                        :duration 100
                                                        :from (j/get p :rotation)
                                                        :to (v3)})
                                      #_(j/assoc! p
                                                  :position (v3)
                                                  :rotation (v3))))})))

(defn forward-dir [factor]
  (let [camera (active-camera)
        forward-dir (j/call camera :getDirection api.const/v3-forward)]
    (-> camera
        (j/get :position)
        (j/call :add (j/call forward-dir :scale factor)))))

(defn update-camera-sensibility [camera value]
  (let [value (* (- 1 value) 1000)]
    (j/assoc! camera
              :angularSensibilityX value
              :angularSensibilityY value)))

(defn reset-camera-sensibility [camera]
  (update-camera-sensibility camera (settings/get-setting :mouse-sensitivity)))

(defn smooth-zoom-sensibility [camera]
  (let [value (settings/get-setting :mouse-zoom-sensitivity)
        value (* (- 1 value) 1000)]
    (j/assoc! camera
              :angularSensibilityX value
              :angularSensibilityY value)))

(defn get-direction-scaled [camera scale]
  (-> camera
      (j/call :getForwardRay)
      (j/get :direction)
      (j/call :scale scale)))

(defn get-char-forward-dir [{:keys [camera forward-temp result-temp]}]
  (let [forward-dir (j/call camera :getDirection api.const/v3-forward)
        forward-dir (api.core/set-v3 forward-temp (j/get forward-dir :x) 0 (j/get forward-dir :z))
        x (j/get forward-dir :x)
        z (j/get forward-dir :z)
        result (api.core/normalize (api.core/set-v3 result-temp x 0 z))
        yaw (js/Math.atan2 (j/get result :x) (j/get result :z))
        offset (* 2 js/Math.PI)]
    [yaw offset]))

(comment
  (shake-camera)
  (js/setInterval (fn []
                    (let [{:keys [camera camera-shake]} (re/query-all)]
                      (j/call camera-shake :shake camera))) 10)
  ;canvas.toDataURL('image/webp', 0.5);
  (j/call-in api.core/db [:canvas :toDataURL] "image/webp" 0.2)
  (create-screenshot (fn [s] (println "S: " s)))
  (j/get (active-camera) :fov)

  (js/setTimeout #(j/assoc! (active-camera) :fov 0.8) 2000)
  (js/setTimeout #(j/assoc! (active-camera) :fov 1) 2000)

  (calculate-new-fov 0.8 500 773)
  (calculate-new-fov 0.8730305915925877 576 522)
  ()
  )
