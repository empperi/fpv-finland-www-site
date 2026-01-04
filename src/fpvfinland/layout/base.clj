(ns fpvfinland.layout.base
  (:require [clojure.string :as str]
            [clojure.walk :as w]
            [fpvfinland.resource-files :as res-files]
            [hiccup2.core :as h]
            [fpvfinland.layout.search :as search]
            [fpvfinland.layout.analytics :as analytics]))

(def COMPILE_TIMESTAMP (System/currentTimeMillis))

(defn add-resource-timestamps [hiccup]
  (let [res-set (set (map name (filter #(not= :html %) res-files/resource-file-types)))
        extract-attr-resource-type (fn [x]
                                     (when (string? x)
                                       (res-set (last (str/split (str/lower-case x) #"\.")))))]
    (w/postwalk
      (fn [x]
        (if (extract-attr-resource-type x)
          (str x "?t=" COMPILE_TIMESTAMP)
          x))
      hiccup)))

(defn extract-plugins [content]
  (let [plugins (atom [])]
    {:content (w/postwalk
                (fn [x]
                  (if (and (map? x)
                           (some? (:plugin x)))
                    (do
                      (swap! plugins conj (:plugin x))
                      (:plugin/body (:plugin x)))
                    x))
                content)
     :plugins @plugins}))

(defn opengraph []
  (seq [[:meta {:property "og:title" :content "FPV Finland Ry"}]
        [:meta {:property "og:type" :content "website"}]
        [:meta {:property "og:image" :content "/img/FF_logo_round.png"}]
        [:meta {:property "og:locale" :content "fi_FI"}]
        [:meta {:property "og:description" :content "FPV Finland on FPV-dronetoimintaa edistävä yhdistys"}]]))

(defn layout [content nav-content]
  (let [{:keys [content plugins]} (extract-plugins content)]
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
      [:link {:rel "stylesheet" :href "/styles/styles.css"}]
      (seq (apply concat (mapv :plugin/style plugins)))
      (opengraph)
      (analytics/analytics)
      [:script {:type "text/javascript"}
       (h/raw (str "const searchDatabase = " (search/memoized-search-json)))]
      [:script {:type "text/javascript" :src "/script/Snowball.min.js"}]
      [:script {:type "text/javascript" :src "/script/scripts.js"}]
      (seq (apply concat (mapv :plugin/script plugins)))]

     [:body
      [:header {:class "d-flex align-items-center justify-content-between py-3 mb-4 border-bottom"}
       [:div {:class "col-md-3 mb-2 mb-md-0"}
        [:a {:href "/"}
         [:img {:src "/img/fpv-finland-logo.png"
                :alt "FPV Finland"
                :class "nav-logo"}]]]
       nav-content
       (search/search-input "full-page")]]
     [:main content]]))

(defn with-layout [nav-content & page-content]
  #_(when (not-empty (:plugins (extract-plugins page-content)))
    (clojure.pprint/pprint (add-resource-timestamps (layout page-content nav-content)))
    (throw (RuntimeException. "lol")))
  (str (h/html (add-resource-timestamps (layout page-content nav-content)))))
