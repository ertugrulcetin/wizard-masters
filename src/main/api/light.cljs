(ns main.api.light
  (:require
    ["@babylonjs/core/Lights/Shadows/shadowGenerator" :refer [ShadowGenerator]]
    ["@babylonjs/core/Lights/directionalLight" :refer [DirectionalLight]]
    ["@babylonjs/core/Lights/hemisphericLight" :refer [HemisphericLight]]
    ["@babylonjs/core/Maths/math" :refer [Vector2 Vector3 Vector4]]
    [applied-science.js-interop :as j]
    [main.api.core :as api.core :refer [v3]]))

(defn directional-light [{:keys [name direction position intensity]
                          :or {direction (v3 0 -1 0)
                               intensity 1}}]
  (let [light (DirectionalLight. name direction)]
    (j/assoc! light
              :position position
              :intensity intensity)))

(defn hemispheric-light [{:keys [name direction position]
                          :or {direction (v3 0 1 0)}}]
  (let [light (HemisphericLight. name direction)]
    (j/assoc! light :position position)))

(defn shadow-generator [{:keys [map-size light]
                         :or {map-size 1024}}]
  (js/console.log light)
  (js/console.log map-size)
  (ShadowGenerator. map-size light))

(defn add-shadow-caster [shadow-generator mesh]
  (j/call shadow-generator :addShadowCaster mesh))
