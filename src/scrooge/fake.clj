;; Copyright (C) 2018, Jozef Wagner. All rights reserved.

(ns scrooge.fake
  "Fake trading engine"
  {:authors ["Jozef Wagner"]}
  (:require [scrooge.engine :as engine]
            [scrooge.util :refer [now]]))


;;;; Implementation details

(def trade-fee
  0.001)

(defn fake-trade
  [frame order]
  (let [{:keys [:frame/stats]} frame
        {:keys [:order/operation :order/trade-amount
                :order/coin]} order
        buy? (= operation :operation/market-buy)
        stat (get stats coin)
        price (if buy? (:coin/ask-price stat) (:coin/bid-price stat))
        commission (if buy?
                     (double (* trade-amount trade-fee))
                     (double (* trade-amount price trade-fee)))]
    {:trade/price price
     :trade/amount trade-amount
     :trade/commission-amount commission
     :trade/commission-coin (if buy? coin "BTC")
     :trade/operation (if buy? :trade/buy :trade/sell)
     :trade/timestamp (now)
     :trade/type :trade/fake}))

(deftype FakeTradingEngine []
  engine/ITradingEngine
  (-execute [this frame order]
    (assoc order
           :order/type :order/fake
           :order/status :order/filled
           :order/timestamp (now)
           :order/trades [(fake-trade frame order)])))

(defmethod engine/-new-engine :engine/fake
  [_]
  (FakeTradingEngine.))
