(ns enion-backend.env
  (:require
    [clojure.tools.logging :as log]))

(def defaults
  {:init
   (fn []
     (log/info "\n-=[enion-backend started successfully]=-"))
   :stop
   (fn []
     (log/info "\n-=[enion-backend has shut down successfully]=-"))
   :middleware identity})
