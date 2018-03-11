;; Copyright (C) 2018, Jozef Wagner. All rights reserved.

(ns scrooge.simulate
  "Simulate trading strategy"
  {:authors ["Jozef Wagner"]}
  (:require [environ.core :refer [env]]
            [scrooge.binance :as binance]
            [scrooge.core :as scrooge]
            [scrooge.engine :as engine]
            [scrooge.io :as sio]
            [scrooge.fake]
            [scrooge.util :refer [pr-double]]
            [taoensso.timbre :refer [debug info warn error]]))

(def my-starting-wallet
  {"BTC" 1
   "NANO" 1000
   "ETH" 10
   "TRX" 10000
   "LTC" 100
   "NEO" 100
   "XRP" 10000
   "ZIL" 10000
   "ONT" 1000
   "NCASH" 1000
   "BCC" 1000
   "EOS" 1000
   "WTC" 1000
   "ADA" 1000
   "VEN" 1000
   "ICX" 1000
   "DGD" 1000
   "MTL" 1000
   "BCPT" 1000
   "IOST" 1000
   "GVT" 1000
   "XMR" 1000
   "ETC" 1000
   "XLM" 1000
   "IOTA" 1000
   "XVG" 1000
   "ZRX" 1000
   "OMG" 1000
   "NEBL" 1000
   "LSK" 1000
   "DASH" 1000
   "BQX" 1000
   "QTUM" 1000
   "VIBE" 1000
   "SUB" 1000
   "POE" 1000
   "SALT" 1000
   "PPT" 1000
   "ARN" 1000
   "ENG" 1000})

;; TODO: configurable BTC/ETH/USDT trade

(def my-strategy
  {:strategy/trade-amount 0.1
   :strategy/sell-high 5
   :strategy/buy-low 5
   :strategy/min-btc-volume 100
   :strategy/max-coin-btc-balance 10})

(defn stored-stats
  [dir]
  (for [f (sio/list-dir dir)]
    (sio/fetch-map f)))

(defn simulate
  [starting-wallet strategy stats]
  (let [stats (take-nth 10 (rest stats))
        frame (scrooge/new-frame
               (first stats) starting-wallet strategy :engine/fake)
        frame (assoc frame :simulation/gains 0.0)
        engine (engine/engine frame)
        gainf #(* 100
                  (-
                   (/ (:frame/wealth
                       (scrooge/assoc-wealth % %2))
                      (:frame/wealth
                       (scrooge/assoc-wealth frame %2)))
                   1))
        rf #(let [new-frame (scrooge/execute-frame! engine %2 %)]
              (assoc new-frame :simulation/gains
                     (gainf new-frame %2)))
        frames (reductions rf frame stats)]
    (map (comp pr-double :simulation/gains) frames)))


;;;; Scratch

(comment

  (def stats (vec (stored-stats "ec2")))

  (simulate my-starting-wallet
            my-strategy
            stats)

  (double (/ (* 5 221) 60 24))

  (count (stored-stats "ec2"))

  (def conn
    (binance/connect! (env :binance-api) (env :binance-secret)))

  conn

  (def stats (binance/exchange-stats! conn "BTC"))

  stats

  (def f0
    (scrooge/new-frame
     stats my-starting-wallet my-strategy :engine/fake))

  f0

  (def engine
    (engine/engine f0))

  engine

  (def stats (stored-stats "out"))

  (scrooge/prepare-next-frame stats f0)

  (def f1 (scrooge/execute-frame! engine stats f0))
  (def f2 (scrooge/execute-frame! engine stats f1))
  (def f3 (scrooge/execute-frame! engine stats f2))
  (def f4 (scrooge/execute-frame! engine stats f3))
  (def f5 (scrooge/execute-frame! engine stats f4))
  (def f6 (scrooge/execute-frame! engine stats f5))
  (def f7 (scrooge/execute-frame! engine stats f6))
  (def f8 (scrooge/execute-frame! engine stats f7))
  (def f9 (scrooge/execute-frame! engine stats f8))

  f1
  f2
  f3
  f4
  f5
  f6
  f7
  f8
  f9


  (def trades
    (map bean (.getMyTrades conn "TRXBTC" (int 20))))

  trades

)
