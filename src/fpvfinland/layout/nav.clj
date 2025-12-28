(ns fpvfinland.layout.nav
  (:require [fpvfinland.layout.search :as search]
            [fpvfinland.site :as site]))

(defn- nav-link [text url]
  [:li {:class "nav-item px-0"}
   [:a {:href url :class "nav-link link-body-emphasis px-3"} text]])

(defn nav-links []
  (for [[site-name site-url] (site/create-main-nav-links)]
    (nav-link site-name site-url)))

(defn nav []
  (list
    [:ul {:class "nav text-uppercase fw-bold col-12 col-md-auto mb-2 justify-content-center mb-md-0"}
     (nav-links)]
    [:button.nav-burger-btn {:onclick "toggleBurgerMenu()"}
     [:img.nav-burger {:id  "burger-menu-closed"
                       :src "/img/burger-menu.svg"}]
     [:span.nav-burger-open {:id    "burger-menu-open"
                             :class "hide"} "X"]]
    [:div#nav-burger-list.nav-burger-list.hide
     (search/search-input "burger")
     [:ul {:class "text-uppercase fw-bold col-12 col-md-auto mb-2 justify-content-center mb-md-0"}
      (nav-links)]]))