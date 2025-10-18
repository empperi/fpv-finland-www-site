(ns fpvfinland.layout.nav)

(defn- nav-link [text url]
  [:li {:class "nav-item px-1 "}
   [:a {:href url :class "nav-link link-body-emphasis px-1"} text]])

(defn nav []
  (list
    [:ul {:class "nav text-uppercase fw-bold col-12 col-md-auto mb-2 justify-content-center mb-md-0"}
     (nav-link "Etusivu" "/")
     (nav-link "Artikkelit" "/artikkelit/")
     (nav-link "Yhdistys" "/yhdistys.html")
     (nav-link "Yhteystiedot" "/yhteystiedot.html")]
    [:button.nav-burger-btn {:onclick "toggleBurgerMenu()"}
     [:img.nav-burger {:id  "burger-menu-closed"
                       :src "/img/burger-menu.svg"}]
     [:span.nav-burger-open {:id      "burger-menu-open"
                             :class   "hide"} "X"]]
    [:ul {:id "nav-burger-list"
          :class "nav-burger-list hide text-uppercase fw-bold col-12 col-md-auto mb-2 justify-content-center mb-md-0"}
     (nav-link "Etusivu" "/")
     (nav-link "Artikkelit" "/artikkelit/")
     (nav-link "Yhdistys" "/yhdistys.html")
     (nav-link "Yhteystiedot" "/yhteystiedot.html")]))