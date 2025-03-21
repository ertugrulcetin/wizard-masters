(ns main.api.mesh
  (:require
    ["@babylonjs/core/Materials/Textures/cubeTexture" :refer [CubeTexture]]
    ["@babylonjs/core/Meshes/Builders/greasedLineBuilder" :refer [CreateGreasedLine]]
    ["@babylonjs/core/Meshes/mesh" :refer [Mesh]]
    ["@babylonjs/core/Meshes/meshBuilder" :refer [MeshBuilder]]
    ["@babylonjs/core/Misc/greasedLineTools" :refer [GreasedLineTools]]
    ["earcut" :as earcut]
    [applied-science.js-interop :as j]
    [clojure.string :as str]
    [main.api.asset :as api.asset]
    [main.api.constant :as api.const]
    [main.api.core :as api.core :refer [v3]]
    [main.api.gui :as gui]
    [main.api.material :as api.material]
    [main.api.physics :as api.physics])
  (:require-macros
    [main.macros :as m]))

(defn reg-mesh
  "Parameters:
   * :url - the url of the mesh
   * :on-success - a function to be called when the mesh is loaded
   * :preload? - whether to preload the mesh or not"
  [id opts]
  (let [mesh-name (name id)
        opts (assoc opts :id id
                    :name mesh-name
                    :type (if (:container? opts) :container :mesh))]
    (when (api.core/get-scene)
      (some-> (api.asset/get-asset id) (api.core/dispose))
      (api.asset/add-mesh-task id (:url opts) (:on-success opts))
      (api.asset/load-async))
    (j/assoc-in! api.core/db [:assets-regs id] opts)
    opts))

(defn upload-map [mesh]
  (api.core/dispose (api.core/get-mesh-by-name "Root_Map"))
  (j/assoc! mesh :name "Root_Map")
  (doseq [m (api.core/get-child-meshes mesh)]
    (let [material (j/get m :material)]
      (m/assoc! material
                :lightmapTexture (j/get material :albedoTexture)
                :sheen.isEnabled true
                :sheen.intensity 0.5)
      (api.material/freeze material)
      (j/call m :freezeWorldMatrix)
      (j/assoc! m :doNotSyncBoundingInfo true)
      (api.physics/physics-agg {:mesh m
                                :type :PhysicsShapeType/MESH
                                :mass 0
                                :motion-type :PhysicsMotionType/STATIC}))))

(defn box [{:keys [name
                   size
                   width
                   height
                   depth
                   face-uv
                   wrap?
                   visibility
                   visible?
                   position
                   rotation
                   pickable?
                   skybox?
                   apply-fog?
                   infinite-distance?
                   check-collisions?
                   do-not-sync-bounding-info?
                   alpha-index
                   mat]
            :or {pickable? false
                 do-not-sync-bounding-info? true}}]
  (let [b (j/call MeshBuilder :CreateBox name #js {:size size
                                                   :faceUV face-uv
                                                   :wrap wrap?
                                                   :width width
                                                   :height height
                                                   :depth depth})]
    (cond-> b
      alpha-index (j/assoc! :alphaIndex alpha-index)
      mat (j/assoc! :material mat)
      position (j/assoc! :position position)
      rotation (j/assoc! :rotation rotation)
      visibility (j/assoc! :visibility visibility)
      (some? check-collisions?) (j/assoc! :checkCollisions check-collisions?)
      (some? apply-fog?) (j/assoc! :applyFog apply-fog?)
      (some? visible?) (j/assoc! :isVisible visible?)
      (some? pickable?) (j/assoc! :isPickable pickable?)
      (some? do-not-sync-bounding-info?) (j/assoc! :doNotSyncBoundingInfo do-not-sync-bounding-info?)
      (some? infinite-distance?) (j/assoc! :infiniteDistance infinite-distance?))))

(defn cylinder [{:keys [name height diameter]}]
  (j/call MeshBuilder :CreateCylinder name #js {:diameter diameter
                                                :height height}))

