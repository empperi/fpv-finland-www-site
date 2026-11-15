(ns fpvfinland.sync.core-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [jsonista.core :as json]
            [fpvfinland.sync.core :as core]
            [fpvfinland.sync.events-file :as ef]
            [fpvfinland.sync.fixture :as fixture]
            [fpvfinland.sync.gemini :as gemini])
  (:import (java.nio.file Files)
           (java.nio.file.attribute FileAttribute)
           (java.time Instant LocalDate)))

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

;; ---- repair round, notes, PR wording, error reporting ----

(defn- gem-resp [text]
  {:status 200 :body (json/write-value-as-string {:candidates [{:content {:parts [{:text text}]}}]})})

(defn- seq-req
  "Request fn answering with the given responses in order; returns [req-fn calls-atom]."
  [responses]
  (let [q (atom responses) calls (atom [])]
    [(fn [req] (swap! calls conj req) (let [r (first @q)] (swap! q rest) r)) calls]))

(defn- ev-json [title & [notes]]
  (json/write-value-as-string
    {:actions [(cond-> {:type "add_event" :date "2026-12-12" :title title :desc "x"} notes (assoc :notes notes))]}))

(def pt-ctx {:today (LocalDate/parse "2026-11-15") :gemini-cfg {:api-key "k" :model "m"}})
(def pt-state {:events-text events-text :new-articles [] :article-names #{} :article-titles []})
(def long-title (apply str (repeat 130 "a")))

(deftest no-repair-when-first-answer-valid
  (let [[req calls] (seq-req [(gem-resp (ev-json "Hyvä" "mietin @joku:n sanaa\nkahdella rivillä"))])
        r (core/process-thread req pt-ctx pt-state [{:author "Jäsen 1" :text "hei"}])]
    (is (= 1 (count @calls)))
    (is (= :changed (:status r)))
    (is (not (:repaired? r)))
    (testing "model notes are kept for the PR body, single line, mentions defanged"
      (let [n (some :model-notes (:notes r))]
        (is (= "mietin @​joku:n sanaa kahdella rivillä" n))))))

(deftest repair-succeeds
  (let [[req calls] (seq-req [(gem-resp (ev-json long-title)) (gem-resp (ev-json "Lyhyt"))])
        r (core/process-thread req pt-ctx pt-state [{:author "Jäsen 1" :text "hei"}])
        second-prompt (get-in (json/read-value (:body (second @calls))) ["contents" 0 "parts" 0 "text"])]
    (is (= 2 (count @calls)))
    (is (= :changed (:status r)))
    (is (:repaired? r))
    (is (str/includes? second-prompt "longer than 120"))
    (is (str/includes? second-prompt long-title) "previous JSON is included")
    (is (str/includes? (:events-text (:state r)) "\"Lyhyt\""))
    (is (not (str/includes? (:events-text (:state r)) long-title)))))

(deftest repair-still-invalid
  (let [[req calls] (seq-req [(gem-resp (ev-json long-title)) (gem-resp (ev-json long-title)) (gem-resp (ev-json "Ei kutsuta"))])
        r (core/process-thread req pt-ctx pt-state [{:author "Jäsen 1" :text "hei"}])]
    (is (= 2 (count @calls)) "at most one repair")
    (is (= :rejected (:status r)))
    (is (= events-text (:events-text (:state r))))
    (is (some #(re-find #"longer than 120" (:text %)) (:notes r)))))

(deftest repair-api-failure-falls-back-to-first-result
  (let [[req calls] (seq-req [(gem-resp (ev-json long-title)) {:status 500 :body "{}"}])
        r (core/process-thread req pt-ctx pt-state [{:author "Jäsen 1" :text "hei"}])]
    (is (= :rejected (:status r)))
    (is (= 2 (count @calls)))))

(deftest gemini-http-error-includes-message-and-model-hint
  (let [r (gemini/generate-prompt
            (fn [_] {:status 404 :body "{\"error\":{\"code\":404,\"message\":\"models/m is not found\",\"status\":\"NOT_FOUND\"}}"})
            {:api-key "k" :model "m"} "p")]
    (is (str/includes? (:error r) "Gemini HTTP 404: models/m is not found"))
    (is (str/includes? (:error r) "check GEMINI_MODEL, current: m")))
  (testing "non-JSON error body"
    (is (= "Gemini HTTP 500" (:error (gemini/generate-prompt (fn [_] {:status 500 :body "oops"}) {:api-key "k" :model "m"} "p"))))))

(deftest pr-body-wording-matches-marking
  (let [o {:permalink "https://x/p1" :status :rejected :repaired? true
           :notes [{:kind :rejected :text "add_event: bad" :model-notes "epävarma"}]}
        with-changes    (core/pr-body [o] [] true)
        without-changes (core/pr-body [o] [] false)]
    (is (str/includes? with-changes "marked as processed and will not be retried"))
    (is (not (str/includes? without-changes "will not be retried")))
    (is (str/includes? without-changes "will be retried on the next run"))
    (is (str/includes? with-changes "one repair request was used"))
    (is (str/includes? with-changes "Model notes (unverified): epävarma"))))

(deftest console-charset-selection
  (let [cs (fn [m kind] (str (core/console-charset m kind)))]
    (is (= "IBM437" (cs {"stdout.encoding" "cp437"} "stdout")))
    (is (= "windows-1252" (cs {"native.encoding" "Cp1252"} "stderr")) "falls back to native.encoding")
    (is (= "UTF-8" (cs {} "stdout")) "falls back to UTF-8")
    (is (= "UTF-8" (cs {"stdout.encoding" "no-such-charset"} "stdout")))
    (is (= "UTF-8" (cs {"stdout.encoding" "UTF-8" "native.encoding" "cp437"} "stdout")))))

(deftest placeholder-desc-is-flagged-in-pr-body
  (let [[req _] (seq-req [(gem-resp (json/write-value-as-string
                                      {:actions [{:type "add_event" :date "2026-12-12" :title "Nimi"}]}))])
        r (core/process-thread req pt-ctx pt-state [{:author "Jäsen 1" :text "hei"}])
        body (core/pr-body [(assoc r :permalink "p")] [] true)]
    (is (= :changed (:status r)))
    (is (not (:repaired? r)) "missing desc no longer triggers a repair")
    (is (str/includes? (:events-text (:state r)) "Lisätietoja tulossa."))
    (is (str/includes? body "No description in the thread; placeholder used"))))

(deftest gemini-progress-lines
  (let [lines (atom [])
        [req _] (seq-req [(gem-resp (ev-json "Hyvä"))])]
    (binding [fpvfinland.sync.log/*log-fn* #(swap! lines conj %)]
      (core/process-thread req (assoc pt-ctx :thread-id "C1/1.5") pt-state [{:author "Jäsen 1" :text "hei"}]))
    (is (= "gemini: thread C1/1.5: request 1 (initial) ..." (first @lines)))
    (is (re-matches #"gemini: thread C1/1.5: done in \d+ s" (second @lines)))))
