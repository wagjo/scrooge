;; Copyright (C) 2018, Jozef Wagner. All rights reserved.

(ns scrooge.specs
  "Specs."
  {:authors ["Jozef Wagner"]}
  (:require [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]))


;;;; Specs

;;; Stats

(s/def :coin/quote
  string?)

(s/def :coin/coin
  string?)

(s/def :coin/volume
  double?)

(s/def :coin/btc-volume
  double?)

(s/def :coin/price
  double?)

(s/def :coin/ask-price
  :coin/price)

(s/def :coin/bid-price
  :coin/price)

(s/def :coin/change
  double?)

(s/def :coin/stat
  (s/keys :req [:coin/coin :coin/volume :coin/btc-volume
                :coin/ask-price :coin/bid-price :coin/change]))

;;; Trade

(s/def :trade/price
  :coin/price)

(s/def :trade/amount
  double?)

(s/def :trade/commission-amount
  double?)

(s/def :trade/commission-coin
  string?)

(s/def :trade/operation
  #{:trade/buy :trade/sell})

(s/def :trade/timestamp
  integer?)

(s/def :trade/type
  keyword?)

(s/def :trade/trade
  (s/keys :req [:trade/price :trade/amount
                :trade/commission-amount :trade/commission-coin
                :trade/operation :trade/timestamp :trade/type]))

;;; Order

(s/def :order/type
  keyword?)

(s/def :order/status
  #{:order/filled})

(s/def :order/timestamp
  integer?)

(s/def :order/trades
  (s/coll-of :trade/trade))

(s/def :order/operation
  #{:operation/market-buy :operation/market-sell})

(s/def :order/coin
  :coin/coin)

(s/def :order/trade-amount
  double?)

(s/def :order/order
  (s/keys :req [:order/type :order/status :order/timestamp
                :order/trades :order/operation :order/coin
                :order/trade-amount]))

;;; Strategy



;;; Frame

(s/def :frame/stats
  (s/map-of string? :coin/stat))

(s/def :frame/frame
  (s/keys :req [:frame/stats]))