(defn sphere [{:keys [name
                      diameter
                      diameter-x
                      diameter-y
                      diameter-z
                      segments
                      side-orientation
                      updatable?
                      arc
                      slice
                      visibility
                      position
                      rotation
                      scale
                      material
                      infinite-distance?
                      visible?
                      rendering-group-id
                      render-overlay?
                      render-outline?
                      do-not-sync-bounding-info?
                      enabled?
                      pickable?]
               :or {segments 32
                    arc 1
                    slice 1
                    updatable? false
                    pickable? false
                    do-not-sync-bounding-info? true
                    side-orientation api.const/mesh-default-side}}]
  (let [s (j/call MeshBuilder :CreateSphere name #js {:diameter diameter
                                                      :diameterX diameter-x
                                                      :diameterY diameter-y
                                                      :diameterZ diameter-z
                                                      :segments segments
                                                      :arc arc
                                                      :slice slice
                                                      :updatable updatable?
                                                      :sideOrientation side-orientation})]
    (m/cond-doto s
      material (j/assoc! :material material)
      position (j/assoc! :position position)
      rotation (j/assoc! :rotation rotation)
      scale (j/assoc! :scaling scale)
      visibility (j/assoc! :visibility visibility)
      rendering-group-id (j/assoc! :renderingGroupId rendering-group-id)
      (some? do-not-sync-bounding-info?) (j/assoc! :doNotSyncBoundingInfo do-not-sync-bounding-info?)
      (some? pickable?) (j/assoc! :isPickable pickable?)
      (some? enabled?) (j/call :setEnabled enabled?)
      (some? render-overlay?) (j/assoc! :renderOverlay render-overlay?)
      (some? render-outline?) (j/assoc! :renderOutline render-outline?)
      (some? visible?) (j/assoc! :isVisible visible?)
      (some? infinite-distance?) (j/assoc! :infiniteDistance infinite-distance?))))

(defn capsule [{:keys [name
                       position
                       visible?
                       height
                       radius
                       visibility
                       pickable?]
                :or {pickable? false}}]
  (let [c (j/call MeshBuilder :CreateCapsule name #js {:height height
                                                       :radius radius})]
    (cond-> c
      position (j/assoc! :position position)
      visibility (j/assoc! :visibility visibility)
      (some? visible?) (j/assoc! :isVisible visible?)
      (some? pickable?) (j/assoc! :isPickable pickable?))))

(defn plane [name & {:keys [position
                            rotation
                            width
                            height
                            billboard-mode
                            visibility
                            subdivisions
                            scale
                            material
                            double-side?
                            type
                            pickable?
                            do-not-sync-bounding-info?]
                     :or {type :plane
                          do-not-sync-bounding-info? true
                          pickable? false
                          double-side? false}}]
  (let [p (j/call MeshBuilder :CreatePlane name #js {:width width
                                                     :height height
                                                     :sideOrientation (if double-side?
                                                                        api.const/mesh-double-side
                                                                        api.const/mesh-default-side)})]
    (m/cond-doto p
      billboard-mode (j/assoc! :billboardMode billboard-mode)
      visibility (j/assoc! :visibility visibility)
      position (j/assoc! :position position)
      subdivisions (j/assoc! :subdivisions subdivisions)
      rotation (j/assoc! :rotation rotation)
      scale (j/assoc! :scaling scale)
      material (j/assoc! :material material)
      (some? pickable?) (j/assoc! :isPickable pickable?)
      (some? do-not-sync-bounding-info?) (j/assoc! :doNotSyncBoundingInfo do-not-sync-bounding-info?))))

(defn create-ground [{:keys [name
                             width
                             height
                             material
                             check-collisions?
                             receive-shadows?
                             pickable?
                             enabled?]
                      :or {pickable? false}}]
  (let [ground (j/call MeshBuilder :CreateGround name #js {:width width
                                                           :height height})]
    (cond-> ground
      material (j/assoc! :material material)
      (some? enabled?) (j/call :setEnabled enabled?)
      (some? check-collisions?) (j/assoc! :checkCollisions check-collisions?)
      (some? receive-shadows?) (j/assoc! :receiveShadows receive-shadows?)
      (some? pickable?) (j/assoc! :isPickable pickable?))))

