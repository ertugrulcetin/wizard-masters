(ns main.common
  (:require
    [applied-science.js-interop :as j]
    [main.api.core :as api.core :refer [v3]]
    [main.api.gui :as api.gui]
    [main.api.mesh :as api.mesh]
    [main.api.tween :as api.tween]
    [main.rule-engine :as re]
    [main.utils :as utils]))

(def new-game-after-milli-secs 7500)

(defn show-got-hit-effect [material-name]
  (let [material (api.core/get-material-by-name material-name)]
    (api.tween/tween {:from {:x 0}
                      :to {:x 1}
                      :duration 100
                      :on-update (fn []
                                   (j/assoc! material :emissiveColor (api.core/color-rgb 160 5 5)))
                      :on-end (fn []
                                (j/assoc! material :emissiveColor (api.core/color-rgb 0 0 0)))})))

(defn show-hit-number [{:keys [pos
                               value
                               link-mesh
                               times
                               duration-factor
                               color]
                        :or {color "white"
                             times 1}}]
  (dotimes [i times]
    (let [hit-text-block-sphere (re/pop-from-pool  :pool/hit-text-block-spheres)
          available-from-pool? (some? hit-text-block-sphere)
          damage value
          sp (or hit-text-block-sphere (api.mesh/sphere {:name "hit-text-sphere" :visible? false}))
          tb (or (j/get sp :text-block) (api.gui/text-block {:name "hit-text"
                                                             :text damage
                                                             :font-family "GROBOLD"
                                                             :font-size 48
                                                             :color color
                                                             :outline-width 2
                                                             :outline-color "black"}))
          duration-factor (or duration-factor 5)]
      (if available-from-pool?
        (let [tb (j/get sp :text-block)]
          (j/assoc! tb
                    :text damage
                    :color color))
        (let [gui (api.core/get-advanced-texture)]
          (j/assoc! sp
                    :text-block tb
                    :temp-pos (v3))
          (api.gui/add-control gui tb)
          (api.gui/link-with-mesh tb (or link-mesh sp))))
      (when-not link-mesh
        (j/assoc! sp :position (api.core/set-v3 (j/get sp :temp-pos) pos)))
      (j/assoc! tb :alpha 1)
      (api.tween/tween
        {:delay (* (inc i) (* 10 duration-factor))
         :from {:scale 0}
         :to {:scale 2}
         :easing :back/out
         :duration (* 100 duration-factor)
         :on-update (fn [v]
                      (j/assoc! tb :scaleX (j/get v :scale)
                                :scaleY (j/get v :scale)))})
      (api.tween/tween
        (let [init 50
              x (if (> (rand) 0.5) init (- init))
              y (if (> (rand) 0.5) init (- init))]
          {:delay (* i (* 10 duration-factor))
           :from {:x x
                  :y y}
           :to {:x (* (* 2 x) (+ 1 (rand)))
                :y (* (* 2 y) (+ 1 (rand)))}
           :duration (* 100 duration-factor)
           :on-update (fn [v]
                        (j/assoc! tb :linkOffsetX (j/get v :x))
                        (j/assoc! tb :linkOffsetY (j/get v :y)))
           :on-end (fn []
                     (api.tween/tween {:from {:alpha 1}
                                       :to {:alpha 0}
                                       :delay (* 100 duration-factor)
                                       :duration (* 100 duration-factor)
                                       :on-update (fn [v]
                                                    (j/assoc! tb :alpha (j/get v :alpha)))
                                       :on-end #(re/push-to-pool  :pool/hit-text-block-spheres sp)}))})))))
