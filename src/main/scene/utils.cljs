(ns main.scene.utils
  (:require
    [applied-science.js-interop :as j]
    [main.api.core :as api.core]
    [main.rule-engine :as re]))

(defn lock-pointer []
  (when-not (re/query :game/mobile?)
    (let [canvas (api.core/get-canvas)
          request-pointer-lock (or (j/get canvas :requestPointerLock)
                                   (j/get canvas :msRequestPointerLock)
                                   (j/get canvas :mozRequestPointerLock)
                                   (j/get canvas :webkitRequestPointerLock))]
      (when request-pointer-lock
        (j/call canvas :requestPointerLock)))))

(defn exit-pointer-lock []
  (when (and (not (re/query :game/mobile?))
             (j/get js/document :exitPointerLock))
    (j/call js/document :exitPointerLock)))
