(ns fpvfinland.plugins.calendar
  (:require [clojure.java.io :as io]
            [clojure.edn :as edn]
            [hiccup2.core :as h]
            [jsonista.core :as json]
            [commonmark-hiccup.core :as cmh]))

(defn- read-calendar-events-file []
  (with-open [events-file (java.io.PushbackReader. (io/reader (io/file "./events.edn")))]
    (json/write-value-as-string
      (mapv
        #(update % :desc (fn [desc] (str (h/html (cmh/markdown->hiccup desc)))))
        (edn/read events-file)))))

(def calendar-html
  (seq [[:div.calendar-container
         [:div.calendar-header
          [:button#prev-month "<"]
          [:h2#current-month-year "Tammikuu 2026"]
          [:button#next-month ">"]]
         [:div.calendar-grid-header
          [:div "Ma"]
          [:div "Ti"]
          [:div "Ke"]
          [:div "To"]
          [:div "Pe"]
          [:div "La"]
          [:div "Su"]]
         [:div#calendar-days.calendar-grid]]
        [:div#event-details.event-details.hidden
         [:h3#detail-title]
         [:p#detail-date]
         [:p#detail-desc]
         [:button#close-details "Sulje"]]]))

(def calendar-script
  [[:script {:type "text/javascript"}
    (h/raw (format "window.calendarEvents = %s;" (read-calendar-events-file)))]
   [:script {:type "text/javascript" :src "/script/calendar.js"}]])

(def calendar-style
  [[:link {:rel "stylesheet" :href "/styles/calendar.css"}]])

(defn calendar []
  {:plugin {:plugin/body   calendar-html
            :plugin/style  calendar-style
            :plugin/script calendar-script}})