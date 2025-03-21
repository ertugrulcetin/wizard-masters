(ns main.scene.settings
  (:require
    [applied-science.js-interop :as j]
    [main.api.core :as api.core]
    [main.api.sound :as api.sound]
    [main.rule-engine :as re]
    [main.utils :as utils]))

(def default
  {:music-volume 0.5
   :sfx-distance-cutoff 50
   :quality -0.39
   :anti-alias? false
   :mouse-sensitivity 0.5
   :mouse-zoom-sensitivity 0.25
   :invert-mouse? false
   :game-mode :solo-death-match})

(defn get-setting [key]
  (key (re/query :settings)))

(defn- apply-setting* [key value]
  (case key
    :music-volume (api.sound/set-global-volume value)
    :sfx-distance-cutoff (api.sound/set-sfx-distance-cutoff value)
    :quality (j/call (api.core/get-engine) :setHardwareScalingLevel (- 1 value))
    :anti-alias? (if value
                   (j/assoc! (re/query :settings/dp)
                             :fxaaEnabled true
                             :samples 4)
                   (j/assoc! (re/query :settings/dp)
                             :fxaaEnabled false
                             :samples 1))
    :mouse-sensitivity (let [value (* (- 1 value) 1000)
                             invert? (boolean (get-setting :invert-mouse?))]
                         (j/assoc! (re/query :camera)
                                   :angularSensibilityX value
                                   :angularSensibilityY (if invert? (* -1 (Math/abs value)) value)))
    :invert-mouse? (j/update! (re/query :camera) :angularSensibilityY #(* (Math/abs %) (if value -1 1)))
    nil))

(defn- persist-setting [key value]
  (re/upsert :settings #(assoc % key value))
  (utils/update-item :settings #(assoc % key value)))

(defn apply-setting [key value]
  (persist-setting key value)
  (apply-setting* key value))

(defn override-defaults [mobile?]
  (utils/set-item :settings (if mobile?
                              (assoc default :mouse-sensitivity 0.9
                                     :mouse-zoom-sensitivity 0.5)
                              default))
  (utils/get-item :settings))

(defn init-settings [mobile?]
  (try
    (let [settings (or (utils/get-item :settings)
                       (override-defaults mobile?))]
      (doseq [[k v] settings]
        ;; Due to duplication
        (if (= k :sfx-distance-cutoff)
          (persist-setting k v)
          (apply-setting k v))))
    (catch js/Error e
      (let [settings (override-defaults mobile?)]
        (doseq [[k v] settings]
          ;; Due to duplication
          (if (= k :sfx-distance-cutoff)
            (persist-setting k v)
            (apply-setting k v))))
      (js/console.error e))))
