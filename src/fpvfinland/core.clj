(ns fpvfinland.core
  (:require [stasis.core :as stasis]
            [ring.adapter.jetty :as jetty]
            [ring.middleware.reload :refer [wrap-reload]]
            [ring.middleware.content-type :refer [wrap-content-type]]
            [ring.middleware.resource :refer [wrap-resource]]

            [optimus.prime :as optimus]
            [optimus.assets :as assets]
            [optimus.optimizations :as optimizations]
            [optimus.strategies :refer [serve-live-assets]]
            [optimus.export]
            [optimus-less.core]

            [fpvfinland.pages :as pages]))

;; ----------------------------------------
;; 1. Page Definitions
;; ----------------------------------------

(defn get-pages
  "Returns a map of URL path to page content (or a function to generate content)."
  []
  (->> (merge {"/" (pages/md-page "main.md")}
              {"/yhdistys.html" (pages/md-page "yhdistys.md")}
              {"/yhteystiedot.html" (pages/md-page "yhteystiedot.md")}

              {"/artikkelit/" (pages/article-list-page)})
       ;; Create all URL mappings for articles
       (apply merge (pages/create-article-pages))
       ;; Slurp any static files in the resources/public directory
       (merge (stasis/slurp-directory "resources/public" #".*\.(html|css|js|jpg|png|gif)$"))))


;; ----------------------------------------
;; 2. Development Server Setup (Ring)
;; ----------------------------------------

(defn get-assets []
  (assets/load-assets "public" ["/styles/styles.less"
                                #"/img/.*\.png"
                                #"/img/.*\.svg"]))

(def app
  "The Ring handler for development. It serves dynamic pages, then static files."
  (-> (stasis/serve-pages #'get-pages)
      (optimus/wrap #'get-assets optimizations/all serve-live-assets)
      (wrap-content-type)      ; Add Content-Type header
      (wrap-reload)))          ; Auto-reload code changes in development

(defn -main
  "The entry point to run the web server for local development."
  [& args]
  (println "Starting development server on http://localhost:3000")
  (jetty/run-jetty app {:port 3000 :join? false}))


;; ----------------------------------------
;; 3. Static Export
;; ----------------------------------------

(defn export
  "Exports the site to a static directory."
  [target-dir]
  (println "Exporting static resources...")
  (let [assets (optimizations/all (get-assets) {})
        pages (get-pages)]
    (stasis/empty-directory! target-dir)
    (optimus.export/save-assets assets target-dir)
    (stasis/export-pages pages target-dir {:optimus-assets assets})))