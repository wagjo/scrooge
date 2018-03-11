;; Copyright (C) 2018, Jozef Wagner. All rights reserved.

(ns scrooge.binance
  "Handling Binance API"
  {:authors ["Jozef Wagner"]}
  (:require [clojure.string :as str]
            [environ.core :refer [env]]
            [taoensso.timbre :refer [debug info warn error]])
  (:import [com.binance.api.client BinanceApiClientFactory]
           [com.binance.api.client.domain.account NewOrder]))


;;;; Public API

(defn connect!
  [api secret]
  (.newRestClient (BinanceApiClientFactory/newInstance api secret)))

(defn to-double
  [s]
  (when-not (str/blank? s)
    (Double/parseDouble s)))

(defn pr-double
  [d]
  (format "%.6f" d))

(defn now
  []
  (System/currentTimeMillis))

(defn coin-stats
  [bean]
  (let [{:keys [:volume :lastPrice :symbol
                :askPrice :bidPrice :priceChangePercent]} bean
        cname (when (str/ends-with? symbol "BTC")
                (subs symbol 0 (- (count symbol) 3)))
        vol (* (to-double volume) (to-double lastPrice))
        m (dissoc bean :class)]
    (assoc m
           :coin/coin cname
           :coin/volume (to-double volume)
           :coin/btc-volume vol
           :coin/ask-price (to-double askPrice)
           :coin/bid-price (to-double lastPrice)
           :coin/change (to-double priceChangePercent))))

(defn current-stats
  [conn]
  (let [coll (.getAll24HrPriceStatistics conn)
        coll (map (comp coin-stats bean) coll)
        coll (filter :coin/coin coll)]
    (zipmap (map :coin/coin coll) coll)))

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

(defn assoc-timestamp
  [frame]
  (assoc frame :frame/timestamp (now)))

(defn assoc-wealth
  [frame]
  (let [{:keys [:frame/stats :frame/wallet]} frame
        mf (fn [[sym v]]
             (if (= "BTC" sym)
               v
               (* v (:coin/ask-price (get stats sym)))))]
    (assoc frame :frame/wealth (reduce + (map mf wallet)))))

(defn archive-orders
  [frame]
  (let [{:keys [:frame/to-order]} frame]
    (assoc frame
           :frame/to-order nil
           :frame/to-order-archive to-order)))

(defn coin-amount
  [stats coin btc-amount buy?]
  (let [stat (get stats coin)
        price (if buy? (:coin/ask-price stat) (:coin/bid-price stat))]
    (/ btc-amount price)))

(defn assoc-analytics
  [frame]
  (let [{:keys [:frame/before-wallet :frame/strategy
                :frame/stats]} frame
        {:keys [:strategy/trade-amount :strategy/max-coin-btc-balance
                :strategy/min-btc-volume]} strategy
        mf (fn [[k v]]
             (when-not (= "BTC" k)
               (let [m (get stats k)]
                 (assoc m
                        :wallet/btc-amount
                        (if (= "BTC" k)
                          v
                          (* v (:coin/ask-price m)))))))
        coins (keep mf before-wallet)
        buy-removef
        (fn [coin]
          (or
           (< (:coin/btc-volume coin) min-btc-volume)
           (< max-coin-btc-balance (:wallet/btc-amount coin))))
        buy-eligible (remove buy-removef coins)
        best-buy (sort-by :coin/change buy-eligible)
        sell-removef
        (fn [coin]
          (or (< (:coin/btc-volume coin) min-btc-volume)
              (< (:wallet/btc-amount coin) trade-amount)))
        sell-eligible (remove sell-removef coins)
        best-sell (sort-by :coin/change > sell-eligible)
        analytics {:analytics/best-buy (mapv :coin/coin best-buy)
                   :analytics/best-sell (mapv :coin/coin best-sell)}]
    (assoc frame :frame/analytics analytics)))

;; create order request
;; execute order - FAKEIT
;; wait until order is filled - FAKEIT
;; handle unfinished/failed order
;; fetch order trades - FAKEIT
;; compute new wallet, mind fees
;; archive executed orders and trades

(defprotocol IBinanceEngine
  (-execute [this conn frame order]))

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
        commision (if buy?
                    (double (* trade-amount trade-fee))
                    (double (* trade-amount price trade-fee)))]
    {:trade/price price
     :trade/amount trade-amount
     :trade/commision-amount commision
     :trade/commision-coin (if buy? coin "BTC")
     :trade/operation (if buy? :trade/buy :trade/sell)
     :trade/timestamp (now)
     :trade/type :trade/fake}))

(deftype FakeBinanceEngine []
  IBinanceEngine
  (-execute [this conn frame order]
    (assoc order
           :order/type :order/fake
           :order/status :order/filled
           :order/timestamp (now)
           :order/trades [(fake-trade frame order)])))

