(ns fpvfinland.plugins.plugins
  (:require [clojure.string :as str]
            [fpvfinland.plugins.calendar :as calendar]
            [fpvfinland.plugins.button :as button]))

(defn extract-plugin-params [plugin-str]
  (map
    #(str/replace % #"\"" "")
    (drop 1 (re-seq #"[^\"\s]+|\"[^\"]*\"" (str/replace plugin-str #"\[|\]" "")))))

(defn build-plugin [plugin-str]
  (cond
    (= plugin-str "[:calendar]") #(apply calendar/calendar (extract-plugin-params plugin-str))
    (str/starts-with? plugin-str "[:button") #(apply button/button (extract-plugin-params plugin-str))
    :else nil))