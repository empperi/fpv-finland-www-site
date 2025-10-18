(defproject fpv-finland-www-site "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"

  :dependencies [[org.clojure/clojure "1.11.1"]
                 [stasis "2023.11.21"]
                 [optimus "2025.01.19.2"]
                 [optimus-less "0.2.1"]
                 [ring/ring-core "1.15.3"]
                 [ring/ring-devel "1.15.3"]
                 [ring/ring-jetty-adapter "1.15.3"]
                 [hiccup "2.0.0"]
                 [commonmark-hiccup "0.2.0"]]

  :plugins [[lein-ring "0.12.6"]]

  :main fpvfinland.core

  :repl-options {:init-ns fpvfinland.core}

  :ring {:handler      fpvfinland.app
         :auto-reload? true})
