(ns fpvfinland.layout.base
  (:require [hiccup2.core :as h]

            [fpvfinland.layout.nav :as nav]
            [fpvfinland.layout.search :as search]
            [fpvfinland.layout.analytics :as analytics]))

(defn opengraph []
  (seq [[:meta {:property "og:title" :content "FPV Finland Ry"}]
        [:meta {:property "og:type" :content "website"}]
        [:meta {:property "og:image" :content "foo.png"}]
        [:meta {:property "og:locale" :content "fi_FI"}]
        [:meta {:property "og:description" :content "FPV Finland on FPV-dronetoimintaa edistävä yhdistys"}]]))

(defn layout [content]
  [:html
   [:head
    [:meta {:charset "utf-8"}]
    [:meta {:name "viewport" :content "width=device-width, initial-scale=1.0"}]
    [:title "FPV Finland Ry"]
    [:link {:rel "preconnect" :href "https://fonts.googleapis.com"}]
    [:link {:rel "preconnect" :href "https://fonts.gstatic.com" :crossorigin "true"}]
    [:link {:href (str "https://fonts.googleapis.com/css2"
                       "?family=Montserrat:ital,wght@0,100..900;1,100..900"
                       "&family=Poppins:ital,wght@0,100;0,200;0,300;0,400;0,500;0,600;0,700;0,800;0,900;1,100;1,200;1,300;1,400;1,500;1,600;1,700;1,800;1,900"
                       "&display=swap") :rel "stylesheet"}]
    [:link {:href "https://cdn.jsdelivr.net/npm/bootstrap@5.3.8/dist/css/bootstrap.min.css" :rel "stylesheet" :integrity "sha384-sRIl4kxILFvY47J16cr9ZwB07vP4J8+LH7qKQnuqkuIAvNWLzeN8tE5YBujZqJLB" :crossorigin "anonymous"}]
    (opengraph)
    (analytics/analytics)
    [:link {:rel "stylesheet" :href "/styles/styles.css"}]
    [:script {:type "text/javascript"}
     (h/raw (str "const searchDatabase = " (search/memoized-search-json)))]
    [:script {:type "text/javascript" :src "/script/Snowball.min.js"}]
    [:script {:type "text/javascript" :src "/script/scripts.js"}]]

   [:body
    [:header {:class "d-flex align-items-center justify-content-between py-3 mb-4 border-bottom"}
     [:div {:class "col-md-3 mb-2 mb-md-0"}
      [:a {:href "/"}
       [:img {:src "/img/fpv-finland-logo.png"
              :alt "FPV Finland"
              :class "nav-logo"}]]]
     (nav/nav)
     (search/search-input "full-page")]]
   [:main content]])

(defn with-layout [& page-content]
  (str (h/html (layout page-content))))
