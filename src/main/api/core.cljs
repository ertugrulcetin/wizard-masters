(ns main.api.core
  (:refer-clojure :exclude [clone])
  (:require
    ["@babylonjs/core/Animations/animationGroupMask" :refer [AnimationGroupMask]]
    ["@babylonjs/core/Audio/sound" :refer [Sound]]
    ["@babylonjs/core/Culling/ray" :refer [Ray]]
    ["@babylonjs/core/Debug/rayHelper" :refer [RayHelper]]
    ["@babylonjs/core/Debug/skeletonViewer" :refer [SkeletonViewer]]
    ["@babylonjs/core/Engines/engine" :refer [Engine]]
    ["@babylonjs/core/Layers/glowLayer" :refer [GlowLayer]]
    ["@babylonjs/core/Loading/sceneLoader" :refer [SceneLoader]]
    ["@babylonjs/core/Materials/Textures/Procedurals/noiseProceduralTexture" :refer [NoiseProceduralTexture]]
    ["@babylonjs/core/Materials/Textures/texture" :refer [Texture]]
    ["@babylonjs/core/Materials/effect" :refer [Effect]]
    ["@babylonjs/core/Maths/math" :refer [Vector2 Vector3 Vector4]]
    ["@babylonjs/core/Maths/math.color" :refer [Color3 Color4]]
    ["@babylonjs/core/Maths/math.scalar" :refer [Scalar]]
    ["@babylonjs/core/Maths/math.vector" :refer [Quaternion Matrix]]
    ["@babylonjs/core/Meshes/mesh" :refer [Mesh]]
    ["@babylonjs/core/Meshes/trailMesh" :refer [TrailMesh]]
    ["@babylonjs/core/Meshes/transformNode" :refer [TransformNode]]
    ["@babylonjs/core/Misc/filesInput" :refer [FilesInput]]
    ["@babylonjs/core/Misc/tools" :refer [Tools]]
    ["@babylonjs/core/PostProcesses/postProcess" :refer [PostProcess]]
    ["@babylonjs/core/scene" :refer [Scene]]
    ["@babylonjs/inspector"]
    [applied-science.js-interop :as j])
  (:require-macros
    [main.macros :as m]))

(defonce db #js {})

(defn create-engine [canvas]
  (let [e (Engine. canvas false #js {:limitDeviceRatio 1.0
                                     :stencil false
                                     :premultipliedAlpha false
                                     :preserveDrawingBuffer false})]
    (j/assoc! db :engine e :canvas canvas)
    e))

(defn get-engine []
  (j/get db :engine))

(defn resize []
  (println "Canvas resized")
  (some-> (get-engine) (j/call :resize)))

(defn create-scene [engine]
  (let [s (Scene. engine)]
    (j/assoc! db :scene s)
    s))

(defn get-canvas []
  (j/get db :canvas))

(defn get-scene []
  (j/get db :scene))

(defn get-bb-renderer []
  (j/call (get-scene) :getBoundingBoxRenderer))

(defn v2
  ([]
   (Vector2.))
  ([n]
   (Vector2. n n))
  ([x z]
   (Vector2. x z)))

(defn v3
  ([]
   (Vector3.))
  ([n]
   (Vector3. n n n))
  ([x y z]
   (Vector3. x y z)))

(defn v3-minimize [left right]
  (j/call Vector3 :Minimize left right))

(defn v3-maximize [left right]
  (j/call Vector3 :Maximize left right))

(defn v4
  ([]
   (Vector4.))
  ([n]
   (Vector4. n n n n))
  ([x y z w]
   (Vector4. x y z w)))

(defn v3? [v]
  (instance? Vector3 v))

(defn distance [v1 v2]
  (j/call Vector3 :Distance v1 v2))

(defn clone [obj & {:keys [name parent]}]
  (j/call obj :clone name parent))

(defn set-v2 [v x y]
  (j/call v :set x y))

(defn set-v3
  ([v v2]
   (j/call v :set (j/get v2 :x) (j/get v2 :y) (j/get v2 :z)))
  ([v x y z]
   (j/call v :set x y z)))

