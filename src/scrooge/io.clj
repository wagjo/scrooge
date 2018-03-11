;; Copyright (C) 2018, Jozef Wagner. All rights reserved.

(ns scrooge.io
  "I/O utils"
  {:authors ["Jozef Wagner"]}
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [taoensso.timbre :refer [debug info warn error]]))


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

(defn store-map
  [dir fname meta-m m]
  (let [file (io/file dir fname)
        _ (io/make-parents file)
        wr (-> file
               io/output-stream
               java.util.zip.GZIPOutputStream.
               (java.io.OutputStreamWriter. "UTF-8")
               java.io.BufferedWriter.)]
    (.write wr (print* meta-m))
    (.write wr (int \newline))
    (doseq [x m]
      (.write wr (print* x))
      (.write wr (int \newline)))
    (.close wr)
    nil))

(defn fetch-map
  [f]
  (try
    (let [coll (when (.isFile f)
                 (let [r (-> f
                             io/input-stream
                             java.util.zip.GZIPInputStream.
                             io/reader
                             java.io.PushbackReader.)
                       ff (fn ff []
                            (lazy-seq
                             (let [x (edn/read {:eof ::done} r)]
                               (if (identical? ::done x)
                                 (do (.close r) nil)
                                 (cons x (ff))))))]
                   (ff)))
          mm (first coll)
          coll (rest coll)]
      (with-meta (into {} coll) mm))
    (catch Exception e
      (.printStackTrace e)
      (error "Exception:" e))))

(defn list-dir
  [dir]
  (let [coll (file-seq (io/file dir))
        coll (filter #(.isFile %) coll)
        coll (sort-by #(.getName %) coll)]
    coll))


;;;; Scratch

(comment

  (first
   (for [f (list-dir "ec2")]
     (fetch-map f)))

)
