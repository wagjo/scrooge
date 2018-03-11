;; Copyright (C) 2018, Jozef Wagner. All rights reserved.

(ns scrooge.io
  "I/O utils"
  {:authors ["Jozef Wagner"]}
  (:require [clojure.java.io :as io]))


;;;; Implementation details

(defn ^java.lang.String print*
  "Returns string representation of x in a way that is readable
  by edn reader."
  [x]
  (binding [*print-dup* false
            *print-length* false
            *print-level* false
            *print-meta* false
            *print-readably* true]
    (pr-str x)))


;;;; Public API

(defn store-coll
  [dir fname m coll]
  (let [file (io/file dir fname)
        _ (io/make-parents file)
        wr (-> file
               io/output-stream
               java.util.zip.GZIPOutputStream.
               (java.io.OutputStreamWriter. "UTF-8")
               java.io.BufferedWriter.)]
    (.write wr (print* m))
    (.write wr (int \newline))
    (doseq [x coll]
      (.write wr (print* x))
      (.write wr (int \newline)))
    (.close wr)
    nil))
