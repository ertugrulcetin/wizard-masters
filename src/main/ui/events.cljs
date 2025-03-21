(ns main.ui.events
  (:require
    [main.ui.db :as db]
    [re-frame.core :refer [reg-event-db reg-event-fx reg-fx]]))

(reg-event-db
  ::initialize-db
  (fn [_ [_ mobile?]]
    (assoc db/default-db :game/mobile? mobile?)))

(reg-event-db
  ::insert
  (fn [db [_ m]]
    (if (and m (map? m))
      (merge db m)
      db)))
