(ns fpvfinland.sync.anonymize
  "Strips identities from Slack threads before anything is sent to an LLM.
   Only the message text and a per-thread pseudonym for the author leave this namespace."
  (:require [clojure.string :as str]))

(def ^:private redacted-email "[sähköposti poistettu]")

(def ^:private skipped-subtypes #{"channel_join" "channel_leave" "channel_topic" "channel_purpose"
                                  "channel_name" "channel_archive" "channel_unarchive"})

(defn- pseudonym [n] (str "Jäsen " n))

(defn anonymize-thread
  "messages: Slack messages in thread order (root first). Returns a vector of
   {:author \"Jäsen 1\" :text \"...\"}. Pseudonyms are assigned by first appearance:
   authors in message order first, then users only mentioned in text. Same user id => same pseudonym."
  [messages]
  (let [messages (remove #(skipped-subtypes (:subtype %)) messages)
        ids      (volatile! {})
        label    (fn [uid]
                   (or (get @ids uid)
                       (let [l (pseudonym (inc (count @ids)))]
                         (vswap! ids assoc uid l)
                         l)))]
    (doseq [m messages :when (:user m)] (label (:user m)))
    (letfn [(clean [text]
              (-> (or text "")
                  (str/replace #"<mailto:[^>]*>" redacted-email)
                  (str/replace #"<@([UW][A-Z0-9]+)(?:\|[^>]*)?>" #(label (second %)))
                  (str/replace #"<!subteam\^[A-Z0-9]+(?:\|[^>]*)?>" "@ryhmä")
                  (str/replace #"<!(channel|here|everyone)(?:\|[^>]*)?>" "@$1")
                  (str/replace #"<#[A-Z0-9]+\|([^>]*)>" "#$1")
                  (str/replace #"<#[A-Z0-9]+>" "#kanava")
                  (str/replace #"<(https?://[^|>]+)(?:\|[^>]*)?>" "$1")
                  (str/replace #"[A-Za-z0-9._%+\-]+@[A-Za-z0-9\-]+(?:\.[A-Za-z0-9\-]+)+" redacted-email)
                  (str/replace "&lt;" "<")
                  (str/replace "&gt;" ">")
                  (str/replace "&amp;" "&")))]
      (vec (for [m messages]
             {:author (if (:user m) (label (:user m)) "Botti")
              :text   (clean (:text m))})))))
