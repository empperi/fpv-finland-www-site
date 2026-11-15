(ns fpvfinland.plugins.front
  "Front page plugins: the goggles-OSD hero and the upcoming events list.
   Both are filled in by front.js from the shared events data."
  (:require [fpvfinland.plugins.events :as events]
            [fpvfinland.site :as site]))

(def hero-html
  [:section.hero {:aria-label "FPV Finland"}
   [:video {:autoplay "" :muted "" :loop "" :playsinline "" :preload "auto"
            :disablePictureInPicture "" :aria-hidden "true" :tabindex "-1"}
    [:source {:src "/video/fpv-finland-www-hero-video.mp4" :type "video/mp4"}]]
   [:div.hero-shade]
   [:div.osd {:aria-hidden "true"}
    [:span#osd-date.osd-tr]
    [:span#osd-title.osd-bl]
    [:span#osd-countdown.osd-br]]
   [:button.hero-toggle {:type "button" :aria-label "Pysäytä video"}
    [:svg.icon-pause {:width 18 :height 18 :viewBox "0 0 18 18" :aria-hidden "true"}
     [:path {:d "M3 2h4v14H3zM11 2h4v14h-4z" :fill "currentColor"}]]
    [:svg.icon-play {:width 18 :height 18 :viewBox "0 0 18 18" :aria-hidden "true"}
     [:path {:d "M4 2l12 7-12 7z" :fill "currentColor"}]]]
   [:div.hero-content
    [:h1 "Yhdistys FPV-harrastajille"]
    [:div.hero-actions
     [:a.btn-fpv.primary {:href site/membership-url} "Liity jäseneksi"]
     [:a.btn-fpv.secondary {:href "/tapahtumat.html"} "Katso tapahtumat"]]]])

(def upcoming-html
  [:section.upcoming
   [:h2 "Tulevat tapahtumat"]
   [:ul#upcoming-list.event-rows]
   [:p#upcoming-empty.hide
    "Ei tulevia tapahtumia juuri nyt. "
    [:a {:href "/tapahtumat.html"} "Katso menneet tapahtumat kalenterista."]]
   [:p#upcoming-more.hide [:a {:href "/tapahtumat.html"} "Kaikki tapahtumat"]]
   [:noscript [:p [:a {:href "/tapahtumat.html"} "Katso tapahtumat kalenterista."]]]])

(defn- front-plugin [body]
  {:plugin {:plugin/body   body
            :plugin/style  []
            :plugin/script (events/script "front.js")}})

(defn hero [] (front-plugin hero-html))

(defn upcoming-events [] (front-plugin upcoming-html))
