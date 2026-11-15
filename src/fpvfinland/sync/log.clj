(ns fpvfinland.sync.log
  "Progress logging to stdout. Rebind *log-fn* to silence or capture lines in tests.")

(def ^:dynamic *log-fn* println)

(defn log [& parts]
  (*log-fn* (apply str parts)))
