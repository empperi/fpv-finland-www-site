(ns fpvfinland.sync.validate-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [fpvfinland.sync.validate :as v])
  (:import (java.time LocalDate)))

(def ctx {:today (LocalDate/parse "2026-11-15")
          :events [{:date "2026-12-01" :title "Kisa" :desc "x"}
                   {:date "2026-12-02" :title "Kisa" :desc "x"}
                   {:date "2026-12-05" :title "Kaksi" :desc "x"}
                   {:date "2026-12-05" :title "Kaksi" :desc "y"}]
          :article-titles ["vanha artikkeli"]})

(defn ok? [a] (contains? (v/validate-action a ctx) :ok))
(defn err [a] (:error (v/validate-action a ctx)))

(def good-event {:type "add_event" :date "2026-12-20" :title "  Uusi\n kisa " :desc "  Teksti [linkki](https://example.com)  "})
(def good-article {:type "add_article" :date "2026-11-10" :title "Uutinen" :body "Hei\n\nMaailma"})

(deftest happy-paths
  (is (= {:type :add-event :date "2026-12-20" :title "Uusi kisa" :desc "Teksti [linkki](https://example.com)"}
         (:ok (v/validate-action good-event ctx))))
  (is (= {:type :add-article :date "2026-11-10" :title "Uutinen" :body "Hei\n\nMaailma"}
         (:ok (v/validate-action good-article ctx))))
  (is (= {:type :none :reason "ei"} (:ok (v/validate-action {:type "none" :reason "ei"} ctx))))
  (is (= {:type :update-event :match-date "2026-12-01" :match-title "Kisa" :desc "Uusi"}
         (:ok (v/validate-action {:type "update_event" :match_date "2026-12-01" :match_title "Kisa" :desc "Uusi"} ctx)))))

