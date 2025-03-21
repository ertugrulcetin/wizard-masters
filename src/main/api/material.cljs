(ns main.api.material
  (:require
    ["@babylonjs/core/Materials/Background/backgroundMaterial" :refer [BackgroundMaterial]]
    ["@babylonjs/core/Materials/Node"]
    ["@babylonjs/core/Materials/Node/nodeMaterial" :refer [NodeMaterial]]
    ["@babylonjs/core/Materials/PBR/pbrMaterial" :refer [PBRMaterial]]
    ["@babylonjs/core/Materials/PBR/pbrMetallicRoughnessMaterial" :refer [PBRMetallicRoughnessMaterial]]
    ["@babylonjs/core/Materials/Textures/texture" :refer [Texture]]
    ["@babylonjs/core/Materials/effect" :refer [Effect]]
    ["@babylonjs/core/Materials/shaderMaterial" :refer [ShaderMaterial]]
    ["@babylonjs/core/Materials/standardMaterial" :refer [StandardMaterial]]
    ["@babylonjs/materials/grid/gridMaterial" :refer [GridMaterial]]
    [applied-science.js-interop :as j]
    [main.api.asset :as api.asset]
    [main.api.core :as api.core])
  (:require-macros
    [main.macros :as m]))

(defn standard-mat [{:keys [name
                            alpha
                            diffuse-texture
                            diffuse-texture-has-alpha?
                            use-alpha-from-diffuse-texture?
                            specular-texture
                            emissive-texture
                            emissive-color
                            bump-texture
                            opacity-texture
                            lightmap-texture
                            diffuse-color
                            specular-color
                            back-face-culling?
                            reflection-texture
                            coordinates-mode
                            disable-lighting?
                            get-alpha-from-rgb?]}]
  (let [sm (StandardMaterial. name)]
    (cond-> sm
      alpha (j/assoc! :alpha alpha)
      diffuse-texture (j/assoc! :diffuseTexture diffuse-texture)
      (some? diffuse-texture-has-alpha?) (j/assoc-in! [:diffuseTexture :hasAlpha] diffuse-texture-has-alpha?)
      specular-texture (j/assoc! :specularTexture specular-texture)
      emissive-texture (j/assoc! :emissiveTexture emissive-texture)
      bump-texture (j/assoc! :bumpTexture bump-texture)
      opacity-texture (j/assoc! :opacityTexture opacity-texture)
      get-alpha-from-rgb? (j/assoc-in! [:opacityTexture :getAlphaFromRGB] get-alpha-from-rgb?)
      specular-color (j/assoc! :specularColor specular-color)
      lightmap-texture (j/assoc! :lightmapTexture lightmap-texture)
      (some? back-face-culling?) (j/assoc! :backFaceCulling back-face-culling?)
      reflection-texture (j/assoc! :reflectionTexture reflection-texture)
      coordinates-mode (j/assoc-in! [:reflectionTexture :coordinatesMode] (j/get Texture coordinates-mode))
      (some? disable-lighting?) (j/assoc! :disableLighting disable-lighting?)
      (some? use-alpha-from-diffuse-texture?) (j/assoc! :useAlphaFromDiffuseTexture use-alpha-from-diffuse-texture?)
      diffuse-color (j/assoc! :diffuseColor diffuse-color)
      emissive-color (j/assoc! :emissiveColor emissive-color))))

(defn grid-mat [name & {:keys [major-unit-frequency
                               minor-unit-visibility
                               grid-ratio
                               back-face-culling?
                               main-color
                               line-color
                               opacity]
                        :as opts}]
  (let [gm (GridMaterial. name)]
    (m/cond-doto gm
      major-unit-frequency (j/assoc! :majorUnitFrequency major-unit-frequency)
      minor-unit-visibility (j/assoc! :minorUnitVisibility minor-unit-visibility)
      grid-ratio (j/assoc! :gridRatio grid-ratio)
      (some? back-face-culling?) (j/assoc! :backFaceCulling back-face-culling?)
      main-color (j/assoc! :mainColor main-color)
      line-color (j/assoc! :lineColor line-color)
      opacity (j/assoc! :opacity opacity))))

(defn shader-mat [{:keys [name
                          fragment
                          vertex
                          attrs
                          uniforms]
                   :as opts}]
  (j/assoc-in! Effect [:ShadersStore (str name "VertexShader")] vertex)
  (j/assoc-in! Effect [:ShadersStore (str name "FragmentShader")] fragment)
  (ShaderMaterial. name nil #js{:vertex name
                                :fragment name}
                   (clj->js {:attributes attrs
                             :uniforms uniforms})))

