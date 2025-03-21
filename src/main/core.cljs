(ns main.core
  (:require
    ["/vendor/camshake" :refer [initBabylon CamShake shakeCamera]]
    ["/vendor/havok" :as HavokPhysics]
    ["@babylonjs/core/Animations/animation" :refer [Animation]]
    ["@babylonjs/core/Audio/audioSceneComponent"]
    ["@babylonjs/core/Engines/engine" :refer [Engine]]
    ["@babylonjs/core/Loading/loadingScreen" :refer [DefaultLoadingScreen]]
    ["@babylonjs/core/Materials/Textures/mirrorTexture" :refer [MirrorTexture]]
    ["@babylonjs/core/Maths/math" :refer [Vector3]]
    ["@babylonjs/core/Maths/math.plane" :refer [Plane]]
    ["@babylonjs/core/Maths/math.vector" :refer [Quaternion Matrix]]
    ["@babylonjs/core/Meshes/Compression/dracoCompression" :refer [DracoCompression]]
    ["@babylonjs/core/Meshes/transformNode" :refer [TransformNode]]
    ["@babylonjs/core/Physics/physicsEngineComponent"]
    ["@babylonjs/core/Physics/v2/IPhysicsEnginePlugin" :refer [PhysicsMotionType]]
    ["@babylonjs/core/Physics/v2/Plugins/havokPlugin" :refer [HavokPlugin]]
    ["@babylonjs/core/Physics/v2/physicsBody" :refer [PhysicsBody]]
    ["@babylonjs/core/Physics/v2/physicsShape" :refer [PhysicsShapeMesh PhysicsShapeConvexHull]]
    ["@babylonjs/core/PostProcesses/RenderPipeline/Pipelines/defaultRenderingPipeline" :refer [DefaultRenderingPipeline]]
    ["@sentry/browser" :as Sentry]
    [applied-science.js-interop :as j]
    [cljs.core.async :as a :refer [go <!]]
    [cljs.core.async.interop :refer-macros [<p!]]
    [clojure.set :as set]
    [goog.functions :as functions]
    [main.ads :as ads]
    [main.api.animation :as api.anim]
    [main.api.asset :as api.asset]
    [main.api.camera :as api.camera]
    [main.api.constant :as api.const]
    [main.api.core :as api.core :refer [db v3]]
    [main.api.gui :as api.gui]
    [main.api.light :as api.light]
    [main.api.material :as api.material]
    [main.api.mesh :as api.mesh]
    [main.api.particle :as api.particle]
    [main.api.physics :as api.physics]
    [main.api.sound :as api.sound]
    [main.api.text]
    [main.api.texture]
    [main.api.tween :as tween]
    [main.config :as config]
    [main.crisp :as crisp]
    [main.rule-engine :as rule-engine]
    [main.scene.mobile :as mobile]
    [main.scene.network :as network]
    [main.scene.player.map :as player.map]
    [main.scene.player.model]
    [main.scene.player.rule :as player.rule]
    [main.scene.player.shop]
    [main.scene.settings :as settings]
    [main.ui.view.view :as ui]
    [main.utils :as utils])
  (:require-macros
    [main.macros :as m]
    [shadow.resource :as rc]))

(defn- enable-physic-engine [hk scene]
  (let [hk (HavokPlugin. true hk)
        gravity (v3 0 -9.8 0)]
    (j/call scene :enablePhysics gravity hk)
    (j/call-in hk [:onTriggerCollisionObservable :add]
               (fn [e]
                 (when-let [on-trigger (m/get e :collider :on-trigger)]
                   (on-trigger (m/get e :type)
                               (m/get e :collidedAgainst :transformNode :name)
                               (m/get e :collidedAgainst)))
                 (when-let [on-trigger (m/get e :collidedAgainst :on-trigger)]
                   (on-trigger (m/get e :type)
                               (m/get e :collider :transformNode :name)
                               (m/get e :collider)))))
    hk))

(defn- update-draco-url []
  (j/assoc-in! DracoCompression [:Configuration :decoder]
               #js {:wasmUrl "js/draco/draco_wasm_wrapper_gltf.js"
                    :wasmBinaryUrl "js/draco/draco_decoder_gltf.wasm"
                    :wasmBinaryFile "js/draco/draco_decoder_gltf.js"}))

