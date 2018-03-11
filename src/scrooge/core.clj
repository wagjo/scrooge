;; Copyright (C) 2018, Jozef Wagner. All rights reserved.

(ns scrooge.core
  "Get Rich Quick"
  {:authors ["Jozef Wagner"]}
  (:require [clojure.string :as str]
            [environ.core :refer [env]]
            [scrooge.engine :as engine]
            [scrooge.util :refer [now to-double pr-double]]
            [taoensso.timbre :refer [debug info warn error]]))


;;;; Implementation details

(defn assoc-timestamp
  [frame]
  (assoc frame :frame/timestamp (now)))

(defn assoc-wealth
  ([frame]
   (assoc-wealth frame (:frame/stats frame)))
  ([frame stats]
   (let [{:keys [:frame/wallet]} frame
         mf (fn [[sym v]]
              (if (= "BTC" sym)
                v
                (* v (:coin/ask-price (get stats sym)))))]
     (assoc frame :frame/wealth (reduce + (map mf wallet))))))

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
  (let [{:keys [:trade/price :trade/amount :trade/commission-amount
                :trade/commission-coin :trade/operation]} trade]
    (if (= :trade/buy operation)
      [[coin amount]
       ["BTC" (- (* amount price))]
       [commission-coin (- commission-amount)]]
      [[coin (- amount)]
       ["BTC" (* amount price)]
       [commission-coin (- commission-amount)]])))

(defn update-wallet
  [wallet trade-delta]
  (let [[coin amount] trade-delta]
    (update wallet coin + amount)))

(defn execute-order
  [engine frame order]
  (if (valid-frame? frame)
    (let [order (engine/execute! engine frame order)
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


;;;; Public API

(defn new-frame
  [stats starting-wallet strategy engine]
  (-> {:frame/wallet starting-wallet
       :frame/stats stats
       :frame/strategy strategy
       :frame/engine engine}
      assoc-wealth
      assoc-timestamp))

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
  [engine stats frame]
  (let [frame (prepare-next-frame stats frame)
        {:keys [:frame/to-order :frame/before-wallet]} frame
        frame (assoc frame
                     :frame/wallet before-wallet
                     :frame/stats stats)
        frame (reduce #(execute-order engine % %2) frame to-order)]
    (-> frame
        assoc-wealth
        assoc-timestamp
        archive-orders)))
