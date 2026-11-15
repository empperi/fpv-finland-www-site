(ns fpvfinland.sync.events-file-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [fpvfinland.sync.events-file :as ef]))

;; Small fixture in the style of the real file (irregular blank lines, indented and unindented descs).
(def fixture
  (str "[{:date \"2026-06-10\"\n"
       "  :title \"Eka\"\n"
       "  :desc \"\n"
       "  Teksti yksi, 5\\\" kokoluokka\n"
       "  \"}\n"
       "\n"
       " {:date \"2026-06-28\"\n"
       "  :title \"Toka\"\n"
       "  :desc \"\n"
       "Teksti kaksi\n"
       "\n"
       "- lista\n"
       "\"}\n"
       "  \n"
       "\n"
       " {:date \"2026-10-30\"\n"
       "  :title \"Kolmas\"\n"
       "  :desc \"\n"
       "Viimeinen\n"
       "\"}]\n"))

(defn lines [s] (str/split s #"\n" -1))
(defn- tail-of [xs ys] (= (take-last (count xs) ys) xs))

(deftest add-in-the-middle-only-adds-lines
  (let [out (ef/add-event fixture {:date "2026-07-15" :title "Uusi" :desc "Hei"})
        old (lines fixture)
        new (lines out)
        i   (.indexOf new " {:date \"2026-07-15\"")]
    (is (= ["Eka" "Toka" "Uusi" "Kolmas"] (map :title (ef/read-events out))))
    (is (= 6 (- (count new) (count old))))
    (testing "removing the 6 added lines gives back the original file byte for byte"
      (is (= fixture (str/join "
" (concat (take i new) (drop (+ i 6) new))))))
    (testing "added lines exactly follow the house style"
      (is (= [" {:date \"2026-07-15\"" "  :title \"Uusi\"" "  :desc \"" "Hei" "\"}" ""]
             (take 6 (drop i new)))))))

(deftest add-at-end-only-touches-closing-bracket
  (let [out (ef/add-event fixture {:date "2027-01-01" :title "Myöhäinen" :desc "Hei"})
        old (lines fixture) new (lines out)]
    (is (= ["Eka" "Toka" "Kolmas" "Myöhäinen"] (map :title (ef/read-events out))))
    (testing "all original lines but the former last entry line are byte-identical, in order"
      (let [idx (.lastIndexOf old "\"}]")]
        (is (= (take idx old) (take idx new)))
        (is (= "\"}" (nth new idx)))))
    (is (str/ends-with? out "  :desc \"
Hei
\"}]
"))))

(deftest add-before-first-date-goes-after-first-entry
  (let [out (ef/add-event fixture {:date "2020-01-01" :title "Vanha" :desc "x"})]
    (is (= ["Eka" "Vanha" "Toka" "Kolmas"] (map :title (ef/read-events out))))
    (is (str/starts-with? out "[{:date \"2026-06-10\""))))

(deftest add-same-date-goes-after-existing
  (let [out (ef/add-event fixture {:date "2026-06-28" :title "Sama" :desc "x"})]
    (is (= ["Eka" "Toka" "Sama" "Kolmas"] (map :title (ef/read-events out))))))

(deftest escaping
  (let [out  (ef/add-event fixture {:date "2026-07-01" :title "5\" kisa \\ polku" :desc "Koko 5\" ja c:\\x \"lainaus\""})
        e    (second (next (ef/read-events out)))]
    (is (= "5\" kisa \\ polku" (:title e)))
    (is (= "\nKoko 5\" ja c:\\x \"lainaus\"\n" (:desc e)))
    (is (str/includes? out "5\\\" kisa \\\\ polku"))))

(deftest update-changes-one-block-only
  (let [out (ef/update-event fixture "2026-06-28" "Toka" {:desc "Uusi teksti"})
        old (lines fixture) new (lines out)]
    (is (= "\nUusi teksti\n" (:desc (second (ef/read-events out)))))
    (is (= "Toka" (:title (second (ef/read-events out)))))
    (testing "lines before the block and after it are unchanged"
      (is (= (take 6 old) (take 6 new)))
      (is (tail-of (drop 13 old) new))
      (is (= (drop 13 old) (take-last (count (drop 13 old)) new))))
    (is (= (ef/read-events fixture)
           (assoc (ef/read-events out) 1 (get (ef/read-events fixture) 1))))))

(deftest update-first-and-last-entries
  (let [first-out (ef/update-event fixture "2026-06-10" "Eka" {:title "Eka uusi"})
        last-out  (ef/update-event fixture "2026-10-30" "Kolmas" {:date "2026-10-31"})]
    (is (str/starts-with? first-out "[{:date \"2026-06-10\"\n  :title \"Eka uusi\""))
    (is (= "Eka uusi" (:title (first (ef/read-events first-out)))))
    (is (str/includes? (:desc (first (ef/read-events first-out))) "5\" kokoluokka") "desc preserved exactly")
    (is (str/ends-with? last-out "\"}]\n"))
    (is (= "2026-10-31" (:date (last (ef/read-events last-out)))))))

(deftest update-needs-single-match
  (is (thrown? clojure.lang.ExceptionInfo (ef/update-event fixture "2026-06-28" "Ei ole" {:desc "x"})))
  (is (thrown? clojure.lang.ExceptionInfo
               (ef/update-event (ef/add-event fixture {:date "2026-06-28" :title "Toka" :desc "x"})
                                "2026-06-28" "Toka" {:desc "y"}))))

(deftest round-trip-equality
  (let [entries [{:date "2026-07-01" :title "A" :desc "x \"q\" \\ y"}
                 {:date "2026-09-01" :title "B" :desc "rivi\n\nkaksi"}
                 {:date "2027-01-01" :title "C" :desc "ääkköset ok"}]
        out     (reduce ef/add-event fixture entries)]
    (is (= (sort-by :date (concat (ef/read-events fixture) (map #(update % :desc ef/desc-value) entries)))
           (ef/read-events out)))))

(deftest empty-file
  (let [out (ef/add-event "[]\n" {:date "2026-07-01" :title "A" :desc "x"})]
    (is (= 1 (count (ef/read-events out))))))

(deftest layout-mismatch-aborts
  (is (thrown? clojure.lang.ExceptionInfo
               (ef/add-event "[{:date \"2026-01-01\" :title \"A\" :desc \"x\"} {:date \"2026-01-02\" :title \"B\" :desc \"y\"}]\n" {:date "2026-07-01" :title "B" :desc "x"}))))
