(ns main.ui.view.shop
  (:require
    [applied-science.js-interop :as j]
    [breaking-point.core :as bp]
    [main.ads :as ads]
    [main.rule-engine :as re]
    [main.scene.network :refer [dispatch-pro-sync]]
    [main.scene.shop-items :as items]
    [main.ui.subs :as subs]
    [main.ui.svg :as svg]
    [main.ui.view.components :refer [modal button]]
    [re-frame.core :refer [subscribe]]
    [reagent.core :as r]))

(def shop-bg-color "#454545")
(def shop-item-size-small "68px")
(def shop-item-size-big "90px")
(def header-height "40px")
(def coin-color "rgb(251, 212, 19)")

(def section (r/atom :head))

(defn- menu-button [_]
  (let [hover? (r/atom false)]
    (fn [{:keys [text on-click icon icon-hover class disabled? selected?]}]
      [:button
       {:class ["shop_type_button" (when selected? "selected") class]
        :style {:font-size (if @(subscribe [::bp/cg?]) "16px" "22px")
                :opacity (when disabled? 0.5)
                :cursor (when disabled? "not-allowed")
                :line-height "1.2em"
                :display "flex"
                :gap "6px"
                :flex-direction "row"
                :justify-content "center"
                :align-items "center"}
        :on-mouse-over #(reset! hover? true)
        :on-mouse-out #(reset! hover? false)
        :disabled disabled?
        :on-click (fn []
                    (when on-click (on-click))
                    (re/fire-rules :shop/section @section))}
       text
       (when (and icon (not @hover?) (not selected?))
         icon)
       (when (or selected? (and icon-hover @hover?))
         icon-hover)])))

(defn- shop-types []
  [:div
   {:id "shop-type"
    :style {:display "flex"
            :flex-direction "column"
            :align-items "center"
            :height "100%"
            :width "50%"
            :padding "10px"
            :box-sizing "border-box"}}
   (let [icon-size (if @(subscribe [::bp/cg?]) 28 32)]
     [:div
      {:style {:display "flex"
               :flex-direction "column"
               :gap "10px"
               :padding-right "10px"
               :overflow-y "auto"}}
      [menu-button {:text "Head"
                    :icon (svg/hat "white" icon-size)
                    :icon-hover (svg/hat shop-bg-color icon-size)
                    :on-click #(reset! section :head)
                    :selected? (= @section :head)}]
      [menu-button {:text "Capes"
                    :icon (svg/cape "white" icon-size)
                    :icon-hover (svg/cape shop-bg-color icon-size)
                    :on-click #(reset! section :cape)
                    :selected? (= @section :cape)}]
      [menu-button {:text "Attachments"
                    :icon (svg/attachment "white" icon-size)
                    :icon-hover (svg/attachment shop-bg-color icon-size)
                    :on-click #(reset! section :attachment)
                    :selected? (= @section :attachment)}]
      [menu-button {:text "Rewarded"
                    :icon (svg/ad "white" icon-size)
                    :icon-hover (svg/ad shop-bg-color icon-size)
                    :on-click #(reset! section :rewarded)
                    :selected? (= @section :rewarded)}]
      [menu-button {:text "Gauntlets (Soon!)"
                    :disabled? true
                    :icon (svg/gauntlet "white" icon-size)
                    :icon-hover (svg/gauntlet shop-bg-color icon-size)
                    :on-click #(reset! section :gauntlet)
                    :selected? (= @section :gauntlet)}]
      [menu-button {:text "Upgrade (Soon!)"
                    :on-click #(reset! section :upgrade)
                    :selected? (= @section :upgrade)
                    ;; :icon (svg/upgrade "white" icon-size)
                    ;; :icon-hover (svg/upgrade shop-bg-color icon-size)
                    :disabled? true}]
      [menu-button {:text "Pets (Soon!)"
                    :on-click #(reset! section :pet)
                    :selected? (= @section :pet)
                    ;; :icon (svg/upgrade "white" icon-size)
                    ;; :icon-hover (svg/upgrade shop-bg-color icon-size)
                    :disabled? true}]])])

(defn- get-img-url-prefix [params]
  (case (:type params)
    :head "img/shop/hat/"
    :cape "img/shop/cape/"
    :attachment "img/shop/attachment/"))

(defn price-in-coin [params purchased?]
  [:div
   [:img {:style {:width "100%"
                  :position "absolute"}
          :src (-> (get-img-url-prefix params)
                   (str (:img params)))}]
   (when-not purchased?
     [:span
      {:style {:color coin-color
               :font-size "16px"
               :position "absolute"
               :bottom "-3px"
               :left "50%"
               :transform "translateX(-50%)"
               :text-stroke "2px"
               :text-shadow "1px 1px 2px #000"}}
      (:price params)])])

