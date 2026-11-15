(ns fpvfinland.sync.fixture
  "Offline request function for `--fixture file.json`: serves recorded Slack and Gemini responses.
   Fixture shape:
   {\"env\":    {\"SLACK_ALLOWED_USER_IDS\": \"U1\"},            ; optional, overrides defaults
    \"slack\":  {\"auth.test\": {...}, \"conversations.history:C1\": {...},
               \"conversations.replies:C1:123.456\": {...}},   ; method[:channel[:ts]] -> response object
    \"gemini\": [{...raw generateContent response...}, ...]}  ; consumed one per call, in order"
  (:require [clojure.string :as str]
            [jsonista.core :as json]
            [fpvfinland.sync.slack :as slack])
  (:import (java.net URLDecoder)))

(defn- parse-form [body]
  (into {} (for [pair (str/split (or body "") #"&") :when (seq pair)
                 :let [[k v] (str/split pair #"=" 2)]]
             [(URLDecoder/decode k "UTF-8") (URLDecoder/decode (or v "") "UTF-8")])))

(defn- json-response [obj] {:status 200 :headers {} :body (json/write-value-as-string obj)})

(defn request-fn
  "Returns a request function backed by the fixture map (string keys). Slack writes are recorded in
   the atom `writes` when given."
  ([fixture] (request-fn fixture (atom [])))
  ([fixture writes]
   (let [gemini-queue (atom (vec (get fixture "gemini")))]
     (fn [{:keys [url body]}]
       (if (str/starts-with? url slack/base-url)
         (let [method (subs url (count slack/base-url))
               p      (parse-form body)
               ch     (get p "channel")
               ts     (or (get p "ts") (get p "timestamp") (get p "message_ts"))
               slack-fx (get fixture "slack")]
           (when (#{"reactions.add" "conversations.join"} method)
             (swap! writes conj {:method method :params p}))
           (json-response
             (or (get slack-fx (str method ":" ch ":" ts))
                 (get slack-fx (str method ":" ch))
                 (get slack-fx method)
                 (if (= method "reactions.add")
                   {"ok" true}
                   {"ok" false "error" (str "fixture_missing_" method)}))))
         (if-let [r (first @gemini-queue)]
           (do (swap! gemini-queue subvec 1) (json-response r))
           {:status 500 :headers {} :body "fixture: no more gemini responses"}))))))
