(ns fpvfinland.plugins.opengraph
  (:require [clojure.string :as str]
            [clojure.walk :as w]))

(defn- opengraph-image-tag? [hiccup]
  (and (vector? hiccup)
       (= :meta (first hiccup))
       (map? (second hiccup))
       (= "og:image" (:property (second hiccup)))))

(defn- opengraph-description-tag? [hiccup]
  (and (vector? hiccup)
       (= :meta (first hiccup))
       (map? (second hiccup))
       (= "og:description" (:property (second hiccup)))))

(defn change-url-relative-path [original url-fragment]
  (let [uri (java.net.URI. original)]
    (str (.getScheme uri)
         "://"
         (.getAuthority uri)
         "/"
         (if (str/starts-with? url-fragment "/")
           (str/replace-first url-fragment #"/" "")
           url-fragment))))

(defn update-opengraph-image-tag [tag url-fragment]
  (update-in tag [1 :content] #(change-url-relative-path % url-fragment)))

(defn update-opengraph-description-tag [tag content]
  (assoc-in tag [1 :content] (str "FPV Finland - " content)))

(defn opengraph [{:keys [description image]}]
  {:plugin {:plugin/body nil
            :plugin/style []
            :plugin/script []
            :plugin/head (fn [head-hiccup]
                           (w/postwalk
                             (fn [x]
                               (cond
                                 (opengraph-image-tag? x) (update-opengraph-image-tag x image)
                                 (opengraph-description-tag? x) (update-opengraph-description-tag x description )
                                 :else x))
                             head-hiccup))}})