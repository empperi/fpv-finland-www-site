(ns fpvfinland.sync.http-test
  (:require [clojure.test :refer [deftest is]]
            [fpvfinland.sync.http :as http]))

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
