;; Copyright (C) 2018, Jozef Wagner. All rights reserved.

(ns scrooge.binance
  "Handling Binance API"
  {:authors ["Jozef Wagner"]}
  (:require [clojure.string :as str]
            [environ.core :refer [env]]
            [scrooge.util :refer [now pr-double to-double]]
            [taoensso.timbre :refer [debug info warn error]])
  (:import [com.binance.api.client BinanceApiClientFactory]
           [com.binance.api.client.domain.account NewOrder]))


;;;; Implementation details

(defn coin-stats
  [bean quote]
  (let [{:keys [:volume :lastPrice :symbol
                :askPrice :bidPrice :priceChangePercent]} bean
        cname (when (str/ends-with? symbol "BTC")
                (subs symbol 0 (- (count symbol) 3)))
        vol (* (to-double volume) (to-double lastPrice))
        m (dissoc bean :class)]
    (assoc m
           :coin/quote quote
           :coin/coin cname
           :coin/volume (to-double volume)
           :coin/btc-volume vol
           :coin/ask-price (to-double askPrice)
           :coin/bid-price (to-double lastPrice)
           :coin/change (to-double priceChangePercent))))

(defn assoc-binance-order
  [order]
  (let [{:keys [:order/operation :order/trade-amount
                :order/coin]} order
        symbol (str coin "BTC")
        amount (pr-double trade-amount)
        new-order (case operation
                    :operation/market-buy
                    (NewOrder/marketBuy symbol amount)
                    :operation/market-sell
                    (NewOrder/marketSell symbol amount))]
    (assoc order :binance/new-order new-order)))


;;;; Public API

(defn connect!
  "Returns connection handler."
  [api secret]
  (.newRestClient (BinanceApiClientFactory/newInstance api secret)))

(defn exchange-stats!
  "Returns exchange stats."
  [conn quote]
  (let [coll (.getAll24HrPriceStatistics conn)
        coll (map (comp #(coin-stats % quote) bean) coll)
        coll (filter :coin/coin coll)]
    (zipmap (map :coin/coin coll) coll)))
