;; Copyright (C) 2018, Jozef Wagner. All rights reserved.

(ns scrooge.engine
  "Trading engine"
  {:authors ["Jozef Wagner"]})


;;;; SPI

(defprotocol ITradingEngine
  (-execute [this frame order]))

(defmulti -new-engine :frame/engine)


;;;; API

(defn execute!
  [engine frame order]
  (-execute engine frame order))

(defn engine
  [frame]
  (-new-engine frame))
