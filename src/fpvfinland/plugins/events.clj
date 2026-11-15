(ns fpvfinland.plugins.events
  "Shared events data pipeline: events.edn -> JSON in window.calendarEvents.
   Which events are upcoming is decided in the browser (see events.js), because the
   site is only rebuilt on push and build-time filtering would go stale."
  (:require [clojure.java.io :as io]
            [clojure.edn :as edn]
            [hiccup2.core :as h]
            [jsonista.core :as json]
            [commonmark-hiccup.core :as cmh]))

(defn- read-events-json []
  (with-open [events-file (java.io.PushbackReader. (io/reader (io/file "./events.edn")))]
    (json/write-value-as-string
      (mapv
        #(update % :desc (fn [desc] (str (h/html (cmh/markdown->hiccup desc)))))
        (edn/read events-file)))))

(defn script
  "Events data followed by events.js and any page specific scripts (e.g. \"front.js\").
   Identical entries from several plugins on one page are de-duplicated by the layout."
  [& page-scripts]
  (concat
    [[:script {:type "text/javascript"}
      (h/raw (format "window.calendarEvents = %s;" (read-events-json)))]
     [:script {:type "text/javascript" :src "/script/events.js"}]]
    (for [s page-scripts]
      [:script {:type "text/javascript" :src (str "/script/" s)}])))
