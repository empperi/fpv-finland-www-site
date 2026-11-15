(ns fpvfinland.sync.validate
  "Validation and normalisation of LLM-proposed actions. The LLM output is untrusted:
   nothing reaches a file unless it passes through validate-action."
  (:require [clojure.string :as str]
            [fpvfinland.sync.events-file :as ef])
  (:import (java.time LocalDate)
           (java.time.format DateTimeParseException)
           (java.time.temporal ChronoUnit)))

(def limits {:event-title 120 :article-title 150 :event-desc 4000 :article-body 20000})
(def max-date-distance-days 730)
(def placeholder-desc "Lisätietoja tulossa.")

(defn- fail [msg] (throw (ex-info msg {::invalid true})))

(defn clean-text
  "Normalises line endings, drops control characters (except newline/tab), trims."
  [s]
  (-> s
      (str/replace "\r\n" "\n")
      (str/replace "\r" "\n")
      (str/replace #"[\p{Cc}&&[^\n\t]]" "")
      str/trim))

(defn normalize-title
  "Trimmed, single line."
  [s]
  (-> (clean-text s) (str/replace #"\s+" " ") str/trim))

(defn parse-date [s]
  (when (and (string? s) (re-matches #"\d{4}-\d{2}-\d{2}" s))
    (try (LocalDate/parse s) (catch DateTimeParseException _ nil))))

(defn- need-string [action k]
  (let [v (get action k)]
    (when-not (string? v) (fail (str "missing or non-string field '" (name k) "'")))
    v))

(defn- check-date! [s ^LocalDate today label]
  (let [d (parse-date s)]
    (when-not d (fail (str label " is not a valid YYYY-MM-DD date: " (pr-str s))))
    (when (> (Math/abs (.between ChronoUnit/DAYS today d)) max-date-distance-days)
      (fail (str label " is more than 2 years from today: " s)))
    s))

(defn- check-title! [s limit label]
  (let [t (normalize-title s)]
    (when (str/blank? t) (fail (str label " is empty")))
    (when (> (count t) limit) (fail (str label " is longer than " limit " characters")))
    (when (re-find #"<[A-Za-z/!?]" t) (fail (str label " contains HTML-like content")))
    t))

(defn- safe-url? [url]
  (let [u (str/trim url)]
    (and (not (re-find #"(?i)&#|&colon;|%3a" u))
         (if-let [scheme (re-find #"^[A-Za-z][A-Za-z0-9+.\-]*:" u)]
           (boolean (#{"http:" "https:"} (str/lower-case scheme)))
           true))))

(defn- check-markdown! [s limit label]
  (let [t (clean-text s)]
    (when (str/blank? t) (fail (str label " is empty")))
    (when (> (count t) limit) (fail (str label " is longer than " limit " characters")))
    (when (str/includes? t "[:") (fail (str label " contains plugin syntax ([:...)")))
    (when (re-find #"<[A-Za-z/!?]" t) (fail (str label " contains HTML-like tags")))
    (doseq [[_ url] (concat (re-seq #"\]\(\s*<?([^)\s>]*)" t)
                            (re-seq #"(?m)^\s{0,3}\[[^\]]+\]:\s*(\S+)" t))]
      (when-not (safe-url? url)
        (fail (str label " contains a link with a disallowed scheme: " (subs url 0 (min 40 (count url)))))))
    t))

(defn- describe [action]
  (or (get action :title) (get action :match_title) ""))

(defn- validate* [{:keys [type] :as action} {:keys [today events article-titles]}]
  (case type
    "none"
    {:type :none :reason (str (or (:reason action) ""))}

    "add_event"
    (let [date  (check-date! (need-string action :date) today "date")
          title (check-title! (need-string action :title) (:event-title limits) "title")
          raw   (:desc action)
          none? (or (nil? raw) (and (string? raw) (str/blank? raw)))
          desc  (if none?
                  placeholder-desc
                  (check-markdown! (need-string action :desc) (:event-desc limits) "desc"))]
      (when (seq (ef/find-matches events date title))
        (fail "an event with the same date and title already exists"))
      (cond-> {:type :add-event :date date :title title :desc desc}
        none? (assoc :placeholder-desc? true)))

    "update_event"
    (let [md      (need-string action :match_date)
          mt      (normalize-title (need-string action :match_title))
          matches (ef/find-matches events md mt)
          _       (when-not (= 1 (count matches))
                    (fail (str "update target (" md ", " (pr-str mt) ") matched " (count matches) " events, expected exactly 1")))
          changes (cond-> {}
                    (some? (:date action))  (assoc :date (check-date! (need-string action :date) today "date"))
                    (some? (:title action)) (assoc :title (check-title! (need-string action :title) (:event-title limits) "title"))
                    (some? (:desc action))  (assoc :desc (check-markdown! (need-string action :desc) (:event-desc limits) "desc")))]
      (when (empty? changes) (fail "update_event changes nothing (no date, title or desc)"))
      (assoc changes :type :update-event :match-date md :match-title mt))

    "add_article"
    (let [date  (check-date! (need-string action :date) today "date")
          title (check-title! (need-string action :title) (:article-title limits) "title")
          body  (check-markdown! (need-string action :body) (:article-body limits) "body")]
      (when (contains? (set article-titles) (str/lower-case title))
        (fail "an article with the same title already exists"))
      {:type :add-article :date date :title title :body body})

    (fail (str "unknown action type " (pr-str type)))))

(defn validate-action
  "action: map with string values as parsed from the LLM JSON (keyword keys).
   ctx: {:today LocalDate :events [event-maps] :article-titles [lower-case titles]}.
   Returns {:ok normalised-action} or {:error message :action-type t :label l}."
  [action ctx]
  (if-not (map? action)
    {:error "action is not an object"}
    (try
      {:ok (validate* action ctx)}
      (catch clojure.lang.ExceptionInfo e
        (if (::invalid (ex-data e))
          {:error (ex-message e) :action-type (:type action) :label (describe action)}
          (throw e))))))
