(ns main.utils
  (:require
    ["bad-words" :refer [Filter]]
    [applied-science.js-interop :as j]
    [cljs.reader :as reader]
    [clojure.string :as str]
    [goog.string :as gstring]
    [goog.string.format]
    [main.rule-engine :as re]))

(defonce db (atom {}))

(defn register-event-listener [element type f]
  (j/call element :addEventListener type f)
  (swap! db update :event-listeners (fnil conj []) [element type f])
  f)

(defn remove-element-listener [element type f]
  (j/call element :removeEventListener type f))

(defn remove-element-listeners []
  (doseq [[element type f] (:event-listeners @db)]
    (j/call element :removeEventListener type f))
  (swap! db assoc :event-listeners [])
  (js/console.log "All events listeners removed"))

(defn shallow-clj->js [m]
  (let [js-obj (js-obj)]
    (doseq [[k v] m]
      (aset js-obj (name k) v))
    js-obj))

(defn rand-between-int [min max]
  (+ (Math/floor (* (Math/random) (+ (- max min) 1))) min))

(defn rand-between [start end]
  (+ start (* (rand) (- end start))))

(defn tab-active? []
  (not (j/get js/document :hidden)))

(defn clamp [val min-val max-val]
  (-> val
      (max min-val)
      (min max-val)))

(defn millis->secs
  ([millis]
   (Math/floor (/ millis 1000)))
  ([millis fixed]
   (j/call (/ millis 1000) :toFixed fixed)))

(def bad-words-filter (Filter.))

(defn clean [text]
  (when-not (str/blank? text)
    (.clean bad-words-filter text)))

(when bad-words-filter
  (.removeWords bad-words-filter "cok" "çok"))

(defn get-cookie [key]
  (let [name-eq (str (name key) "=")
        cookies (-> (j/get js/document :cookie)
                    (str/split #";")
                    (->> (map str/trim)))]
    (println cookies)
    (when-let [match (->> cookies
                          (filter #(.startsWith % name-eq))
                          first)]
      (try
        (-> match
            (subs (count name-eq))
            js/decodeURIComponent
            cljs.reader/read-string)
        (catch :default _
          nil)))))

(defn set-cookie [key value]
  (let [days 365
        date (js/Date.)
        expires (do
                  (j/call date :setTime (+ (j/call date :getTime) (* days 24 60 60 1000)))
                  (str "; expires=" (j/call date :toUTCString)))]
    (println (str (name key) "=" (js/encodeURIComponent (pr-str value))
                  expires
                  "; path=/"))
    (j/assoc! js/document :cookie
              (str (name key) "=" (js/encodeURIComponent (pr-str value))
                   expires
                   "; path=/"))))

(defn remove-cookie [key]
  (j/assoc! js/document
            :cookie
            (str (name key) "=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/")))

(defn set-item [key val]
  (cond
    (j/get js/window :localStorage)
    (j/call (j/get js/window :localStorage) :setItem (name key) (pr-str val))

    (j/get js/window :cookie)
    (set-cookie key val)

    :else (re/insert key val)))

(defn get-item [key]
  (cond
    (j/get js/window :localStorage)
    (reader/read-string (j/call (j/get js/window :localStorage) :getItem (name key)))

    (j/get js/window :cookie)
    (get-cookie key)

    :else
    (re/query key)))

(defn update-item [key f]
  (set-item key (f (get-item key))))

(defn remove-item! [key]
  (cond
    (j/get js/window :localStorage)
    (j/call (j/get js/window :localStorage) :removeItem (name key))

    (j/get js/window :cookie)
    (remove-cookie key)

    :else
    (re/insert key nil)))

(defn copy-to-clipboard [value]
  (.writeText (.-clipboard js/navigator) value))

(defn wm-domain? []
  (= "wizardmasters.io" (j/get-in js/window [:location :host])))

(defn format [pattern v]
  (gstring/format pattern v))
