(ns main.scene.player.model
  (:require
    ["lil-gui" :refer [GUI]]
    [applied-science.js-interop :as j]
    [clojure.string :as str]
    [main.ads :as ads]
    [main.api.animation :as api.anim]
    [main.api.asset :as api.asset]
    [main.api.constant :as api.const]
    [main.api.core :as api.core :refer [db v3]]
    [main.api.material :as api.material]
    [main.api.mesh :as api.mesh]
    [main.api.mesh :refer [reg-mesh]]
    [main.api.particle :as api.particle]
    [main.api.physics :as api.physics]
    [main.api.sound :as api.sound]
    [main.config :as config]
    [main.rule-engine :as re]
    [main.scene.player.map :as player.map]
    [main.utils :as utils])
  (:require-macros
    [main.macros :as m]))

(comment
  (let [body (j/get (re/query :player/body) :body)
        gravity-factor 3
        linear-damping 0.5
        angular-damping 0]
    (j/call body :setGravityFactor gravity-factor)
    (j/call body :setLinearDamping linear-damping)
    (j/call body :setAngularDamping angular-damping)
    )
  )

(def gravity-factor 2)

#_(reg-mesh
  :mesh/bow
  {:url "bow.glb"
   ;; :preload? true
   :on-success (fn [mesh]
                 (api.core/set-enabled mesh false)
                 (doseq [m (api.core/get-child-meshes mesh)]
                   (api.core/dispose (j/get m :material))
                   (j/assoc! m
                             :name "Bow"
                             :isVisible false
                             :rotationQuaternion nil
                             :isPickable false
                             :material (api.asset/get-asset :material/kingdom)
                             :parent nil))
                 (api.core/dispose mesh))})

(reg-mesh
  :mesh/wind-tornado
  {:url "tornado.glb"
   ;; :preload? true
   :on-success (fn [mesh]
                 (doseq [m (api.core/get-child-meshes mesh)]
                   (api.core/dispose (j/get m :material))
                   (j/assoc! m
                             :name "Tornado"
                             :rotationQuaternion nil
                             :isPickable false
                             :material (api.asset/get-asset :material/tornado)
                             :parent nil)
                   (api.core/set-enabled m false))
                 (api.core/dispose mesh))})

(defn- create-trigger-box [tn id mesh pos]
  (let [trigger-box-main (or (api.core/get-mesh-by-name "collectable_trigger_main")
                             (api.mesh/box {:name "collectable_trigger_main"
                                            :size 0.5
                                            :visibility 0.001}))
        trigger-box (j/call trigger-box-main :createInstance (str "collectable_trigger_" id))
        _ (j/assoc! mesh
                    :parent trigger-box
                    :position (v3))
        _ (j/assoc! trigger-box :parent tn)
        _ (j/assoc! trigger-box :position pos)
        p-shape (api.physics/physics-shape {:mesh trigger-box
                                            :trigger? true})
        body (api.physics/physics-body {:tn trigger-box
                                        :motion-type :PhysicsMotionType/STATIC
                                        :shape p-shape})]
    (j/assoc! trigger-box :body body :shape p-shape)
    trigger-box))

(defn- create-hp-potion [m]
  (let [hp (j/call m :createInstance "hp_potion")]
    (j/assoc! hp
              :alwaysSelectAsActiveMesh true
              :doNotSyncBoundingInfo true
              :isPickable false
              :rotation (v3 (api.core/to-rad 90) 0 0))))

