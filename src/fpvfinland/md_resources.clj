(ns fpvfinland.md-resources)

(defn slurp-md-page [md-file-name]
  (slurp (str "resources/public/pages/" md-file-name)))