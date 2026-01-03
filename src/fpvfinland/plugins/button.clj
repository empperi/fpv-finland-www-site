(ns fpvfinland.plugins.button)

(defn button-html [label url]
  [:a.button {:href url
              :target "_blank"}
   label])

(defn button [label url]
  {:plugin {:plugin/body (button-html label url)
            :plugin/style []
            :plugin/script []}})