(reg-mesh
  :mesh/hp-potion
  {:url "hp_potion.glb"
   ;; :preload? true
   :on-success (fn [mesh]
                 (let [m (first (api.core/get-child-meshes mesh))
                       mat (-> (api.asset/get-asset :material/kingdom)
                               api.core/clone
                               (j/assoc! :name "hp_potion_mat"
                                         :emissiveColor (api.core/color 1 0 0)))]
                   (api.core/dispose (j/get m :material))
                   (j/assoc! m
                             :name "hp_potion_main"
                             :alwaysSelectAsActiveMesh true
                             :doNotSyncBoundingInfo true
                             :rotationQuaternion nil
                             :isPickable false
                             :isVisible false
                             :material mat
                             :parent nil)
                   (re/register-item-creation :pool/collectable-hp-potion #(create-hp-potion m))
                   (api.core/dispose mesh)))})

(defn- create-mp-potion [m]
  (let [hp (j/call m :createInstance "mp_potion")]
    (j/assoc! hp
              :isPickable false
              :rotation (v3 0 0 0)
              :scaling (v3 0.04))))

#_(reg-mesh
  :mesh/mp-potion
  {:url "mp_potion.glb"
   :on-success (fn [mesh]
                 (let [m (first (api.core/get-child-meshes mesh))
                       mat (-> (api.asset/get-asset :material/kingdom)
                               api.core/clone
                               (j/assoc! :name "mp_potion_mat"
                                         :emissiveColor (api.core/color 0 0 1)))]
                   (api.core/dispose (j/get m :material))
                   (j/assoc! m
                             :name "mp_potion_main"
                             :alwaysSelectAsActiveMesh true
                             :doNotSyncBoundingInfo true
                             :rotationQuaternion nil
                             :isPickable false
                             :isVisible false
                             :material mat
                             :parent nil)
                   (re/register-item-creation :pool/collectable-mp-potion #(create-mp-potion m))
                   (api.core/dispose mesh)))})

(defn- create-speed-potion [m]
  (let [hp (j/call m :createInstance "speed_potion")]
    (j/assoc! hp
              :isPickable false
              :rotation (v3 (api.core/to-rad 90) 0 0))))

#_(reg-mesh
  :mesh/speed-potion
  {:url "speed_potion.glb"
   :on-success (fn [mesh]
                 (let [m (first (api.core/get-child-meshes mesh))
                       mat (-> (api.asset/get-asset :material/kingdom)
                               api.core/clone
                               (j/assoc! :name "speed_potion_mat"
                                         :emissiveColor (api.core/color 1 1 0)))]
                   (api.core/dispose (j/get m :material))
                   (j/assoc! m
                             :name "speed_potion_main"
                             :rotationQuaternion nil
                             :alwaysSelectAsActiveMesh true
                             :doNotSyncBoundingInfo true
                             :isPickable false
                             :isVisible false
                             :material mat
                             :parent nil)
                   (re/register-item-creation :pool/collectable-speed-potion #(create-speed-potion m))
                   (api.core/dispose mesh)))})

(defn- create-mp-air-regen-potion [m]
  (let [hp (j/call m :createInstance "mp_air_regen_potion")]
    (j/assoc! hp
              :isPickable false
              :rotation (v3 (api.core/to-rad 90) 0 0))))

#_(reg-mesh
    :mesh/mp-air-regen-potion
    {:url "air_mp_regen_potion.glb"
     :on-success (fn [mesh]
                   (let [m (first (api.core/get-child-meshes mesh))
                         mat (-> (api.asset/get-asset :material/kingdom)
                                 api.core/clone
                                 (j/assoc! :name "mp_air_regen_potion_mat"
                                           :emissiveColor (api.core/color-rgb 196 129 0)))]
                     (api.core/dispose (j/get m :material))
                     (j/assoc! m
                               :name "mp_air_regen_potion_main"
                               :rotationQuaternion nil
                               :isPickable false
                               :isVisible false
                               :material mat
                               :parent nil)
                     (re/register-item-creation :pool/collectable-mp-air-regen-potion #(create-mp-air-regen-potion m))
                     (api.core/dispose mesh)))})

(defn- create-player-physics [player-capsule mass]
  (let [body (api.physics/physics-agg {:mesh player-capsule
                                       :type :PhysicsShapeType/CAPSULE
                                       :filter-group api.const/collision-group-player
                                       :mass mass
                                       :restitution 0
                                       :friction 0
                                       :linear-damping 2.2
                                       :angular-damping 2.2
                                       :gravity-factor gravity-factor
                                       :motion-type :PhysicsMotionType/DYNAMIC
                                       :mass-props {:inertia (v3)
                                                    :mass mass}})]
    (re/insert :player/body body)))

(defn create-lil-gui []
  #_(when (or config/dev?
              (= "?dev" (j/get-in js/window [:location :search])))
      (let [{:keys [player/forward-speed
                    player/normal-speed
                    player/gravity
                    player/jump-force]} (re/query-all)
            controls #js {:forward-speed forward-speed
                          :normal-speed normal-speed
                          :gravity gravity
                          :jump-force jump-force
                          :pos-x 0
                          :pos-y 0
                          :pos-z 0}
            lil-gui (GUI.)]
        (j/call lil-gui :close)
        (-> (j/call lil-gui :add controls "forward-speed" 12 30)
            (j/call :onChange (fn [v]
                                (re/insert :player/forward-speed v))))
        (-> (j/call lil-gui :add controls "normal-speed" 8 20)
            (j/call :onChange (fn [v]
                                (re/insert :player/normal-speed v))))
        (-> (j/call lil-gui :add controls "gravity" 1 10)
            (j/call :onChange (fn [v]
                                (re/insert :player/gravity v))))
        (-> (j/call lil-gui :add controls "jump-force" 500 3500)
            (j/call :onChange (fn [v]
                                (re/insert :player/jump-force v))))
        (-> (j/call lil-gui :add controls "pos-x" 0)
            (j/call :onChange (fn [x]
                                (let [capsule (re/query :player/capsule)
                                      {:keys [y z]} (j/lookup (api.core/get-pos capsule))]
                                  (api.physics/set-pos capsule [x y z])))))
        (-> (j/call lil-gui :add controls "pos-y" 0)
            (j/call :onChange (fn [y]
                                (let [capsule (re/query :player/capsule)
                                      {:keys [x z]} (j/lookup (api.core/get-pos capsule))]
                                  (api.physics/set-pos capsule [x y z])))))
        (-> (j/call lil-gui :add controls "pos-z" 0)
            (j/call :onChange (fn [z]
                                (let [capsule (re/query :player/capsule)
                                      {:keys [x y]} (j/lookup (api.core/get-pos capsule))]
                                  (api.physics/set-pos capsule [x y z]))))))))

