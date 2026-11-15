(ns fpvfinland.sync.anonymize-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [fpvfinland.sync.anonymize :as a]))

(def thread
  [{:user "U111" :ts "1" :text "Hei <@U222> ja <@U333|matti>, kisa on la. Mail: <mailto:foo@bar.fi|foo@bar.fi>"}
   {:user "U222" :ts "2" :text "Joo, <@U111> kertoo lisää. Tai foo.bar@example.com"}
   {:user "U111" :ts "3" :text "Linkki <https://example.com/x|tästä> &amp; <#C1|yleinen>" :user_profile {:real_name "Matti"}}
   {:subtype "channel_join" :user "U999" :text "<@U999> has joined"}
   {:bot_id "B1" :text "bot says hi"}])

(deftest pseudonyms
  (let [r (a/anonymize-thread thread)]
    (testing "authors get stable pseudonyms in order of appearance"
      (is (= ["Jäsen 1" "Jäsen 2" "Jäsen 1" "Botti"] (map :author r))))
    (testing "mentions use the same pseudonym as authorship; mention-only users come after authors"
      (is (str/starts-with? (:text (first r)) "Hei Jäsen 2 ja Jäsen 3,"))
      (is (str/includes? (:text (second r)) "Jäsen 1 kertoo")))
    (testing "join messages are dropped"
      (is (= 4 (count r))))))

(deftest identities-removed
  (let [all (pr-str (a/anonymize-thread thread))]
    (doseq [s ["U111" "U222" "U333" "matti" "foo@bar.fi" "foo.bar@example.com" "mailto" "Matti"]]
      (is (not (str/includes? all s)) s))
    (is (str/includes? all "[sähköposti poistettu]"))))

(deftest text-cleanup
  (let [t (:text (nth (a/anonymize-thread thread) 2))]
    (is (= "Linkki https://example.com/x & #yleinen" t))))

(deftest deterministic
  (is (= (a/anonymize-thread thread) (a/anonymize-thread thread))))
