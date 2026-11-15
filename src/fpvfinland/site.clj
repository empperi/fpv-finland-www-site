(ns fpvfinland.site
  (:require [clojure.string :as str]
            [fpvfinland.files :as files])
  (:import (java.io File)))

(def membership-url "https://secure.membook.fi/#/main/joining/0dee5211-30aa-4a5b-b233-98c988eff27a")
(def slack-url "https://fpvfinland.slack.com")
(def webshop-url "https://fpv-finland.myspreadshop.fi/")

(defn page-html-links []
  (mapv
    (comp #(if (= "main.md" %)
             ["/etusivu.html" "/"]
             [(format "/%s.html" (str/replace % ".md" "")) (format "/%s.html" (str/replace % ".md" ""))])
          #(.getName %))
    (filter
      (complement #(= "404.md" (.getName %)))
      (files/list-files "./resources/public/pages"))))

(defn create-main-nav-links []
  (let [extract-name-fn #(str/replace % #"/([a-zA-Z]+).*" "$1")
        links           (conj (page-html-links) ["/artikkelit.html" "/artikkelit/"])]
    (->> (seq (group-by (comp extract-name-fn first) links))
         (map (fn [[page-name [[_ page-url] _]]]
                [(str/capitalize page-name) page-url]))
         (sort-by (comp count first))
         vec)))