(defn background-mat [{:keys [name
                              reflection-texture
                              diffuse-texture
                              has-alpha?
                              alpha
                              opacity-fresnel?
                              primary-color
                              shadow-level]
                       :or {shadow-level 0.4}}]
  (let [bm (BackgroundMaterial. name)]
    (m/cond-doto bm
      reflection-texture (j/assoc! :reflectionTexture reflection-texture)
      diffuse-texture (j/assoc! :diffuseTexture diffuse-texture)
      alpha (j/assoc! :alpha alpha)
      (some? opacity-fresnel?) (j/assoc! :opacityFresnel opacity-fresnel?)
      (some? has-alpha?) (j/assoc-in! [:diffuseTexture :hasAlpha] has-alpha?)
      primary-color (j/assoc! :primaryColor primary-color)
      shadow-level (j/assoc! :shadowLevel shadow-level))))

(defn pbr-mat [{:keys [name
                       alpha
                       metallic
                       roughness
                       reflection-texture
                       emissive-color
                       emissive-intensity
                       albedo-texture
                       albedo-color
                       lightmap-texture
                       sheen?
                       sheen-intensity
                       bump-texture]}]
  (let [pbr (PBRMaterial. name)]
    (m/cond-doto pbr
      alpha (j/assoc! :alpha alpha)
      emissive-color (j/assoc! :emissiveColor emissive-color)
      emissive-intensity (j/assoc! :emissiveIntensity emissive-intensity)
      reflection-texture (j/assoc! :reflectionTexture reflection-texture)
      albedo-texture (j/assoc! :albedoTexture albedo-texture)
      albedo-color (j/assoc! :albedoColor albedo-color)
      bump-texture (j/assoc! :bumpTexture bump-texture)
      lightmap-texture (j/assoc! :lightmapTexture lightmap-texture)
      (some? sheen?) (j/assoc-in! [:sheen :isEnabled] sheen?)
      sheen-intensity (j/assoc-in! [:sheen :intensity] sheen-intensity)
      metallic (j/assoc! :metallic metallic)
      roughness (j/assoc! :roughness roughness))))

(defn pbr-metallic-roughness-mat [name & {:keys [base-color
                                                 metallic
                                                 roughness
                                                 environment-texture]
                                          :as opts}]
  (let [pbr (PBRMetallicRoughnessMaterial. name)]
    (m/cond-doto pbr
      base-color (j/assoc! :baseColor base-color)
      metallic (j/assoc! :metallic metallic)
      roughness (j/assoc! :roughness roughness)
      environment-texture (j/assoc! :environmentTexture environment-texture))))

(defn- replace-old-material [id new-mat]
  (doseq [mesh (api.core/get-all-meshes)
          :let [mesh-material (j/get mesh :material)]
          :when mesh-material]
    (when (= (api.asset/get-asset id) mesh-material)
      (j/assoc! mesh :material new-mat))))

(defn reg-mat [id {:keys [f] :as opts}]
  (let [mat-name (name id)
        opts (assoc opts :id id
                    :name mat-name
                    :type :material)]
    (when (api.core/get-scene)
      (when (api.asset/get-asset id)
        (let [new-mat (j/assoc! (f) :name (name id))]
          (replace-old-material id new-mat)
          (api.core/dispose (api.asset/get-asset id))
          (j/assoc-in! api.core/db [:assets id] new-mat))))
    (j/assoc-in! api.core/db [:assets-regs id] opts)
    opts))

(defn register-material [id mat]
  (j/assoc-in! api.core/db [:assets id] mat))

