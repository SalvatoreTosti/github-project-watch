(ns github-project-watch.routes.home
  (:require
    [ctmx.core :as ctmx]
    [ctmx.render :as render]
    [hiccup.page :refer [html5]]
    [github-project-watch.models.core :as models]
    [clj-http.client :as client]
    [cheshire.core :as ch]
    [clj-time.format :as f]
    [clj-time.core :as t]))

(defn html-response [body]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body body})

(defn html5-response
  ([body]
   (html-response
    (html5
     [:head
      [:meta {:name "viewport"
              :content "width=device-width, initial-scale=1, shrink-to-fit=no"}]]
     [:body (render/walk-attrs body)]
     [:script {:src "https://unpkg.com/htmx.org@1.5.0"}]
     [:script {:src "https://kit.fontawesome.com/e8c67d78ce.js" :crossorigin "anonymous"}]
     [:link {:href "https://unpkg.com/tailwindcss@^2/dist/tailwind.min.css" :rel "stylesheet"}]
     ))))

(defn nav-bar []
  [:nav {:class "relative flex flex-wrap items-center justify-between px-2 py-3 bg-blue-500 mb-3"}
   [:div {:class "container px-4 mx-auto flex flex-wrap items-center justify-between"}
    [:div {:class "relative flex justify-between w-auto px-4 static block justify-start"}
     [:a {:class "text-sm font-bold leading-relaxed inline-block mr-4 py-2 whitespace-nowrap uppercase text-white hover:opacity-75"
          :href "https://github.com/SalvatoreTosti/github-project-watch"
          :target "_"}
      "Github"]]
    [:div {:class "flex flex-grow items-center"}
     [:ul {:class "flex flex-row list-none ml-auto"}
      [:li {:class "nav-item"}
       [:a {:class "px-3 py-2 flex items-center text-xs uppercase font-bold leading-snug text-white hover:opacity-75"
            :href "http://www.saltosti.com"
            :target "_"}
        [:i {:class "text-lg leading-lg text-white"} "Salvatore"]]]]]]])

