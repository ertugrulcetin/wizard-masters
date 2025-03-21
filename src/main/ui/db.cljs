(ns main.ui.db
  (:refer-clojure :exclude [get]))

(def default-db
  {:name "Wizard Masters"})

(comment
  @re-frame.db/app-db
  )
