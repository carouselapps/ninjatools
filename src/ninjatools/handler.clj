(ns ninjatools.handler
  (:require [compojure.core :refer [defroutes routes wrap-routes]]
            [ninjatools.routes.home :refer [home-routes]]
            [ninjatools.routes.services :refer [service-routes]]
            [ninjatools.middleware :as middleware]
            [ninjatools.db.core :as db]
            [compojure.route :as route]
            [taoensso.timbre :as timbre]
            [taoensso.timbre.appenders.3rd-party.rotor :as rotor]
            [selmer.parser :as parser]
            [environ.core :refer [env]]
            [clojure.tools.nrepl.server :as nrepl]))

(defonce nrepl-server (atom nil))

(defroutes base-routes
           (route/resources "/")
           (route/not-found "Not Found"))

(defn parse-port [port]
  (when port
    (cond
      (string? port) (Integer/parseInt port)
      (number? port) port
      :else          (throw (Exception. (str "invalid port value: " port))))))

(defn start-nrepl
  "Start a network repl for debugging when the :nrepl-port is set in the environment."
  []
  (when-let [port (env :nrepl-port)]
    (try
      (->> port
           (parse-port)
           (nrepl/start-server :port)
           (reset! nrepl-server))
      (timbre/info "nREPL server started on port" port)
      (catch Throwable t
        (timbre/error "failed to start nREPL" t)))))

(defn stop-nrepl []
  (when-let [server @nrepl-server]
    (nrepl/stop-server server)))

(defn init
  "init will be called once when
   app is deployed as a servlet on
   an app server such as Tomcat
   put any initialization code here"
  []

  (timbre/merge-config!
    {:level     (if (env :dev) :trace :info)
     :appenders {:rotor (rotor/rotor-appender
                          {:path "ninjatools.log"
                           :max-size (* 512 1024)
                           :backlog 10})}})

  (if (env :dev) (parser/cache-off!))
  (start-nrepl)
  (db/connect!)
  (timbre/info (str
                 "\n-=[ninjatools started successfully"
                 (when (env :dev) " using the development profile")
                 "]=-")))

(defn destroy
  "destroy will be called when your application
   shuts down, put any clean up code here"
  []
  (timbre/info "ninjatools is shutting down...")
  (stop-nrepl)
  (db/disconnect!)
  (timbre/info "shutdown complete!"))

(def app-base
  (routes
    (var service-routes)
    (wrap-routes #'home-routes middleware/wrap-csrf)
    #'base-routes))

(def app (middleware/wrap-base #'app-base))
