;; Copyright (C) 2018, Jozef Wagner. All rights reserved.

(ns scrooge.scratch
  "Scrooge!"
  {:authors ["Jozef Wagner"]}
  (:require [environ.core :refer [env]]
            [scrooge.binance :as binance]
            [scrooge.core :as scrooge]
            [scrooge.engine :as engine]
            [scrooge.fake]
            [taoensso.timbre :refer [debug info warn error]]))


(def my-starting-wallet
  {"BTC" 1
   "NANO" 0
   "ETH" 0
   "LTC" 0
   "NEO" 0
   "BCC" 0
   "TNT" 0
   "FUN" 0})

;; TODO: configurable BTC/ETH/USDT trade

(def my-strategy
  {:strategy/trade-amount 0.01
   :strategy/sell-high 2
   :strategy/buy-low 2
   :strategy/min-btc-volume 100
   :strategy/max-coin-btc-balance 10})

(comment

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
