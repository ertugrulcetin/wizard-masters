(ns main.scene.player.map
  (:require
    [applied-science.js-interop :as j]
    [clojure.string :as str]
    [main.api.asset :as api.asset]
    [main.api.core :as api.core :refer [v3]]
    [main.api.material :as api.material]
    [main.api.mesh :as api.mesh :refer [reg-mesh]]
    [main.api.physics :as api.physics]
    [main.rule-engine :as re]))

(defn- create-map-aggregates [m]
  (api.physics/physics-agg {:mesh m
                            :type :PhysicsShapeType/MESH
                            :mass 0
                            :motion-type :PhysicsMotionType/STATIC}))

(def maps
  {:map/ruins {:json :map/ruins-json}
   :map/arena {:json :map/arena-json}
   :map/temple {:json :map/temple-json
                :scaling (v3 1.25)
                :regen-box {:w 1.25
                            :h 2
                            :d 1.2}}
   :map/towers {:json :map/towers-json
                :on-after (fn []
                            (->> (j/get (api.core/get-scene) :materials)
                                 (filter
                                   (fn [m]
                                     (when-let [name (j/get m :name)]
                                       (str/includes? name "PolygonFantasyKingdom_Mat_Castle_Floor"))))
                                 (map (fn [m]
                                        (api.material/unfreeze m)
                                        (j/assoc! m :sideOrientation 1)
                                        (j/assoc-in! m [:albedoTexture :uScale] 10)
                                        (j/assoc-in! m [:albedoTexture :vScale] 10)
                                        (j/assoc-in! m [:bumpTexture :uScale] 10)
                                        (j/assoc-in! m [:bumpTexture :vScale] 10)
                                        (api.material/freeze m)))
                                 doall))}})

