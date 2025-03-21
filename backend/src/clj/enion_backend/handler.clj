(ns enion-backend.handler
  (:require
    [enion-backend.env :refer [defaults]]
    [enion-backend.middleware :as middleware]
    [enion-backend.routes.home :refer [home-routes]]
    [mount.core :as mount]
    [reitit.ring :as ring]))

(mount/defstate init-app
                :start ((or (:init defaults) (fn [])))
                :stop  ((or (:stop defaults) (fn []))))

(mount/defstate app-routes
                :start
                (ring/ring-handler
                  (ring/router
                    [(home-routes)])))

(defn app []
  (middleware/wrap-base #'app-routes))
