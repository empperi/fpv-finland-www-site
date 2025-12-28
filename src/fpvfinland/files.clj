(ns fpvfinland.files
  (:require [clojure.java.io :as io]))

(defn list-files
  "Coerces argument x as File. If it represents a directory then
   provides a seq of files in that directory, otherwise returns nil."
  [x]
  (let [file (io/as-file x)]
    (when (.isDirectory file)
      (seq (.listFiles file)))))
