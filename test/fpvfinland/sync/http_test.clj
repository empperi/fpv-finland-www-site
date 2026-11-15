(ns fpvfinland.sync.http-test
  (:require [clojure.test :refer [deftest is]]
            [fpvfinland.sync.http :as http]
            [fpvfinland.sync.log :as log]))

(deftest retries-429-with-retry-after
  (let [calls  (atom 0)
        sleeps (atom [])
        f      (http/with-retry (fn [_] (if (< (swap! calls inc) 3)
                                          {:status 429 :headers {"retry-after" "5"}}
                                          {:status 200 :body "ok"}))
                                {:sleep-fn #(swap! sleeps conj %)})]
    (is (= 200 (:status (f {}))))
    (is (= [5000 5000] @sleeps))))

(deftest gives-up-after-max-attempts
  (let [f (http/with-retry (fn [_] {:status 503 :headers {}}) {:max-attempts 2 :sleep-fn (fn [_])})]
    (is (= 503 (:status (f {}))))))

(def gem-opts {:service "Gemini" :max-attempts 3 :max-timeout-attempts 2 :timeout-seconds 300
               :sleep-fn (fn [_])})

(deftest timeout-retried-once-then-clear-error
  (let [calls (atom 0) lines (atom [])]
    (binding [log/*log-fn* #(swap! lines conj %)]
      (let [f (http/with-retry (fn [_] (swap! calls inc) (throw (java.net.http.HttpTimeoutException. "t"))) gem-opts)
            e (try (f {}) nil (catch clojure.lang.ExceptionInfo e e))]
        (is (= 2 @calls))
        (is (= "Gemini request timed out after 300 s (2 attempts)" (ex-message e)))
        (is (:timeout (ex-data e)))
        (is (= ["gemini: timed out after 300 s, retrying (2/2)"] @lines))))))

(deftest timeout-then-success
  (let [calls (atom 0)
        f (http/with-retry (fn [_] (if (= 1 (swap! calls inc))
                                     (throw (java.net.http.HttpTimeoutException. "t"))
                                     {:status 200 :body "ok"}))
                           gem-opts)]
    (binding [log/*log-fn* (fn [_])]
      (is (= 200 (:status (f {})))))))

(deftest gemini-429-retried-up-to-three-attempts
  (let [calls (atom 0) lines (atom [])
        f (http/with-retry (fn [_] (swap! calls inc) {:status 429 :headers {"retry-after" "7"}}) gem-opts)]
    (binding [log/*log-fn* #(swap! lines conj %)]
      (is (= 429 (:status (f {}))))
      (is (= 3 @calls))
      (is (= ["gemini: HTTP 429, retrying in 7 s (2/3)" "gemini: HTTP 429, retrying in 7 s (3/3)"] @lines)))))
