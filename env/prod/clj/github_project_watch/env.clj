(ns github-project-watch.env
  (:require [clojure.tools.logging :as log]))

(def defaults
  {:init
   (fn []
     (log/info "\n-=[github-project-watch started successfully]=-"))
   :stop
   (fn []
     (log/info "\n-=[github-project-watch has shut down successfully]=-"))
   :middleware identity})
