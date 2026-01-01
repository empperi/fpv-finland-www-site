(ns fpvfinland.core
  (:require [stasis.core :as stasis]
            [ring.adapter.jetty :as jetty]
            [ring.middleware.reload :refer [wrap-reload]]
            [ring.middleware.content-type :refer [wrap-content-type]]


            [optimus.prime :as optimus]
            [optimus.assets :as assets]
            [optimus.optimizations :as optimizations]
            [optimus.strategies :refer [serve-live-assets]]
            [optimus.export]
            [optimus-less.core]

            [fpvfinland.pages :as pages]
            [fpvfinland.layout.analytics :as analytics]
            [fpvfinland.resource-files :as res-files]))

;; ----------------------------------------
;; 1. Page Definitions
;; ----------------------------------------

(defn get-pages
  "Returns a map of URL path to page content (or a function to generate content)."
  []
  (->> (merge (pages/create-main-navigation-pages)
              {"/artikkelit/" (pages/article-list-page)})
       ;; Create all URL mappings for articles
       (apply merge (pages/create-article-pages))
       ;; Slurp any static files in the resources/public directory
       (merge (stasis/slurp-directory "resources/public" (res-files/resource-file-types-as-regexp)))))


;; ----------------------------------------
;; 2. Development Server Setup (Ring)
;; ----------------------------------------

(defn get-assets []
  (assets/load-assets "public" ["/styles/styles.less"
                                #"/img/.*\.png"
                                #"/img/.*\.svg"
                                #"/video/.*\.mp4"]))

(def app
  "The Ring handler for development. It serves dynamic pages, then static files."
  (-> (stasis/serve-pages #'get-pages)
      (optimus/wrap #'get-assets optimizations/all serve-live-assets)
      (wrap-content-type)      ; Add Content-Type header
      (wrap-reload)))          ; Auto-reload code changes in development

(defn run-lein-dev-server
  "Same as -main but uses jetty join so that main thread is stopped to wait. This
   allows Leiningen to keep on running thus not requiring IDE and Clojure development
   environment outside of Leiningen and Java"
  []
  (println "Starting development server")
  (let [server (jetty/run-jetty app {:port 3000 :join? false})]
    (println "  Server started. You can edit or add md-files, add new images etc.")
    (println "  For changes to the actual site generation logic please see README for instructions.")
    (println "  Please open http://localhost:3000 and start editing")
    (.join server)))

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
  []
  (println "Exporting static resources...")
  (binding [analytics/enable-analytics? true]
    (let [target-dir "./firebase/public"
          assets     (-> (get-assets)
                         (optimizations/minify-js-assets)
                         (optimizations/minify-css-assets)
                         (optimizations/inline-css-imports))
          pages (get-pages)]
      (stasis/empty-directory! target-dir)
      (stasis/export-pages pages target-dir {})
      (optimus.export/save-assets assets target-dir)
      (println "Export finished to" target-dir))))