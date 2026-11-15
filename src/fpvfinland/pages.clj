(ns fpvfinland.pages
  (:require [clojure.string :as str]
            [clojure.walk :as w]
            [commonmark-hiccup.core :as cmh]
            [fpvfinland.articles :as articles]
            [fpvfinland.files :as files]
            [fpvfinland.md-resources :as md]
            [fpvfinland.layout.nav :as nav]
            [fpvfinland.layout.base :as base]
            [fpvfinland.plugins.plugins :as plugins]))

(defn with-layout [html-url & page-content]
  (apply base/with-layout html-url (nav/nav html-url) page-content))

(defn md->hiccup [md]
  (if (string? md)
    (cmh/markdown->hiccup md)
    md))

(defn apply-layout [html-url markdown & [wrap-hiccup]]
  (with-layout
    html-url
    (if (some? wrap-hiccup)
      (conj wrap-hiccup (seq [(md->hiccup markdown)]))
      (seq [(md->hiccup markdown)]))))

(defn ->html5-page [html-str]
  (str "<!DOCTYPE html>\n" html-str))

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
    (with-layout
      "/artikkelit/"
      [:div.content-wrap.article-list
       [:h1 "Artikkelit"]
       [:ul.article-links article-links]])))

(defn wrap-as-readable-article [hiccup]
  [:article
   [:div.progress-container [:div.progress-bar]]
   hiccup])

(defn apply-plugins [hiccup]
  (w/postwalk
    (fn [x]
      (if-let [plugin-fn (and (vector? x)
                              (= :p (first x))
                              (plugins/build-plugin (first (second x))))]
        (plugin-fn)
        x))
    hiccup))

(defn md-file-name->html-url [md-file-name & [prefix]]
  (let [html-url (str "/" (str/replace md-file-name #"\.md" ".html"))]
    (if (some? prefix)
      (str prefix html-url)
      html-url)))

(defn enriched-md-page [md-file-name]
  (let [layout-fn (partial apply-layout (md-file-name->html-url md-file-name))]
    (->html5-page
      (-> (md/slurp-md-page md-file-name)
          cmh/markdown->hiccup
          apply-plugins
          (layout-fn [:div.content-wrap])))))

(defn create-article-pages []
  (->> (articles/get-articles)
       (map (fn [article]
              (let [article-link (articles/article-link article)
                    layout-fn    (partial with-layout article-link)]
                {article-link (-> (:content article)
                                  cmh/markdown->hiccup
                                  wrap-as-readable-article
                                  apply-plugins
                                  layout-fn)})))
       (apply merge)))

(defn create-main-navigation-pages []
  (->> (for [file (files/list-files "./resources/public/pages")]
         (let [file-name (.getName file)]
           (if (= "main.md" file-name)
             ["/" (enriched-md-page file-name)]
             [(format "/%s.html" (str/replace file-name ".md" "")) (enriched-md-page file-name)])))
       (into {})
       (apply merge (create-article-pages))))