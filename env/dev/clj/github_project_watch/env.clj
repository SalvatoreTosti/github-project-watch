(ns github-project-watch.env
  (:require
    [selmer.parser :as parser]
    [clojure.tools.logging :as log]
    [github-project-watch.dev-middleware :refer [wrap-dev]]))

(def defaults
  {:init
   (fn []
     (parser/cache-off!)
     (log/info "\n-=[github-project-watch started successfully using the development profile]=-"))
   :stop
   (fn []
     (log/info "\n-=[github-project-watch has shut down successfully]=-"))
   :middleware wrap-dev})