(defn submit-button
  ([title]
   (submit-button title {} nil))
  ([title button-attrs additional-classes]
  [:button 
   (cond-> 
     {:class "bg-blue-500 hover:bg-blue-700 text-white font-bold py-2 px-4 rounded"}
     additional-classes (update :class #(str % " " additional-classes))
     :always (merge button-attrs))
   [:div {:class "flex justify-center"} 
    title
    [:img
     {:class "htmx-indicator w-6 ml-1"
      :src "https://samherbert.net/svg-loaders/svg-loaders/tail-spin.svg"}]]]))
  
(defn req->user-id [req]
  (get-in req [:cookies "JSESSIONID" :value]))

(defn validate-api-key [api-key]
  (let [{:keys [status body]} (->> {:throw-exceptions false
                                    :headers {:Authorization (str "token " api-key)}
                                    :accept :json}
                                   (client/get "https://api.github.com/user"))]
    (when (= 200 status) 
      (ch/parse-string body true))))

(defn validate-repo-link [user-id repo-link]
  (let [[user repo-name] (->> #"/"
                              (clojure.string/split (or repo-link ""))
                              (filter seq)
                              (drop 2))]
    (if (and user repo-name)
      (let [{:keys [status body]} (models/http-get-authenticated 
                                    user-id 
                                    (str "https://api.github.com/repos/" user "/" repo-name "/releases"))
            {:keys [html_url created_at] :as latest-release} (-> :published_at
                                                                 (sort-by (ch/parse-string body true))
                                                                 first)]
        (cond 
          (not= 200 status) :cannot-connect
          (not body) :no-releases
          :else :valid))
      :cannot-connect)))

(ctmx/defcomponent ^:endpoint pal [req ^:string input]
  [:form
   {:hx-target "this" :hx-swap "outerHTML" :class "grid grid-cols-1 lg:grid-cols-3 gap-3 mb-3"
     :hx-post "pal"}
   [:div {:class "col-span-1  lg:col-span-2"}
    (let [{:keys [login]} (validate-api-key input)
          input-border (cond 
                         login "border-blue-500"
                         input "border-red-500"
                         :else "border-blue-500")]
      (when login
        (models/save-api-key (req->user-id req) input))
      [:div 
       (cond 
        login [:label {:class "text-green-500"} login [:div {:class "fab fa-github ml-1"}]]
        input [:label {:class "text-red-500"} "Invalid Github API Key"]
        :else [:label "Github API Key"])
      [:input
       {:name "input"
        :autocomplete "off"
        :type "password"
        :value input 
        :class (str "form-textarea w-full border rounded " input-border)
        :placeholder "Enter your Github API key..."}]])]
   (submit-button "Save API key" {} "col-span-1")])

(defn hidden-input [name value]
  [:input {:type "hidden" :name name :value value}])

(ctmx/defcomponent ^:endpoint new-toggle [req repo-link viewed]
  (if viewed 
    (do
      (models/mark-viewed (req->user-id req) repo-link true)
      "")
    [:div
     {:class "rounded-full bg-green-400 uppercase px-2 py-1 text-xs font-bold ml-2 cursor-pointer hover:opacity-75 text-white "
      :hx-trigger "click"
      :hx-swap "outerHTML"
      :hx-post "new-toggle"
      :hx-vals {:viewed true}
      :hx-target "this"}
     "new"]))

(ctmx/defcomponent ^:endpoint release-card 
  [{:keys [request-method] :as req}
   repo-name
   repo-link
   release-name
   release-description
   html-url
   published-at
   viewed]
  (if (= :delete request-method)
    (do (models/remove-repo (req->user-id req) repo-link)
        "")
    (let [id (-> repo-name
                 (clojure.string/replace #"[^a-zA-Z\d\s:]" "-")
                 gensym
                 str)] 
      [:form {:class "rounded overflow-hidden shadow-lg mt-2" :id id}
       [:div {:class "px-6 py-4"}
        [:div {:class "items-center flex mb-2"}
         [:span {:class "font-bold text-xl text-blue-500 hover:opacity-75"} [:a {:href repo-link :target "_"} repo-name]
          (hidden-input "repo-name" repo-name)
          (hidden-input "repo-link" repo-link)]
         (new-toggle req repo-link viewed)
         [:div {:class "text-gray-500 hover:opacity-75 ml-auto fas fa-times fa-lg cursor-pointer"
                :hx-delete "release-card"
                :hx-confirm (str "Are you sure you wish to stop watching " repo-name "?")
                :hx-target (str "#" id)
                :hx-swap "outerHTML"}]]
        (when (and 
                (not release-name) 
                (not published-at)
                (not release-description))
          [:div {:class "font-bold text-gray-500"}
           "no releases yet, check back later!"])
        [:a {:class "font-bold text-gray-700 hover:opacity-75" :href html-url :target "_"} release-name
         (hidden-input "release-name" release-name)
         (hidden-input "html-url" html-url)]
        (when published-at 
          (let [release-date (->> published-at
                                  f/parse
                                  (f/unparse (f/formatter "YYYY-MM-dd")))

                days-since-release (->> (t/now)
                                        (t/interval (f/parse published-at))
                                        t/in-days)
                days-string (if (> days-since-release 365) 
                              "over a year ago" 
                              (str days-since-release " days ago"))]
            [:div {:class "text-sm text-gray-600"} 
             (str release-date " (" days-string ")")
             (hidden-input "published-at" published-at)]))
        (when release-description
          [:div {:class "text-sm text-gray-600 overflow-auto mt-2"} 
           release-description
           (hidden-input "release-description" release-description)])]])))

(ctmx/defcomponent ^:endpoint repo-cards 
  [{:keys [request-method] :as req} ^:boolean reload ^:boolean reset]
  (let [_ (when reload (models/reload (req->user-id req)))
        _ (when reset (models/reset-viewed-repos (req->user-id req)))
        repos (models/fetch-repos (req->user-id req))]
    [:div {:class "grid grid-cols-1 lg:grid-cols-3 gap-4" :id "cards"}
     (for [{:keys [repo-name repo-link latest-release viewed] :as repo} repos
           :let [{release-name :name release-description :body :keys [published_at html_url tag_name]} latest-release]]
       (release-card
         req 
         repo-name
         repo-link
         (or tag_name release-name)
         release-description 
         html_url
         published_at
         viewed))]))

(ctmx/defcomponent ^:endpoint release-list [req ^:string input]
  (let [repo-status (validate-repo-link (req->user-id req) input)
        repos (models/fetch-repos (req->user-id req))
        repo-exists (-> :repo-link
                        (mapv repos)
                        set
                        (get input))]
    (when (and (= :valid repo-status) (not repo-exists))
      (models/save-repo (req->user-id req) input))
    [:div {:id "release-list"}
    [:form
     {:hx-post "release-list"
      :hx-target "#release-list"
      :hx-swap "outerHTML"
      :class  "grid-cols-1 lg:grid-cols-3 grid gap-3"}
     [:div {:class "col-span-1 lg:col-span-2"}
      (if (empty? input)
        [:label "Github Repository Link"]
        (let [label 
              (cond 
                (= :cannot-connect repo-status) [:label {:class "text-red-500"} "We couldn't find that repo!"]
                (= :no-releases repo-status) [:label {:class  "text-yellow-500"} "That repo hasn't performed any releases!"]
                repo-exists [:label {:class  "text-yellow-500"} "You're already watching that repo!"]

                (= :valid repo-status) [:label {:class "text-green-500"} "Added repo to list!"])] 
          label))
      [:input 
      {:name "input"
       :placeholder "Add a repo link here..."
       :value (if (= repo-status :valid) nil input)
       :class (let [classes " form-textarea w-full border rounded "]
                (cond 
                  (not input) (str classes "border-blue-500")
                  (not= repo-status :valid) (str classes "border-red-500")
                  :else (str classes "border-blue-500")))}]]
     (submit-button "Add repo")]
    [:div {:class "flex my-2"}
     (submit-button 
      "Check for releases..." 
      {:hx-post "repo-cards"
       :hx-target "#cards"
       :hx-swap "outerHTML"
       :hx-vals {:reload true}}
      nil) 
    [:button 
     {:class "bg-yellow-500 hover:bg-yellow-700 text-white font-bold py-2 px-4 rounded ml-3"
      :hx-post "repo-cards"
      :hx-target "#cards"
      :hx-swap "outerHTML"
      :hx-confirm (str "Are you sure you wish to reset your viewed releases?")
      :hx-vals {:reset true}}
     [:div {:class "flex justify-center"} 
      "Reset viewed releases..."
      [:img
       {:class "htmx-indicator w-6 ml-1"
        :src "https://samherbert.net/svg-loaders/svg-loaders/tail-spin.svg"}]]]]
    (repo-cards req false false)]))

(defn home-routes []
  (ctmx/make-routes
   "/"
   (fn [req]
      (html5-response
       [:div (nav-bar)
        [:div {:class "container mx-auto p-4"}
         [:div {:class "font-bold text-blue-500 text-center my-5 text-5xl"} "Github Project Watch"]
         (pal req (models/fetch-api-key (req->user-id req)))
         (release-list req nil)]]))))
