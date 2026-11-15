(ns fpvfinland.sync.http
  "Minimal HTTP layer. A 'request function' is (fn [{:keys [method url headers body]}])
   returning {:status int :headers {lowercase-name value} :body string}. Everything else
   in the sync code takes such a function as an argument so tests never touch the network."
  (:require [clojure.string :as str]
            [fpvfinland.sync.log :as log])
  (:import (java.net URI)
           (java.net.http HttpClient HttpRequest HttpRequest$BodyPublishers HttpResponse$BodyHandlers)
           (java.time Duration)))

(defn real-request-fn
  "opts: {:timeout-seconds n} per-request timeout (default 90)."
  ([] (real-request-fn {}))
  ([{:keys [timeout-seconds] :or {timeout-seconds 90}}]
   (let [client (HttpClient/newHttpClient)]
     (fn [{:keys [method url headers body]}]
       (let [builder (doto (HttpRequest/newBuilder (URI/create url))
                       (.timeout (Duration/ofSeconds timeout-seconds)))]
         (doseq [[k v] headers] (.header builder (str k) (str v)))
         (let [req  (.build (if (= method :post)
                              (.POST builder (HttpRequest$BodyPublishers/ofString (or body "")))
                              (.GET builder)))
               resp (.send client req (HttpResponse$BodyHandlers/ofString))]
           {:status  (.statusCode resp)
            :headers (into {} (for [[k v] (.map (.headers resp))]
                                [(str/lower-case k) (first v)]))
            :body    (.body resp)}))))))

(def retryable-status? #{429 500 502 503 504})

(defn retry-after-seconds
  "Seconds to wait before retrying: Retry-After header (capped to 60) or a linear backoff."
  [resp attempt]
  (let [h (get-in resp [:headers "retry-after"])
        n (when h (try (Long/parseLong (str/trim h)) (catch NumberFormatException _ nil)))]
    (min 60 (or n (* 2 attempt)))))

(defn with-retry
  "Wraps a request function: retries 429/5xx and I/O exceptions up to :max-attempts (default 4).
   Timeouts (HttpTimeoutException) are retried only up to :max-timeout-attempts (default = max-attempts)
   and then fail with a clear error. :service (\"Slack\"/\"Gemini\") and :timeout-seconds are used in
   log lines and messages; :sleep-fn is injectable for tests."
  ([req-fn] (with-retry req-fn {}))
  ([req-fn {:keys [max-attempts max-timeout-attempts timeout-seconds service sleep-fn]
            :or   {max-attempts 4
                   service      "HTTP"
                   sleep-fn     (fn [ms] (Thread/sleep (long ms)))}}]
   (let [max-to (or max-timeout-attempts max-attempts)
         prefix (str (str/lower-case service) ": ")]
     (fn [req]
       (loop [attempt 1]
         (let [resp (try (req-fn req)
                         (catch java.net.http.HttpTimeoutException e
                           (if (< attempt max-to)
                             ::timeout
                             (throw (ex-info (str service " request timed out after " timeout-seconds
                                                  " s (" attempt " attempts)")
                                             {:timeout true} e))))
                         (catch java.io.IOException e
                           (if (< attempt max-attempts) ::io-error (throw e))))]
           (cond
             (= resp ::timeout)
             (do (log/log prefix "timed out after " timeout-seconds " s, retrying (" (inc attempt) "/" max-to ")")
                 (recur (inc attempt)))

             (and (or (= resp ::io-error) (retryable-status? (:status resp)))
                  (< attempt max-attempts))
             (let [secs (if (= resp ::io-error) (* 2 attempt) (retry-after-seconds resp attempt))]
               (log/log prefix (if (= resp ::io-error) "I/O error" (str "HTTP " (:status resp)))
                        ", retrying in " secs " s (" (inc attempt) "/" max-attempts ")")
               (sleep-fn (* 1000 secs))
               (recur (inc attempt)))

             :else resp)))))))
