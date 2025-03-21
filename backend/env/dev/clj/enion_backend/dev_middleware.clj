(ns enion-backend.dev-middleware
  (:require
    [enion-backend.config :refer [env]]
    [prone.middleware :refer [wrap-exceptions]]
    [ring.middleware.reload :refer [wrap-reload]]
    [selmer.middleware :refer [wrap-error-page]]))

(defn wrap-dev
  [handler]
  (-> handler
      wrap-reload
      wrap-error-page
      ;; disable prone middleware, it can not handle async
      (cond-> (not (env :async?)) (wrap-exceptions {:app-namespaces ['enion-backend]}))))
