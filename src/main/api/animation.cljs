(ns main.api.animation
  (:require
    ["@babylonjs/core/Animations/animatable"]
    ["@babylonjs/core/Animations/animation" :refer [Animation]]
    ["@babylonjs/core/Animations/animationGroup" :refer [AnimationGroup]]
    ["@babylonjs/core/Animations/easing" :refer [CubicEase EasingFunction]]
    [applied-science.js-interop :as j]
    [cljs.core.async :as a]
    [clojure.string :as str]
    [main.api.constant :as api.const]
    [main.api.core :as api.core :refer [v3]]
    [main.rule-engine :as rule-engine])
  (:require-macros
    [main.macros :as m]))

(def default-fps 60)

(defn start [{:keys [anim-group
                     loop?
                     speed
                     from
                     to
                     blending-speed
                     additive?]
              :or {loop? false
                   additive? false}}]
  (when blending-speed
    (j/assoc! anim-group :blendingSpeed (* blending-speed (api.core/get-anim-ratio))))
  (j/call anim-group :start
          loop?
          (or speed 1.0)
          (or from (j/get anim-group :from))
          (or to (j/get anim-group :to))
          additive?))

(defn play
  ([anim-group]
   (play anim-group false))
  ([anim-group loop?]
   (j/call anim-group :play loop?)))

(defn pause [anim-group]
  (j/call anim-group :pause))

(defn reset [anim-group]
  (j/call anim-group :reset))

(defn restart [anim-group]
  (j/call anim-group :restart))

(defn stop [anim-group]
  (j/call anim-group :stop))

(defn normalize [anim-group begin end]
  (j/call anim-group :normalize begin end))

(defn enable-blending [anim-group enable?]
  (j/assoc! anim-group :enableBlending enable?))

(defn playing? [anim-group]
  (j/get anim-group :isPlaying))

(defn get-playing-anim-groups [anim-groups]
  (filter playing? anim-groups))

(defn get-first-get-playing-anim-group-name [anim-groups]
  (some-> (get-playing-anim-groups anim-groups)
          first
          (j/get :name)))

(defn stop-all [anim-groups]
  (doseq [ag anim-groups]
    (stop ag)))

(defn find-and-play-anim-group [name]
  (play (j/call-in api.core/db [:scene :getAnimationGroupByName] name)))

(defn find-animation-group [name animation-groups]
  (some #(when (= name (j/get % :name)) %) animation-groups))

(defn add-on-anim-group-end [anim-group f]
  (j/call-in anim-group [:onAnimationGroupEndObservable :add] f))

(defn lerp [{:keys [start
                    end
                    on-lerp
                    on-end
                    speed]
             :or {speed 1}}]
  (let [p (a/promise-chan)
        elapsed-time (atom 0)
        _ (api.core/register-on-before-render
            (fn lerp-fn []
              (let [elapsed-time (swap! elapsed-time + (api.core/get-delta-time))
                    amount (min (* speed elapsed-time) 1)
                    new-val (api.core/lerp start end amount)]
                (when on-lerp (on-lerp new-val))
                (when (= amount 1)
                  (when on-end (on-end))
                  (a/put! p true)
                  (api.core/remove-on-before-render lerp-fn)))))]
    {:ch p}))

(defn run-dur-fn [{:keys [duration f on-end]
                   :or {duration 1}}]
  (let [elapsed-time (atom 0)
        on-before-render-fn (fn run-fn []
                              (let [elapsed-time (swap! elapsed-time + (api.core/get-delta-time))]
                                (if (>= elapsed-time duration)
                                  (do
                                    (api.core/remove-on-before-render run-fn)
                                    (when on-end (on-end)))
                                  (f #(api.core/remove-on-before-render run-fn)))))]
    (api.core/register-on-before-render on-before-render-fn)))

(defn cubic-ease [mode]
  (doto (CubicEase.)
    (j/call :setEasingMode (j/get EasingFunction mode))))

(defn animation [{:keys [anim-name
                         target-prop
                         delay
                         fps
                         data-type
                         loop-mode
                         easing
                         from
                         to
                         duration
                         keys]
                  :or {duration 1.0}}]
  (let [fps (or fps default-fps)
        keys (or keys (and from to [{:frame 0 :value from}
                                    {:frame (* duration fps) :value to}]))
        anim (Animation. anim-name target-prop fps (j/get Animation data-type) (j/get Animation loop-mode))]
    (m/cond-doto anim
      duration (j/assoc! :duration duration)
      delay (j/assoc! :delay delay)
      easing (j/call :setEasingFunction easing)
      keys (j/call :setKeys (clj->js keys)))))

(defn begin-direct-animation [{:keys [target
                                      animations
                                      from
                                      to
                                      loop?
                                      speed-ratio
                                      on-animation-end]
                               :or {from 0
                                    loop? false
                                    speed-ratio 1.0}}]
  (let [on-animation-end (fn []
                           (when on-animation-end
                             (on-animation-end target)))
        animations (if (vector? animations)
                     animations
                     [animations])]
    (j/call-in api.core/db [:scene :beginDirectAnimation]
               target
               (clj->js animations)
               from
               to
               loop?
               speed-ratio
               on-animation-end)))

(comment
  (api.core/dispose (api.core/get-object-by-name "text-dots"))
  (pcs-text-anim "text-dots"
                 {:type :pcs-text
                  :text "      Welcome to the\n\n\n\n\n\n\n\nFuture of Presentation"
                  :visibility 1
                  :duration 0.0
                  :point-size 5
                  :rand-range [-10 20]
                  :position (v3 -5.5 1 9)
                  :color (api.const/color-white)})
  )
