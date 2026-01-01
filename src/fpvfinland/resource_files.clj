(ns fpvfinland.resource-files
  (:require [clojure.java.io :as io]
            [clojure.string :as str]))

(def binary-resource-file-types
  [:jpg :png :gif :mp4])

(def text-resource-file-types
  [:html :css :js :svg])

(def fsep java.io.File/separator)

(def fsep-regex (java.util.regex.Pattern/quote fsep))

(defn- normalize-path [^String path]
  (if (= fsep "/")
    path
    (.replaceAll path fsep-regex "/")))

(defn- get-path [path]
  (normalize-path (.getPath path)))

(def resource-file-types
  (vec (concat text-resource-file-types binary-resource-file-types)))

(defn resource-file-types-as-regexp [file-types]
  (re-pattern
    (format
    ".*\\.(%s)$"
    (str/join "|" (map name file-types)))))

(defn directory-files
  "Returns a map of paths to file contents in the `dir`. `dir` should be
  accessible via `clojure.java.io/as-file`. `regexp` will be used to filter the
  files. Returns results as path+file map."
  [dir regexp]
  (let [dir (io/as-file dir)
        path-len (count (get-path dir))
        path-from-dir #(subs (get-path %) path-len)]
    (->> (file-seq dir)
         (filter #(re-find regexp (path-from-dir %)))
         (map (juxt path-from-dir identity))
         (into {}))))