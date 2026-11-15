(ns fpvfinland.plugins.calendar
  (:require [fpvfinland.plugins.events :as events]))

(def calendar-html
  (seq [[:div.view-toggle.wide {:role "group" :aria-label "Tapahtumien näkymä"}
         [:button#view-list-btn {:type "button" :aria-pressed "true"} "Lista"]
         [:button#view-month-btn {:type "button" :aria-pressed "false"} "Kuukausi"]]

        [:div#view-list.wide
         [:div#upcoming-groups]
         [:p#no-upcoming.hide "Ei tulevia tapahtumia juuri nyt."]
         [:button#toggle-past.text-btn {:type "button" :aria-expanded "false"} "Näytä menneet tapahtumat"]
         [:div#past-events.past-events.hide]]

        [:div#view-month.wide.hide
         [:div.calendar-container
          [:div.calendar-header
           [:button#prev-month {:type "button" :aria-label "Edellinen kuukausi"} "‹"]
           [:h2#current-month-year ""]
           [:button#next-month {:type "button" :aria-label "Seuraava kuukausi"} "›"]]
          [:div.calendar-grid-header
           [:div "Ma"] [:div "Ti"] [:div "Ke"] [:div "To"] [:div "Pe"] [:div "La"] [:div "Su"]]
          [:div#calendar-days.calendar-grid]]
         [:div#event-details.event-details.hidden
          [:h3#detail-title]
          [:p#detail-date]
          [:div#detail-desc]
          [:button#close-details.btn-fpv.sm {:type "button"} "Sulje"]]
         [:ul#month-events.event-rows.month-events]]]))

(def calendar-style
  [[:link {:rel "stylesheet" :href "/styles/calendar.css"}]])

(defn calendar []
  {:plugin {:plugin/body   calendar-html
            :plugin/style  calendar-style
            :plugin/script (events/script "calendar.js")}})
