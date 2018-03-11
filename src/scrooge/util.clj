;; Copyright (C) 2018, Jozef Wagner. All rights reserved.

(ns scrooge.util
  "Misc utils."
  {:authors ["Jozef Wagner"]}
  (:require [clojure.string :as str]))


;;;; Public API

(defn to-double
  "Returns double from string, or nil."
  [s]
  (when-not (str/blank? s)
    (Double/parseDouble s)))

(defn pr-double
  "Returns string of a given double, rounded on 6 digits"
  [d]
  (format "%.6f" d))

(defn now
  "Returns current milliseconds."
  []
  (System/currentTimeMillis))


;;;; Scratch

(comment

  (pr-double 123.45678982345)

  )
