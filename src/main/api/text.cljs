(ns main.api.text
  (:require
    [applied-science.js-interop :as j]
    [main.api.core :as api.core]
    [main.rule-engine :as re]))

(defn reg-text
  "Parameters:
   * :url - the url of the text
   * :on-success - a function to be called when the mesh is loaded
   * :preload? - whether to preload the mesh or not"
  [id opts]
  (let [text-name (name id)
        opts (assoc opts :id id
                    :name text-name
                    :type :text)]
    (j/assoc-in! api.core/db [:assets-regs id] opts)
    opts))

(reg-text
  :font/droid
  {:url "font/droid.json"
   :preload? true
   :on-success (fn [text]
                 (re/insert :font/droid (js/JSON.parse text)))})

(reg-text
  :map/arena
  {:url "map/arena.json"
   :preload? true
   :on-success (fn [text]
                 (->> (js->clj (js/JSON.parse text) :keywordize-keys true)
                      (re/insert :map/arena-json)))})

(reg-text
  :map/temple
  {:url "map/temple.json"
   :preload? true
   :on-success (fn [text]
                 (->> (js->clj (js/JSON.parse text) :keywordize-keys true)
                      (re/insert :map/temple-json)))})

(reg-text
  :map/towers
  {:url "map/towers.json"
   :preload? true
   :on-success (fn [text]
                 (->> (js->clj (js/JSON.parse text) :keywordize-keys true)
                      (re/insert :map/towers-json)))})

(reg-text
  :map/ruins
  {:url "map/ruins.json"
   :preload? true
   :on-success (fn [text]
                 (->> (js->clj (js/JSON.parse text) :keywordize-keys true)
                      (re/insert :map/ruins-json)))})

(comment
  (re/query :map/arena-json))
