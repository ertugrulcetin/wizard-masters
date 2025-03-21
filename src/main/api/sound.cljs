(ns main.api.sound
  (:require
    ["@babylonjs/core/Audio/sound" :refer [Sound]]
    ["@babylonjs/core/Engines/engine" :refer [Engine]]
    [applied-science.js-interop :as j]
    [main.api.asset :as api.asset]
    [main.api.core :as api.core]
    [main.rule-engine :as re]
    [main.utils :as utils])
  (:require-macros
    [main.macros :as m]))

(defn sound [{:keys [name url loop? on-ready autoplay? speed volume pitch spatial? max-distance]
              :or {volume 1.0}}]
  (m/cond-doto (Sound. name
                       url
                       (api.core/get-scene)
                       on-ready
                       #js{:loop loop?
                           :autoplay autoplay?
                           :playbackRate speed
                           :volume volume
                           :spatialSound spatial?
                           :maxDistance max-distance})
    pitch (j/call :setPlaybackRate pitch)))

(defn get-sound-by-name [name]
  (j/call-in api.core/db [:scene :getSoundByName] name))

(defn get-sound [id]
  (if-let [s (-> (api.asset/get-asset id) (j/get :sound))]
    s
    (let [s (sound (j/get-in api.core/db [:assets-regs id]))]
      (j/assoc-in! api.core/db [:assets id :sound] s)
      s)))

(defn init-sounds []
  (->> (js/Object.values (j/get api.core/db :assets-regs))
       (filter #(= (:type %) :sound))
       (remove #(:load-manual? %))
       (sort-by :preload? >)
       (map (comp get-sound :id))
       doall))

(defn play [id & {:keys [time offset length position volume]}]
  (let [s (get-sound id)]
    (when position
      (j/call s :setPosition position))
    (when volume
      (j/call s :setVolume volume))
    (j/call s :play time offset length)))

(defn stop [id]
  (j/call (get-sound id) :stop))

(defn pause [id]
  (j/call (get-sound id) :pause))

(defn playing? [id]
  (j/get (get-sound id) :isPlaying))

(defn set-volume
  ([id volume]
   (set-volume id volume nil))
  ([id volume time]
   (j/call (get-sound id) :setVolume volume time)))

(defn set-global-volume [volume]
  (j/call-in Engine [:audioEngine :setGlobalVolume] volume))

(defn set-sfx-distance-cutoff [distance]
  (doseq [{:keys [id] :as params} (->> (js/Object.values (j/get api.core/db :assets-regs))
                                       (filter #(and (:spatial? %) (= (:type %) :sound))))]
    (let [params (assoc params :max-distance distance)
          s (sound params)]
      (j/assoc-in! api.core/db [:assets id :sound] s)
      s)))

(defn play-audio-element [id]
  (when-let [a (js/document.getElementById id)]
    (j/assoc! a
              :volume 0.12
              :muted true)
    (-> (j/call a :play)
        (j/call :then #(j/assoc! a :muted false))
        (j/call :catch (fn [e]
                         (js/console.warn "Audio failed"))))))

(defn stop-audio-element [id]
  (try
    (when-let [a (js/document.getElementById id)]
      (j/call a :pause)
      (j/assoc! a :currentTime 0))
    (catch js/Error e
      (js/console.warn e))))

(defn reg-sound [id opts]
  (let [sound-name (name id)
        opts (assoc opts :id id
                    :name sound-name
                    :type :sound)
        m (if (api.core/get-scene)
            (let [s (sound opts)]
              (some-> sound-name get-sound-by-name api.core/dispose)
              (j/assoc-in! api.core/db [:assets id :sound] s)
              (assoc opts :sound s))
            opts)
        max-distance (-> (utils/get-item :settings) :sfx-distance-cutoff)
        m (assoc m :max-distance max-distance)]
    (j/assoc-in! api.core/db [:assets-regs id] m)
    m))
