(ns fpvfinland.sync.slack
  "Slack Web API access (bot token) plus pure reaction-filtering logic.
   Required bot scopes: channels:read, channels:history, channels:join, reactions:read, reactions:write.
   User profiles are never fetched (no users:read)."
  (:require [clojure.string :as str]
            [jsonista.core :as json])
  (:import (java.net URLEncoder)))

(def base-url "https://slack.com/api/")

;; ------------------------------------------------------------------
;; Low level
;; ------------------------------------------------------------------

(defn- form-encode [params]
  (->> params
       (remove (comp nil? val))
       (map (fn [[k v]] (str (URLEncoder/encode (name k) "UTF-8") "=" (URLEncoder/encode (str v) "UTF-8"))))
       (str/join "&")))

(def ^:private hints
  {"missing_scope"     " (the bot token lacks a required scope, see docs/slack-sync.md)"
   "not_in_channel"    " (bot is not a member of the channel)"
   "invalid_auth"      " (check SLACK_BOT_TOKEN)"
   "not_authed"        " (check SLACK_BOT_TOKEN)"
   "token_revoked"     " (check SLACK_BOT_TOKEN)"
   "method_not_supported_for_channel_type" " (channel type not supported)"})

(defn call
  "Calls a Slack Web API method. Returns the parsed response (keyword keys) or throws ex-info."
  [req-fn token method params]
  (let [resp (req-fn {:method  :post
                      :url     (str base-url method)
                      :headers {"Authorization" (str "Bearer " token)
                                "Content-Type"  "application/x-www-form-urlencoded; charset=utf-8"}
                      :body    (form-encode params)})]
    (when-not (= 200 (:status resp))
      (throw (ex-info (str "Slack " method " failed: HTTP " (:status resp)) {:method method :status (:status resp)})))
    (let [data (json/read-value (:body resp) json/keyword-keys-object-mapper)]
      (if (:ok data)
        data
        (throw (ex-info (str "Slack " method " returned error '" (:error data) "'" (hints (:error data) ""))
                        {:method method :error (:error data)}))))))

(defn paginate
  "Calls a paginated method and returns a lazy-free vector of all items under result-key."
  [req-fn token method params result-key]
  (loop [cursor nil, acc []]
    (let [data   (call req-fn token method (cond-> (assoc params :limit 200) cursor (assoc :cursor cursor)))
          acc    (into acc (get data result-key))
          cursor (not-empty (get-in data [:response_metadata :next_cursor]))]
      (if cursor (recur cursor acc) acc))))

;; ------------------------------------------------------------------
;; API wrappers
;; ------------------------------------------------------------------

(defn bot-user-id [req-fn token] (:user_id (call req-fn token "auth.test" {})))

(defn list-public-channels [req-fn token]
  (paginate req-fn token "conversations.list" {:types "public_channel" :exclude_archived "true"} :channels))

(defn join-channel! [req-fn token channel-id]
  (call req-fn token "conversations.join" {:channel channel-id}))

(defn channel-history [req-fn token channel-id oldest-epoch-seconds]
  (paginate req-fn token "conversations.history"
            {:channel channel-id :oldest (str oldest-epoch-seconds ".000000")} :messages))

(defn full-reactions
  "Complete reaction list of one message (reactions.get with full=true)."
  [req-fn token channel-id ts]
  (get-in (call req-fn token "reactions.get" {:channel channel-id :timestamp ts :full "true"})
          [:message :reactions]))

(defn thread-messages
  "Whole thread (root first). Root message alone when there are no replies."
  [req-fn token channel-id root]
  (if (pos? (or (:reply_count root) 0))
    (paginate req-fn token "conversations.replies" {:channel channel-id :ts (:ts root)} :messages)
    [root]))

(defn permalink [req-fn token channel-id ts]
  (:permalink (call req-fn token "chat.getPermalink" {:channel channel-id :message_ts ts})))

(defn add-reaction!
  "Adds a reaction. An already existing reaction counts as success."
  [req-fn token channel-id ts name]
  (try (call req-fn token "reactions.add" {:channel channel-id :timestamp ts :name name})
       (catch clojure.lang.ExceptionInfo e
         (when-not (= "already_reacted" (:error (ex-data e))) (throw e)))))

;; ------------------------------------------------------------------
;; Pure reaction logic
;; ------------------------------------------------------------------

(defn- reaction [msg name] (first (filter #(= name (:name %)) (:reactions msg))))

(defn- truncated? [r] (and r (> (or (:count r) 0) (count (:users r)))))

(defn reaction-lookup-needed?
  "True when the (possibly truncated) users list in conversations.history is not enough to decide
   whether the message qualifies, so reactions.get must be called."
  [msg {:keys [allowed-user-ids trigger-reaction done-reaction]} bot-id]
  (let [trig (reaction msg trigger-reaction)
        done (reaction msg done-reaction)]
    (boolean
      (and trig
           (or (and (truncated? trig)
                    (not-any? (set allowed-user-ids) (:users trig)))
               (and (truncated? done)
                    (not (some #{bot-id} (:users done)))))))))

(defn qualifies?
  "Message (with a reliable reactions list) is a sync candidate: an allowlisted user added the
   trigger reaction and the bot has not yet added the done reaction. Only thread roots qualify."
  [msg {:keys [allowed-user-ids trigger-reaction done-reaction]} bot-id]
  (let [trig (reaction msg trigger-reaction)
        done (reaction msg done-reaction)
        root? (or (nil? (:thread_ts msg)) (= (:thread_ts msg) (:ts msg)))]
    (boolean
      (and root?
           trig
           (some (set allowed-user-ids) (:users trig))
           (not (some #{bot-id} (:users done)))))))

(defn candidate-messages
  "Filters history messages. lookup-fn: (fn [msg]) -> full reactions list, called only when needed."
  [messages cfg bot-id lookup-fn]
  (->> messages
       (filter #(reaction % (:trigger-reaction cfg)))
       (map (fn [m] (if (reaction-lookup-needed? m cfg bot-id)
                      (assoc m :reactions (lookup-fn m))
                      m)))
       (filter #(qualifies? % cfg bot-id))
       vec))
