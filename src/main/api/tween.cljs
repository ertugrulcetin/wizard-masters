(ns main.api.tween
  (:refer-clojure :exclude [update])
  (:require
    ["@tweenjs/tween.js" :as TWEEN :refer [Tween]]
    [applied-science.js-interop :as j]
    [main.api.core :as api.core :refer [v3]])
  (:require-macros
    [main.macros :as m]))

(defn- get-easing [easing]
  (case easing
    :quadratic/in-out (m/get TWEEN :Easing :Quadratic :InOut)
    :bounce/in (m/get TWEEN :Easing :Bounce :In)
    :bounce/out (m/get TWEEN :Easing :Bounce :Out)
    :bounce/in-out (m/get TWEEN :Easing :Bounce :InOut)
    :back/in (m/get TWEEN :Easing :Back :In)
    :back/out (m/get TWEEN :Easing :Back :Out)
    :back/in-out (m/get TWEEN :Easing :Back :InOut)
    :elastic/in (m/get TWEEN :Easing :Elastic :In)
    :elastic/out (m/get TWEEN :Easing :Elastic :Out)
    :elastic/in-out (m/get TWEEN :Easing :Elastic :InOut)))

(defn- shortest-path-angle [from-angle to-angle]
  (let [delta (- to-angle from-angle)
        pi Math/PI
        two-pi (* 2 pi)]
    (- (mod (+ delta pi) two-pi) pi)))

(defn- shortest-path-rotation [from to]
  {:x (+ (:x from) (shortest-path-angle (:x from) (:x to)))
   :y (+ (:y from) (shortest-path-angle (:y from) (:y to)))
   :z (+ (:z from) (shortest-path-angle (:z from) (:z to)))})

(defn tween [{:keys [target
                     from
                     to
                     duration
                     easing
                     on-update
                     on-start
                     on-end
                     start?
                     delay]
              :or {start? true
                   duration 1000}}]
  (let [tween (new Tween (if (map? from)
                           (clj->js from)
                           from))
        [obj attr] target
        to (if (and (= attr :rotation)
                    (map? from)
                    (map? to))
             (shortest-path-rotation from to)
             to)
        temp-v3 (v3)]
    (m/cond-doto tween
      to (j/call :to (if (map? to)
                       (clj->js to)
                       to) duration)
      easing (j/call :easing (get-easing easing))
      (and (not target) on-update) (j/call :onUpdate on-update)
      (or (and target on-update)
          target) (j/call :onUpdate (fn [v]
                                      (when (or (= attr :position)
                                                (= attr :rotation)
                                                (= attr :scaling))
                                        (j/assoc! obj attr (api.core/set-v3 temp-v3
                                                                            (j/get v :x)
                                                                            (j/get v :y)
                                                                            (j/get v :z))))
                                      (when on-update (on-update v))))
      on-end (j/call :onComplete on-end)
      on-start (j/call :onStart on-start))
    (when start?
      (when delay
        (j/call tween :delay delay))
      (j/call tween :start))
    tween))

(defn update []
  ((j/get TWEEN :update)))

(defn stop [tw]
  (j/call tw :stop))
