(ns user
  "Userspace functions you can run by default in your local REPL."
  (:require
    [clojure.pprint]
    [enion-backend.config :refer [env]]
    [enion-backend.core]
    [mount.core :as mount]))

(add-tap (bound-fn* clojure.pprint/pprint))

(defn start
  "Starts application.
  You'll usually want to run this on startup."
  []
  (mount/start-without #'enion-backend.core/repl-server))

(defn stop
  "Stops application."
  []
  (mount/stop-except #'enion-backend.core/repl-server))

(defn restart
  "Restarts application."
  []
  (stop)
  (start))
