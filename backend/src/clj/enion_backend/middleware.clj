(ns enion-backend.middleware
  (:require
    [clojure.tools.logging :as log]
    [enion-backend.config :refer [env]]
    [enion-backend.env :refer [defaults]]
    [enion-backend.layout :refer [error-page]]
    [enion-backend.middleware.formats :as formats]
    [muuntaja.middleware :refer [wrap-format wrap-params]]
    [promesa.core :as p]
    [ring-ttl-session.core :refer [ttl-memory-store]]
    [ring.middleware.anti-forgery :refer [wrap-anti-forgery]]
    [ring.middleware.cors :refer [wrap-cors]]
    [ring.middleware.defaults :refer [site-defaults wrap-defaults]]
    [ring.middleware.gzip :refer [wrap-gzip]]))

(defn wrap-internal-error
  [handler]
  (let [error-result (fn [^Throwable t]
                       (log/error t (.getMessage t))
                       (error-page {:status 500
                                    :title "Something very bad has happened!"
                                    :message "We've dispatched a team of highly trained gnomes to take care of the problem."}))]
    (fn wrap-internal-error-fn
      ([req respond _]
       (handler req respond #(respond (error-result %))))
      ([req]
       (try
         (handler req)
         (catch Throwable t
           (error-result t)))))))

(defn wrap-csrf
  [handler]
  (wrap-anti-forgery
    handler
    {:error-response
     (error-page
       {:status 403
        :title "Invalid anti-forgery token"})}))

(defn wrap-formats
  [handler]
  (let [wrapped (-> handler wrap-params (wrap-format formats/instance))]
    (fn
      ([request]
       ;; disable wrap-formats for websockets
       ;; since they're not compatible with this middleware
       ((if (:websocket? request) handler wrapped) request))
      ([request respond raise]
       ((if (:websocket? request) handler wrapped) request respond raise)))))

(defn- method-name-and-arity-pred [name n]
  (fn [^java.lang.reflect.Method m]
    (and (= (.getName m) name)
         (== n (-> m .getParameterTypes alength)))))

(defn- function-has-arity? [f n]
  (and (fn? f)
       (let [f2 ^clojure.lang.Fn f
             c (.getClass f2)
             ms (.getDeclaredMethods c)]
         (->> ms
              (filter (method-name-and-arity-pred "invoke" n))
              seq
              ;; support your local garbage collector
              boolean))))

(defn wrap-as-async [handler]
  "Execute a sync-only handler async.

 This allows you to work with simple sync handlers.
 Note that this must be the outermost middleware that is executed.

 If the handler is async (= it can take 3 arguments)
 the async version will be called."
  (let [async? (function-has-arity? handler 3)]
    (fn
      ([req]
       ;; sync - server/middleware is calling the shots
       (if async?
         (handler req identity (fn [^Throwable t] (throw t)))
         (let [rsp (handler req)]
           (if (p/promise? rsp)
             (do
               (log/warn "using promise from sync ring handler, must block")
               @rsp)
             rsp))))
      ;; async so far - use sync version if the handler can not talk async
      ([req respond raise]
       (if async?
         (handler req respond raise)
         (-> (p/do (handler req))
             (p/handle (fn [result err]
                         (if result
                           (respond result)
                           (raise err))))))))))

(defn wrap-base
  [handler]
  (-> ((:middleware defaults) handler)
      wrap-gzip
      (wrap-cors
        :access-control-allow-origin [#".*"]
        :access-control-allow-methods [:get :put :post :delete :patch])
      (wrap-defaults
        (-> site-defaults
            (assoc-in [:security :anti-forgery] false)
            (assoc-in  [:session :store] (ttl-memory-store (* 60 30)))))
      wrap-internal-error))