(defn sub-v3 [v1 v2]
  (j/call v1 :subtract v2))

(defn v3->v [v3]
  [(j/get v3 :x) (j/get v3 :y) (j/get v3 :z)])

(defn v->v3 [[x y z :as v]]
  (v3 x y z))

(defn equals? [v1 v2]
  (j/call v1 :equals v2))

(defn get-delta-time []
  (/ (j/call-in db [:engine :getDeltaTime]) 1000))

(defn color
  ([c]
   (color c c c))
  ([r g b]
   (Color3. r g b))
  ([r g b a]
   (Color4. r g b a)))

(defn color-rgb
  ([c]
   (color-rgb c c c))
  ([r g b]
   (j/call Color3 :FromInts r g b))
  ([r g b a]
   (j/call Color4 :FromInts r g b a)))

(defn color->color-rgb [[r g b]]
  [(* 255.0 r) (* 255.0 g) (* 255.0 b)])

(defn color->v [c]
  (let [r (j/get c :r)
        g (j/get c :g)
        b (j/get c :b)]
    [(* 255.0 r) (* 255.0 g) (* 255.0 b)]))

(defn set-enabled [obj enabled?]
  (j/call obj :setEnabled enabled?))

(defn enabled? [obj]
  (j/call obj :isEnabled))

(defn get-pos
  ([obj]
   (get-pos obj false))
  ([obj clone?]
   (if clone?
     (clone (j/call obj :getAbsolutePosition))
     (j/call obj :getAbsolutePosition))))

(defn set-pos [obj v3]
  (j/call obj :setPosition v3))

(defn update-pos [obj v3]
  (j/assoc! obj :position v3))

(defn texture
  ([path]
   (texture path nil))
  ([path {:keys [u-scale
                 v-scale
                 on-success
                 on-error
                 no-mipmap-or-options
                 invert-y]
          :or {no-mipmap-or-options false
               invert-y false}}]
   (let [tex (Texture. path (get-scene) no-mipmap-or-options invert-y)]
     (m/cond-doto tex
       u-scale (j/assoc! :uScale u-scale)
       v-scale (j/assoc! :vScale v-scale)
       on-success (j/call-in [:onLoadObservable :add] on-success)
       on-error (j/assoc! :onError on-error)))))

(defn register-on-before-render [f]
  (j/call-in db [:scene :onBeforeRenderObservable :add] f))

(defn remove-on-before-render [f]
  (j/call-in db [:scene :onBeforeRenderObservable :removeCallback] f))

(defn register-on-after-render [f]
  (j/call-in db [:scene :onAfterRenderObservable :add] f))

(defn register-on-before-anim [f]
  (j/call-in db [:scene :onBeforeAnimationsObservable :add] f))

(defn register-on-after-anim [f]
  (j/call-in db [:scene :onAfterAnimationsObservable :add] f))

(defn get-advanced-texture []
  (j/get db :gui-advanced-texture))

(defn import-mesh [file f]
  (j/call SceneLoader :ImportMesh "" "models/" file (j/get db :scene) f))

(defn normalize [v]
  (j/call v :normalize))

(defn scale [v n]
  (j/call v :scale n))

(defn to-rad [angle]
  (j/call Tools :ToRadians angle))

(defn to-deg [angle]
  (j/call Tools :ToDegrees angle))

(defn dispose [obj]
  (j/call obj :dispose))

