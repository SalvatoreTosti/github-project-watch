(ns github-project-watch.models.core)

(def db (atom {}))

(defn save-api-key [user-id api-key]
  (swap! db assoc-in [user-id :api-key] api-key)
  (println @db))

(defn fetch-api-key [user-id]
  (get-in @db [user-id :api-key]))


