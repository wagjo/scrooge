;; Copyright (C) 2018, Jozef Wagner. All rights reserved.

(ns scrooge.logger
  "Logging coin stats"
  {:authors ["Jozef Wagner"]}
  (:require [environ.core :refer [env]]
            [clojurewerkz.quartzite.conversion :as qc]
            [clojurewerkz.quartzite.jobs :as j]
            [clojurewerkz.quartzite.schedule.simple :as qss]
            [clojurewerkz.quartzite.scheduler :as qs]
            [clojurewerkz.quartzite.triggers :as t]
            [scrooge.binance :as binance]
            [scrooge.io :as sio]
            [taoensso.timbre :refer [debug info warn error]]))


;;;; Implementation details

(j/defjob LogStats
  [ctx]
  (try
    (let [m (qc/from-job-data ctx)
          {:strs [conn dir]} m
          coll (binance/exchange-stats! conn "BTC")
          date (System/currentTimeMillis)
          fname (str date ".edn.gz")
          mm {:dump/timestamp date :dump/interval (* 5 60 1000)}]
      (info "New dump in:" fname)
      (sio/store-coll dir fname mm coll))
    (catch Exception e
      (.printStackTrace e)
      (error "Exception:" e))))

(defn scheduler-run!
  [conn dir]
  (let [s (qs/initialize)
        s (qs/start s)
        job (j/build
             (j/of-type LogStats)
             (j/using-job-data
              {"conn" conn "dir" dir})
             (j/with-identity (j/key "jobs.logstats.1")))
        trigger (t/build
                 (t/with-identity (t/key "triggers.1"))
                 (t/start-now)
                 (t/with-schedule
                   (qss/schedule
                    (qss/repeat-forever)
                    (qss/with-interval-in-minutes 5))))]
    (qs/schedule s job trigger)))


;;;; Public API

(defn log-stats!
  [conn dir]
  (scheduler-run! conn dir)
  (info "STARTED"))

(defn -main
  [& m]
  (let [dir "out"
        conn (binance/connect!
              (env :binance-api) (env :binance-secret))]
    (log-stats! conn dir)))


;;;; Scratch

(comment

  (log-stats!)

  )