(defn create-crosshair-&-debug-button []
  (let [advanced-texture (api.gui/advanced-dynamic-texture)
        crosshair (api.gui/image "crosshair" "img/texture/crosshair2.png")
        offscreen-damage (api.gui/image "crosshair" "img/texture/arrow.png")
        debug (api.gui/button "but" "Debug")
        scale 0.9
        offscreen-damage-scale 1]
    (j/assoc! crosshair
              :autoScale true
              :scaleX scale
              :scaleY scale
              :isVisible false)
    (j/assoc! offscreen-damage
              :autoScale true
              :scaleX offscreen-damage-scale
              :scaleY offscreen-damage-scale
              :isVisible false)
    (j/assoc! debug
              :width 0.1
              :height "80px"
              :color "white"
              :zIndex 1
              :background "green"
              :paddingTop "20px"
              :left "-550px"
              :verticalAlignment api.const/gui-vertical-align-top
              :horizontalAlignment api.const/gui-horizontal-align-right)
    (j/call-in debug [:onPointerClickObservable :add]
               (fn []
                 (if (j/call-in db [:scene :debugLayer :isVisible])
                   (j/call-in db [:scene :debugLayer :hide])
                   (api.core/show-debug))))
    (api.gui/add-control advanced-texture crosshair)
    (api.gui/add-control advanced-texture offscreen-damage)
    (when false #_(or config/dev?
                      (= "?dev" (j/get-in js/window [:location :search])))
          (api.gui/add-control advanced-texture debug))
    (rule-engine/insert :image/crosshair crosshair)
    (rule-engine/insert :image/offscreen-damage offscreen-damage)))

(comment
  (let [camera (rule-engine/query :camera)]
    (js/console.log (j/get camera :position)))
  (rule-engine/remove-rules :boss/attack-range :boss/attack-swing :boss/attack-throw :boss/move)
  (js/setTimeout
    #(let [boss (rule-engine/query :boss/capsule)]
       (println "apply-impulse")
       (api.physics/apply-impulse boss (api.core/v3 1000 0 0) (v3))) 2000))

