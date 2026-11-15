(ns fpvfinland.sync.core
  "Slack -> website sync. Usage (see docs/slack-sync.md):
     lein slack-sync                         ; collect threads, ask Gemini, write events.edn / posts
     lein slack-sync mark-done <file.json>   ; add the done reaction to processed root messages
   Options: --fixture <file.json> (offline run) and --root <dir> (repo root; default \".\")."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [jsonista.core :as json]
            [fpvfinland.sync.anonymize :as anon]
            [fpvfinland.sync.articles-file :as af]
            [fpvfinland.sync.events-file :as ef]
            [fpvfinland.sync.fixture :as fixture]
            [fpvfinland.sync.gemini :as gemini]
            [fpvfinland.sync.http :as http]
            [fpvfinland.sync.slack :as slack]
            [fpvfinland.sync.validate :as validate])
  (:import (java.nio.file Files OpenOption StandardOpenOption)
           (java.time Instant LocalDate ZoneId)))

;; ------------------------------------------------------------------
;; Config
;; ------------------------------------------------------------------

(defn- blank->nil [s] (when-not (str/blank? s) (str/trim s)))

(defn parse-config
  "env: map of env var name -> string. Returns {:config {...}} or {:missing [names] :invalid [msgs]}."
  ([env] (parse-config env nil))
  ([env command]
  (let [get*    #(blank->nil (get env %))
        required (if (= command "mark-done")
                   ["SLACK_BOT_TOKEN"]
                   ["SLACK_BOT_TOKEN" "SLACK_ALLOWED_USER_IDS" "GEMINI_API_KEY"])
        missing (vec (remove get* required))
        days-s  (get* "SYNC_LOOKBACK_DAYS")
        days    (if days-s (try (Long/parseLong days-s) (catch NumberFormatException _ nil)) 60)
        ids     (some->> (get* "SLACK_ALLOWED_USER_IDS") (#(str/split % #",")) (map str/trim) (remove str/blank?) vec)
        invalid (cond-> []
                  (or (nil? days) (<= days 0)) (conj "SYNC_LOOKBACK_DAYS must be a positive integer"))]
    (if (or (seq missing) (seq invalid))
      {:missing missing :invalid invalid}
      {:config {:slack-token       (get* "SLACK_BOT_TOKEN")
                :allowed-user-ids  ids
                :gemini-key        (get* "GEMINI_API_KEY")
                :gemini-model      (or (get* "GEMINI_MODEL") "gemini-2.5-flash")
                :lookback-days     days
                :dry-run?          (= "true" (some-> (get* "SYNC_DRY_RUN") str/lower-case))
                :trigger-reaction  (or (get* "SYNC_REACTION") "to-www-site")
                :done-reaction     (or (get* "SYNC_DONE_REACTION") "white_check_mark")}}))))

;; ------------------------------------------------------------------
;; Files (UTF-8, LF)
;; ------------------------------------------------------------------

(defn- read-file [f] (slurp f :encoding "UTF-8"))

(defn- article-files [root]
  (let [dir (io/file root "resources/public/posts")]
    (vec (for [f (sort (.listFiles dir)) :when (str/ends-with? (.getName f) ".md")]
           {:name (.getName f) :title (second (re-find #"header=\"([^\"]*)\"" (read-file f)))}))))

(defn- write-new-file! [f content]
  (Files/writeString (.toPath (io/file f)) content
                     (into-array OpenOption [StandardOpenOption/CREATE_NEW StandardOpenOption/WRITE])))

;; ------------------------------------------------------------------
;; Per-thread processing (pure apart from the Gemini call)
;; ------------------------------------------------------------------

(defn- apply-action
  "Returns [state' note] for one action. note: {:kind :applied|:none|:rejected :text str}."
  [state {:keys [today]} action]
  (let [events (ef/read-events (:events-text state))
        v      (validate/validate-action action {:today today :events events
                                                 :article-titles (map str/lower-case (:article-titles state))})]
    (if-let [msg (:error v)]
      [state {:kind :rejected
              :text (str (or (:action-type v) "action") (when-let [l (:label v)] (when (seq l) (str " \"" l "\""))) ": " msg)}]
      (let [a (:ok v)]
        (case (:type a)
          :none [state {:kind :none :text (str "Nothing to publish: " (:reason a))}]
          :add-event [(update state :events-text ef/add-event (select-keys a [:date :title :desc]))
                      {:kind :applied :text (str "Added event " (:date a) " \"" (:title a) "\"")}]
          :update-event [(update state :events-text ef/update-event (:match-date a) (:match-title a)
                                 (select-keys a [:date :title :desc]))
                         {:kind :applied :text (str "Updated event " (:match-date a) " \"" (:match-title a) "\"")}]
          :add-article (let [{:keys [filename content]} (af/new-article (:article-names state) a)]
                         [(-> state
                              (update :new-articles conj {:filename filename :content content})
                              (update :article-names conj filename)
                              (update :article-titles conj (:title a)))
                          {:kind :applied :text (str "Drafted new article \"" (:title a) "\" (" filename ")")}]))))))

(defn process-thread
  "Asks Gemini about one anonymised thread and applies validated actions to state.
   Returns {:state :status (:changed :none :rejected :error) :notes [{:kind :text}]}."
  [req-fn {:keys [gemini-cfg today] :as ctx} state anonymized]
  (let [res (gemini/generate req-fn gemini-cfg
                             {:today (str today)
                              :events (ef/read-events (:events-text state))
                              :article-titles (:article-titles state)
                              :thread anonymized})]
    (if (:error res)
      {:state state :status :error :notes [{:kind :error :text (first (str/split-lines (:error res)))}]}
      (if (empty? (:actions res))
        {:state state :status :rejected :notes [{:kind :rejected :text "Model returned no actions"}]}
        (let [{:keys [state notes]}
              (reduce (fn [{:keys [state notes]} action]
                        (let [[s n] (apply-action state ctx action)] {:state s :notes (conj notes n)}))
                      {:state state :notes []} (:actions res))
              kinds (set (map :kind notes))]
          {:state state
           :notes notes
           :status (cond (kinds :applied) :changed
                         (kinds :rejected) :rejected
                         :else :none)})))))

;; ------------------------------------------------------------------
;; Slack gathering
;; ------------------------------------------------------------------

(defn- gather-threads
  "Returns {:threads [{:channel :channel-name :ts :messages :permalink}] :problems [{:text}]}."
  [req-fn {:keys [slack-token dry-run? lookback-days] :as cfg} now]
  (let [bot-id (slack/bot-user-id req-fn slack-token)
        oldest (- (.getEpochSecond ^Instant now) (* lookback-days 86400))
        problems (atom [])
        problem! (fn [s] (println "WARN:" s) (swap! problems conj {:kind :problem :text s}))
        threads (doall
                  (for [ch (slack/list-public-channels req-fn slack-token)
                        :let [cid (:id ch)
                              ok? (cond (:is_member ch) true
                                        dry-run? (do (println "dry run: not joining" (:name ch)) false)
                                        :else (try (slack/join-channel! req-fn slack-token cid) true
                                                   (catch Exception e (problem! (str "Cannot join #" (:name ch) ": " (ex-message e))) false)))]
                        :when ok?
                        m (try (slack/candidate-messages
                                 (slack/channel-history req-fn slack-token cid oldest) cfg bot-id
                                 #(slack/full-reactions req-fn slack-token cid (:ts %)))
                               (catch Exception e (problem! (str "Cannot read #" (:name ch) ": " (ex-message e))) []))]
                    (try {:channel cid :channel-name (:name ch) :ts (:ts m)
                          :messages (slack/thread-messages req-fn slack-token cid m)
                          :permalink (slack/permalink req-fn slack-token cid (:ts m))}
                         (catch Exception e
                           (problem! (str "Cannot fetch thread " cid "/" (:ts m) ": " (ex-message e)))
                           nil))))]
    {:threads (vec (remove nil? threads)) :problems @problems}))

;; ------------------------------------------------------------------
;; Report
;; ------------------------------------------------------------------

(defn pr-body
  "outcomes: [{:permalink :status :notes}]. problems: [{:text}]."
  [outcomes problems]
  (str "> **Generated from Slack by a bot. Review all text (and the Finnish language) before merging.**\n"
       "> Nothing is published until this PR is merged.\n\n"
       (str/join "\n"
         (for [[i o] (map-indexed vector outcomes)]
           (str "### Thread " (inc i) ": " (:permalink o) "\n"
                (str/join "\n" (for [n (:notes o)]
                                 (str "- " (case (:kind n) :applied "" :none "Skipped. " :rejected "**Rejected.** " :error "**Error (will be retried next run).** ") (:text n))))
                "\n"
                (when (= :rejected (:status o))
                  "- _Validation failure: this thread is marked as processed and will not be retried._\n")
                (when (= :error (:status o))
                  "- _Not marked as processed; it is retried on the next run._\n"))))
       (when (seq problems)
         (str "\n### Warnings\n" (str/join "\n" (map #(str "- " (:text %)) problems)) "\n"))))

(defn- processed-entries
  "Which threads get the done reaction. Validation failures only count when there is a PR to tell about it."
  [outcomes changed?]
  (vec (for [o outcomes
             :when (or (#{:changed :none} (:status o)) (and changed? (= :rejected (:status o))))]
         {:channel (:channel o) :ts (:ts o)})))

;; ------------------------------------------------------------------
;; Main flows
;; ------------------------------------------------------------------

(defn run-sync
  "Does the whole sync. Writes files under root. Returns {:changed? :outcomes :processed}."
  [{:keys [req-fn config root now]}]
  (let [cfg      config
        today    (.toLocalDate (.atZone ^Instant now (ZoneId/of "Europe/Helsinki")))
        events-f (io/file root "events.edn")
        orig     (read-file events-f)
        arts     (article-files root)
        state0   {:events-text orig :new-articles []
                  :article-names (set (map :name arts)) :article-titles (vec (keep :title arts))}
        {:keys [threads problems]} (gather-threads req-fn cfg now)
        ctx      {:today today :gemini-cfg {:api-key (:gemini-key cfg) :model (:gemini-model cfg)}}
        [state outcomes]
        (reduce (fn [[state outs] t]
                  (let [anonymized (anon/anonymize-thread (:messages t))
                        r (process-thread req-fn ctx state anonymized)]
                    (println (str "thread " (:channel t) "/" (:ts t) ": " (name (:status r))))
                    [(:state r) (conj outs (merge (select-keys t [:channel :ts :permalink]) (select-keys r [:status :notes])))]))
                [state0 []] threads)
        changed? (boolean (or (not= orig (:events-text state)) (seq (:new-articles state))))
        processed (processed-entries outcomes changed?)
        out-dir  (io/file root "sync-result")]
    (when (not= orig (:events-text state))
      (spit events-f (:events-text state) :encoding "UTF-8"))
    (doseq [{:keys [filename content]} (:new-articles state)]
      (write-new-file! (io/file root "resources/public/posts" filename) content))
    (.mkdirs out-dir)
    (spit (io/file out-dir "pr-body.md") (pr-body outcomes problems) :encoding "UTF-8")
    (spit (io/file out-dir "processed.json") (json/write-value-as-string processed) :encoding "UTF-8")
    (println (str "threads: " (count threads) ", changed: " changed? ", to mark done: " (count processed)
                  (when (:dry-run? cfg) " (dry run: no Slack writes)")))
    {:changed? changed? :outcomes outcomes :processed processed}))

(defn mark-done
  "Adds the done reaction to each {channel, ts} in the JSON file. Returns number of failures."
  [req-fn token done-reaction file]
  (let [entries (json/read-value (read-file file) json/keyword-keys-object-mapper)]
    (count
      (remove true?
        (for [{:keys [channel ts]} entries]
          (try (slack/add-reaction! req-fn token channel ts done-reaction)
               (println "marked" channel ts) true
               (catch Exception e (println "WARN: could not mark" channel ts ":" (ex-message e)) false)))))))

;; ------------------------------------------------------------------
;; CLI
;; ------------------------------------------------------------------

(defn parse-args [args]
  (loop [[a & more :as args] args, opts {:positional []}]
    (cond (empty? args) opts
          (= a "--fixture") (recur (rest more) (assoc opts :fixture (first more)))
          (= a "--root")    (recur (rest more) (assoc opts :root (first more)))
          :else             (recur more (update opts :positional conj a)))))

(defn -main [& args]
  (let [{:keys [positional fixture root]} (parse-args args)
        fx      (when fixture (json/read-value (read-file fixture)))
        env     (merge (when fx {"SLACK_BOT_TOKEN" "fixture" "GEMINI_API_KEY" "fixture"
                                 "SLACK_ALLOWED_USER_IDS" "U_FIXTURE" "SYNC_DRY_RUN" "true"})
                       (into {} (System/getenv))
                       (get fx "env"))
        parsed  (parse-config env (first positional))]
    (when-not (:config parsed)
      (binding [*out* *err*]
        (when (seq (:missing parsed)) (println "Missing required environment variables:" (str/join ", " (:missing parsed))))
        (doseq [m (:invalid parsed)] (println "Invalid configuration:" m)))
      (System/exit 2))
    (let [config (:config parsed)
          req-fn (if fx (fixture/request-fn fx) (http/with-retry (http/real-request-fn)))
          root   (or root ".")]
      (try
        (case (first positional)
          nil         (do (run-sync {:req-fn req-fn :config config :root root :now (Instant/now)})
                          (System/exit 0))
          "mark-done" (do (when-not (second positional) (throw (ex-info "usage: mark-done <file.json>" {})))
                          (System/exit (if (zero? (mark-done req-fn (:slack-token config) (:done-reaction config) (second positional))) 0 1)))
          (throw (ex-info (str "unknown command " (first positional)) {})))
        (catch Exception e
          (binding [*out* *err*] (println "ERROR:" (ex-message e)))
          (System/exit 1))))))