(defn line [name & {:keys [points]}]
  (j/call MeshBuilder :CreateLines name (clj->js {:points points})))

(defn text [{:keys [name
                    text
                    font-data
                    resolution
                    depth
                    size
                    visibility
                    position
                    rotation
                    scale
                    color
                    emissive-color
                    emissive-intensity
                    metallic
                    roughness
                    alpha
                    mat
                    mat-type
                    face-to-screen?
                    hl-blur
                    pickable?]
             :or {size 1
                  pickable? false
                  depth 0.0001
                  resolution 1
                  mat (api.material/standard-mat (str name "-mat"))
                  mat-type :pbr
                  hl-blur 1.0}}]
  (let [mesh (j/call MeshBuilder :CreateText name
                     text
                     font-data
                     #js {:size size
                          :resolution resolution
                          :depth 0.01
                          :sideOrientation api.const/mesh-default-side}
                     nil
                     earcut)]
    (j/assoc! mesh :text text)
    (when (= mat-type :pbr)
      (cond-> mat
        true (j/assoc! :reflectivityColor (api.const/color-black))
        ;; We're using the albedo color as the main color and emissive color as the brightness
        color (j/assoc! :albedoColor color)
        color (j/assoc! :emissiveColor color)
        emissive-color (j/assoc! :emissiveColor emissive-color)
        emissive-intensity (j/assoc! :emissiveIntensity emissive-intensity)
        roughness (j/assoc! :roughness roughness)
        metallic (j/assoc! :metallic metallic)
        alpha (j/assoc! :alpha alpha)))
    (cond-> mesh
      face-to-screen? (j/assoc! :billboardMode api.const/mesh-billboard-mode-all)
      mat (j/assoc! :material mat)
      visibility (j/assoc! :visibility visibility)
      position (j/assoc! :position position)
      rotation (j/assoc! :rotation rotation)
      scale (j/assoc! :scaling scale)
      (some? pickable?) (j/assoc! :isPickable pickable?))))

(defn- get-points-from-text [& {:keys [text size resolution font]}]
  (j/call GreasedLineTools :GetPointsFromText text size resolution font))

(defn greased-line [name & {:keys [text
                                   position
                                   rotation
                                   visibility
                                   size
                                   resolution
                                   font
                                   material-type
                                   color-mode
                                   width
                                   color]
                            :or {width 0.1
                                 size 1
                                 resolution 4
                                 ;; font font/droid
                                 color (api.const/color-white)}
                            :as opts}]
  (let [points (get-points-from-text :text text
                                     :size size
                                     :resolution resolution
                                     :font font)
        gl (CreateGreasedLine. name
                               #js {:points points}
                               #js {:color color
                                    :colorMode color-mode
                                    :materialType material-type
                                    :width width}
                               (api.core/get-scene))]
    (m/cond-doto gl
      position (j/assoc! :position position)
      rotation (j/assoc! :rotation rotation)
      visibility (j/assoc! :visibility visibility))))

(defn create-sky-box []
  (let [skybox (box {:name "skyBox"
                     :size 5000.0})
        mat (api.material/standard-mat {:name "skyBox"
                                        :back-face-culling? false
                                        :reflection-texture (CubeTexture. "" nil nil nil #js ["img/skybox/px.jpeg"
                                                                                              "img/skybox/py.jpeg"
                                                                                              "img/skybox/pz.jpeg"
                                                                                              "img/skybox/nx.jpeg"
                                                                                              "img/skybox/ny.jpeg"
                                                                                              "img/skybox/nz.jpeg"])
                                        :coordinates-mode :SKYBOX_MODE
                                        :diffuse-color (api.core/color 0 0 0)
                                        :specular-color (api.core/color 0 0 0)
                                        :disable-lighting? true})]
    (j/assoc! skybox :material mat)
    (api.material/freeze mat)
    skybox))