(defn init-materials []
  (doseq [{:keys [id f]} (->> (js/Object.values (j/get api.core/db :assets-regs))
                              (filter #(= (:type %) :material))
                              (sort-by :preload? >))
          :let [material (f)]]
    (if (j/get material :then)
      (j/call material :then (fn [mat]
                               (register-material id (j/assoc! mat :name (name id)))))
      (register-material id (j/assoc! material :name (name id))))))

(defn freeze [mat]
  (j/call mat :freeze))

(defn unfreeze [mat]
  (j/call mat :unfreeze))

(defn parse-from-snippet [snipped-id on-load]
  (-> (j/call NodeMaterial :ParseFromSnippetAsync snipped-id)
      (.then on-load)))

(defn parse-from-file-async
  ([name url]
   (j/call NodeMaterial :ParseFromFileAsync name url (api.core/get-scene)))
  ([name url on-load]
   (-> (j/call NodeMaterial :ParseFromFileAsync name url (api.core/get-scene))
       (.then on-load))))

(reg-mat
  :material/kingdom
  {:f (fn []
        (let [albedo-tex (api.asset/get-asset :texture/kingdom-1-a)
              bump-texture (api.asset/get-asset :texture/kingdom-1-a-normal)
              mat (standard-mat
                    {:diffuse-texture albedo-tex
                     :lightmap-texture albedo-tex
                     :bump-texture bump-texture
                     :roughness 1.0})]
          (freeze mat)
          mat))})

(reg-mat
  :material/hero-blue
  {:f (fn []
        (let [albedo-tex (api.asset/get-asset :texture/fantasy-hero-blue)
              mat (pbr-mat
                    {:name "hero-blue"
                     :albedo-texture albedo-tex
                     :lightmap-texture albedo-tex
                     :emissive-intensity 1.0
                     :roughness 1.0})]
          (freeze mat)
          mat))})

(reg-mat
  :material/hero-red
  {:f (fn []
        (let [albedo-tex (api.asset/get-asset :texture/fantasy-hero-red)
              mat (pbr-mat
                    {:name "hero-red"
                     :albedo-texture albedo-tex
                     :lightmap-texture albedo-tex
                     :emissive-intensity 1.0
                     :roughness 1.0})]
          (freeze mat)
          mat))})

(reg-mat
  :material/hero-no-team
  {:f (fn []
        (let [albedo-tex (api.asset/get-asset :texture/fantasy-hero-no-team)
              mat (pbr-mat
                    {:name "hero-no-team"
                     :albedo-texture albedo-tex
                     :lightmap-texture albedo-tex
                     :emissive-intensity 1.0
                     :roughness 1.0})]
          (freeze mat)
          mat))})

(reg-mat
  :material/fire-nova
  {:f (fn []
        (parse-from-file-async "fire-nova" "node_materials/fire-nova.json"))})

(reg-mat
  :material/fire-nova-disallowed
  {:f (fn []
        (parse-from-file-async "fire-nova-disallowed" "node_materials/fire-nova-disallowed.json"))})

(reg-mat
  :material/toxic-cloud
  {:f (fn []
        (parse-from-file-async "fire-nova-disallowed" "node_materials/toxic-cloud.json"))})

(reg-mat
  :material/ice-tornado
  {:f (fn []
        (standard-mat {:name "ice-tornado-mat"
                       :diffuse-texture (main.api.asset/get-asset :texture/ice-wind)
                       :back-face-culling? false
                       :diffuse-texture-has-alpha? true
                       :use-alpha-from-diffuse-texture? true
                       :specular-color (api.core/color-rgb 0)
                       :diffuse-color (api.core/color-rgb 102 158 173 255)
                       :emissive-color (api.core/color-rgb 78 117 163 255)}))})

(reg-mat
  :material/arrow-trail
  {:f (fn []
        (standard-mat {:name "arrow-trail-material"
                       :back-face-culling? false
                       :alpha 0.5
                       :emissive-color (api.core/color-rgb 179 204 255 255)
                       :diffuse-color (api.core/color 0)
                       :specular-color (api.core/color 0)}))})

(reg-mat
  :material/light-strike-cylinder
  {:f (fn []
        (standard-mat {:name "light-strike-cylinder"
                       :alpha 0.5
                       :disable-lighting? true
                       :emissive-color (api.core/color-rgb 255 255 255 255)
                       :diffuse-color (api.core/color 0)
                       :specular-color (api.core/color 0)}))})

(reg-mat
  :material/light-strike-cylinder-disallowed
  {:f (fn []
        (standard-mat {:name "light-strike-cylinder-disallowed"
                       :alpha 0.5
                       :disable-lighting? true
                       :emissive-color (api.core/color-rgb 255 0 0 255)
                       :diffuse-color (api.core/color 0)
                       :specular-color (api.core/color 0)}))})

(reg-mat
  :material/tornado
  {:f (fn []
        (standard-mat {:name "tornado"
                       :alpha 0.5
                       :back-face-culling? false
                       :disable-lighting? true
                       :emissive-color (api.core/color-rgb 255 255 255 255)}))})

(reg-mat
  :material/tornado-disallowed
  {:f (fn []
        (standard-mat {:name "tornado"
                       :alpha 0.5
                       :back-face-culling? false
                       :disable-lighting? true
                       :emissive-color (api.core/color-rgb 255 0 0 255)}))})

(reg-mat
  :material/wind-tornado-mat
  {:f (fn []
        (standard-mat {:name "wind-tornado-mat"
                       :diffuse-texture (main.api.asset/get-asset :texture/ice-wind)
                       :back-face-culling? false
                       :diffuse-texture-has-alpha? true
                       :use-alpha-from-diffuse-texture? true
                       :specular-color (api.core/color-rgb 0)
                       :diffuse-color (api.core/color-rgb 255 255 255 255)
                       :emissive-color (api.core/color-rgb 255 255 255 255)}))})

(reg-mat
  :material/map-mana-regen-box
  {:f (fn []
        (standard-mat {:name "mana-regen-box"
                       :alpha 0.5
                       :disable-lighting? true
                       :emissive-color (api.core/color-rgb 112 192 230 255)}))})
