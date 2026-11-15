(ns fpvfinland.layout.base
  (:require [clojure.string :as str]
            [clojure.walk :as w]
            [fpvfinland.resource-files :as res-files]
            [fpvfinland.site :as site]
            [hiccup2.core :as h]
            [fpvfinland.layout.search :as search]))

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

(defn opengraph [html-url]
  (seq [[:meta {:property "og:title" :content "FPV Finland Ry"}]
        [:meta {:property "og:type" :content "website"}]
        [:meta {:property "og:url" :content (str "https://fpvfinland.fi" html-url)}]
        [:meta {:property "og:logo" :content (str "https://fpvfinland.fi" "/img/FF_logo_round.png")}]
        [:meta {:property "og:image" :content (str "https://fpvfinland.fi" "/img/FF_logo_round.png")}]
        [:meta {:property "og:locale" :content "fi_FI"}]
        [:meta {:property "og:description" :content "FPV Finland on FPV-dronetoimintaa edistävä yhdistys"}]]))

(def tokens-css
  (str ":root{--ink:#000000;--text:#2A2A2A;--muted:#6B6E73;--paper:#FFFFFF;--page:#ECEEF1;"
       "--line:#D5D8DD;--signal:#C83700;--signal-osd:#FF6A1F;"
       "--font-body:\"Barlow\",system-ui,sans-serif;"
       "--font-display:\"Barlow Condensed\",\"Barlow\",sans-serif;"
       "--font-osd:\"Share Tech Mono\",ui-monospace,monospace;}"))

(defn footer []
  [:footer.site-footer
   [:div.footer-inner
    [:div
     [:p.footer-title "FPV Finland ry"]
     [:p "Suomen Ilmailuliiton jäsenyhdistys"]
     [:p [:a {:href "mailto:hallitus@fpvfinland.fi"} "hallitus@fpvfinland.fi"]]]
    [:div
     [:p.footer-title "Yhdistys"]
     [:ul
      [:li [:a {:href "/yhteystiedot.html"} "Yhteystiedot"]]
      [:li [:a {:href site/membership-url} "Liity jäseneksi"]]]]
    [:div
     [:p.footer-title "Yhteisö"]
     [:ul
      [:li [:a {:href site/slack-url} "Slack"]]
      [:li [:a {:href site/webshop-url} "Verkkokauppa"]]]]]])

(defn layout [html-url content nav-content]
  (let [{:keys [content plugins]} (extract-plugins content)
        head [:head
              [:meta {:charset "utf-8"}]
              [:meta {:name "viewport" :content "width=device-width, initial-scale=1.0"}]
              [:title "FPV Finland Ry"]
              [:link {:rel "preconnect" :href "https://fonts.googleapis.com"}]
              [:link {:rel "preconnect" :href "https://fonts.gstatic.com" :crossorigin "true"}]
              [:link {:href (str "https://fonts.googleapis.com/css2"
                                 "?family=Barlow:ital,wght@0,400;0,500;0,600;1,400"
                                 "&family=Barlow+Condensed:ital,wght@0,600;0,700;1,700"
                                 "&family=Share+Tech+Mono"
                                 "&display=swap") :rel "stylesheet"}]
              [:link {:href "https://cdn.jsdelivr.net/npm/bootstrap@5.3.8/dist/css/bootstrap.min.css" :rel "stylesheet" :integrity "sha384-sRIl4kxILFvY47J16cr9ZwB07vP4J8+LH7qKQnuqkuIAvNWLzeN8tE5YBujZqJLB" :crossorigin "anonymous"}]
              [:style (h/raw tokens-css)]
              [:link {:rel "stylesheet" :href "/styles/styles.css"}]
              (seq (distinct (apply concat (mapv :plugin/style plugins))))
              (opengraph html-url)
              [:link {:rel "manifest" :href "/manifest.webmanifest"}]
              [:link {:rel "icon" :href "/favicon.ico" :sizes "32x32"}]
              [:link {:rel "icon" :href "/img/icon.svg" :type "image/svg+xml"}]
              [:link {:rel "apple-touch-icon" :href "/img/apple-touch-icon.png"}]
              [:script {:type "text/javascript"}
               (h/raw (str "const searchDatabase = " (search/memoized-search-json)))]
              [:script {:type "text/javascript" :src "/script/Snowball.min.js"}]
              [:script {:type "text/javascript" :src "/script/scripts.js"}]
              (seq (distinct (apply concat (mapv :plugin/script plugins))))]]

    [:html
     (reduce
       (fn [h head-plugin] (head-plugin h))
       head
       (concat [identity] (filter some? (map :plugin/head plugins))))

     [:body
      [:header.site-header
       [:a.brand {:href "/"}
        [:img {:src "/img/fpv-finland-logo.png"
               :alt "FPV Finland"
               :class "nav-logo"}]]
       nav-content
       (search/search-input "full-page")]
      [:main content]
      (footer)
      [:div#cookie-consent.cookies.hide {:role "region" :aria-label "Evästeet"}
       [:p "Käytämme analytiikkaevästeitä sivuston kehittämiseen."]
       [:div.buttons
        [:button.btn-fpv.sm {:type "button" :onclick "acceptCookies()"} "Hyväksy"]
        [:button.btn-fpv.sm {:type "button" :onclick "declineCookies()"} "Kieltäydy"]]]
      [:div#tracking-blocked.cookies.hide {:role "region" :aria-label "Seurannan esto"}
       [:p (str "Hyväksyit analytiikkaseurannan, mutta käytössäsi on seurannan esto (ad blocker). Pyydämme, että "
                "sallisit sivustollemme poikkeuksen estosovelluksessasi. Jos et halua tehdä näin, niin voit "
                "estää seurannan oheisella napilla")]
       [:div.buttons
        [:button.btn-fpv.sm {:type "button" :onclick "declineCookies()"} "Estä"]]]]]))

(defn with-layout [html-url nav-content & page-content]
  (str (h/html (add-resource-timestamps (layout html-url page-content nav-content)))))
