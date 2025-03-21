(ns main.scene.player.shop
  (:require
    [applied-science.js-interop :as j]
    [clojure.string :as str]
    [main.api.camera :as api.camera]
    [main.api.core :as api.core :refer [v3]]
    [main.api.sound :as api.sound]
    [main.api.tween :as api.tween]
    [main.rule-engine :as re :refer [reg-rule]]
    [main.scene.network :as network :refer [dispatch-pro-response dispatch-pro-sync-response]]
    [main.scene.player.map :as player.map]
    [main.scene.player.model :as player.model]
    [main.scene.shop-items :as shop-items])
  (:require-macros
    [main.macros :as m]))

(defn- reset-camera-dir [camera]
  (j/assoc! camera
            :radius api.camera/default-radius
            :alpha api.camera/init-beta-alpha
            :beta api.camera/init-beta-alpha))

(defn- lock-camera-beta [camera]
  (j/assoc! camera
            :lowerBetaLimit api.camera/init-beta-alpha
            :upperBetaLimit api.camera/init-beta-alpha))

(defn- unlock-camera-beta [camera]
  (j/assoc! camera
            :lowerBetaLimit api.camera/beta-lower-limit
            :upperBetaLimit api.camera/beta-upper-limit))

(defn- show-model [camera mesh]
  (let [_ (j/assoc! mesh
                    :isVisible true
                    :scaling (v3 -1 1 1))
        _ (api.core/set-enabled mesh true)
        offset-box-pos (api.core/get-pos (re/query :player/offset-box) true)
        dir (api.camera/get-direction-scaled camera 0)
        pos (j/call offset-box-pos :addInPlace dir)]
    (player.model/apply-hero-material mesh :blue)
    (m/assoc! mesh
              :rotation.y (* 2.2 js/Math.PI)
              :position pos)))

(defn equip-player [model type item-data]
  (let [tn (case type
             :head (j/get model :item-head)
             :cape (j/get model :item-cape)
             :attachment (j/get model :item-attachment)
             nil)
        _ (doseq [c (some-> tn api.core/get-children)]
            (api.core/dispose c))]
    (when item-data
      (let [item (api.core/get-mesh-by-name (:model item-data))
            item (api.core/clone item :name (:name item-data))
            char-mesh (api.core/find-child-mesh model #(and % (str/includes? % "Chr_Hips_Male_")))]
        (m/assoc! item
                  :parent tn
                  :material (j/get char-mesh :material)
                  :rotationQuaternion nil
                  :position (apply v3 (:position item-data))
                  :rotation.x (api.core/to-rad (or (:rotation-x item-data) 90))
                  :rotation.y (api.core/to-rad (or (:rotation-y item-data) 0))
                  :rotation.z (api.core/to-rad (or (:rotation-z item-data) 0))
                  :scaling (if (number? (:scale item-data))
                             (v3 (:scale item-data))
                             (apply v3 (:scale item-data)))
                  :alwaysSelectAsActiveMesh true
                  :doNotSyncBoundingInfo true
                  :isPickable false)))))

(defn update-equipped [model]
  (let [player-data (re/query :player/data)
        player-equipped (:equipped player-data)]
    (doseq [[k v] player-equipped]
      (equip-player model k (get shop-items/all-items (keyword v))))))

(defn update-equipped-other-players [model equipped-data]
  (doseq [[k v] equipped-data]
    (equip-player model k (get shop-items/all-items (keyword v)))))

(defn- disable-other-players []
  (doseq [[_ player] (re/query :players)]
    (let [player-model (get player :mesh)]
      (api.core/set-enabled player-model false))))

(defn open-shop []
  (network/disconnect)
  (player.map/hide-current-map)
  (let [{:keys [camera
                game/skybox
                game/shop-model
                game/shop-ground
                map/regen-box]} (re/query-all)]
    (api.core/set-enabled skybox false)
    (some-> regen-box (api.core/set-enabled false))
    (api.core/set-enabled shop-model true)
    (api.core/set-enabled shop-ground true)
    (show-model camera shop-model)
    (j/assoc! camera :targetScreenOffset (v3 1.5 0 0))
    (j/call camera :setTarget (-> (api.core/get-pos shop-model true)
                                  (j/call :addInPlace (v3 0 1 0))))
    (j/assoc! shop-ground :position (api.core/get-pos shop-model true))
    (reset-camera-dir camera)
    (lock-camera-beta camera)
    (api.camera/update-camera-sensibility camera 0.9)
    (update-equipped shop-model)
    (disable-other-players)))

(defn close-shop []
  (player.map/show-current-map)
  (let [{:keys [camera
                game/skybox
                game/shop-model
                player/offset-box
                game/shop-ground
                map/regen-box]} (re/query-all)]
    (api.core/set-enabled skybox true)
    (some-> regen-box (api.core/set-enabled true))
    (api.core/set-enabled shop-model false)
    (api.core/set-enabled shop-ground false)
    (j/assoc! camera :targetScreenOffset (v3))
    (j/call camera :setTarget offset-box)
    (reset-camera-dir camera)
    (unlock-camera-beta camera)
    (api.camera/reset-camera-sensibility camera)))

(reg-rule
  :shop/open
  {:what {:game/shop? {:when-value-change? true}}
   :then (fn [{{shop? :game/shop?} :session}]
           (if shop?
             (open-shop)
             (close-shop)))})

(reg-rule
  :shop/equip
  {:what {:shop/equip {:when-value-change? true
                       :allow-nil? true}}
   :then (fn [{{equip :shop/equip
                model :game/shop-model} :session}]
           (if equip
             (equip-player model (:type equip) equip)
             (update-equipped model)))})

(reg-rule
  :shop/section
  {:what {:shop/section {:when-value-change? true}}
   :then (fn [{{camera :camera
                section :shop/section
                model :game/shop-model} :session}]
           (reset-camera-dir camera)
           (let [y (if (= section :cape)
                     (* 1 js/Math.PI)
                     (* 2.2 js/Math.PI))]
             (api.tween/tween {:target [model :rotation]
                               :duration 500
                               :from (api.core/clone (j/get model :rotation))
                               :to (v3 0 y 0)})))})

(defmethod dispatch-pro-sync-response :purchase [{:keys [result error fail?]}]
  (re/insert :shop/purchasing? false)
  (re/insert :shop/selected-item nil)
  (cond
    fail? (re/insert :shop/show-error-modal :fail)
    error (re/insert :shop/show-error-modal error)
    result (do
             (re/insert :player/data result)
             (api.sound/play :sound/coin)
             (update-equipped (re/query :game/shop-model)))))

(defmethod dispatch-pro-sync-response :equip [{:keys [result error fail?]}]
  (re/insert :shop/selected-item nil)
  (cond
    fail? (re/insert :shop/show-error-modal :fail)
    error (re/insert :shop/show-error-modal error)
    result (do
             (re/insert :player/data result)
             (update-equipped (re/query :game/shop-model)))))

(defmethod dispatch-pro-response :coins [{{:keys [coins]} :coins}]
  (re/upsert :player/data (fn [data] (assoc data :coins coins))))