(defn create-health-bar [parent]
  (let [plane (plane "health-bar" {:width 1.2
                                   :height 0.1})]
    (j/assoc! plane
              :parent parent
              :material nil)
    (j/assoc-in! plane [:position :y] -1.93)
    (j/assoc! plane :billboardMode api.const/mesh-billboard-mode-all)
    (let [advanced-texture (gui/create-for-mesh plane)
          background (gui/rectangle "background"
                                    :width "100%"
                                    :height "100%"
                                    :color "black"
                                    :background "black"
                                    :thickness 0)]
      (gui/add-control advanced-texture background)
      (let [health-bar (gui/rectangle "healthBar"
                                      :width "100%"
                                      :height "100%"
                                      :color "red"
                                      :background "red"
                                      :thickness 0)]
        (j/assoc! health-bar :paddingLeft 10)
        (j/assoc! health-bar :paddingRight 10)
        (j/assoc! health-bar :paddingTop 50)
        (j/assoc! health-bar :paddingBottom 50)
        (j/assoc! health-bar :horizontalAlignment api.const/gui-horizontal-align-left)
        (j/assoc! health-bar :plane plane)
        (gui/add-control advanced-texture health-bar)
        (j/assoc! health-bar :width 0.995)
        (j/assoc! parent :health-bar health-bar)
        health-bar))))

(defn update-health-bar [health-bar health-percentage]
  (j/assoc! health-bar :width (* health-percentage 0.995)))

#_(reg-mesh
    :mesh/ter
    {:url "castle_meshes.glb"
     :preload? true
     :on-success (fn [mesh]
                   (j/assoc! mesh :name "Root_Map")
                   (api.core/set-enabled mesh false)
                   (let [box (box {:name "map_trigger"
                                   :size 110
                                   :visible? false})
                         _ (j/assoc! box :position (v3 25 11 -25))
                         p-shape (api.physics/physics-shape {:mesh box
                                                             :trigger? true})
                         body (api.physics/physics-body {:tn box
                                                         :motion-type :PhysicsMotionType/STATIC
                                                         :shape p-shape})])

                   (doseq [m (api.core/get-child-meshes mesh)]
                     (when (or (str/includes? (str/lower-case (j/get m :name)) "collider")
                               (str/includes? (j/get m :name) "coll_wall"))
                       (api.core/dispose m)))

                   (let [root (api.core/transform-node {:name "Root_Castles"})
                         floor (api.core/get-mesh-by-name "Floor")
                         floor-mat (j/get floor :material)
                         grouped-meshes (group-by
                                          (fn [m]
                                            (cons (j/get-in m [:material :name]) (js->clj (j/call m :getVerticesDataKinds))))
                                          (api.core/get-child-meshes mesh))]
                     (j/call floor :setParent root)
                     (j/assoc-in! floor-mat [:albedoTexture :uScale] 0.1)
                     (j/assoc-in! floor-mat [:albedoTexture :vScale] 0.1)
                     (j/assoc-in! floor-mat [:bumpTexture :uScale] 0.1)
                     (j/assoc-in! floor-mat [:bumpTexture :vScale] 0.1)
                     (j/assoc! floor-mat :lightmapTexture (j/get floor-mat :albedoTexture))
                     (api.material/freeze floor-mat)
                     (api.physics/physics-agg {:mesh floor
                                               :type :PhysicsShapeType/BOX
                                               :mass 0
                                               :motion-type :PhysicsMotionType/STATIC})
                     (doseq [[_ meshes] grouped-meshes]
                       (let [meshes (remove (fn [m] (= (j/get m :name) "Floor")) meshes)
                             m (api.core/merge-meshes {:name "merged meshes"
                                                       :meshes (to-array meshes)
                                                       :allow-32-bits-indices? true})
                             material (j/get m :material)]
                         (when m
                           (api.physics/physics-agg {:mesh m
                                                     :type :PhysicsShapeType/MESH
                                                     :mass 0
                                                     :motion-type :PhysicsMotionType/STATIC})
                           (j/call m :freezeWorldMatrix)
                           (j/assoc! m
                                     :doNotSyncBoundingInfo true
                                     :isPickable false
                                     :parent root)
                           (m/assoc! material
                                     :lightmapTexture (j/get material :albedoTexture)
                                     :sheen.isEnabled true
                                     :sheen.intensity 0.5)
                           (api.material/freeze material)))))

                   (api.core/dispose mesh))})
