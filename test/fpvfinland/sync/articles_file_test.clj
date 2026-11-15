(ns fpvfinland.sync.articles-file-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [fpvfinland.articles :as articles]
            [fpvfinland.plugins.plugins :as plugins]
            [fpvfinland.sync.articles-file :as af]))

(deftest slugs
  (is (= "Ylla_ja_ohi" (af/slugify "Yllä ja öhi")))
  (is (= "Aanekkaat_Oljyt" (af/slugify "Äänekkäät Öljyt")))
  (is (= "Kayttaja_ala_x" (af/slugify "Käyttäjä, älä: \"x\"!")))
  (is (= "a_b" (af/slugify "a   b")))
  (is (= "Artikkeli" (af/slugify "!!!")))
  (is (= "Artikkeli" (af/slugify "")))
  (testing "max length, no trailing separator"
    (let [s (af/slugify (str "Aaaaaaaaaa Bbbbbbbbbb Cccccccccc Dddddddddd Eeeeeeeeee"))]
      (is (<= (count s) 40))
      (is (not (str/ends-with? s "_")))))
  (testing "result only has safe characters"
    (is (re-matches #"[A-Za-z0-9_\-]+" (af/slugify "../../etc/passwd Ünï ☃ cødé")))))

(deftest unique-names
  (is (= "2026-01-01-A.md" (af/unique-filename [] "2026-01-01" "A")))
  (is (= "2026-01-01-A_2.md" (af/unique-filename ["2026-01-01-A.md"] "2026-01-01" "A")))
  (is (= "2026-01-01-A_3.md" (af/unique-filename ["2026-01-01-A.md" "2026-01-01-a_2.md"] "2026-01-01" "A"))))

(deftest header-escaping
  (is (= "Hei ”maailma” a-b a/b" (af/header-title "Hei \"maailma\" [a=b] a\\b"))))

(defn- parse-like-site [content]
  (let [flatten (deref #'articles/flatten-plugin-definitions)
        flat    (flatten (str/replace content #"\r" ""))]
    (->> flat (re-find #"\[:article-lead.*?\]") plugins/extract-plugin-params)))

(deftest rendered-article-parses-with-site-code
  (doseq [title ["Tavallinen otsikko" "Otsikko \"lainauksilla\"" "Yllä [hakasulut] = merkki \\ ja ääkköset" "Pitkä   väli"]]
    (let [{:keys [content filename]} (af/new-article [] {:date "2026-11-10" :title title :body "# Otsikko\n\nTeksti [linkki](https://a.fi)"})
          params (let [{:keys [date header]} (parse-like-site content)] {:date date :header header})]
      (is (= "2026-11-10" (:date params)) title)
      (is (= (af/header-title title) (:header params)) title)
      (is (str/starts-with? filename "2026-11-10-"))
      (is (str/ends-with? content "\n\n# Otsikko\n\nTeksti [linkki](https://a.fi)\n")))))

(deftest new-article-never-overwrites
  (let [a {:date "2026-11-10" :title "Sama" :body "x"}]
    (is (= "2026-11-10-Sama.md" (:filename (af/new-article [] a))))
    (is (= "2026-11-10-Sama_2.md" (:filename (af/new-article ["2026-11-10-Sama.md"] a))))))
