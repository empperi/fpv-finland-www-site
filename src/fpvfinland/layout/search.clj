(ns fpvfinland.layout.search
  (:require [clojure.string :as str]
            [fpvfinland.articles :as articles]
            [fpvfinland.md-resources :as md]
            [stemmer.snowball :as snowball]
            [jsonista.core :as json])
  (:import (java.time LocalDateTime)
           (java.time.format DateTimeFormatter)))

(def stemmer (snowball/stemmer :finnish))

(def url-regex
  #"\((?i)\b((?:[a-z][\w-]+:(?:/{1,3}|[a-z0-9%])|www\d{0,3}[.]|[a-z0-9.\-]+[.][a-z]{2,4}/)(?:[^\s()<>]+|\(([^\s()<>]+|(\([^\s()<>]+\)))*\))+(?:\(([^\s()<>]+|(\([^\s()<>]+\)))*\)|[^\s`!()\[\]{};:'\".,<>?«»“”‘’]))\)")

(def finnish-stopwords-pred
  (complement (set (map stemmer (str/split-lines (slurp "resources/public/fi-stopwords.txt"))))))

(defn stem-md-content
  "Sanitizes and stems all distinct words for use in search"
  [md-content]
  (let [content-words (-> md-content
                          (str/replace "\n" " ")
                          (str/replace "  " " ")
                          (str/replace url-regex "")
                          (str/replace #"\[(.*?)\]" "$1")
                          (str/lower-case)
                          (str/split #" "))]
    (->> content-words
         (map #(str/replace % #".*?([a-zA-ZäÄöÖåÅ]*).*" "$1"))
         (filter not-empty)
         (map stemmer)
         (filter (every-pred finnish-stopwords-pred #(> (count %) 2)))
         distinct)))

(defn create-search-database []
  (let [today-str (.format (LocalDateTime/now) DateTimeFormatter/ISO_DATE)]
    (concat
      [{:title "Etusivu"
        :date  today-str
        :link  "/"
        :stems (stem-md-content (md/slurp-md-page "main.md"))}
       {:title "Yhdistys"
        :date  today-str
        :link  "/yhdistys.html"
        :stems (stem-md-content (md/slurp-md-page "yhdistys.md"))}
       {:title "Tapahtumat"
        :date  today-str
        :link  "/tapahtumat.html"
        :stems (stem-md-content (md/slurp-md-page "tapahtumat.md"))}
       {:title "Yhteystiedot"
        :date  today-str
        :link  "/yhteystiedot.html"
        :stems (stem-md-content (md/slurp-md-page "yhteystiedot.md"))}]
      (->> (articles/get-articles)
           (map
             (fn [article]
               (select-keys article [:date :title :content :file])))
           (map
             (fn [article]
               (assoc
                 (select-keys article [:date :title])
                 :link  (articles/article-link article)
                 :stems (stem-md-content (:content article)))))))))

(defn search-json []
  (json/write-value-as-string (create-search-database)))

(def memoized-search-json (memoize search-json))

(defn search-input [& extra-classes]
  [:div {:class (vec (conj extra-classes "search"))}
   [:div.search-click-target.hide]
   [:div.search-wrapper
    [:input.search-input {:type "search"
                          :placeholder "Hae..."
                          :name "search"}]
    [:img.search-icon {:src "/img/search.svg"}]]
   [:div.search-results.hide
    [:ul
     [:li.search-result-template.search-result.hide
      [:a {:href ""}
       [:span.title ""]
       [:span.date ""]]]]]])