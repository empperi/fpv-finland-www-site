(ns fpvfinland.plugins.article-lead
  (:require [clojure.string :as str]))

(def ARTICLE_IMAGE_LEAD_CLASS "lead")

(defn lead-img-tag [alt image]
  [:img {:class ARTICLE_IMAGE_LEAD_CLASS :src image :alt alt}])

(defn article-date-elem [date]
  (let [[year month day] (str/split date #"[-]")]
    [:date {:datetime date
            :class    ["article-date"]}
     (format "%s.%s.%s" day month year)]))

(defn lead-div [date header image]
  (if (some? image)
    [:div.article-lead
     (article-date-elem date)
     [:h1 header]
     (lead-img-tag header image)]
    (seq [(article-date-elem date)
          [:h1 header]])))

(defn article-lead [{:keys [date header image]}]
  {:plugin {:plugin/body (lead-div date header image)
            :plugin/style []
            :plugin/script []}})