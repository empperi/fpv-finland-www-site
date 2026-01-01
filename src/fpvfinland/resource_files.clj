(ns fpvfinland.resource-files
  (:require [clojure.string :as str]))

(def resource-file-types
  [:html :css :js :jpg :png :gif :svg :mp4])

(defn resource-file-types-as-regexp []
  (re-pattern
    (format
    ".*\\.(%s)$"
    (str/join "|" (map name resource-file-types)))))