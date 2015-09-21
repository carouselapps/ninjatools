;;;; Copyright © 2015 Carousel Apps, Ltd. All rights reserved.

(ns ninjatools.core
  (:require [ninjatools.handler :refer [app init destroy parse-port]]
            [immutant.web :as immutant]
            [ninjatools.db.migrations :as migrations]
            [taoensso.timbre :as timbre]
            [environ.core :refer [env]])
  (:gen-class))

(defn http-port [port]
  (parse-port (or port (env :port) 3000)))

(defonce server (atom nil))

(defn start-server [port]
  (init)
  (reset! server (immutant/run app
                               :host (if (env :production) "0.0.0.0" "localhost")
                               :port port)))

(defn stop-server []
  (when @server
    (destroy)
    (immutant/stop @server)
    (reset! server nil)))

(defn start-app [[port]]
  (.addShutdownHook (Runtime/getRuntime) (Thread. stop-server))
  (start-server (http-port port))
  (timbre/info "server started on port:" (str (:host @server) ":" (:port @server))))

(defn -main [& args]
  (cond
    (some #{"migrate" "rollback"} args) (migrations/migrate args)
    :else (start-app args)))
  