(defn- clone-bow-to-duplicated-models [bow]
  (let [players (re/query :player/character-pool)]
    (doseq [p players]
      (let [left-hand-tn (api.core/find-bone-tn p "mixamorig:LeftHand")
            bow (api.core/clone bow)]
        (m/assoc! bow
                  :parent left-hand-tn
                  :rotation.x 0
                  :rotation.y (api.core/to-rad 270)
                  :position.x 0
                  :position.y 0.2
                  :position.z 0.02
                  :scaling (v3 0.007))))))

(defn- position-player-for-the-first-time [player-capsule]
  (let [interval-id (atom nil)
        id (js/setInterval
             (fn []
               (api.physics/set-pos player-capsule [-12.1 1.6 -31.4])
               (when (= (j/call (api.physics/get-linear-velocity player-capsule) :length) 0)
                 (some-> @interval-id js/clearInterval)
                 (j/assoc! (re/query :camera) :beta 1.63 :alpha -0.038))) 100)]
    (reset! interval-id id)))

(defn init-player [cooldowns]
  (let [player-capsule (api.mesh/capsule {:name "player-capsule"
                                          :height 2
                                          :radius 0.5
                                          :visible? false})
        mass 50
        {mesh :player/model
         anim-groups :player/anim-groups
         camera :camera} (re/query-all)
        bb (api.mesh/box {:name "offset_box"
                          :size 0.5
                          :visible? false})
        bb-temp (api.core/clone bb)
        fire-ball-sphere-mat (api.material/standard-mat {:name "fire-ball-sphere-mat"
                                                         :diffuse-texture (api.asset/get-asset :texture/fire-ball-sphere)
                                                         :specular-color (api.core/color 0)
                                                         :emissive-color (api.core/color 1)})
        fire-ball-sphere (api.mesh/sphere {:name "fire-ball-sphere"
                                           :diameter-x 1
                                           :diameter-y 0.5
                                           :diameter-z 1
                                           :enabled? false
                                           :visibility 0.5
                                           :material fire-ball-sphere-mat})
        dash-trail (api.core/trail-mesh {:name "trail_mesh"
                                         :diameter 0.2
                                         :length 30
                                         :auto-start? false
                                         :generator player-capsule})
        dash-trail-mat (api.material/standard-mat {:name "dash-trail-material"
                                                   :back-face-culling? false
                                                   :diffuse-color (api.core/color-rgb 255 255 255)
                                                   :emissive-color (api.core/color-rgb 255 255 255)})
        speed-line-emitter (re/query :particle/speed-line-emitter)
        left-hand-tn (api.core/find-bone-tn mesh "mixamorig:LeftHand")
        light-strike-height 10]
    (j/assoc! dash-trail-mat
              :transparencyMode 2
              :alpha 0.1)
    (j/assoc! dash-trail
              :renderingGroupId 0
              :position (v3 0 -0.5 0)
              :material dash-trail-mat)
    (api.core/set-enabled dash-trail false)
    (api.material/freeze fire-ball-sphere-mat)
    (let [idle-anim (api.anim/find-animation-group "idle" anim-groups)]
      (when idle-anim
        (j/call idle-anim
                :start true
                1.0
                (j/get idle-anim :from)
                (j/get idle-anim :to)
                false))

      (doseq [c (api.core/get-child-meshes mesh)]
        (j/assoc! c :material (api.asset/get-asset :material/hero-no-team)))

      (re/insert {:player/total-health 1000
                  :player/current-health 1000
                  :player/respawn-duration (if config/dev?
                                             0
                                             3500)
                  :player/mana 100
                  :player/fast-forward-speed 12
                  :player/forward-speed 9
                  :player/normal-speed 9
                  :player/gravity 1
                  :player/jump-force 750
                  :player/dash-trail dash-trail
                  :player/particle-light-staff (api.particle/light-staff {:render-group-id 1})
                  :player/particle-light-staff-emitter (api.core/transform-node {:name "light-staff-emitter"})
                  :particle/levitate (api.particle/levitate-trail {:name "levitate-trail"
                                                                   :emitter player-capsule})
                  :particle/dash (api.particle/dash {:name "dash"
                                                     :emitter player-capsule})
                  :particle/shockwave (api.particle/shockwave {:name "shockwave"
                                                               :emitter player-capsule})
                  :particle/speed-line (api.particle/speed-line {:name "speed-line"
                                                                 :emitter speed-line-emitter})
                  :player/light-strike-height light-strike-height
                  :player/light-strike-cylinder (let [m (api.mesh/cylinder {:name "light-strike-cylinder"
                                                                            :height light-strike-height
                                                                            :diameter light-strike-height})]
                                                  (api.core/set-enabled m false)
                                                  m)
                  :player/temp-wind-tornado (let [circle (api.mesh/sphere {:name "temp-wind-tornado-circle"
                                                                           :diameter-x 1
                                                                           :diameter-y 0.2
                                                                           :diameter-z 1
                                                                           :segments 8
                                                                           :material (api.asset/get-asset :material/wind-tornado-mat)})
                                                  tornado (api.core/clone (api.core/get-mesh-by-name "Tornado"))
                                                  tornado-trigger-box (api.mesh/box {:name "temp-wind-tornado-box"
                                                                                     :width 4.5
                                                                                     :height 4
                                                                                     :depth 4.5
                                                                                     :visible? false})
                                                  tn (api.core/transform-node {:name "temp-tornado-wrapper"})
                                                  p-shape (api.physics/physics-shape {:mesh tornado-trigger-box
                                                                                      :trigger? true})
                                                  body (api.physics/physics-body {:tn tn
                                                                                  :motion-type :PhysicsMotionType/ANIMATED
                                                                                  :shape p-shape})
                                                  _ (m/assoc! tornado
                                                              :parent tn
                                                              :name "temp-wind-tornado"
                                                              :material (api.core/clone (j/get tornado :material))
                                                              :material.alpha 0.3
                                                              :position.y 1
                                                              :scaling (v3 5))
                                                  _ (m/assoc! circle
                                                              :parent tornado
                                                              :material.alpha 0.5
                                                              :scaling (v3 3)
                                                              :position.y -1.35)
                                                  _ (m/assoc! tornado-trigger-box :parent tn)]
                                              (j/assoc! body :on-trigger
                                                        (fn [type collider-name _]
                                                          (j/assoc! body :collided? (and
                                                                                      (not= collider-name "trigger_player")
                                                                                      (= type "TRIGGER_ENTERED")))))
                                              (j/assoc! body :disablePreStep false)
                                              (j/assoc! tornado :body body)
                                              (api.core/set-enabled tornado false)
                                              tornado)
                  :mouse/left-click? false
                  :mouse/right-click? false
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
                  :player/mobile-jump-click? false
                  :player/mobile-dash-click? false
                  :ad/video-running? false
                  :spell/count 0})
      (create-lil-gui)
      (j/call player-capsule :addChild mesh)
      (m/assoc! mesh :position.y -1)
      (m/assoc! player-capsule
                :checkCollisions true
                :visibility 0.2
                :position.y 2
                :position.x 15
                :position.z 15)
      (create-player-physics player-capsule mass)
      (j/assoc! bb :parent player-capsule)
      (j/call camera :setTarget bb)
      (re/insert (merge cooldowns {:player/capsule player-capsule
                                   :player/offset-box bb
                                   :player/temp-offset-box bb-temp
                                   :player/fire-ball-sphere fire-ball-sphere}))
      (ads/game-loading-finished)
      (position-player-for-the-first-time player-capsule)
      player-capsule)))

