(ns main.ui.view.login
  (:require
    [applied-science.js-interop :as j]
    [breaking-point.core :as bp]
    [clojure.string :as str]
    [main.rule-engine :as re]
    [main.scene.network :refer [dispatch-pro-sync]]
    [main.ui.subs :as subs]
    [main.ui.view.components :refer [modal]]
    [main.utils :as utils]
    [re-frame.core :refer [subscribe]]
    [reagent.core :as r]))

(def shop-bg-color "#454545")
(def header-height "40px")

(def username-regex #"^[A-Za-z0-9_]{2,20}$")
(def password-regex #"^.{8,}$")

(def username-req "* Username must be 2–20 characters and can include only letters (A–Z), numbers (0–9), and underscores. Spaces are not allowed.")
(def password-req "* Password must be at least 8 characters long.")

(def username (r/atom ""))
(def password (r/atom ""))

(defn- menu-button [_]
  (let [hover? (r/atom false)]
    (fn [{:keys [text on-click style icon icon-hover class disabled? selected?]}]
      [:button
       {:class ["shop_type_button" (when selected? "selected") class]
        :style (merge {:font-size (if @(subscribe [::bp/cg?]) "16px" "22px")
                       :opacity (when disabled? 0.5)
                       :cursor (when disabled? "not-allowed")
                       :line-height "1.2em"
                       :display "flex"
                       :gap "6px"
                       :flex-direction "row"
                       :justify-content "center"
                       :align-items "center"}
                      style)
        :on-mouse-over #(reset! hover? true)
        :on-mouse-out #(reset! hover? false)
        :disabled disabled?
        :on-click (fn []
                    (when on-click (on-click)))}
       text
       (when (and icon (not @hover?) (not selected?))
         icon)
       (when (or selected? (and icon-hover @hover?))
         icon-hover)])))

(defn- valid-username-password? []
  (cond
    (not (re-find username-regex (str/trim @username)))
    (do
      (re/insert :login/show-error-modal username-req)
      false)

    (not (re-find password-regex @password))
    (do
      (re/insert :login/show-error-modal password-req)
      false)

    :else true))

(defn- sign-up-login-types []
  [:div
   {:id "shop-type"
    :style {:display "flex"
            :flex-direction "column"
            :align-items "center"
            :width "100%"
            :padding "10px"
            :box-sizing "border-box"}}
   (if @(subscribe [::subs/signed-up?])
     [:div
      {:style {:display "flex"
               :flex-direction "row"
               :gap "20px"
               :padding-right "10px"
               :overflow-y "auto"}}
      [menu-button {:text "Logout"
                    :style {:color "#ff6f6f"}
                    :on-click (fn []
                                (when (j/call js/window :confirm "Logging out will cause the page to refresh. Do you want it?")
                                  (utils/remove-item! :auth-token)
                                  (j/call-in js/window [:location :reload])))
                    :disabled? @(subscribe [::subs/login-processing?])}]
      [menu-button {:text "Update"
                    :style {:color "#5bbc5b"}
                    :on-click (fn []
                                (when (valid-username-password?)
                                  (re/insert :login/processing? true)
                                  (dispatch-pro-sync :sign-up {:username (str/trim @username)
                                                               :password @password
                                                               :update? true})))
                    :disabled? @(subscribe [::subs/login-processing?])}]]
     [:div
      {:style {:display "flex"
               :flex-direction "row"
               :gap "20px"
               :padding-right "10px"
               :overflow-y "auto"}}
      [menu-button {:text "Login"
                    :on-click (fn []
                                (when (valid-username-password?)
                                  (re/insert :login/processing? true)
                                  (dispatch-pro-sync :log-in {:username (str/trim @username)
                                                              :password @password})))
                    :disabled? @(subscribe [::subs/login-processing?])}]
      [menu-button {:text "Sign up"
                    :on-click (fn []
                                (when (valid-username-password?)
                                  (re/insert :login/processing? true)
                                  (dispatch-pro-sync :sign-up {:username (str/trim @username)
                                                               :password @password})))
                    :disabled? @(subscribe [::subs/login-processing?])}]])])

(defn username-and-password []
  [:div {:style {:display "flex"
                 :flex-direction "column"
                 :width "100%"
                 :padding "10px"
                 :box-sizing "border-box"
                 ;; :justify-content "center"
                 ;; :align-items "center"
                 :background-color "#454545"
                 :color "white"}}
   [:div {:style {:display "flex"
                  :flex-direction "column"
                  :justify-content "space-between"
                  :font-weight "bold"
                  :background-color "#454545"
                  :align-items "center"
                  :gap "6px"
                  :z-index "1"}}
    [:div {:style {:display "flex"
                   :align-items "center"
                   :gap "4px"}}
     [:span {:style {:letter-spacing "1px"
                     :font-size "20px"}} "Username: "]
     [:input {:type "text"
              :default-value @username
              :on-change (fn [e]
                           (reset! username (j/get-in e [:target :value])))
              :style {:outline "none"
                      :width "200px"}}]]
    [:div {:style {:display "flex"
                   :align-items "center"
                   :gap "4px"}}
     [:span {:style {:letter-spacing "1px"
                     :font-size "20px"}} "Password: "]
     [:input {:type "password"
              :on-change (fn [e]
                           (reset! password (j/get-in e [:target :value])))
              :style {:outline "none"
                      :width "200px"}}]]]
   [:div
    {:style {:display "flex"
             :margin-top "10px"
             :flex-direction "column"
             :gap "6px"}}
    [:span {:style {:font-family "sans-serif"
                    :font-size "13px"}}
     username-req]
    [:span {:style {:font-family "sans-serif"
                    :font-size "13px"}}
     password-req]]])

(defn log-in-sign-up []
  (r/create-class
    {:component-will-mount
     (fn []
       (reset! username (or (re/query :player/data :username) ""))
       (reset! password ""))
     :reagent-render
     (fn []
       [:<>
        [:div
         {:style {:display "flex"
                  :user-select "none"
                  :pointer-events "all"
                  :position "absolute"
                  :justify-content "center"
                  :align-items "center"
                  :width "500px"
                  :height "230px"
                  :top "50%"
                  :left "50%"
                  :z-index 998
                  :transform "translate(-50%, -50%)"}}
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

           [:div {:style {:display "flex"
                          :align-items "center"
                          :justify-content "center"
                          :width "100%"
                          :height header-height
                          :box-sizing "border-box"
                          :padding "15px"
                          :font-size "24px"
                          :color "white"}}

            [:span {:style {:left "50%"
                            :font-size "24px"
                            :color "white"
                            :white-space "nowrap"
                            :overflow "hidden"
                            :text-overflow "ellipsis"
                            :max-width "440px"}}
             (if-not (str/blank? (re/query :player/data :username))
               (str "Logged in as (" (re/query :player/data :username) ")")
               "Log in and keep your loot safe!")]

            [:span {:on-click #(re/fire-rules :game/login? false)
                    :style {:position "fixed"
                            :left "calc(100% - 30px)"
                            :font-size "24px"
                            :color "white"
                            :cursor "pointer"}} "X"]]
           [:div {:style {:display "flex"
                          :flex-direction "row"
                          :overflow "hidden"
                          :width "100%"
                          :height "100%"}}
            [username-and-password]]
           [sign-up-login-types]]]]
        (when-let [error @(subscribe [::subs/login-error-modal])]
          [modal {:open? true
                  :header "Error!"
                  :content (cond
                             (= :fail? error) "Something went wrong, try again later or refresh the page."
                             (and error (string? error)) error)
                  :close-button-text "Ok"
                  :close-fn (fn []
                              (re/insert :login/show-error-modal nil))}])])}))
