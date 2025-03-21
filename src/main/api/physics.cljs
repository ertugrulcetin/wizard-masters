(ns main.api.physics
  (:require
    ["@babylonjs/core/Physics/physicsRaycastResult" :refer [PhysicsRaycastResult]]
    ["@babylonjs/core/Physics/v2/IPhysicsEnginePlugin" :refer [PhysicsMotionType PhysicsShapeType]]
    ["@babylonjs/core/Physics/v2/physicsAggregate" :refer [PhysicsAggregate]]
    ["@babylonjs/core/Physics/v2/physicsBody" :refer [PhysicsBody]]
    ["@babylonjs/core/Physics/v2/physicsShape" :refer [PhysicsShapeMesh]]
    [applied-science.js-interop :as j]
    [main.api.constant :as api.const]
    [main.api.core :as api.core])
  (:require-macros
    [main.macros :as m]))

(defn physics-agg [{:keys [mesh
                           type
                           mass
                           friction
                           restitution
                           motion-type
                           mass-props
                           linear-damping
                           disable-pre-step?
                           angular-damping
                           gravity-factor
                           filter-group]}]
  (let [agg (PhysicsAggregate. mesh (j/get PhysicsShapeType (name type)) #js {:mass mass
                                                                              :friction friction
                                                                              :restitution restitution})
        filter-group (or filter-group api.const/collision-group-environment)]
    (j/assoc-in! agg [:shape :filterMembershipMask] filter-group)
    (m/cond-doto agg
      (some? disable-pre-step?) (j/assoc-in! [:body :disablePreStep] disable-pre-step?)
      gravity-factor (j/call-in [:body :setGravityFactor] gravity-factor)
      linear-damping (j/call-in [:body :setLinearDamping] linear-damping)
      angular-damping (j/call-in [:body :setAngularDamping] angular-damping)
      mass-props (j/call-in [:body :setMassProperties] (clj->js mass-props))
      motion-type (j/call-in [:body :setMotionType] (j/get PhysicsMotionType (name motion-type))))))

(defn physics-shape [{:keys [mesh trigger?]}]
  (let [shape (PhysicsShapeMesh. mesh (api.core/get-scene))]
    (m/cond-doto shape
      (some? trigger?) (j/assoc! :isTrigger trigger?))))

(defn physics-body [{:keys [tn motion-type start-as-sleep? disable-pre-step? shape]}]
  (let [body (PhysicsBody. tn
                           (j/get PhysicsMotionType (name motion-type))
                           start-as-sleep?
                           (api.core/get-scene))]
    (m/cond-doto body
      (some? disable-pre-step?) (j/assoc! :disablePreStep disable-pre-step?)
      shape (j/assoc! :shape shape))))

(defn raycast-result []
  (PhysicsRaycastResult.))

(defn raycast-to-ref [p1 p2 result collide-with]
  (let [scene (api.core/get-scene)
        physics-engine (j/call scene :getPhysicsEngine)]
    (j/call physics-engine :raycastToRef p1 p2 result collide-with)
    result))

(defn apply-force [mesh force location]
  (j/call-in mesh [:physicsBody :applyForce] force location))

(defn apply-impulse [mesh impulse location]
  (j/call-in mesh [:physicsBody :applyImpulse] impulse location))

(defn get-linear-velocity [mesh]
  (j/call-in mesh [:physicsBody :getLinearVelocity]))

(defn get-linear-velocity-to-ref [mesh ref]
  (j/call-in mesh [:physicsBody :getLinearVelocityToRef] ref))

(defn set-linear-velocity [mesh dir]
  (j/call-in mesh [:physicsBody :setLinearVelocity] dir))

(defn set-angular-velocity [mesh dir]
  (j/call-in mesh [:physicsBody :setAngularVelocity] dir))

(defn get-object-center-world [mesh]
  (j/call-in mesh [:physicsBody :getObjectCenterWorld]))

(defn get-gravity-factor [mesh]
  (j/get-in mesh [:physicsBody :getGravityFactor]))

(defn set-gravity-factor [mesh factor]
  (j/call-in mesh [:physicsBody :setGravityFactor] factor))

(defn set-pos [mesh pos]
  (let [hk (-> (api.core/get-scene)
               (j/call :getPhysicsEngine)
               (j/call :getPhysicsPlugin))]
    (j/call-in hk [:_hknp :HP_Body_SetPosition] (j/get-in mesh [:physicsBody :_pluginData :hpBodyId]) (if (vector? pos)
                                                                                                        (clj->js pos)
                                                                                                        pos))))

(defn- add-collision-observable [body f]
  (when-not (m/get body :collision-callback-enabled?)
    (j/call body :setCollisionCallbackEnabled true)
    (m/assoc! body :collision-callback-enabled? true))
  (-> body
      (j/call :getCollisionObservable)
      (j/call :add f)))

(defn make-physics-body-static [body]
  (j/call body :setMotionType api.const/motion-type-static)
  (j/call body :setMassProperties #js {:mass 0}))

(defn make-physics-body-dynamic
  ([body]
   (make-physics-body-dynamic body 1))
  ([body mass]
   (j/call body :setMotionType api.const/motion-type-dynamic)
   (j/call body :setMassProperties #js {:mass mass})))