(defn apply-hero-material [mesh team]
  (let [mat (if (= :red team)
              (re/pop-from-pool :pool/material-hero-red)
              (re/pop-from-pool :pool/material-hero-blue))]
    (doseq [m (api.core/get-child-meshes (api.core/find-bone-tn mesh "Fat_Mage"))]
      (j/assoc! m :material mat))))

(defn- remove-target-animations
  ([anim-group]
   (remove-target-animations anim-group nil))
  ([anim-group masks]
   (let [masks (concat masks ["Toe" "HandThumb" "HandIndex"])]
     (doseq [ta (j/call-in anim-group [:targetedAnimations :slice])]
       (when (some (fn [m]
                     (str/includes? (j/get-in ta [:target :name]) m))
                   masks)
         (j/call anim-group :removeTargetedAnimation (j/get ta :animation)))))))

(defn- mask-anim-groups [ag]
  (when (#{"ice_ball" "spell_light_staff" "spell_wind_slash_right" "spell_wind_slash_left"} (j/get ag :name))
    (remove-target-animations ag ["Foot" "Leg"]))
  (when (#{"fall_light"} (j/get ag :name))
    (remove-target-animations ag ["Hand"]))
  (remove-target-animations ag))

(defn- create-item-positions [mesh]
  (j/assoc! mesh
            :item-head (api.core/transform-node {:name "Item_Head"
                                                 :parent (api.core/find-bone-tn mesh "mixamorig:HeadTop_End")})
            :item-cape (api.core/transform-node {:name "Item_Cape"
                                                 :parent (api.core/find-bone-tn mesh "mixamorig:Neck")})
            :item-attachment (api.core/transform-node {:name "Item_Attachment"
                                                       :parent (api.core/find-bone-tn mesh "mixamorig:Hips")})))

(defn- duplicate-animated-model [container health-bar?]
  (let [entries (j/call container :instantiateModelsToScene
                        (fn [name] name)
                        false
                        #js {:doNotInstantiate true})
        mesh (m/get entries :rootNodes 0)
        anim-groups (m/get entries :animationGroups)
        ice-arrow-point (api.mesh/box {:name "ice_arrow_point"
                                       :size 0.1
                                       :visible? false})]
    (j/assoc! mesh
              :name "Root_Player"
              :isPickable false
              :doNotSyncBoundingInfo true
              :alwaysSelectAsActiveMesh true
              :receiveShadows false
              :rotationQuaternion nil
              :anim-groups anim-groups
              ;; Special case for duplicated models
              :scaling (v3 1.25 -1 1.25))

    (doseq [c (api.core/get-child-meshes mesh)]
      (j/assoc! c
                :doNotSyncBoundingInfo true
                :alwaysSelectAsActiveMesh true
                :isPickable false)
      (when (str/includes? (j/get c :name) "Chr_Hips_Male")
        (j/assoc! ice-arrow-point
                  :parent c
                  :position (v3 -0.3 0.2 -1.5))))
    (doseq [ag anim-groups]
      (mask-anim-groups ag)
      (j/assoc! ag
                :enableBlending true
                :blendingSpeed (if (or (str/includes? (j/get ag :name) "fall")
                                       (str/includes? (j/get ag :name) "levitate"))
                                 0.05
                                 0.1)))
    (let [idle-anim (api.anim/find-animation-group "idle" anim-groups)]
      (when idle-anim
        (j/call idle-anim
                :start true
                1.0
                (j/get idle-anim :from)
                (j/get idle-anim :to)
                false)))
    (let [trigger-box (api.mesh/capsule {:name "trigger_player"
                                         :radius 0.65
                                         :height 2
                                         :visibility 0.001})
          _ (api.core/set-parent trigger-box mesh)
          _ (j/assoc! trigger-box
                      :position (v3 0 1 0)
                      :model-mesh mesh)
          _ (j/assoc! mesh
                      :trigger-box trigger-box
                      :ice-arrow-point ice-arrow-point
                      :particle-light-staff (api.particle/light-staff :target-stop-duration 1)
                      :particle-light-staff-emitter (api.core/transform-node {:name "light-staff-emitter"}))
          p-shape (api.physics/physics-shape {:mesh trigger-box
                                              :trigger? true})
          filter-group api.const/collision-group-other-players]
      (j/assoc! p-shape :filterMembershipMask filter-group)
      (api.physics/physics-body {:tn trigger-box
                                 :motion-type :PhysicsMotionType/STATIC
                                 :start-as-sleep? false
                                 :disable-pre-step? false
                                 :shape p-shape}))
    (when health-bar?
      (api.mesh/create-health-bar mesh))
    (create-item-positions mesh)
    mesh))

(defn- disable-char-meshes-not-pickable [mesh]
  (doseq [m (api.core/get-child-meshes mesh)]
    (j/assoc! m :isPickable false)))

(def max-num-of-players 8)

(reg-mesh
  :mesh/character
  {:url "fat_character.glb"
   :container? true
   :preload? true
   :on-success (fn [container meshes anim-groups skeletons]
                 (re/upsert :player/skeletons (fnil conj []) (first skeletons))
                 (j/call container :addAllToScene)
                 (dotimes [_ (dec max-num-of-players)]
                   (let [mesh (duplicate-animated-model container true)]
                     (re/push-to-pool :player/character-pool mesh)
                     (api.core/set-enabled mesh false)))

                 (let [mesh (duplicate-animated-model container false)]
                   (j/assoc! mesh :name "Shop_Model")
                   (api.core/set-enabled mesh false)
                   (re/insert :game/shop-model mesh))

                 (let [mesh (first meshes)]
                   (api.core/set-enabled mesh false)
                   (m/assoc! mesh
                             :name "Root_Player"
                             :isPickable false
                             :doNotSyncBoundingInfo true
                             :alwaysSelectAsActiveMesh true
                             :receiveShadows false
                             :rotationQuaternion nil)
                   (disable-char-meshes-not-pickable mesh)
                   (doseq [m (api.core/get-child-meshes mesh)]
                     (j/assoc! m
                               :doNotSyncBoundingInfo true
                               :alwaysSelectAsActiveMesh true
                               :isPickable false))

                   (doseq [ag anim-groups]
                     (mask-anim-groups ag)
                     (j/assoc! ag
                               :enableBlending true
                               :blendingSpeed (if (or (str/includes? (j/get ag :name) "fall")
                                                      (str/includes? (j/get ag :name) "levitate"))
                                                0.05
                                                0.1)))

                   (create-item-positions mesh)

                   (api.anim/add-on-anim-group-end
                     (api.anim/find-animation-group "dash" anim-groups)
                     (fn []
                       (re/insert {:player/dash? false
                                   :player/dash-finished-time (js/Date.now)})))
                   (api.anim/add-on-anim-group-end
                     (api.anim/find-animation-group "ice_ball" anim-groups)
                     (fn []
                       (re/insert :player/spell-ice-arrow? false)))
                   (api.anim/add-on-anim-group-end
                     (api.anim/find-animation-group "spell_wind_tornado" anim-groups)
                     (fn []
                       (re/insert :player/spell-wind-tornado? false)))
                   (api.anim/add-on-anim-group-end
                     (api.anim/find-animation-group "spell_wind_slash_right" anim-groups)
                     (fn []
                       (re/insert :player/spell-wind-slash? false)))
                   (api.anim/add-on-anim-group-end
                     (api.anim/find-animation-group "spell_wind_slash_left" anim-groups)
                     (fn []
                       (re/insert :player/spell-wind-slash? false)))
                   (api.anim/add-on-anim-group-end
                     (api.anim/find-animation-group "spell_right" anim-groups)
                     (fn []
                       (re/insert :player/spell? false)))
                   (api.anim/add-on-anim-group-end
                     (api.anim/find-animation-group "spell_fire_ball" anim-groups)
                     (fn []
                       (re/insert :player/spell-super-nova? false)))
                   (api.anim/add-on-anim-group-end
                     (api.anim/find-animation-group "spell_ice" anim-groups)
                     (fn []
                       (re/insert :player/spell-ice-tornado? false)))
                   (api.anim/add-on-anim-group-end
                     (api.anim/find-animation-group "jump_light" anim-groups)
                     (fn []
                       (re/insert :player/jump-up? false)))
                   (api.anim/add-on-anim-group-end
                     (api.anim/find-animation-group "spell_light_strike" anim-groups)
                     (fn []
                       (re/insert :player/spell-light-strike? false)))
                   (api.anim/add-on-anim-group-end
                     (api.anim/find-animation-group "spell_toxic" anim-groups)
                     (fn []
                       (re/insert :player/spell-toxic? false)))
                   (api.anim/add-on-anim-group-end
                     (api.anim/find-animation-group "spell_toxic_cloud" anim-groups)
                     (fn []
                       (re/insert :player/spell-toxic-cloud? false)))
                   (api.anim/add-on-anim-group-end
                     (api.anim/find-animation-group "spell_rock" anim-groups)
                     (fn []
                       (re/insert :player/spell-rock? false)))
                   (api.anim/add-on-anim-group-end
                     (api.anim/find-animation-group "spell_rock_wall" anim-groups)
                     (fn []
                       (re/insert :player/spell-rock-wall? false)))

                   #_(api.core/skeleton-viewer {:skeleton (first skeletons)
                                                :mesh (api.core/get-mesh-by-name "Chr_Hips_Male_04.003")
                                                :display-mode api.const/skeleton-viewer-display-sphere-and-spurs})
                   (re/insert {:player/model mesh
                               :player/container container
                               :player/skeleton (first skeletons)
                               :player/anim-groups anim-groups})))})

(defn add-crystals-to-players [container merged-crystals]
  (doseq [p (re/query :player/character-pool)]
    (let [merged-crystals (api.core/clone merged-crystals :name "crystals")]
      (j/assoc! merged-crystals :parent container)
      (api.physics/physics-agg {:mesh merged-crystals
                                :disable-pre-step? false
                                :type :PhysicsShapeType/CONVEX_HULL
                                :mass 0
                                :motion-type :PhysicsMotionType/STATIC})
      (j/assoc! p :ice-tornado merged-crystals))))

#_(reg-mesh
    :mesh/crystal
    {:url "crystal.glb"
     ;; :preload? true
     :on-success (fn [mesh]
                   (let [container (api.core/transform-node {:name "crystals"})
                         index-range (range -2 3)
                         start-point (v3 0 0 0)
                         material (api.core/get-material-by-name "Crystal")
                         _ (j/assoc! material :alpha 0.5)
                         _ (api.material/freeze material)
                         crystals (for [i index-range]
                                    (let [x (* (+ (j/get start-point :x) (* i 2)))
                                          z (* (+ (j/get start-point :z) (* i 2)))
                                          mesh (api.core/clone mesh :name (str "copy_crystal_" i))]
                                      (m/assoc! mesh
                                                :scaling (v3 (utils/rand-between 1.5 2))
                                                :position (v3 x 0.1 z)
                                                :rotation.y (utils/rand-between 0 (* Math/PI 2))
                                                :receiveShadows true
                                                :rotationQuaternion nil)
                                      mesh))
                         crystals (to-array (mapcat api.core/get-child-meshes crystals))
                         merged-crystals (api.core/merge-meshes {:meshes crystals
                                                                 :dispose-source? true})]
                     (j/assoc! merged-crystals :parent container)
                     (api.physics/physics-agg {:mesh merged-crystals
                                               :disable-pre-step? false
                                               :type :PhysicsShapeType/CONVEX_HULL
                                               :mass 0
                                               :motion-type :PhysicsMotionType/STATIC})
                     (j/assoc! merged-crystals :position (v3 0 -8 0))
                     (api.core/set-enabled merged-crystals false)
                     (add-crystals-to-players container merged-crystals)
                     (doseq [i index-range]
                       (api.core/dispose (api.core/get-mesh-by-name (str "copy_crystal_" i))))
                     (api.core/dispose mesh)
                     (re/insert {:crystal/container merged-crystals})))})

(comment
  (let [mesh (api.mesh/box {:name "ground2"
                            :size 1
                            :width 3
                            :height 3
                            })]
    (j/assoc! mesh :position (v3 5 0.5 1))
    (api.physics/physics-agg {:mesh mesh
                              :type :PhysicsShapeType/MESH
                              :mass 0
                              :motion-type :PhysicsMotionType/STATIC}))
  )