(reg-mesh
  :mesh/all-map-objects
  {:url "map.glb"
   :preload? true
   :on-success (fn [mesh]
                 (api.core/set-enabled mesh false)
                 (j/assoc! mesh :name "All_Map_Objects")
                 (doseq [m (j/get-in mesh [:_children 0 :_children])]
                   (re/upsert :map/objects #(assoc % (j/get m :name) m))))})

(defn- create-map-regen-box-trigger [box]
  (let [p-shape (api.physics/physics-shape {:mesh box
                                            :trigger? true})
        body (api.physics/physics-body {:tn box
                                        :motion-type :PhysicsMotionType/STATIC
                                        :shape p-shape})]
    (j/assoc! box
              :p-shape p-shape
              :p-body body)))

(defn- create-map-regen-box [mesh {:keys [w h d] :as regen-box-dimensions}]
  (some-> (re/query :map/regen-box) api.core/dispose)
  (let [min (atom (v3 js/Number.MAX_VALUE))
        max (atom (v3 (- js/Number.MAX_VALUE)))]
    (doseq [m (api.core/get-child-meshes mesh)]
      (let [b-info (j/call m :getBoundingInfo)
            mesh-min (j/get-in b-info [:boundingBox :minimumWorld])
            mesh-max (j/get-in b-info [:boundingBox :maximumWorld])]
        (reset! min (api.core/v3-minimize @min mesh-min))
        (reset! max (api.core/v3-maximize @max mesh-max))))
    (let [max @max
          min @min
          dimensions (j/call max :subtract min)
          center (-> (j/call min :add max)
                     (j/call :scale 0.5))
          mat (api.asset/get-asset :material/map-mana-regen-box)]
      (let [box (api.mesh/box {:name "trigger-map-regen-box"
                               :width (* (j/get dimensions :x) w)
                               :height (* (j/get dimensions :y) h)
                               :depth (* (j/get dimensions :z) d)
                               :position center
                               :mat mat})]
        (re/insert :map/regen-box box)
        (api.core/freeze-world-matrix box)
        (create-map-regen-box-trigger box)))))

(defn collision-mesh? [m]
  (or (str/includes? (str/lower-case (j/get m :name)) "collision")
      (str/includes? (str/lower-case (j/get m :name)) "collider")
      (str/includes? (str/lower-case (j/get m :name)) "convex")))

(defn- get-found-name [mesh-name available-mesh-names]
  (when-not (str/blank? mesh-name)
    (let [mesh-name (str/trim mesh-name)]
      (cond
        (str/includes? mesh-name "primitive")
        nil

        (available-mesh-names (first (str/split mesh-name #"\s+")))
        (first (str/split mesh-name #"\s+"))

        (available-mesh-names mesh-name)
        mesh-name

        (available-mesh-names (str/join "_" (drop-last (str/split mesh-name "_"))))
        (str/join "_" (drop-last (str/split mesh-name "_")))))))

(defn get-all-nodes-global-transforms [loaded-meshes loaded-transform-nodes]
  (let [available-mesh-names (set (keys (re/query :map/objects)))]
    (reduce
      (fn [acc m]
        (let [name (j/get m :name)
              found-name (get-found-name name available-mesh-names)]
          (if found-name
            (update acc found-name (fn [coll]
                                     (->> (conj (or coll [])
                                                (-> (api.core/get-world-transforms m)
                                                    (dissoc :name)
                                                    (update :position api.core/v3->v)
                                                    (update :rotation api.core/v3->v)
                                                    (update :scaling api.core/v3->v)))
                                          distinct
                                          vec)))
            acc)))
      {}
      (concat loaded-meshes loaded-transform-nodes))))

(defn- get-collectables-and-respawn-positions [mesh]
  (cljs.pprint/pprint
    (reduce (fn [acc m]
              (js/console.log (j/get m :name))
              (cond
                (str/includes? (j/get m :name) "blue_respawn")
                (update acc :blue (fnil conj []) (api.core/v3->v (api.core/get-pos m)))

                (str/includes? (j/get m :name) "red_respawn")
                (update acc :red (fnil conj []) (api.core/v3->v (api.core/get-pos m)))

                (str/includes? (j/get m :name) "speed")
                (update acc :speed (fnil conj []) {:position (api.core/v3->v (api.core/get-pos m))
                                                   :collectables [:speed]})

                (str/includes? (j/get m :name) "hp")
                (update acc :hp (fnil conj []) {:position (api.core/v3->v (api.core/get-pos m))
                                                :collectables [:hp]})

                :else acc))
            {}
            (api.core/get-child-meshes mesh))))

(defn- create-map [map-json new-scaling regen-box-dimensions]
  (let [root (api.core/transform-node {:name "wrapper_map"})]
    (doseq [[name* transforms] map-json]
      (let [name (name name*)]
        (when-let [model-mesh (get (re/query :map/objects) name)]
          (doseq [{:keys [position rotation scaling]} (distinct transforms)]
            (let [cloned (api.core/clone model-mesh {:name name
                                                     :parent root})]
              (j/assoc! cloned
                        :position (api.core/v->v3 position)
                        :rotation (api.core/v->v3 rotation)
                        :scaling (api.core/v->v3 scaling))
              (api.core/set-enabled cloned false))))))

    (let [all-meshes (api.core/get-child-meshes root)
          all-tn-nodes (api.core/get-child-transform-nodes root)
          root-merged-ones (api.core/transform-node {:name "merged_root"})
          _ (j/assoc! root-merged-ones :parent root)
          group-fn (fn [m]
                     (cons (j/get-in m [:material :name]) (js->clj (j/call m :getVerticesDataKinds))))
          grouped-meshes (group-by group-fn (remove collision-mesh? all-meshes))
          grouped-coll-meshes (group-by group-fn (filter collision-mesh? all-meshes))]
      (doseq [[_ meshes] grouped-meshes]
        (let [m (api.core/merge-meshes {:name "merged meshes"
                                        :meshes (to-array meshes)
                                        :allow-32-bits-indices? true})
              material (j/get m :material)]
          (when m
            (j/call m :freezeWorldMatrix)
            (j/assoc! m
                      :scaling new-scaling
                      :alwaysSelectAsActiveMesh true
                      :doNotSyncBoundingInfo true
                      :isPickable false
                      :parent root-merged-ones)
            (j/assoc! material :lightmapTexture (j/get material :albedoTexture))
            (api.material/freeze material))))
      (doseq [[_ meshes] grouped-coll-meshes]
        (let [m (api.core/merge-meshes {:name "collision merged meshes"
                                        :meshes (to-array meshes)
                                        :allow-32-bits-indices? true})
              material (j/get m :material)]
          (when m
            (j/call m :freezeWorldMatrix)
            (j/assoc! m
                      :scaling new-scaling
                      :alwaysSelectAsActiveMesh true
                      :doNotSyncBoundingInfo true
                      :isPickable false
                      :parent root-merged-ones)
            (api.material/freeze material)
            (api.core/set-enabled m false)
            (create-map-aggregates m))))
      (doseq [m (concat all-tn-nodes all-meshes)]
        (api.core/dispose m))
      (create-map-regen-box root-merged-ones regen-box-dimensions))))

(defn load-map [map]
  (let [map-json-key (get-in maps [map :json])
        on-after (get-in maps [map :on-after])
        scaling (get-in maps [map :scaling] (v3 1))
        regen-box-dimensions (get-in maps [map :regen-box] {:w 1.05
                                                            :h 1.25
                                                            :d 1.05})]
    (if (= map (re/query :map/current-map))
      (println "Map already exists in the memory")
      (do
        (some-> (api.core/get-node-by-name "wrapper_map") api.core/dispose)
        (create-map (re/query map-json-key) scaling regen-box-dimensions)
        (when on-after (on-after))
        (re/insert :map/current-map map)))))

(defn hide-current-map []
  (some-> (api.core/get-node-by-name "wrapper_map") (api.core/set-enabled false)))

(defn show-current-map []
  (some-> (api.core/get-node-by-name "wrapper_map") (api.core/set-enabled true)))

(comment
  (api.core/v3->v (api.core/get-pos (re/query :player/capsule)))

  (load-map :map/arena)
  (load-map :map/temple)

  (reg-mesh
    :mesh/get-map-data-as-json
    {:url "temple.glb"
     :preload? true
     :on-success (fn [mesh _ loaded-meshes loaded-transform-nodes]
                   (get-collectables-and-respawn-positions mesh)
                   (-> (get-all-nodes-global-transforms loaded-meshes loaded-transform-nodes)
                       clj->js
                       js/JSON.stringify
                       js/console.log))})
  )
