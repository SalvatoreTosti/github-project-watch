(ns github-project-watch.routes.home
  (:require
    [ctmx.core :as ctmx]
    [ctmx.render :as render]
    [hiccup.page :refer [html5]]
    [github-project-watch.models.core :as models]
    [clj-http.client :as client]
    [cheshire.core :as ch]
    [clj-time.format :as f]
    ))

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
     [:a {:class "text-sm font-bold leading-relaxed inline-block mr-4 py-2 whitespace-nowrap uppercase text-white hover:opacity-75" :href "#"}
      "Home"]
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

(def user-id "123")

(defn validate-api-key [api-key]
  (let [response (client/get "https://api.github.com/user"  
                               {:throw-exceptions false
                                :headers {:Authorization (str "token " api-key)}
                                :accept :json})]
    (= 200 (:status response))))

(defn validate-repo-link [repo-link]
  (let [[user repo-name] (->> #"/"
                              (clojure.string/split (or repo-link ""))
                              (filter seq)
                              (drop 2))]
    (if (and user repo-name)
      (let [{:keys [status body]} (client/get (str "https://api.github.com/repos/" user "/" repo-name "/releases") 
                                              {:throw-exceptions false
                                               :headers {:Authorization (str "token " (models/fetch-api-key user-id))}
                                               :accept :json})
            
            {:keys [html_url created_at] :as latest-release} (-> :published_at
                                                                 (sort-by (ch/parse-string body true))
                                                                 first)]
        (cond 
          (not= 200 status) :cannot-connect
          (not body) :no-releases
          :else :valid))
      :cannot-connect)))

(ctmx/defcomponent ^:endpoint pal [req ^:string input]
  (models/save-api-key user-id input)
  [:form
   {:hx-target "this" :hx-swap "outerHTML" :class "grid grid-cols-1 lg:grid-cols-3 gap-3 mb-3"
     :hx-post "pal"
    }
   [:div {:class "col-span-1  lg:col-span-2"}
    [:label (if (validate-api-key input) 
              {:class "text-green-500"} 
              {:class "text-red-500"})
     "Github API Key"]

    [:input
     {:name "input"
      :autocomplete "off"
      :value input 
      :class "form-textarea w-full border-blue-500 border rounded"
      :placeholder "Enter your Github API key..."}]]
   [:button 
    {:class "col-span-1 bg-blue-500 hover:bg-blue-700 text-white font-bold py-2 px-4 rounded"}
    "Save API Key"]])

(defn hidden-input [name value]
  [:input {:type "hidden" :name name :value value}])

(ctmx/defcomponent ^:endpoint new-toggle [req repo-link viewed]
  (if viewed 
    (do
      (models/mark-seen user-id repo-link true)
      "")
    [:div
     {:class "rounded-full bg-green-400 uppercase px-2 py-1 text-xs font-bold ml-2 cursor-pointer hover:opacity-75 text-white "
      :hx-trigger "click"
      :hx-swap "outerHTML"
      :hx-post "new-toggle"
      :hx-vals {:viewed true}
      :hx-target "this"}
     "new"
     ])
  
; <span class="flex rounded-full bg-indigo-500 uppercase px-2 py-1 text-xs font-bold mr-3">New</span>
  #_(if viewed 
    (do (models/mark-seen user-id repo-link)
      [:div "seen"])
    [:label {:class "inline-flex items-center"}
     [:input {:type "checkbox" 
              :class "form-checkbox"
              :name "viewed"
              :checked viewed
              :hx-target "this"
              :hx-swap "outerHTML"
              :hx-post "new-toggle"}]
     [:span {:class "ml-2"} "new!"]]))

