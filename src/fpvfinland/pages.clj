(ns fpvfinland.pages
  (:require [clojure.string :as str]
            [clojure.walk :as w]
            [commonmark-hiccup.core :as cmh]
            [fpvfinland.articles :as articles]
            [fpvfinland.files :as files]
            [fpvfinland.md-resources :as md]
            [fpvfinland.layout.nav :as nav]
            [fpvfinland.layout.base :as base]))

(def ARTICLE_IMAGE_LEAD_CLASS "lead")

(defn with-layout [& page-content]
  (apply base/with-layout (nav/nav) page-content))

(defn md->hiccup [md]
  (if (string? md)
    (cmh/markdown->hiccup md)
    md))

(defn apply-layout [markdown & [wrap-hiccup]]
  (with-layout
    (if (some? wrap-hiccup)
      (conj wrap-hiccup (seq [(md->hiccup markdown)]))
      (seq [(md->hiccup markdown)]))))

(defn ->html5-page [html-str]
  (str "<!DOCTYPE html>\n" html-str))

(defn md-page [md-file-name]
  (->html5-page
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
    (with-layout
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

(defn ->child-tag [x]
  (first (second x)))

(defn convert-lead-image [hiccup]
  (w/postwalk
    (fn [x]
      (if (and (img-tag? x)
               (str/starts-with? (:alt (second x)) "LEAD:"))
        (update x 1 assoc :class ARTICLE_IMAGE_LEAD_CLASS)
        x))
    hiccup))

(defn convert-video [hiccup]
  (w/postwalk
    (fn [x]
      (if (and (paragraph-tag? x)
               (img-tag? (->child-tag x))
               (str/starts-with? (:alt (second (->child-tag x))) "LEADVIDEO"))
        (let [alt-txt      (-> x ->child-tag second :alt)
              video-header (-> alt-txt (str/replace #"LEADVIDEO_[0-9]{1,4}[a-z]{2,}\:[ ]*(.*)" "$1"))
              video-height (-> alt-txt (str/replace #"LEADVIDEO_([0-9]{1,4}[a-z]{2,})\:.*" "$1"))]
          [:div {:class "video-space-reserve"
                 :style {:height video-height}}
           [:div {:class "lead-video"
                  :style {:height video-height}}
            [:div.video-caption [:h1 video-header]]
            [:video {:autoplay                ""
                     :muted                   ""
                     :loop                    ""
                     :disablePictureInPicture ""
                     :playsInline             ""
                     :style                   {:height video-height}}
             [:source {:src  (-> x ->child-tag second :src)
                       :type "video/mp4"}]]]])
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

(defn enriched-md-page [md-file-name]
  (->html5-page
    (-> (md/slurp-md-page md-file-name)
        cmh/markdown->hiccup
        convert-lead-image
        remove-empty-paragraphs-from-lead-images
        convert-video
        (apply-layout [:div.content-wrap]))))

(defn create-article-pages []
  (->> (articles/get-articles)
       (map (fn [article]
              {(articles/article-link article) (-> (:content article)
                                                   cmh/markdown->hiccup
                                                   convert-lead-image
                                                   remove-empty-paragraphs-from-lead-images
                                                   (wrap-as-readable-article article)
                                                   with-layout)}))
       (apply merge)))

(defn create-main-navigation-pages []
  (->> (for [file (files/list-files "./resources/public/pages")]
         (let [file-name (.getName file)]
           (if (= "main.md" file-name)
             ["/" (enriched-md-page file-name)]
             [(format "/%s.html" (str/replace file-name ".md" "")) (enriched-md-page file-name)])))
       (into {})
       (apply merge (create-article-pages))))