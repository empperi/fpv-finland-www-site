(ns fpvfinland.sync.articles-file
  "Pure rendering of new article files for resources/public/posts."
  (:require [clojure.string :as str])
  (:import (java.text Normalizer Normalizer$Form)))

(def ^:private max-slug 40)

(defn slugify [title]
  (let [s (-> title
              (str/replace "ä" "a") (str/replace "Ä" "A")
              (str/replace "ö" "o") (str/replace "Ö" "O")
              (str/replace "å" "a") (str/replace "Å" "A")
              (Normalizer/normalize Normalizer$Form/NFD)
              (str/replace #"\p{M}+" "")
              (str/replace #"\s+" "_")
              (str/replace #"[^A-Za-z0-9_\-]" "")
              (str/replace #"_+" "_")
              (str/replace #"^[_\-]+|[_\-]+$" ""))
        s (if (> (count s) max-slug) (str/replace (subs s 0 max-slug) #"[_\-]+$" "") s)]
    (if (str/blank? s) "Artikkeli" s)))

(defn unique-filename
  "existing: collection of file names already in posts/. Never returns an existing name (case-insensitive)."
  [existing date slug]
  (let [taken (set (map str/lower-case existing))]
    (->> (cons (str date "-" slug ".md")
               (map #(str date "-" slug "_" % ".md") (iterate inc 2)))
         (remove #(taken (str/lower-case %)))
         first)))

(defn header-title
  "Makes a title safe for the article-lead plugin header (see plugins/extract-plugin-params):
   no double quotes, brackets, '=' or backslashes."
  [title]
  (-> title
      (str/replace "\"" "”")
      (str/replace #"[\[\]]" "")
      (str/replace "=" "-")
      (str/replace "\\" "/")
      (str/replace #"\s+" " ")
      str/trim))

(defn render [{:keys [date title body]}]
  (str "[:article-lead\n"
       "  date=\"" date "\"\n"
       "  header=\"" (header-title title) "\"]\n\n"
       (str/trim body) "\n"))

(defn new-article
  "Returns {:filename :content} for a validated add-article action."
  [existing-names {:keys [date title] :as action}]
  {:filename (unique-filename existing-names date (slugify title))
   :content  (render action)})