(defn execute!
  [engine conn frame order]
  (-execute engine conn frame order))

(defn engine
  [frame]
  (case (:frame/engine frame)
    :engine/fake (FakeBinanceEngine.)
    ;; TODO
    :engine/live (FakeBinanceEngine.)))

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

(defn order-success?
  [order]
  (= :order/filled (:order/status order)))

(defn valid-frame?
  [frame]
  (let [{:keys [:frame/failed-orders :frame/wallet
                :frame/before-wallet]} frame
        balances (concat (vals wallet) (vals before-wallet))]
    (and (not-any? neg? balances)
         (empty? failed-orders))))

(defn trade-deltas
  [coin trade]
  (let [{:keys [:trade/price :trade/amount :trade/commision-amount
                :trade/commision-coin :trade/operation]} trade]
    (if (= :trade/buy operation)
      [[coin amount]
       ["BTC" (- (* amount price))]
       [commision-coin (- commision-amount)]]
      [[coin (- amount)]
       ["BTC" (* amount price)]
       [commision-coin (- commision-amount)]])))

(defn update-wallet
  [wallet trade-delta]
  (let [[coin amount] trade-delta]
    (update wallet coin + amount)))

(defn execute-order
  [conn frame order]
  (if (valid-frame? frame)
    (let [engine (engine frame)
          order (assoc-binance-order order)
          order (execute! engine conn frame order)
          {:keys [:frame/executed-orders
                  :frame/failed-orders
                  :frame/wallet]} frame]
      (if (order-success? order)
        (let [{:keys [:order/coin :order/trades]} order
              deltas (mapcat #(trade-deltas coin %) trades)
              wallet (reduce update-wallet wallet deltas)]
          (info "ORDER SUCCESS")
          (assoc frame
                 :frame/executed-orders
                 (cons order executed-orders)
                 :frame/wallet wallet))
        (reduced
         (assoc frame
                :frame/failed-orders (cons order failed-orders)))))
    (reduced frame)))

(defn new-frame
  [stats starting-wallet strategy engine]
  (-> {:frame/wallet starting-wallet
       :frame/stats stats
       :frame/strategy strategy
       :frame/engine engine}
      assoc-wealth
      assoc-timestamp))

(defn pick-orders
  [frame]
  (let [{:keys [:frame/analytics :frame/strategy :frame/stats]} frame
        {:keys [:strategy/trade-amount :strategy/sell-high
                :strategy/buy-low]} strategy
        {:keys [:analytics/best-buy :analytics/best-sell]} analytics
        mf (fn [coin buy?]
             {:order/operation (if buy?
                                 :operation/market-buy
                                 :operation/market-sell)
              :order/trade-amount
              (coin-amount stats coin trade-amount buy?)
              :order/coin coin})
        sells (take (or sell-high 0) best-sell)
        sellset (set sells)
        buys (take (min (inc (count sells)) (or buy-low 0))
                   (remove sellset best-buy))
        sells (map #(mf % false) sells)
        buys (map #(mf % true) buys)
        orders (vec (concat sells buys))]
    (assoc frame :frame/to-order orders)))

(defn prepare-next-frame
  [stats frame]
  (let [{:keys [:frame/strategy :frame/wallet]} frame
        frame (assoc frame
                     :frame/wallet nil
                     :frame/timestamp nil
                     :frame/executed-orders nil
                     :frame/before-wallet wallet
                     :frame/stats stats
                     :frame/strategy strategy
                     :frame/prepare-timestamp (now))]
    (-> frame
        assoc-analytics
        pick-orders)))

(defn execute-frame!
  [conn frame]
  (let [stats (current-stats conn)
        frame (prepare-next-frame stats frame)
        {:keys [:frame/to-order :frame/before-wallet]} frame
        frame (assoc frame
                     :frame/wallet before-wallet
                     :frame/stats stats)
        frame (reduce #(execute-order conn % %2) frame to-order)]
    (-> frame
        assoc-wealth
        assoc-timestamp
        archive-orders)))


;;;; Scratch

(comment

  (def conn (connect! (env :binance-api) (env :binance-secret)))

  (def f0
    (new-frame (current-stats conn)
               my-starting-wallet
               my-strategy
               :engine/fake))

  f0

  (prepare-next-frame (current-stats conn) f0)

  (def f1 (execute-frame! conn f0))
  (def f2 (execute-frame! conn f1))
  (def f3 (execute-frame! conn f2))
  (def f4 (execute-frame! conn f3))
  (def f5 (execute-frame! conn f4))
  (def f6 (execute-frame! conn f5))
  (def f7 (execute-frame! conn f6))
  (def f8 (execute-frame! conn f7))
  (def f9 (execute-frame! conn f8))

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
