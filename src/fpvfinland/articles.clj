(ns fpvfinland.articles
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [commonmark-hiccup.core :as cmh]
            [fpvfinland.plugins.plugins :as plugins])
  (:import (java.io File)))

(defn- normalize-eol [s]
  (str/replace s #"\r" ""))

(defn- flatten-plugin-definitions [content-str]
  (str/replace content-str
               #"(?ms)\[:.*?\]"
               #(str/replace % #"[ ]*\n[ ]*" " ")))

(defn- article-lead-params [content]
  (->> content
       (re-find #"\[:article-lead.*?\]")
       (plugins/extract-plugin-params)))

(defn get-articles []
  (for [^File f (seq (.listFiles (io/as-file "./resources/public/posts")))]
    (let [content               (flatten-plugin-definitions (normalize-eol (slurp f)))
          {:keys [date header]} (article-lead-params content)]
      {:date    date
       :file    (.getName f)
       :title   header
       :content content})))

(defn article-link [article]
  (str "/artikkelit/" (str/replace (:file article) ".md" ".html")))
