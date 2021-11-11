(ns github-project-watch.routes.home
  (:require
    [ctmx.core :as ctmx]
    [ctmx.render :as render]
    [hiccup.page :refer [html5]]
    [github-project-watch.models.core :as models]
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
     [:link {:href "https://unpkg.com/tailwindcss@^2/dist/tailwind.min.css" :rel "stylesheet"}]
     ))))

(defn nav-bar []
  [:nav {:class "relative flex flex-wrap items-center justify-between px-2 py-3 bg-blue-500 mb-3"}
   [:div {:class "container px-4 mx-auto flex flex-wrap items-center justify-between"}
    [:div {:class "relative flex justify-between w-auto px-4 static block justify-start"}
     [:a {:class "text-sm font-bold leading-relaxed inline-block mr-4 py-2 whitespace-nowrap uppercase text-white hover:opacity-75" :href "#"}
      "Home"]
     [:a {:class "text-sm font-bold leading-relaxed inline-block mr-4 py-2 whitespace-nowrap uppercase text-white hover:opacity-75"
          :href "/api/api-docs/"
          :target "_"}
      "API"]
     [:a {:class "text-sm font-bold leading-relaxed inline-block mr-4 py-2 whitespace-nowrap uppercase text-white hover:opacity-75" 
          :href "https://www.colorhunt.co/"
          :target "_"}
      "colorhunt.co" ]]
    [:div {:class "flex flex-grow items-center"}
     [:ul {:class "flex flex-row list-none ml-auto"}
      [:li {:class "nav-item"}
       [:a {:class "px-3 py-2 flex items-center text-xs uppercase font-bold leading-snug text-white hover:opacity-75"
            :href "http://www.saltosti.com"
            :target "_"}
        [:i {:class "text-lg leading-lg text-white"} "Salvatore"]]]]]]])

(def user-id "123")

(ctmx/defcomponent ^:endpoint root [req ^:int num-clicks]
  [:div.m-3 {:hx-post "root"
             :hx-swap "outerHTML"
             :hx-vals {:num-clicks (inc num-clicks)}}
   "You have clicked me " num-clicks " times."])

(ctmx/defcomponent ^:endpoint pal [req ^:string input]
  (models/save-api-key user-id input)
  
  [:form
   {:hx-target "this" :hx-swap "outerHTML" :class "grid justify-center items-center grid-flow-col grid-cols-3 gap-5 mb-3"

     :hx-post "pal"
    }
   [:div {:class "col-span-2"}
    [:label "Github API Key"]
    [:input
     {:name "input"
      :autocomplete "off"
      :value input 
      :class "form-textarea w-full border-blue-500 border rounded"
      :placeholder "Enter your Github API key..."}]]
   [:button 
    {:class "bg-blue-500 hover:bg-blue-700 text-white font-bold py-2 px-4 rounded"}
    "Submit"]])

(defn hidden-input [name value]
  [:input {:type "hidden" :name name :value value}]
  )

(ctmx/defcomponent ^:endpoint release-card 
  [req repo-name release-version last-release-date release-notes viewed]
  (println viewed)
  (let [id (str (gensym repo-name))] 
    [:form {:class "rounded overflow-hidden shadow-lg col-span-1" :id id}
     [:div {:class "px-6 py-4"}
      [:div {:class "font-bold text-xl mb-2"} [:a {:href "https://github.com"} repo-name]
       (hidden-input "repo-name" repo-name)]
      (when (not viewed)
      [:label {:class "inline-flex items-center"}
       [:input {:type "checkbox" 
                :class "form-checkbox"
                :name "viewed"
                :checked viewed
                :hx-target (str "#" id)
                :hx-post "release-card"}]
       [:span {:class "ml-2"} "new!"]])
      [:div {:class "font-bold text-xl mb-2"} release-version
       (hidden-input "release-version" release-version)]
      [:div {:class "font-bold text-lg mb-1"} (str "Last release date: " last-release-date)
       (hidden-input "last-release-date" last-release-date)
       ]
      [:div {:class "font-bold text-lg mb-1"} (str "Release notes: " release-notes)
       (hidden-input "release-notes" release-notes)
       ]]])
  )

(ctmx/defcomponent ^:endpoint release-list [req ^:string input]
  (let [valid-repo? (= input "good")
        repos [{:repo-name "test" :last-release-date "2021-11-10" :release-version "1.0.0"}
               {:repo-name "test" :last-release-date "2021-11-10" :release-version "1.0.0"}
               {:repo-name "test" :last-release-date "2021-11-10" :release-version "1.0.0"}
               ; {:name "test" :last-release-date "2021-11-10" :release-version "1.0.0"}
               ; {:name "test" :last-release-date "2021-11-10" :release-version "1.0.0"}
               
               ]]
    [:div {:class "grid-cols-3" :id "release-list"}
    [:form
     {:hx-post "release-list"
      :hx-target "#release-list"
      :hx-swap "outerHTML"}
     [:input 
      {:name "input"
       :placeholder "Add a Repo link here.."
       :value (if valid-repo? nil input)
       :class "form-textarea w-full border-blue-500 border rounded"}]
     [:button 
      {:class "bg-blue-500 hover:bg-blue-700 text-white font-bold py-2 px-4 rounded"}
      "Submit"]
     (when (not valid-repo?)
       [:div "we couldn't find that repo!"]
       )]
    [:button 
     {:hx-post "release-list"
      :hx-target "#release-list"
      :hx-swap "outerHTML"
      :class "bg-blue-500 hover:bg-blue-700 text-white font-bold py-2 px-4 rounded"}
      "Reload..."]

    [:div {:class "grid justify-center items-center grid-flow-col grid-cols-3 gap-5 mb-3"}
     (for [{:keys [repo-name release-version last-release-date release-notes] :as repo} repos]
       (release-card req repo-name release-version last-release-date release-notes false)
       #_(release-card repo)
       
      
      )]
     


   ])
  
  )

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