(ctmx/defcomponent ^:endpoint release-card 
  [{:keys [request-method] :as req}
   repo-name
   repo-link
   release-name
   release-description
   upload-url
   published-at
   viewed]
  (if (= :delete request-method)
    (do (models/remove-repo user-id repo-link)
        "")
    (let [id (str (gensym repo-name))] 
      [:form {:class "rounded overflow-hidden shadow-lg mt-2" :id id}
       [:div {:class "px-6 py-4"}
        [:div {:class "items-center flex mb-2"}
         [:span {:class "font-bold text-xl text-blue-500 hover:opacity-75"} [:a {:href repo-link} repo-name]
          (hidden-input "repo-name" repo-name)
          (hidden-input "repo-link" repo-link)]
         (new-toggle req repo-link viewed)
         [:div {:class "text-gray-500 hover:opacity-75 ml-auto fas fa-times fa-lg cursor-pointer"
                :hx-delete "release-card"
                :hx-target (str "#" id)
                :hx-swap "outerHTML"
                }]]
        (when (and 
                (not release-name) 
                (not published-at)
                (not release-description))
          [:div {:class "font-bold text-gray-500"}
           "no releases yet, check back later!"])
        [:a {:class "font-bold text-gray-700" :href upload-url} release-name
         (hidden-input "release-name" release-name)
         (hidden-input "upload-url" upload-url)]
        (when published-at 
          [:div {:class "text-sm text-gray-600"} 
           (->> published-at
                f/parse
                (f/unparse (f/formatter "YYYY-MM-dd"))
                #_(str "latest release date: "))
           (hidden-input "published-at" published-at)])
        (when release-description
          [:div {:class "text-sm text-gray-600 overflow-auto"} 
           release-description
           (hidden-input "release-description" release-description)])
        ]])))


(ctmx/defcomponent ^:endpoint repo-cards [req ^:boolean reload]
  (let [_ (when reload (models/reload user-id))
        repos (models/fetch-repos user-id)]
    [:div {:class "grid grid-cols-1 lg:grid-cols-3 gap-4" :id "cards"}
     (for [{:keys [repo-name repo-link latest-release viewed] :as repo} repos
           :let [{release-name :name release-description :body :keys [published_at upload_url tag_name]} latest-release]]
       (release-card
         req 
         repo-name
         repo-link
         (or tag_name release-name)
         release-description 
         upload_url
         published_at
         viewed))]))

(ctmx/defcomponent ^:endpoint release-list [req ^:string input]
  (let [repo-status (validate-repo-link input)
        _ (when (= :valid repo-status)
            (models/save-repo user-id input))
        repos (models/fetch-repos user-id)]
    [:div {:id "release-list"}
    [:form
     {:hx-post "release-list"
      :hx-target "#release-list"
      :hx-swap "outerHTML"
      :class  "grid-cols-1 lg:grid-cols-3 grid gap-3"}
     [:div {:class "col-span-1 lg:col-span-2"}
      (when input
        (let [label (case repo-status 
                      :cannot-connect [:label {:class "text-red-500"} "We couldn't find that repo!"]
                      :no-releases [:label {:class  "text-yellow-500"} "That repo hasn't performed any releases!"]
                      :valid [:label {:class "text-green-500"} "Added repo to list!"])] 
          label))

    
      [:input 
      {:name "input"
       :placeholder "Add a repo link here.."
       :value (if (= repo-status :valid) nil input)
       :class (let [classes " form-textarea w-full border rounded "]
                (cond 
                  (not input) (str classes "border-blue-500")
                  (not= repo-status :valid) (str classes "border-red-500")
                  :else (str classes "border-blue-500")))}]]
     [:button 
      {:class "col-span-1 bg-blue-500 hover:bg-blue-700 text-white font-bold py-2 px-4 rounded"}
      [:div {:class "flex items-center justify-center "} "Submit"
      [:img
        {:class "htmx-indicator w-6 ml-1"
         :src "https://samherbert.net/svg-loaders/svg-loaders/tail-spin.svg"}]]]]
    [:button 
     {:hx-post "repo-cards"
      :hx-target "#cards"
      :hx-swap "outerHTML"
      :hx-vals {:reload true}
      :class "bg-blue-500 hover:bg-blue-700 text-white font-bold my-2 py-2 px-4 rounded"}
      [:div {:class "flex items-center"} 
       "Check releases..."
       [:img
        {:class "htmx-indicator w-6 ml-1"
         :src "https://samherbert.net/svg-loaders/svg-loaders/tail-spin.svg"}]]]
    (repo-cards req false)]))

; <div class="max-w-sm w-full lg:max-w-full lg:flex">
;   <div class="h-48 lg:h-auto lg:w-48 flex-none bg-cover rounded-t lg:rounded-t-none lg:rounded-l text-center overflow-hidden" style="background-image: url('/img/card-left.jpg')" title="Woman holding a mug">
;   </div>
;   <div class="border-r border-b border-l border-gray-400 lg:border-l-0 lg:border-t lg:border-gray-400 bg-white rounded-b lg:rounded-b-none lg:rounded-r p-4 flex flex-col justify-between leading-normal">
;     <div class="mb-8">
;       <p class="text-sm text-gray-600 flex items-center">
;         <svg class="fill-current text-gray-500 w-3 h-3 mr-2" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20">
;           <path d="M4 8V6a6 6 0 1 1 12 0v2h1a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H3a2 2 0 0 1-2-2v-8c0-1.1.9-2 2-2h1zm5 6.73V17h2v-2.27a2 2 0 1 0-2 0zM7 6v2h6V6a3 3 0 0 0-6 0z" />
;         </svg>
;         Members only
;       </p>
;       <div class="text-gray-900 font-bold text-xl mb-2">Can coffee make you a better developer?</div>
;       <p class="text-gray-700 text-base">Lorem ipsum dolor sit amet, consectetur adipisicing elit. Voluptatibus quia, nulla! Maiores et perferendis eaque, exercitationem praesentium nihil.</p>
;     </div>
;     <div class="flex items-center">
;       <img class="w-10 h-10 rounded-full mr-4" src="/img/jonathan.jpg" alt="Avatar of Jonathan Reinink">
;       <div class="text-sm">
;         <p class="text-gray-900 leading-none">Jonathan Reinink</p>
;         <p class="text-gray-600">Aug 18</p>
;       </div>
;     </div>
;   </div>
; </div>
(defn home-routes []
  (ctmx/make-routes
   "/"
   (fn [req]
      (html5-response
       [:div (nav-bar)
        [:div {:class "container mx-auto p-4"}
         [:div {:class "font-bold text-blue-500 text-center my-5 text-5xl"} "Github Project Watch"]
         (pal req (models/fetch-api-key user-id))
         (release-list req nil)
         ]]))))
