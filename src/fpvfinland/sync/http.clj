(ns fpvfinland.sync.http
  "Minimal HTTP layer. A 'request function' is (fn [{:keys [method url headers body]}])
   returning {:status int :headers {lowercase-name value} :body string}. Everything else
   in the sync code takes such a function as an argument so tests never touch the network."
  (:require [clojure.string :as str])
  (:import (java.net URI)
           (java.net.http HttpClient HttpRequest HttpRequest$BodyPublishers HttpResponse$BodyHandlers)
           (java.time Duration)))

(defn real-request-fn []
  (let [client (HttpClient/newHttpClient)]
    (fn [{:keys [method url headers body]}]
      (let [builder (doto (HttpRequest/newBuilder (URI/create url))
                      (.timeout (Duration/ofSeconds 90)))]
        (doseq [[k v] headers] (.header builder (str k) (str v)))
        (let [req  (.build (if (= method :post)
                             (.POST builder (HttpRequest$BodyPublishers/ofString (or body "")))
                             (.GET builder)))
              resp (.send client req (HttpResponse$BodyHandlers/ofString))]
          {:status  (.statusCode resp)
           :headers (into {} (for [[k v] (.map (.headers resp))]
                               [(str/lower-case k) (first v)]))
           :body    (.body resp)})))))

(def retryable-status? #{429 500 502 503 504})

(defn retry-after-seconds
  "Seconds to wait before retrying: Retry-After header (capped to 60) or a linear backoff."
  [resp attempt]
  (let [h (get-in resp [:headers "retry-after"])
        n (when h (try (Long/parseLong (str/trim h)) (catch NumberFormatException _ nil)))]
    (min 60 (or n (* 2 attempt)))))

(defn with-retry
  "Wraps a request function: retries 429/5xx and I/O exceptions a few times."
  ([req-fn] (with-retry req-fn {}))
  ([req-fn {:keys [max-attempts sleep-fn]
            :or   {max-attempts 4
                   sleep-fn     (fn [ms] (Thread/sleep (long ms)))}}]
   (fn [req]
     (loop [attempt 1]
       (let [resp (try (req-fn req)
                       (catch java.io.IOException e
                         (if (< attempt max-attempts) ::io-error (throw e))))]
         (if (and (or (= resp ::io-error) (retryable-status? (:status resp)))
                  (< attempt max-attempts))
           (do (sleep-fn (* 1000 (if (= resp ::io-error) (* 2 attempt) (retry-after-seconds resp attempt))))
               (recur (inc attempt)))
           resp))))))
