(ns github-project-watch.models.core)

(defonce db (atom {}))

(defn save-api-key [user-id api-key]
  (swap! db assoc-in [user-id :api-key] api-key))

(defn fetch-api-key [user-id]
  (get-in @db [user-id :api-key]))

(defn add-repo [user-id repo]
  (swap! db update-in [user-id :repos] 
         (fn [repos]
           (cond
             (not repos) [repo]
             (empty? (filter #(= (:repo-link %) (:repo-link repo)) repos)) (conj repos repo)
             :else repos))))

(defn fetch-repos [user-id]
  (get-in @db [user-id :repos]))