(deftest dates
  (is (re-find #"valid" (err (assoc good-event :date "2026-02-30"))))
  (is (re-find #"valid" (err (assoc good-event :date "20.12.2026"))))
  (is (re-find #"missing" (err (assoc good-event :date nil))))
  (is (re-find #"2 years" (err (assoc good-event :date "2029-01-01"))))
  (is (re-find #"2 years" (err (assoc good-event :date "2020-01-01"))))
  (is (ok? (assoc good-event :date "2028-11-10"))))

(deftest titles-and-lengths
  (is (re-find #"empty" (err (assoc good-event :title "   "))))
  (is (re-find #"longer" (err (assoc good-event :title (apply str (repeat 121 "a"))))))
  (is (ok? (assoc good-event :title (apply str (repeat 120 "a")))))
  (is (re-find #"longer" (err (assoc good-article :title (apply str (repeat 151 "a"))))))
  (is (ok? (assoc good-article :title (apply str (repeat 150 "a")))))
  (is (re-find #"longer" (err (assoc good-event :desc (apply str (repeat 4001 "a"))))))
  (is (re-find #"longer" (err (assoc good-article :body (apply str (repeat 20001 "a"))))))
  (testing "event desc is optional: placeholder and flag"
    (doseq [a [(dissoc good-event :desc) (assoc good-event :desc "") (assoc good-event :desc "  \n ")]]
      (is (= {:type :add-event :date "2026-12-20" :title "Uusi kisa" :desc "Lisätietoja tulossa." :placeholder-desc? true}
             (:ok (v/validate-action a ctx))))))
  (is (re-find #"missing or non-string" (err (assoc good-event :desc 5))))
  (is (re-find #"HTML" (err (assoc good-event :desc "<b>x</b>"))))
  (is (re-find #"empty" (err (assoc good-article :body "")))))

(deftest markdown-safety
  (doseq [bad ["<script>alert(1)</script>" "x <iframe src=a></iframe>" "<style>x</style>" "<img src=x onerror=a()>"
               "a <b>bold</b>" "<https://example.com>"]]
    (is (re-find #"HTML" (err (assoc good-event :desc bad))) bad)
    (is (re-find #"HTML" (err (assoc good-article :body bad))) bad))
  (testing "plugin syntax"
    (is (re-find #"plugin" (err (assoc good-article :body "Teksti\n[:button \"x\"]"))))
    (is (re-find #"plugin" (err (assoc good-event :desc "[:calendar]")))))
  (testing "link schemes"
    (doseq [bad ["[x](javascript:alert(1))" "[x](JavaScript:alert(1))" "![i](data:text/html;base64,AAA)"
                 "[x](mailto:a@b.fi)" "[x](ftp://a)" "[x]: javascript:alert(1)" "[x](javascript&#58;alert(1))"]]
      (is (re-find #"scheme" (err (assoc good-event :desc bad))) bad))
    (doseq [good ["[x](https://a.fi)" "[x](http://a.fi)" "![i](https://a.fi/i.png)" "[x](/artikkelit/a.html)" "a < b ja 3 < 5"]]
      (is (ok? (assoc good-event :desc good)) good)))
  (testing "plain text with a lone <"
    (is (ok? (assoc good-event :desc "Hinta <3 euroa")))))

(deftest update-matching
  (is (re-find #"matched 0" (err {:type "update_event" :match_date "2026-12-01" :match_title "Ei ole" :desc "x"})))
  (is (re-find #"matched 2" (err {:type "update_event" :match_date "2026-12-05" :match_title "Kaksi" :desc "x"})))
  (is (re-find #"changes nothing" (err {:type "update_event" :match_date "2026-12-01" :match_title "Kisa"})))
  (is (re-find #"valid" (err {:type "update_event" :match_date "2026-12-01" :match_title "Kisa" :date "foo"})))
  (is (ok? {:type "update_event" :match_date "2026-12-01" :match_title " Kisa " :title "Uusi nimi" :date "2026-12-03"})))

(deftest duplicates-and-unknown
  (is (re-find #"already exists" (err (assoc good-event :date "2026-12-01" :title "Kisa"))))
  (is (re-find #"already exists" (err (assoc good-article :title "Vanha Artikkeli"))))
  (is (re-find #"unknown" (err {:type "delete_everything"})))
  (is (re-find #"not an object" (err "hello"))))

(deftest control-characters-stripped
  (is (= "ab\ncd" (:desc (:ok (v/validate-action (assoc good-event :desc "a\u0000b\r\ncd\u0007") ctx))))))

(deftest field-name-leniency
  (testing "article text in desc is used as body"
    (is (= {:type :add-article :date "2026-11-10" :title "Uutinen" :body "Koko teksti"}
           (:ok (v/validate-action {:type "add_article" :date "2026-11-10" :title "Uutinen" :desc "Koko teksti"} ctx)))))
  (testing "correct field wins when both are present"
    (is (= "Oikea" (:body (:ok (v/validate-action (assoc good-article :body "Oikea" :desc "Väärä") ctx))))))
  (testing "event body used as desc when desc is missing"
    (is (= "Teksti" (:desc (:ok (v/validate-action {:type "add_event" :date "2026-12-20" :title "X" :body "Teksti"} ctx)))))
    (is (= "Teksti" (:desc (:ok (v/validate-action {:type "update_event" :match_date "2026-12-01" :match_title "Kisa" :body "Teksti"} ctx))))))
  (testing "event: correct desc wins"
    (is (= "Oikea" (:desc (:ok (v/validate-action (assoc good-event :desc "Oikea" :body "Väärä") ctx)))))))

(deftest missing-field-message-lists-present-fields
  (let [e (err {:type "add_article" :date "2026-11-10" :title "T" :notes "n"})]
    (is (re-find #"missing or non-string field 'body'" e))
    (is (re-find #"fields present: .*type" e))
    (is (re-find #"fields present: .*title" e))
    (is (re-find #"fields present: .*notes" e))
    (is (not (re-find #"fields present: .*body" e)))))
