(ns main.scene.mobile
  (:require
    ["nipplejs" :as npj]
    [applied-science.js-interop :as j]
    [main.rule-engine :as re]))

(defn get-orientation []
  (if (-> js/window
          (j/call :matchMedia "(orientation: portrait)")
          (j/get :matches))
    :portrait
    :landscape))

(defn mobile? []
  (let [mobile-regex (js/RegExp. "Mobi|Android|iPhone|iPad|iPod|BlackBerry|IEMobile|Opera Mini" "i")
        user-agent (j/get js/navigator :userAgent)]
    (j/call mobile-regex :test user-agent)))

(let [joy (atom nil)
      static (atom nil)]
  (defn create-controller []
    (when @joy
      (j/call @joy :destroy))
    #_(when @static
      (j/call @static :destroy))

    #_(reset! static
            ((j/get npj :create)
             #js {"zone" (js/document.getElementById "mobile-controller-area")
                  "color" "white"
                  "mode" "static"
                  "position" #js {"left" "10%"
                                  "bottom" "20%"}}))
    (reset! joy
            ((j/get npj :create)
             #js {"zone" (js/document.getElementById "mobile-controller-area")
                  "color" "white"
                  ;; "mode" "static"
                  "catchDistance" 50
                  ;; "multitouch" true
                  }))


    (j/call @joy :on "added"
            (fn [_ nipple]
              (j/call nipple :on "start"
                      (fn []
                        #_(j/call (j/get @static 0) :remove)))
              (j/call nipple :on "end"
                      (fn []
                        (re/insert {:player/js-angle nil
                                    :player/js-distance nil})
                        #_(when-let [dom (js/document.getElementById "mobile-controller-area")]
                          (j/call (j/get @static 0) :add dom))))
              (j/call nipple :on "move"
                      (fn [e data]
                        (re/insert {:player/js-angle (j/get-in data [:angle :degree])
                                    :player/js-distance (j/get data :distance)})))))
    (j/call @joy :on "removed"
            (fn [_ nipple]
              (j/call nipple :off "start end move")))))

(comment
  (create-controller)
  )
