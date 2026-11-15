(ns fpvfinland.sync.slack-test
  (:require [clojure.test :refer [deftest is testing]]
            [jsonista.core :as json]
            [fpvfinland.sync.slack :as s]))

(def cfg {:allowed-user-ids ["UALLOWED"] :trigger-reaction "to-www-site" :done-reaction "white_check_mark"})
(def bot "UBOT")

(defn msg [& reactions] {:ts "1.0" :reactions (vec reactions)})
(defn r [name users & [n]] {:name name :users users :count (or n (count users))})

(deftest qualifies
  (testing "allowlisted user reacted"
    (is (s/qualifies? (msg (r "to-www-site" ["UALLOWED"])) cfg bot)))
  (testing "reaction by someone else is ignored"
    (is (not (s/qualifies? (msg (r "to-www-site" ["UOTHER"])) cfg bot))))
  (testing "mixed users: one allowlisted is enough"
    (is (s/qualifies? (msg (r "to-www-site" ["UOTHER" "UALLOWED"])) cfg bot)))
  (testing "already done by bot"
    (is (not (s/qualifies? (msg (r "to-www-site" ["UALLOWED"]) (r "white_check_mark" ["UBOT"])) cfg bot))))
  (testing "done reaction by a human does not count as processed"
    (is (s/qualifies? (msg (r "to-www-site" ["UALLOWED"]) (r "white_check_mark" ["UOTHER"])) cfg bot)))
  (testing "no trigger reaction"
    (is (not (s/qualifies? (msg (r "eyes" ["UALLOWED"])) cfg bot))))
  (testing "thread replies are not roots"
    (is (not (s/qualifies? (assoc (msg (r "to-www-site" ["UALLOWED"])) :thread_ts "0.5") cfg bot)))
    (is (s/qualifies? (assoc (msg (r "to-www-site" ["UALLOWED"])) :thread_ts "1.0") cfg bot))))

(deftest lookup-needed
  (testing "complete lists never need a lookup"
    (is (not (s/reaction-lookup-needed? (msg (r "to-www-site" ["UOTHER"])) cfg bot))))
  (testing "truncated trigger list without an allowlisted user is ambiguous"
    (is (s/reaction-lookup-needed? (msg (r "to-www-site" ["UOTHER"] 30)) cfg bot)))
  (testing "truncated trigger list that already shows an allowlisted user is decided"
    (is (not (s/reaction-lookup-needed? (msg (r "to-www-site" ["UALLOWED"] 30)) cfg bot))))
  (testing "truncated done list that does not show the bot is ambiguous"
    (is (s/reaction-lookup-needed? (msg (r "to-www-site" ["UALLOWED"]) (r "white_check_mark" ["UX"] 20)) cfg bot)))
  (testing "no trigger, no lookup"
    (is (not (s/reaction-lookup-needed? (msg (r "white_check_mark" ["UX"] 20)) cfg bot)))))

(deftest candidate-messages-uses-lookup
  (let [calls (atom 0)
        m1    (assoc (msg (r "to-www-site" ["UOTHER"] 30)) :ts "1")
        m2    (assoc (msg (r "to-www-site" ["UALLOWED"])) :ts "2")
        m3    (assoc (msg (r "eyes" ["UALLOWED"])) :ts "3")
        res   (s/candidate-messages [m1 m2 m3] cfg bot
                                    (fn [_] (swap! calls inc) [(r "to-www-site" ["UOTHER" "UALLOWED"] 30)]))]
    (is (= ["1" "2"] (map :ts res)))
    (is (= 1 @calls))))

(defn- resp [status obj] {:status status :headers {} :body (json/write-value-as-string obj)})

(deftest call-errors
  (testing "ok false becomes a clear exception"
    (let [e (try (s/call (fn [_] (resp 200 {:ok false :error "missing_scope"})) "t" "conversations.list" {})
                 (catch clojure.lang.ExceptionInfo e e))]
      (is (instance? clojure.lang.ExceptionInfo e))
      (is (re-find #"missing_scope" (ex-message e)))))
  (testing "HTTP error"
    (is (thrown? clojure.lang.ExceptionInfo (s/call (fn [_] (resp 500 {})) "t" "x" {})))))

(deftest pagination
  (let [pages (atom [{:ok true :channels [{:id "C1"}] :response_metadata {:next_cursor "abc"}}
                     {:ok true :channels [{:id "C2"}] :response_metadata {:next_cursor ""}}])
        req   (fn [_] (let [p (first @pages)] (swap! pages rest) (resp 200 p)))]
    (is (= ["C1" "C2"] (map :id (s/list-public-channels req "t"))))))

(deftest add-reaction-already-reacted-is-ok
  (is (nil? (s/add-reaction! (fn [_] (resp 200 {:ok false :error "already_reacted"})) "t" "C" "1" "x"))))
