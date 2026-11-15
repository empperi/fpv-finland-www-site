(ns fpvfinland.sync.core-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [jsonista.core :as json]
            [fpvfinland.sync.core :as core]
            [fpvfinland.sync.events-file :as ef]
            [fpvfinland.sync.fixture :as fixture])
  (:import (java.nio.file Files)
           (java.nio.file.attribute FileAttribute)
           (java.time Instant)))

(def base-env {"SLACK_BOT_TOKEN" "x" "SLACK_ALLOWED_USER_IDS" "U1, U2 ,," "GEMINI_API_KEY" "k"})

(deftest config
  (testing "missing vars listed"
    (is (= ["SLACK_BOT_TOKEN" "SLACK_ALLOWED_USER_IDS" "GEMINI_API_KEY"] (:missing (core/parse-config {})))))
  (testing "mark-done needs only the Slack token"
    (is (= ["SLACK_BOT_TOKEN"] (:missing (core/parse-config {} "mark-done"))))
    (is (:config (core/parse-config {"SLACK_BOT_TOKEN" "x"} "mark-done"))))
  (testing "blank counts as missing"
    (is (= ["GEMINI_API_KEY"] (:missing (core/parse-config (assoc base-env "GEMINI_API_KEY" "  "))))))
  (testing "defaults and parsing"
    (let [c (:config (core/parse-config base-env))]
      (is (= ["U1" "U2"] (:allowed-user-ids c)))
      (is (= 60 (:lookback-days c)))
      (is (= "to-www-site" (:trigger-reaction c)))
      (is (= "white_check_mark" (:done-reaction c)))
      (is (false? (:dry-run? c)))))
  (testing "overrides"
    (let [c (:config (core/parse-config (assoc base-env "SYNC_DRY_RUN" "true" "SYNC_LOOKBACK_DAYS" "7" "GEMINI_MODEL" "m")))]
      (is (true? (:dry-run? c)))
      (is (= 7 (:lookback-days c)))
      (is (= "m" (:gemini-model c)))))
  (testing "invalid lookback"
    (is (seq (:invalid (core/parse-config (assoc base-env "SYNC_LOOKBACK_DAYS" "abc")))))))

(def events-text
  (str "[{:date \"2026-10-30\"\n"
       "  :title \"Kolmas\"\n"
       "  :desc \"\n"
       "Vanha kuvaus\n"
       "\"}]\n"))

(defn- temp-root []
  (let [d (.toFile (Files/createTempDirectory "slack-sync-test" (make-array FileAttribute 0)))]
    (.mkdirs (io/file d "resources/public/posts"))
    (spit (io/file d "events.edn") events-text :encoding "UTF-8")
    (spit (io/file d "resources/public/posts/2026-01-01-Vanha.md")
          "[:article-lead\n  date=\"2026-01-01\"\n  header=\"Vanha\"]\n\nteksti\n" :encoding "UTF-8")
    d))

(deftest end-to-end-with-fixture
  (let [fx     (json/read-value (slurp "test/fixtures/slack-sync.json" :encoding "UTF-8"))
        writes (atom [])
        req    (fixture/request-fn fx writes)
        root   (temp-root)
        config (:config (core/parse-config (merge base-env {"SLACK_ALLOWED_USER_IDS" "U_ADMIN,U_ADMIN2" "SYNC_DRY_RUN" "true"})))
        result (core/run-sync {:req-fn req :config config :root (str root)
                               :now (Instant/parse "2026-11-15T12:00:00Z")})
        events (ef/read-events (slurp (io/file root "events.edn") :encoding "UTF-8"))
        posts  (map #(.getName %) (.listFiles (io/file root "resources/public/posts")))
        body   (slurp (io/file root "sync-result/pr-body.md") :encoding "UTF-8")
        processed (json/read-value (slurp (io/file root "sync-result/processed.json")) json/keyword-keys-object-mapper)]
    (is (:changed? result))
    (testing "event added (with escaped inch mark) and existing one updated"
      (is (= ["Kolmas" "Talvikisa"] (map :title events)))
      (is (= "\nPäivitetty kuvaus\n" (:desc (first events))))
      (is (str/includes? (:desc (second events)) "5\" kokoluokalla")))
    (testing "article drafted without overwriting"
      (is (= 2 (count posts)))
      (is (some #(str/starts-with? % "2026-11-14-Kisaraportti_Talvi_meni_hyvin") posts)))
    (testing "PR body lists permalinks, rejections and errors; never message text"
      (is (str/includes? body "https://example.slack.com/archives/C1/p10001"))
      (is (str/includes? body "**Rejected.** add_event"))
      (is (str/includes? body "Error (will be retried"))
      (is (str/includes? body "Skipped. Nothing to publish"))
      (is (not (str/includes? body "mikko")))
      (is (not (str/includes? body "Launoisissa"))))
    (testing "processed: changed, rejected (a PR exists), none; not the Gemini failure or ignored messages"
      (is (= ["1000.1" "1000.4" "1000.5" "1000.7"] (map :ts processed))))
    (testing "no Slack writes in this run (marking is a separate step)"
      (is (empty? @writes)))))

(deftest rejected-only-run-is-not-marked
  (let [fx   (-> (json/read-value (slurp "test/fixtures/slack-sync.json" :encoding "UTF-8"))
                 (assoc "gemini" (vec (concat [(get-in (json/read-value (slurp "test/fixtures/slack-sync.json")) ["gemini" 2])]
                                              (repeat 4 {"candidates" []})))))
        root (temp-root)
        config (:config (core/parse-config (merge base-env {"SLACK_ALLOWED_USER_IDS" "U_ADMIN,U_ADMIN2"})))
        result (core/run-sync {:req-fn (fixture/request-fn fx) :config config :root (str root)
                               :now (Instant/parse "2026-11-15T12:00:00Z")})]
    (is (not (:changed? result)))
    (is (= events-text (slurp (io/file root "events.edn") :encoding "UTF-8")))
    (is (empty? (:processed result)))))

(deftest mark-done-adds-reactions
  (let [writes (atom [])
        req    (fixture/request-fn {"slack" {}} writes)
        f      (java.io.File/createTempFile "processed" ".json")]
    (spit f "[{\"channel\":\"C1\",\"ts\":\"1.5\"},{\"channel\":\"C2\",\"ts\":\"2.5\"}]")
    (is (zero? (core/mark-done req "tok" "white_check_mark" (str f))))
    (is (= [["C1" "1.5" "white_check_mark"] ["C2" "2.5" "white_check_mark"]]
           (map (fn [w] [(get-in w [:params "channel"]) (get-in w [:params "timestamp"]) (get-in w [:params "name"])]) @writes)))))
