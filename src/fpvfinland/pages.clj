(ns fpvfinland.pages
  (:require [clojure.string :as str]
            [clojure.walk :as w]
            [commonmark-hiccup.core :as cmh]
            [fpvfinland.articles :as articles]
            [fpvfinland.md-resources :as md]
            [fpvfinland.layout.base :as base]))

(def ARTICLE_IMAGE_LEAD_CLASS "lead")

(defn apply-layout [markdown & [wrap-hiccup]]
  (base/with-layout
    (if (some? wrap-hiccup)
      (conj wrap-hiccup (seq [(cmh/markdown->hiccup markdown)]))
      (seq [(cmh/markdown->hiccup markdown)]))))

(defn md-page [md-file-name]
  (str
  "<!DOCTYPE html>\n"
  (apply-layout
    (md/slurp-md-page md-file-name)
    [:div.content-wrap])))

(defn article-date-elem [article]
  (let [[year month day] (str/split (:date article) #"[-]")]
    [:date {:datetime (:date article)
            :class    ["article-date"]}
     (format "%s.%s.%s" day month year)]))

(defn article-list-page []
  (let [articles (->> (articles/get-articles)
                      (sort-by :date)
                      reverse)
        article-links (map (fn [article]
                             [:li.article-link
                              [:a {:href (articles/article-link article)}
                               (article-date-elem article)
                               [:span.article-title (:title article)]]])
                           articles)]
    (base/with-layout
      [:div.content-wrap.article-list
       [:h3 "FPV Finlandin julkaistut artikkelit aikajärjestyksessä"]
       [:ul.article-links article-links]])))

(defn class-matches? [class-attr class-to-check]
  (-> (cond
        (string? class-attr) (str/split class-attr #" ")
        (coll? class-attr) class-attr
        :else [class-attr])
      set
      (contains? class-to-check)))

(defn img-tag? [x & [class-to-check]]
  (let [img? (and (vector? x)
                  (= :img (first x)))]
    (if (and img? (some? class-to-check))
      (class-matches? (:class (second x)) class-to-check)
      img?)))

(defn paragraph-tag? [x]
  (and (vector? x)
       (= :p (first x))))

(defn convert-lead-image [hiccup]
  (w/postwalk
    (fn [x]
      (if (and (img-tag? x)
               (str/starts-with? (:alt (second x)) "LEAD:"))
        (update x 1 assoc :class ARTICLE_IMAGE_LEAD_CLASS)
        x))
    hiccup))

(defn remove-empty-paragraphs-from-lead-images [hiccup]
  (w/postwalk
    (fn [x]
      (if (and (paragraph-tag? x)
               (img-tag? (first (second x)) ARTICLE_IMAGE_LEAD_CLASS)
               ; ensure only :p with one child
               (= 2 (count x))
               ; ensure no inline text elements, only img tag
               (= 1 (count (second x))))
        (first (second x))
        x))
    hiccup))

(defn has-lead-image? [hiccup]
  (some
    #(img-tag? % ARTICLE_IMAGE_LEAD_CLASS)
    hiccup))

(defn wrap-as-readable-article [hiccup article]
  (let [date-elem (article-date-elem article)]
    [:article
     [:div.progress-container [:div.progress-bar]]
     (if (has-lead-image? hiccup)
       (let [header-with-lead (take 2 hiccup)]
         (list [:div.article-lead
                date-elem
                header-with-lead]
               (drop 2 hiccup)))
       (list date-elem
             hiccup))]))

(defn create-article-pages []
  (->> (articles/get-articles)
       (map (fn [article]
              {(articles/article-link article) (-> (:content article)
                                                   cmh/markdown->hiccup
                                                   convert-lead-image
                                                   remove-empty-paragraphs-from-lead-images
                                                   (wrap-as-readable-article article)
                                                   base/with-layout)}))
       (apply merge)))