(defn price-in-ads [hover? params purchased?]
  [:<>
   [:img {:style {:width "100%"
                  :position "absolute"}
          :src (-> (get-img-url-prefix params)
                   (str (:img params)))}]
   (when-not purchased?
     [:div
      {:style {:display "flex"
               :width "100%"
               :height "100%"}}
      [:div
       {:style {:position "absolute"
                :top "calc(50% + 5px)"
                :left "50%"
                :transform "translate(-50%, -50%)"}}
       (svg/ad (if @hover? "#45454500" "#cacacac4") 52)]])])

(def pos (r/atom nil))

(defn- item-name-header [name]
  [:span {:style {:font-size "18px"}} name])

(defn- shop-popover []
  (let [popover-ref (r/atom nil)
        exceeds-top? (r/atom nil)]
    (r/create-class
      {:component-did-mount
       (fn []
         (when-let [popover @popover-ref]
           (let [rect (.getBoundingClientRect popover)]
             (reset! exceeds-top? (< (.-top rect) 0)))))
       :reagent-render
       (fn []
         (let [current-item-id @(subscribe [::subs/shop-hovered-item])
               {:keys [speed
                       damage
                       defense
                       fire-immune
                       ice-immune
                       wind-immune
                       light-immune
                       stun]} (get-in items/all-items [current-item-id :powers])]
           [:div
            {:ref #(reset! popover-ref %)
             :style {:display "flex"
                     :visibility (when-not (some? @exceeds-top?) "hidden")
                     :flex-direction "column"
                     :align-items "center"
                     :justify-content "center"
                     :position "absolute"
                     :line-height "1.2em"
                     :top (str (:y @pos) "px")
                     :left (str (:x @pos) "px")
                     :transform (if @exceeds-top?
                                  "translate(-50%, 50%)"
                                  "translate(-50%, -100%)")
                     :background "#454545"
                     :border "1px solid white"
                     :color "white"
                     :box-shadow "0px 4px 8px rgba(0,0,0,0.2)"
                     :padding "10px"
                     :z-index 999}}
            [:div {:style {:font-size "16px"
                           :letter-spacing "1px"}}
             (str (get-in items/all-items [current-item-id :name]) " (+1)")]
            [:hr {:style {:width "100%"
                          :height "1px"
                          :border "none"
                          :background "white"
                          :margin "8px 0"}}]
            (when damage
              [:span {:style {:font-size "14px"
                              :color "#27d827"}} (str "Damage: +" damage "%")])
            (when speed
              [:span {:style {:font-size "14px"
                              :color "#f4d243"}} "Speed: +" speed "%"])
            (when defense
              [:span {:style {:font-size "14px"
                              :color "#79a2f4"}} "Defense: +" defense "%"])
            (when stun
              [:span {:style {:font-size "14px"
                              :color "#e3e3e3"}} "Stun: +" stun "%"])
            (when fire-immune
              [:span {:style {:font-size "14px"
                              :color "#ffafaf"}} "Fire Immune: +" fire-immune "%"])
            (when ice-immune
              [:span {:style {:font-size "14px"
                              :color "#71efef"}} "Ice Immune: +" ice-immune "%"])
            (when wind-immune
              [:span {:style {:font-size "14px"
                              :color "#ffffff"}} "Wind Immune: +" wind-immune "%"])
            (when light-immune
              [:span {:style {:font-size "14px"
                              :color "#bbffec"}} "Light Immune: +" light-immune "%"])]))})))

(defn- item [_]
  (let [hover? (r/atom false)]
    (fn [params]
      (let [shop-item-size (if @(subscribe [::bp/cg?]) shop-item-size-small shop-item-size-big)
            id (name (:id params))
            purchased? (@(subscribe [::subs/shop-purchased-items]) (:id params))
            equipped? (@(subscribe [::subs/player-equipped]) (:id params))]
        [:div
         {:id id
          :class ["shop_item_button" (when equipped? "selected")]
          :style {:width shop-item-size
                  :height shop-item-size
                  :cursor "pointer"
                  :background "#e3e3e3"
                  :border (when equipped? "2px solid white")
                  :position "relative"}
          :on-click (fn []
                      (if purchased?
                        (dispatch-pro-sync :equip {:item (:id params)})
                        (re/insert :shop/selected-item params)))
          :on-mouse-over (fn []
                           (re/fire-rules :shop/equip params)
                           (reset! hover? true)
                           (let [e (js/document.getElementById id)
                                 rect (-> e .getBoundingClientRect)]
                             ;; Capture item's position for the popover
                             (reset! pos {:x (+ (.-left rect) (/ (.-width rect) 2))
                                          :y (.-top rect)})))
          :on-mouse-out #(do
                           (re/fire-rules :shop/equip nil)
                           (reset! pos nil)
                           (reset! hover? false))}
         (if (:rewarded? params)
           [price-in-ads hover? params purchased?]
           [price-in-coin params purchased?])]))))

