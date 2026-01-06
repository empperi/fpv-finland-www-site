(ns fpvfinland.plugins.plugins
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [fpvfinland.plugins.calendar :as calendar]
            [fpvfinland.plugins.button :as button]
            [fpvfinland.plugins.opengraph :as opengraph]
            [fpvfinland.plugins.article-lead :as article-lead]))

(defn extract-plugin-params [plugin-str]
  (let [clean-str (str/replace plugin-str #"\[|\]" "")
        extracted (->> (re-seq #"[^\s=]+=\"[^\"]*\"|\"[^\"]*\"|[^\"\s]+" clean-str)
                       (drop 1)
                       (map (fn [token]
                              (if (re-matches #"^\".*\"$" token)
                                (str/replace token #"\"" "")
                                token)))
                       (map (fn [token]
                              (if (re-matches #"[a-z]+=.*" token)
                                (-> (str/split token #"=")
                                    (update 0 keyword)
                                    (update 1 edn/read-string))
                                token))))
        map-args (into {} (filter vector? extracted))
        str-args (filter (complement vector?) extracted)]
    (if (not-empty map-args)
      (concat [map-args] str-args)
      str-args)))

(defn ->plugin-handler [plugin-str f]
  (fn []
    (apply f (extract-plugin-params plugin-str))))

(defn build-plugin [plugin-str]
  (cond
    (= plugin-str "[:calendar]")                   (->plugin-handler plugin-str calendar/calendar)
    (str/starts-with? plugin-str "[:button")       (->plugin-handler plugin-str button/button)
    (str/starts-with? plugin-str "[:opengraph")    (->plugin-handler plugin-str opengraph/opengraph)
    (str/starts-with? plugin-str "[:article-lead") (->plugin-handler plugin-str article-lead/article-lead)
    :else nil))