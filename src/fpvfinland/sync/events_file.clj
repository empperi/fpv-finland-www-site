(ns fpvfinland.sync.events-file
  "Pure text -> text operations on events.edn that preserve its hand formatting.

   Insertion order: a new entry is inserted right before the first existing entry (other than the
   very first one) with a later date, i.e. the file is assumed to be roughly date-sorted. (A date
   earlier than every entry therefore lands after the first entry, so line 1 is never touched.)
   If no such entry exists it is appended before the closing `]`. Inserting in the middle only adds lines; appending additionally removes the `]` from the
   previously last line. Updates replace only the text block of the matched entry.
   Every write is verified by re-reading the result with clojure.edn."
  (:require [clojure.edn :as edn]
            [clojure.string :as str]))

(defn read-events [text] (edn/read-string text))

(defn escape-edn-string [s]
  (-> s (str/replace "\\" "\\\\") (str/replace "\"" "\\\"")))

(defn desc-value
  "The :desc string stored for new markdown text (leading and trailing newline, like the hand-written entries)."
  [markdown]
  (str "\n" markdown "\n"))

(defn entry-text
  "Text of one entry. :desc here is the exact string value to store."
  [{:keys [date title desc]} first? last?]
  (str (if first? "[{" " {") ":date \"" (escape-edn-string date) "\"\n"
       "  :title \"" (escape-edn-string title) "\"\n"
       "  :desc \"" (escape-edn-string desc) "\"}" (when last? "]")))

(defn- split-lines [text] (vec (str/split text #"\n" -1)))

(defn- blocks
  "Entry blocks as {:start i :end j} (inclusive line indexes; end is the last non-blank line)."
  [lines]
  (let [starts (vec (keep-indexed (fn [i l] (when (re-find #"^(\[| )\{:date \"" l) i)) lines))]
    (vec (for [[k s] (map-indexed vector starts)]
           (let [limit (get starts (inc k) (count lines))
                 e     (last (filter #(not (str/blank? (lines %))) (range s limit)))]
             {:start s :end e})))))

(defn- parse [text]
  (let [data  (read-events text)
        lines (split-lines text)
        blks  (blocks lines)]
    (when-not (= (count data) (count blks))
      (throw (ex-info "events.edn layout not recognised (entry count mismatch)"
                      {:data (count data) :blocks (count blks)})))
    {:data data :lines lines :blocks blks}))

(defn find-matches
  "Indexes of events matching exactly the given date and title."
  [events date title]
  (vec (keep-indexed (fn [i e] (when (and (= date (:date e)) (= title (str/trim (:title e)))) i))
                     events)))

(defn- verify! [text expected]
  (let [actual (try (read-events text)
                    (catch Exception e
                      (throw (ex-info "events.edn round-trip: result unreadable" {} e))))]
    (when-not (= expected actual)
      (throw (ex-info "events.edn round-trip mismatch; aborting"
                      {:expected-count (count expected) :actual-count (count actual)})))
    text))

(defn add-event
  "entry: {:date :title :desc <markdown>}. Returns new file text."
  [text {:keys [date title desc]}]
  (let [new-data {:date date :title title :desc (desc-value desc)}]
    (if (str/blank? (str/replace text #"[\[\]\s]" ""))
      (verify! (str (entry-text new-data true true) "\n") [new-data])
      (let [{:keys [data lines blocks]} (parse text)
            idx (first (keep-indexed (fn [i e] (when (and (pos? i) (pos? (compare (:date e) date))) i))
                                     data))]
        (if idx
          (let [s      (:start (blocks idx))
                new-ls (split-lines (str (entry-text new-data false false) "\n"))
                out    (concat (subvec lines 0 s) new-ls (subvec lines s))]
            (verify! (str/join "\n" out)
                     (vec (concat (subvec data 0 idx) [new-data] (subvec data idx)))))
          (let [end       (:end (peek blocks))
                last-line (lines end)]
            (when-not (str/ends-with? last-line "\"}]")
              (throw (ex-info "events.edn: last entry does not end with \"}]" {})))
            (let [out (concat (subvec lines 0 end)
                              [(subs last-line 0 (dec (count last-line)))]
                              [""]
                              (split-lines (entry-text new-data false true))
                              (subvec lines (inc end)))]
              (verify! (str/join "\n" out) (conj data new-data)))))))))

(defn update-event
  "Replaces the entry at exactly (match-date, match-title) with changes merged in
   (changes: any of :date :title :desc(markdown)). Returns new file text."
  [text match-date match-title changes]
  (let [{:keys [data lines blocks]} (parse text)
        matches (find-matches data match-date match-title)]
    (when-not (= 1 (count matches))
      (throw (ex-info (str "update target must match exactly once, got " (count matches)) {})))
    (let [i      (first matches)
          new    (cond-> (merge (data i) (select-keys changes [:date :title]))
                   (:desc changes) (assoc :desc (desc-value (:desc changes))))
          {:keys [start end]} (blocks i)
          new-ls (split-lines (entry-text new (zero? i) (= i (dec (count data)))))
          out    (concat (subvec lines 0 start) new-ls (subvec lines (inc end)))]
      (verify! (str/join "\n" out) (assoc data i new)))))
