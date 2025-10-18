(ns fpvfinland.articles
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [commonmark-hiccup.core :as cmh])
  (:import (java.io File)))

(defn get-articles []
  (for [^File f (seq (.listFiles (io/as-file "./resources/public/posts")))]
    (let [date    (str/replace (.getName f) #"([0-9]+-[0-9]+-[0-9]+)-(.*)" "$1")
          content (slurp f)]
      {:date    date
       :file    (.getName f)
       :title   (last (flatten (cmh/markdown->hiccup (first (str/split-lines content)))))
       :content content})))

(defn article-link [article]
  (str "/artikkelit/" (str/replace (:file article) ".md" ".html")))