(defn register-rules [hkp arc-camera am]
  (j/call-in
    (api.core/get-scene)
    [:onKeyboardObservable :add]
    (fn [info]
      (when-not (j/get-in info [:event :repeat])
        (let [key (j/get-in info [:event :code])]
          (rule-engine/fire-rules
            {:keys-pressed (if (= (j/get info :type) api.const/keyboard-type-key-down)
                             (conj (rule-engine/query :keys-pressed) key)
                             (disj (rule-engine/query :keys-pressed) key))})))))
  (j/call-in (api.core/get-scene) [:onPointerObservable :add]
             (fn [info]
               (when (and (= (j/get info :type) api.const/pointer-type-down)
                          (not (rule-engine/query :pointer-locked?))
                          (not (rule-engine/query :game/shop?)))
                 (let [canvas (api.core/get-canvas)
                       request-pointer-lock (or (j/get canvas :requestPointerLock)
                                                (j/get canvas :msRequestPointerLock)
                                                (j/get canvas :mozRequestPointerLock)
                                                (j/get canvas :webkitRequestPointerLock))]
                   (when (and (= (j/get-in info [:event :button]) 0)
                              request-pointer-lock
                              (not (rule-engine/query :game/mobile?)))
                     (j/call canvas :requestPointerLock))))

               (when (and (= (j/get info :type) api.const/pointer-type-down)
                          (= (j/get-in info [:event :button]) 0)
                          (rule-engine/query :pointer-locked?))
                 (rule-engine/fire-rules {:mouse/left-click? true}))

               (when (and (= (j/get info :type) api.const/pointer-type-up)
                          (= (j/get-in info [:event :button]) 0)
                          (rule-engine/query :pointer-locked?))
                 (rule-engine/fire-rules {:mouse/left-click? false}))

               (when (and (= (j/get info :type) api.const/pointer-type-down)
                          (= (j/get-in info [:event :button]) 2)
                          (rule-engine/query :pointer-locked?))
                 (rule-engine/fire-rules {:mouse/right-click? true}))

               (when (and (= (j/get info :type) api.const/pointer-type-up)
                          (= (j/get-in info [:event :button]) 2)
                          (rule-engine/query :pointer-locked?))
                 (rule-engine/fire-rules {:mouse/right-click? false}))))
  (let [tn (api.core/transform-node {:name "speedline-emitter"})
        _ (j/assoc! tn
                    :position (v3 0 0 5)
                    :rotation (v3 (/ js/Math.PI 2) 0 0)
                    :parent arc-camera)]
    (rule-engine/insert {:keys-pressed #{}
                         :assets-manager am
                         :particle/speed-line-emitter tn
                         :player/last-jump-time 0
                         :player/died? false
                         :player/spell? false
                         :player/spell-rock? false
                         :player/spell-rock-wall? false
                         :player/spell-toxic? false
                         :player/spell-toxic-cloud? false
                         :player/spell-ice-tornado? false
                         :player/spell-ice-arrow? false
                         :player/spell-super-nova? false
                         :player/spell-light-strike? false
                         :player/spell-light-staff? false
                         :player/levitate? false
                         :player/roll? false
                         :player/forward? false
                         :player/backward? false
                         :player/jump-up? false
                         :player/left? false
                         :player/right? false
                         :player/unlocked-light-element? true
                         :player/unlocked-wind-element? true
                         :player/unlocked-toxic-element? true
                         :player/unlocked-earth-element? true
                         :camera arc-camera
                         :engine (api.core/get-engine)
                         :scene (api.core/get-scene)
                         :camera-shake (CamShake.)
                         :physics/havok-plugin hkp
                         :network/connected? false}))
  (api.core/register-on-before-render
    (fn []
      (rule-engine/fire-rules {:dt (api.core/get-delta-time)
                               :current-time (js/Date.now)})
      (tween/update)))
  (api.core/register-on-after-render
    (let [prev-pressed-keys (volatile! #{})
          prev-mouse-left-click? (volatile! false)
          prev-mouse-right-click? (volatile! false)]
      (fn []
        (let [keys-pressed (rule-engine/query :keys-pressed)
              mouse-left-click? (rule-engine/query :mouse/left-click?)
              mouse-right-click? (rule-engine/query :mouse/right-click?)]
          (rule-engine/fire-rules
            {:keys-was-pressed (set/intersection @prev-pressed-keys keys-pressed)
             :mouse/left-was-clicked? (and @prev-mouse-left-click? (not mouse-left-click?))
             :mouse/right-was-clicked? (and @prev-mouse-right-click? (not mouse-right-click?))})
          (vreset! prev-pressed-keys keys-pressed)
          (vreset! prev-mouse-left-click? mouse-left-click?)
          (vreset! prev-mouse-right-click? mouse-right-click?))))))

(defn- on-before-anim* [anim-groups anim-map-type]
  (doseq [ag (api.anim/get-playing-anim-groups anim-groups)
          :let [ag-name (m/get ag :name)
                anim-map (rule-engine/get-anim-map anim-map-type)
                events (get-in anim-map [ag-name :events])
                current-frame (m/get ag :animatables 0 :masterFrame)]
          :when events]
    (when-not (j/get ag :on-loop-fn?)
      (j/assoc! ag :on-loop-fn? true
                :events-to-run-fns (atom #{}))
      (j/call-in ag [:onAnimationGroupLoopObservable :add] (fn []
                                                             (doseq [f @(j/get ag :events-to-run-fns)]
                                                               (j/assoc! f :run? false))
                                                             (reset! (j/get ag :events-to-run-fns) #{})))
      (j/call-in ag [:onAnimationGroupPlayObservable :add] (fn []
                                                             (doseq [f @(j/get ag :events-to-run-fns)]
                                                               (j/assoc! f :run? false))
                                                             (reset! (j/get ag :events-to-run-fns) #{}))))
    (let [events-to-run (filter (fn [[frame f]]
                                  (and (<= frame current-frame)
                                       (not (j/get f :run?)))) events)]
      (swap! (j/get ag :events-to-run-fns) set/union (set (map second events-to-run)))
      (doseq [[_ f] events-to-run]
        (j/assoc! f :run? true)
        (f)))))

(defn on-before-anim []
  (let [player-anim-groups (rule-engine/query :player/anim-groups)]
    (on-before-anim* player-anim-groups :player)))

(defn enable-default-rendering-pipeline [scene camera]
  (api.core/register-shader "shockwave" (rc/inline "shader/shockwave.glsl"))
  (rule-engine/insert
    {:settings/dp (DefaultRenderingPipeline. "default" false scene #js[camera])
     :texture/noise (api.core/noise-procedural-tex)
     :post-process/shockwave (api.core/post-proc {:name "shockwave"
                                                  :fragment "shockwave"
                                                  :uniforms ["time" "intensity"]
                                                  :samplers ["noiseTexture"]
                                                  :camera camera})}))

(defn load-sound-effects []
  (m/assoc! Engine :audioEngine.useCustomUnlockedButton true)
  (api.sound/init-sounds))

(defn- load-materials []
  (api.material/init-materials))

(defn- create-shop-ground []
  (when-let [shop-model (rule-engine/query :game/shop-model)]
    (let [ground-material (api.material/background-mat {:name "shop_ground_mat"
                                                        :has-alpha? true
                                                        :opacity-fresnel? false
                                                        :diffuse-texture (api.asset/get-asset :texture/shop-bg)})
          ground (api.mesh/create-ground {:name "shop_ground"
                                          :width 6
                                          :height 6
                                          :material ground-material})
          mirror (MirrorTexture. "mirror" 512 (api.core/get-scene))]
      (j/assoc! mirror :mirrorPlane (Plane. 0 -1 0 0))
      (j/call-in mirror [:renderList :push] shop-model)
      (j/assoc! ground-material
                :reflectionTexture mirror
                :reflectionFresnel true
                :reflectionStandardFresnelWeight 0.8)
      (api.core/set-enabled ground false)
      (rule-engine/insert :game/shop-ground ground)
      ground)))

(defn get-matrix-trans []
  (j/call Matrix :Translation -0.1 -0.3 0))

(defn update-cam [camera mat]
  (-> (j/call camera :getProjectionMatrix)
      (j/call :multiplyToRef mat (j/call camera :getProjectionMatrix))))

(defn optimize-scene [scene]
  (j/assoc! scene
            :autoClear false
            :autoClearDepthAndStencil false
            :skipFrustumClipping true
            :skipPointerMovePicking true
            :pointerMovePredicate js/undefined
            :pointerDownPredicate js/undefined
            :pointerUpPredicate js/undefined
            :constantlyUpdateMeshUnderPointer false))

(defn- visibility-props []
  (cond
    (some? js/document.hidden) {:hidden "hidden"
                                :visibility-change "visibilitychange"}
    (some? js/document.msHidden) {:hidden "msHidden"
                                  :visibility-change "msvisibilitychange"}
    (some? js/document.webkitHidden) {:hidden "webkitHidden"
                                      :visibility-change "webkitvisibilitychange"}
    :else (js/console.warn "visibility prop not found in visibility-props fn")))

(defn- register-focus-unfocus []
  (let [{:keys [hidden visibility-change]} (visibility-props)]
    (when hidden
      (js/document.addEventListener
        visibility-change
        (fn []
          (rule-engine/fire-rules :tab/focus? (not (boolean (j/get js/document hidden)))))))
    (js/window.addEventListener "focus" (fn []
                                          (rule-engine/fire-rules :window/focus? true)))
    (js/window.addEventListener "blur" (fn []
                                         (rule-engine/fire-rules :window/focus? false)))
    (rule-engine/insert {:tab/focus? true
                         :window/focus? true})))

(defn- init-pools []
  (api.particle/init-particle-pool)
  (player.rule/init-fire-projectile-balls)
  (player.rule/init-rock-walls)
  (player.rule/init-create-rock-projectiles)
  (player.rule/init-toxic-projectile-balls)
  (player.rule/init-super-nova-balls)
  (player.rule/init-toxic-cloud-balls)
  (player.rule/init-ice-tornado)
  (player.rule/init-create-wind-tornados)
  (player.rule/init-kill-splash-balls)
  (player.rule/init-arrow-trail-balls)
  (player.rule/init-spell-wind-emitter)
  (player.rule/init-hero-materials))

(defn- disable-space-jump-arrow-up-down []
  (when (not config/dev?)
    (let [keys #{"ArrowDown" "ArrowUp"}]
      (utils/register-event-listener js/window "keydown"
                                     (fn [e]
                                       (when (and (= " " (j/get e :key))
                                                  (not (crisp/opened?))
                                                  (not (rule-engine/query :player/chat-focus?)))
                                         (j/call e :preventDefault))
                                       (when (keys (j/get e :key))
                                         (j/call e :preventDefault))))
      (j/call js/window :addEventListener "wheel"
              (fn [e]
                (when-not (or (rule-engine/query :game/leaderboard?)
                              (rule-engine/query :game/shop?)
                              (rule-engine/query :game/booster-panel?))
                  (j/call e :preventDefault)))
              #js {:passive false}))))

(defn- register-loading-ui-fns [am]
  (j/call-in am
             [:onProgressObservable :add]
             (fn [event]
               (let [reaming (j/get event :remainingCount)
                     total (j/get event :totalCount)]
                 (rule-engine/insert-with-ui {:scene/loading-progress (* 100 (/ (- total reaming) total))}))))
  (j/assoc-in! DefaultLoadingScreen
               [:prototype :displayLoadingUI]
               (fn []
                 (rule-engine/insert-with-ui {:scene/show-loading-progress? true})))
  (j/assoc-in! DefaultLoadingScreen
               [:prototype :hideLoadingUI]
               (fn []
                 (rule-engine/insert-with-ui {:scene/show-loading-progress? false}))))

(defn- unregister-loading-ui-fns []
  (j/assoc-in! DefaultLoadingScreen [:prototype :displayLoadingUI] (fn []))
  (j/assoc-in! DefaultLoadingScreen [:prototype :hideLoadingUI] (fn [])))

(defn- detect-ads []
  (rule-engine/insert :ad/adblocker? (boolean (j/get js/window :adblock))))

(defn handle-orientation-change [e]
  (if (j/get e :matches)
    (rule-engine/fire-rules :screen/orientation :landscape)
    (rule-engine/fire-rules :screen/orientation :portrait)))

(defn start-scene []
  (a/go
    (let [canvas (js/document.getElementById "renderCanvas")
          _ (update-draco-url)
          engine (api.core/create-engine canvas)
          scene (api.core/create-scene engine)
          _ (j/assoc! scene
                      :clearColor (api.core/color-rgb 105 105 141 255)
                      ;; :clearColor (api.core/color-rgb 125 120 120 255)
                      :fogMode 1
                      :fogDensity 0
                      :shadowsEnabled false
                      :probesEnabled false
                      :lensFlaresEnabled false)
          _ (optimize-scene scene)
          hk (<p! (HavokPhysics))
          hkp (enable-physic-engine hk scene)
          mobile? (mobile/mobile?)
          _ (rule-engine/create-session)
          server-ping-ch (a/promise-chan)
          _ (network/ping-servers)
          _ (js/setTimeout
              (fn []
                (network/select-server)
                (a/put! server-ping-ch true))
              3200)
          _ (ui/init mobile?)
          _ (settings/init-settings mobile?)
          _ (rule-engine/insert {:game/loading? true
                                 :game/mobile? mobile?})
          _ (when true
              (rule-engine/insert :pointer-locked? true))
          am (api.asset/assets-manager)
          _ (register-loading-ui-fns am)
          _ (api.asset/create-preload-asset-tasks)
          _ (<! (api.asset/load-async))
          _ (unregister-loading-ui-fns)
          ;; _ (mobile/create-controller)
          arc-camera (api.camera/create-arc-camera {:name "arc-camera"
                                                    :radius api.camera/default-radius
                                                    :lower-beta-limit api.camera/beta-lower-limit
                                                    :upper-beta-limit api.camera/beta-upper-limit
                                                    :lower-radius-limit 0
                                                    :upper-radius-limit 7
                                                    :position (v3 0 2 -10)
                                                    :canvas canvas})
          _ (j/call-in arc-camera [:inputs :remove] (m/get arc-camera :inputs :attached :mousewheel))
          _ (j/assoc! arc-camera :fov api.camera/fov)
          _ (api.camera/reset-camera-sensibility arc-camera)]
      (api.camera/export-fns)
      (api.light/hemispheric-light {:name "hemispheric-light"})
      (rule-engine/insert :game/skybox (api.mesh/create-sky-box))
      (disable-space-jump-arrow-up-down)
      (utils/register-event-listener js/window "resize" (functions/debounce api.core/resize 150))
      (utils/register-event-listener js/window "click"
                                     (fn []
                                       (when-not (m/get Engine :audioEngine :unlocked)
                                         (-> (j/get Engine :audioEngine)
                                             (j/call :unlock)))))
      (utils/register-event-listener js/document "pointerlockchange"
                                     (fn []
                                       (rule-engine/fire-rules
                                         {:pointer-locked? (boolean (j/get js/document :pointerLockElement))})))
      (utils/register-event-listener js/document "pointerlockerror"
                                     (fn []
                                       (rule-engine/fire-rules
                                         {:pointer-locked? false})))
      (when mobile?
        (utils/register-event-listener js/document "contextmenu"
                                       (fn [e]
                                         (.preventDefault e)))
        (utils/register-event-listener js/document "selectstart"
                                       (fn [e]
                                         (.preventDefault e)))
        (utils/register-event-listener js/document "dragstart"
                                       (fn [e]
                                         (.preventDefault e)))
        (j/call js/window :addEventListener "touchstart"
                (fn [e]
                  (when (rule-engine/query :player/game-started?)
                    (j/call e :preventDefault)))
                #js {:passive false})

        (let [landscape-query (j/call js/window :matchMedia "(orientation: landscape)")]
          (handle-orientation-change landscape-query)
          (j/call landscape-query :addEventListener "change" handle-orientation-change)))
      (j/assoc! scene :onPointerMove (fn [e]
                                       (j/assoc! arc-camera
                                                 :lastMouseMoveTime (js/Date.now)
                                                 :movementY (Math/abs (j/get e :movementY)))))
      (j/call engine :runRenderLoop #(j/call scene :render))
      (j/call scene :executeWhenReady (fn []
                                        (a/go
                                          (<! (ads/init))
                                          (detect-ads)
                                          (ads/game-loading-start)
                                          (load-materials)
                                          (api.asset/create-leftover-asset-tasks)
                                          (<! (api.asset/load-async))
                                          (println "Loaded all assets")
                                          (initBabylon Vector3 Quaternion TransformNode Matrix Animation)
                                          (register-rules hkp arc-camera am)
                                          (player.map/load-map :map/arena)
                                          (println "Init map loaded")
                                          (create-shop-ground)
                                          (println "Getting CG user token")
                                          (<! (ads/get-user-token))
                                          (println "CG user token passed")
                                          (ads/set-username
                                            (fn [username]
                                              (println "Username: " username)
                                              (rule-engine/insert :player/cg-username username)))
                                          (ads/add-auth-listener)
                                          (println "Added CG auth listener")
                                          (create-crosshair-&-debug-button)
                                          (load-sound-effects)
                                          (api.core/register-on-before-anim
                                            (fn []
                                              (on-before-anim)))
                                          (enable-default-rendering-pipeline scene arc-camera)
                                          (println "Will connect to network")
                                          (<! server-ping-ch)
                                          (network/connect)
                                          (register-focus-unfocus)
                                          (init-pools)
                                          (settings/init-settings mobile?)
                                          (api.sound/play-audio-element "background-music")
                                          (rule-engine/insert :game/loading? false)
                                          (println "All complete!")))))))

(defn ^:dev/before-load before-load []
  (js/console.clear)
  (js/console.log "before-load"))

(defn ^:dev/after-load after-load []
  (js/console.log "after-load"))

(defn- init-sentry []
  (when-not config/dev?
    (println "Init Sentry")
    (.init Sentry #js{:dsn "dsn-uri"
                      :release (m/get-env "GITHUB_SHA")
                      ;; :environment (if (not dev?) "production" "dev")
                      :integrations #js [(.browserTracingIntegration Sentry)]
                      :tracesSampleRate 1.0
                      :replaysSessionSampleRate 0.1
                      :replaysOnErrorSampleRate 1.0})))

(defn init []
  (js/console.log "init")
  #_(init-sentry)
  (start-scene))

(comment
  (j/call (api.core/get-scene) :freezeActiveMeshes)
  (j/assoc! (api.core/get-scene)
            :animationsEnabled true
            :lightsEnabled false
            :postProcessesEnabled true
            :shadowsEnabled false
            :particlesEnabled true
            :fogEnabled false
            :lensFlaresEnabled false
            :probesEnabled false
            :renderTargetsEnabled true
            )
  (j/call (api.core/get-engine) :getHardwareScalingLevel)
  (j/call (api.core/get-engine) :setHardwareScalingLevel 3)

  (let [tn (api.core/transform-node {:name "cam-wrapper"})
        camera (rule-engine/query :camera)]
    (j/assoc! camera :parent tn)
    (j/assoc! tn :rotation (api.core/clone (j/get camera :rotation)))
    (js/setTimeout
      #(shakeCamera
         camera
         (api.core/get-scene)
         3
         1)
      2000))

  (let [arc-camera (rule-engine/query :camera)
        _ (j/assoc! tn :parent arc-camera)
        pp (api.core/get-particle-by-id "particles")]
    (j/assoc! pp :emitter tn)
    )
  )
