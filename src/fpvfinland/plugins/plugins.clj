(ns fpvfinland.plugins.plugins
  (:require [fpvfinland.plugins.calendar :as calendar]))

(defn match-plugin [key-str]
  (cond
    (= "[:calendar]" key-str) calendar/calendar
    :else nil))