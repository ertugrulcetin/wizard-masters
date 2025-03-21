(ns main.crisp
  (:require
    [applied-science.js-interop :as j]))

(def open-params #js["do" "chat:open"])
(def close-params #js["do" "chat:close"])

(def show-params #js["do" "chat:show"])
(def hide-params #js["do" "chat:hide"])

(defn show []
  (j/call-in js/window [:$crisp :push] show-params))

(defn open []
  (j/call-in js/window [:$crisp :push] open-params))

(defn hide []
  (j/call-in js/window [:$crisp :push] hide-params))

(defn close []
  (j/call-in js/window [:$crisp :push] close-params))

(defn opened? []
  (try
    (j/call-in js/window [:$crisp :is] "chat:opened")
    (catch js/Error _
      false)))

(comment
  (open)
  (close)
  )
