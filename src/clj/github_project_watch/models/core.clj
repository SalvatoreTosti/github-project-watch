(ns github-project-watch.models.core
  (:require [clj-http.client :as client]
            [cheshire.core :as ch]
            [clj-time.core :as t]
            [clj-time.format :as f]
            ))

(defonce db (atom {}))

(comment
  (clojure.pprint/pprint @db)
  )

(defn save-api-key [user-id api-key]
  (swap! db assoc-in [user-id :api-key] api-key))

(defn fetch-api-key [user-id]
  (get-in @db [user-id :api-key]))


(defn- fetch-repo [user-id repo-link]
  (let [[user repo-name] (->> #"/"
                              (clojure.string/split (or repo-link ""))
                              (filter seq)
                              (drop 2))]
    (when (and user repo-name)
      (let [{:keys [status body]} (client/get (str "https://api.github.com/repos/" user "/" repo-name "/releases") 
                                              {:throw-exceptions false
                                               :headers {:Authorization (str "token" (fetch-api-key user-id))}
                                               :accept :json})

            latest-release (-> :published_at
                               (sort-by (ch/parse-string body true))
                               first)]
        (when (and (= 200 status) body)
          {:user user
           :repo-name repo-name
           :repo-link repo-link
           :latest-release latest-release})))))

(defn add-repo [user-id repo]
  (swap! db update-in [user-id :repos] 
         (fn [repos]
           (cond
             (not repos) [repo]
             (empty? (filter #(= (:repo-link %) (:repo-link repo)) repos)) (conj repos repo)
             :else repos))))

(defn remove-repo [user-id repo-link]
  (swap! db update-in [user-id :repos] 
         (fn [repos]
           (->> repos
                (mapv #(if (= repo-link (:repo-link %)) nil %))
                (filterv seq) ;; TODO: this could be smarter
                ))))

(defn update-repo [user-id {:keys [repo-link] :as repo}]
  (swap! db update-in [user-id :repos] 
         (fn [repos]
           (mapv 
             #(if (= repo-link (:repo-link %))
                repo
                %)
             repos))))

(defn save-repo [user-id repo-link]
  (when-let [repo (fetch-repo user-id repo-link)]
    (add-repo
      user-id
      (assoc repo :viewed false))))

(defn fetch-repos [user-id]
  (get-in @db [user-id :repos]))

(defn mark-seen [user-id repo-link seen?]
  (swap! db update-in [user-id :repos] 
         (fn [repos]
           (mapv 
             #(if (= repo-link (:repo-link %))
                (assoc % :viewed seen?)
                %)
             repos))))

(defn reload [user-id]
  (let [repo-links (mapv :repo-link (get-in @db [user-id :repos])) 
        fresh-repos (->> repo-links
                   (mapv #(vector % (fetch-repo user-id %)))
                   (into {}))]
    (swap! db update-in [user-id :repos] 
           #(mapv (fn [{:keys [repo-link] :as existing-repo}]
                   (let [fresh-repo (get fresh-repos repo-link)
                         latest-publish-date-str (get-in fresh-repos [repo-link :latest-release :published_at])
                         latest-publish-date (f/parse latest-publish-date-str)
                         existing-publish-date-str (get-in existing-repo [:latest-release :published_at])
                         existing-publish-date (f/parse existing-publish-date-str)]
                     (if (or 
                           (and (nil? existing-publish-date) latest-publish-date)
                           (and 
                             existing-publish-date 
                             latest-publish-date
                             (t/before? existing-publish-date latest-publish-date)))
                       (assoc fresh-repo :viewed false)
                       existing-repo))) %))))

(comment 
  (reload "123")
  
  )

(comment
  (mark-seen "123"
              "https://github.com/borkdude/jet"
             false 
             )
  )