(defn- shop-items []
  [:div {:id "shop-items"
         :style {:display "flex"
                 :width "100%"
                 :flex-direction "column"
                 :padding "10px"
                 :box-sizing "border-box"
                 :overflow-y "auto"
                 :height "100%"}}
   [:div
    {:style {:display "flex"
             :flex-wrap "wrap"
             :align-items "center"
             :justify-content "center"
             :gap "6px"
             :height "auto"}}
    (let [f (case @section
              :head (fn [[_ params]]
                      (= :head (:type params)))
              :cape (fn [[_ params]]
                      (= :cape (:type params)))
              :attachment (fn [[_ params]]
                            (= :attachment (:type params)))
              :rewarded (fn [[_ params]]
                          (:rewarded? params)))]
      (for [[id params] (sort-by (comp :price second) (filter f items/all-items))]
        ^{:key id}
        [item (assoc params :id id)]))]])

(defn shop-panel []
  (r/create-class
    {:component-did-mount
     (fn []
       (reset! section :head)
       (re/fire-rules :shop/section @section)
       (ads/request-banner {:id "banner-container-728-90"
                            :width 728
                            :height 90}))
     :reagent-render
     (fn []
       (let [height (if @(subscribe [::bp/cg?]) "100px" "250px")
             cg? @(subscribe [::bp/cg?])]
         [:<>
          [:div
           {:style {:display "flex"
                    :user-select "none"
                    :pointer-events "all"
                    :position "absolute"
                    :justify-content "center"
                    :align-items "center"
                    :width "50%"
                    :height (str "calc(100% - " height ")")
                    :top "50%"
                    :left "25px"
                    :z-index 998
                    :transform "translate(0%, -50%)"}}
           [:div {:style {:display "flex"
                          :flex-direction "row"
                          :width "100%"
                          :height "100%"
                          :background shop-bg-color
                          :border-radius "3px"}}

            [:div {:style {:display "flex"
                           :flex-direction "column"
                           :width "100%"
                           :height "100%"}}

             [:div {:style {:width "100%"
                            :height header-height
                            :box-sizing "border-box"
                            :padding "15px"
                            :font-size "24px"
                            :color "white"}}
              [:span {:style {:position "absolute"
                              :left "22px"
                              :font-size "20px"
                              :color coin-color}}
               (str "Coins: " @(subscribe [::subs/player-coins]))]

              [:span {:style {:position "absolute"
                              :left "50%"
                              :font-size "24px"
                              :color "white"}}
               "Store"]

              [:span {:on-click #(re/fire-rules :game/shop? false)
                      :style {:position "fixed"
                              :left "calc(100% - 30px)"
                              :font-size "24px"
                              :color "white"
                              :cursor "pointer"}} "X"]]

             [:div {:style {:display "flex"
                            :flex-direction "row"
                            :overflow "hidden"
                            :width "100%"
                            :height (str "calc(100% - " header-height ")")}}
              [shop-types]
              [shop-items]]]]]
          (when @pos
            [shop-popover])
          (when-let [item @(subscribe [::subs/shop-selected-item])]
            [modal {:open? true
                    :header "Confirm Purchase"
                    :content [:img {:style {:width "100px"
                                            :border "2px solid white"}
                                    :src (-> (get-img-url-prefix item)
                                             (str (:img item)))}]
                    :close-button-text "Cancel"
                    :confirm-button (if (:rewarded? item)
                                      [button {:text "Get"
                                               :style {:color "white"}
                                               :disabled? @(subscribe [::subs/shop-purchasing?])
                                               :on-click (fn []
                                                           (ads/request-rewarded #(dispatch-pro-sync :purchase {:item (:id item)})))
                                               :icon (svg/ad "white" 24)
                                               :icon-hover (svg/ad "white" 24)}]
                                      [button {:text (str "Buy " (:price item))
                                               :disabled? @(subscribe [::subs/shop-purchasing?])
                                               :on-click (fn []
                                                           (re/insert :shop/purchasing? true)
                                                           (dispatch-pro-sync :purchase {:item (:id item)}))
                                               :style {:color coin-color}
                                               :icon (svg/coin coin-color 24)
                                               :icon-hover (svg/coin coin-color 24)}])
                    :close-fn (fn []
                                (re/insert :shop/selected-item nil))}])
          (when-let [error @(subscribe [::subs/shop-error-modal])]
            [modal {:open? true
                    :header "Error!"
                    :content (cond
                               (= :fail? error) "Something went wrong, try again later or refresh the page."
                               (and error (string? error)) error)
                    :close-button-text "Ok"
                    :close-fn (fn []
                                (re/insert :shop/show-error-modal nil))}])
          [:div
           {:id "banner-container-728-90"
            :style (cond-> {:position "absolute"
                            :background "rgba(0, 0, 0, 0.25)"
                            :pointer-events "all"
                            :z-index 99999
                            :transform "translate(-50%,-50%)"
                            :bottom "-30px"
                            :left "50%"
                            :width "728px"
                            :height "90px"}
                     cg? (merge {:transform "translate(-50%,-50%) scale(0.5)"
                                 :bottom "-65px"}))}]]))}))
