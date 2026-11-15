(ns fpvfinland.layout.nav
  (:require [clojure.string :as str]
            [fpvfinland.layout.search :as search]
            [fpvfinland.site :as site]))

(defn- active? [html-url url]
  (or (= html-url url)
      ;; section links such as /artikkelit/ are active for everything below them
      (and (not= url "/")
           (str/ends-with? url "/")
           (str/starts-with? html-url url))))

(defn- nav-link [html-url text url]
  [:li {:class "nav-item"}
   [:a (cond-> {:href url :class "nav-link"}
               (active? html-url url) (assoc :aria-current "page"))
    text]])

(defn nav-links [html-url]
  (for [[site-name site-url] (site/create-main-nav-links)]
    (nav-link html-url site-name site-url)))

(defn nav [html-url]
  (list
    [:ul {:class "nav"}
     (nav-links html-url)]
    [:button.nav-burger-btn {:onclick "toggleBurgerMenu()"
                             :type "button"
                             :aria-label "Valikko"}
     [:img.nav-burger {:id  "burger-menu-closed"
                       :src "/img/burger-menu.svg"
                       :alt ""}]
     [:span.nav-burger-open {:id    "burger-menu-open"
                             :class "hide"} "X"]]
    [:div#nav-burger-list.nav-burger-list.hide
     (search/search-input "burger")
     [:ul (nav-links html-url)]]))
