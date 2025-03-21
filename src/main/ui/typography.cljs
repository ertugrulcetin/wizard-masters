(ns main.ui.typography
  (:require
    [clojure.string :as str]))

(def font
  (str/join "," ["\"GROBOLD\""
                 "sans-serif"
                 "\"Inter UI\""
                 "\"SF Pro Display\""
                 "-apple-system"
                 "BlinkMacSystemFont"
                 "\"Segoe UI\""
                 "Roboto"
                 "Oxygen"
                 "Ubuntu"
                 "Cantarell"
                 "\"Open Sans\""
                 "\"Helvetica Neue\""]))

(def xxs "10px")
(def xs "11px")
(def s "12px")
(def m "13px")
(def l "14px")
(def xl "15px")
(def xxl "18px")
(def xxxl "21px")

(def light 300)
(def regular 400)
(def medium 500)
(def semi-bold 600)
(def bold 700)
