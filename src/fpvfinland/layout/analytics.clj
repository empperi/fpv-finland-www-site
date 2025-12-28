(ns fpvfinland.layout.analytics
  (:require [hiccup2.core :as h]))

(def ^:dynamic enable-analytics? false)

(defn analytics []
  (when enable-analytics?
    (seq
      [[:script {:async "true" :src "https://www.googletagmanager.com/gtag/js?id=G-W8XH03FX5C"}]
       [:script
        (h/raw "window.dataLayer = window.dataLayer || []; function gtag(){dataLayer.push(arguments);} gtag('js', new Date()); gtag('config', 'G-W8XH03FX5C');")]])))