(defn dispose-engine []
  (j/call-in db [:engine :dispose])
  (set! db #js {}))

(defn show-debug []
  (let [p (j/call-in db [:scene :debugLayer :show] #js {:embedMode true})]
    (.then p #(j/assoc-in! (js/document.getElementById "embed-host") [:style :position] "absolute"))))

(defn hide-debug []
  (j/call-in db [:scene :debugLayer :hide]))

(defn clear-scene-color [color]
  (j/assoc-in! db [:scene :clearColor] color))

(defn transform-node [{:keys [name
                              position
                              rotation
                              scale
                              parent]}]
  (let [tn (TransformNode. name)]
    (m/cond-doto tn
      position (j/assoc! :position position)
      rotation (j/assoc! :rotation rotation)
      scale (j/assoc! :scaling scale)
      parent (j/assoc! :parent parent))))

(defn mesh [name & {:keys [position
                           rotation
                           scale
                           visibility]
                    :as opts}]
  (let [tn (Mesh. name)]
    (m/cond-doto tn
      position (j/assoc! :position position)
      visibility (j/assoc! :visibility visibility)
      rotation (j/assoc! :rotation rotation)
      scale (j/assoc! :scaling scale))))

(defn add-children [parent & children]
  (doseq [c children]
    (j/assoc! c :parent parent)))

(defn lerp [start end amount]
  (j/call Scalar :Lerp start end amount))

(defn rand-range [start end]
  (j/call Scalar :RandomRange start end))

(defn color-lerp [start end amount]
  (j/call Color3 :Lerp start end amount))

(defn get-child-meshes
  ([obj]
   (j/call obj :getChildMeshes))
  ([obj name]
   (some (fn [m] (when (= (j/get m :name) name) m)) (j/call obj :getChildMeshes))))

(defn find-child-mesh [obj f]
  (some (fn [m] (when (f (j/get m :name)) m)) (j/call obj :getChildMeshes)))

(defn get-mesh-by-name [name]
  (j/call-in db [:scene :getMeshByName] name))

(defn get-mesh-by-id [id]
  (j/call-in db [:scene :getMeshByID] id))

(defn get-world-transforms [mesh]
  (let [world-matrix (j/call mesh :getWorldMatrix true)
        position (v3)
        rotation (j/call Quaternion :Zero)
        scale (v3)
        _ (j/call world-matrix :decompose scale rotation position)
        euler-rotation (j/call rotation :toEulerAngles)]
    {:name (j/get mesh :name)
     :position position
     :rotation euler-rotation
     :scaling scale}))

(defn get-all-meshes []
  (->> (j/get (get-scene) :meshes)
       (mapcat get-child-meshes)))

(defn get-node-by-name [name]
  (j/call-in db [:scene :getNodeByName] name))

(defn get-child-transform-nodes [n]
  (j/call n :getChildTransformNodes))

(defn get-children [n]
  (j/call n :getChildren))

(defn get-particle-by-id [id]
  (j/call-in db [:scene :getParticleSystemById] id))

(defn set-parent [child parent]
  (j/call child :setParent parent))

(defn get-material-by-name [name]
  (j/call-in db [:scene :getMaterialByName] name))

(defn get-material-by-id [id]
  (j/call-in db [:scene :getMaterialByUniqueID] id))

(defn get-anim-ratio []
  (j/call-in db [:scene :getAnimationRatio]))

(defn get-texture-by-name [name]
  (j/call-in db [:scene :getTextureByName] name))

(defn look-at [obj target]
  (j/call obj :lookAt target))

(defn quat [x y z w]
  (Quaternion. x y z w))

(defn rotation-yaw-pitch-roll [x y z]
  (j/call Quaternion :RotationYawPitchRoll x y z))

(defn quat-rot-axis [axis rad]
  (j/call Quaternion :RotationAxis axis rad))

(defn trail-mesh [{:keys [name
                          generator
                          diameter
                          length
                          auto-start?
                          pickable?]
                   :or {diameter 1.0
                        length 60
                        auto-start? true}}]
  (m/cond-doto (TrailMesh. name generator (get-scene) diameter length auto-start?)
    (some? pickable?) (j/assoc! :isPickable pickable?)))

(defn post-proc [{:keys [name fragment uniforms samplers opts camera]
                  :or {uniforms []
                       samplers []
                       opts 1.0}}]
  (PostProcess. name fragment (clj->js uniforms) (clj->js samplers) opts camera))

(defn noise-procedural-tex
  ([]
   (noise-procedural-tex nil))
  ([{:keys [animation-speed-factor
            brightness
            persistence]
     :or {animation-speed-factor 1.0
          brightness 0.5
          persistence 0.8}}]
   (m/cond-doto (NoiseProceduralTexture. "perlin" 256 (get-scene))
     true (j/assoc! :refreshRate 0)
     animation-speed-factor (j/assoc! :animationSpeedFactor animation-speed-factor)
     brightness (j/assoc! :brightness brightness)
     persistence (j/assoc! :persistence persistence))))

(defn register-shader [name shader]
  (j/assoc-in! Effect [:ShadersStore (str name "FragmentShader")] shader))

(defn find-bone-tn [mesh skeleton-name]
  (let [child-transforms (get-child-transform-nodes mesh)]
    (some #(when (= skeleton-name (j/get % :name)) %) child-transforms)))

(defn pick [pointer-x pointer-y]
  (j/call-in db [:scene :pick] pointer-x pointer-y))

(defn pick-with-ray [ray]
  (j/call-in db [:scene :pickWithRay] ray))

(defn pick-multi-with-ray [ray]
  (j/call-in db [:scene :multiPickWithRay] ray))

(defn create-picking-ray [camera]
  (let [canvas (get-canvas)
        scaling-factor (/ 2 (j/call (get-engine) :getHardwareScalingLevel))
        center-x (/ (j/get canvas :width) scaling-factor)
        center-y (/ (j/get canvas :height) scaling-factor)]
    (j/call-in db [:scene :createPickingRay]
               center-x
               center-y
               (j/call Matrix :Identity)
               camera
               false)))

(defn ray [origin dir len]
  (Ray. origin dir len))

(defn ray-helper [ray]
  (j/call (RayHelper. ray) :show (get-scene)))

(defn merge-meshes [{:keys [name
                            meshes
                            dispose-source?
                            allow-32-bits-indices?
                            mesh-subclass
                            subdivide-with-sub-meshes?
                            multi-multi-materials?]
                     :or {dispose-source? false
                          allow-32-bits-indices? false
                          subdivide-with-sub-meshes? false
                          multi-multi-materials? false}}]
  (some-> (j/call Mesh :MergeMeshes
                  meshes
                  dispose-source?
                  allow-32-bits-indices?
                  mesh-subclass
                  subdivide-with-sub-meshes?
                  multi-multi-materials?)
          (j/assoc! :name name)))

(defn freeze-active-meshes []
  (j/call (get-scene) :freezeActiveMeshes))

(defn freeze-world-matrix [m]
  (j/call m :freezeWorldMatrix))

(defn unfreeze-world-matrix [m]
  (j/call m :unfreezeWorldMatrix))

(defn cross [v1 v2]
  (j/call Vector3 :Cross v1 v2))
+
(defn dot [v1 v2]
  (j/call Vector3 :Dot v1 v2))

(defn get-angle-between-vecs [v1 v2 normal]
  (j/call Vector3 :GetAngleBetweenVectors v1 v2 normal))

(defn glow-layer
  ([name]
   (glow-layer name nil))
  ([name opts]
   (GlowLayer. name (get-scene) (clj->js opts))))

(defn skeleton-viewer [{:keys [skeleton
                               mesh
                               display-mode
                               auto-update-bones-matrices?
                               rendering-group-id]
                        :or {auto-update-bones-matrices? true
                             rendering-group-id 3}}]
  (let [viewer (SkeletonViewer. skeleton
                                mesh
                                (get-scene)
                                auto-update-bones-matrices?
                                rendering-group-id
                                #js {:displayMode display-mode})]
    (j/assoc! viewer :isEnabled true)))

(defn anim-mask-group [bone-names]
  (AnimationGroupMask. (clj->js bone-names) 1))

(defn set-files-to-load [name blob]
  (j/assoc-in! FilesInput [:FilesToLoad name] blob))

(defn compute-world-matrix [o]
  (j/call o :computeWorldMatrix true))

(defn attach-to-bone [mesh bone affected-tn]
  (j/call mesh :attachToBone bone affected-tn))
