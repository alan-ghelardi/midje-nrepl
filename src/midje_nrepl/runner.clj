(ns midje-nrepl.runner
  (:require [clojure.java.io :as io]
            [clojure.main :as clojure.main]
            [clojure.string :as string]
            [midje-nrepl.project-info :as project-info]
            [midje-nrepl.reporter :as reporter :refer [with-in-memory-reporter]]
            [midje.config :as midje.config])
  (:import [clojure.lang LineNumberingPushbackReader Symbol]
           java.io.StringReader))

(def test-results (atom {}))

(defn get-exception-at [ns index]
  {:pre [(symbol? ns) (or (zero? index) (pos-int? index))]}
  (get-in @test-results [ns index :error]))

(defn- fact-filter
  "Takes two predicates exclude-test? and include-test? and returns a
  new predicate function that applies a logical and between those
  predicates on a supplied fact function."
  [exclude-test? include-test?]
  (let [exclude-test? (or exclude-test? (constantly false))
        include-test? (or include-test? (constantly true))]
    (fn [fact-function]
      (and  (not (exclude-test? fact-function))
            (include-test? fact-function)))))

(defn- source-pushback-reader [source line]
  {:pre [(string? source) (or (nil? line) (pos-int? line))]}
  (let [reader (LineNumberingPushbackReader. (StringReader. source))]
    (.setLineNumber reader (or line 1))
    reader))

(defn- make-pushback-reader [file source line]
  (if source
    (source-pushback-reader source line)
    (LineNumberingPushbackReader. (io/reader file))))

(defn- ensure-ns [namespace]
  (or (find-ns namespace)
      (binding [*ns* (the-ns 'user)]
        (eval `(ns ~namespace))
        (the-ns namespace))))

(defn- check-facts [& {:keys [ns source line exclude-test? include-test?]}]
  {:pre [(symbol? ns)]}
  (let [the-ns (ensure-ns ns)
        file   (project-info/file-for-ns ns)
        reader (make-pushback-reader file source line)]
    (binding [midje.config/*config* (merge midje.config/*config* {:fact-filter (fact-filter exclude-test? include-test?)})]
      (with-in-memory-reporter {:ns the-ns :file file}
        (clojure.main/repl
         :read                         #(read reader false %2)
         :need-prompt (constantly false)
         :prompt (fn [])
         :print  (fn [_])
         :caught #(throw %))))))

(defn- save-test-results!
  "Saves test results in the current session and returns the same report
  map."
  [report-map]
  (reset! test-results (:results report-map))
  report-map)

(defn run-test
  [{:keys [ns source line] :or {line 1}}]
  (save-test-results!
   (check-facts :ns ns :source source :line line)))

(defn run-tests-in-ns
  "Runs all tests in the given namespace."
  [{:keys [ns]}]
  (save-test-results!
   (check-facts :ns ns)))

(defn- merge-test-reports [reports]
  (reduce (fn [a b]
            {:results (merge (:results a) (:results b))
             :summary (merge-with + (:summary a) (:summary b))})
          reporter/no-tests reports))

(defn- test-filter
  "Takes a seq of keywords and returns a predicate function that
  verifies whether a supplied test function contains at least one of
  the keywords in its meta."
  [keywords]
  (fn [test-function]
    (let [metadata (meta test-function)]
      (some #(contains? metadata %)
            keywords))))

(defn- ns-filter
  "Takes a seq of regexes and returns a predicate function that matches
  a supplied namespace symbol against each regex, in a logical or."
  [regexes]
  (fn [^Symbol ns]
    (some #(re-find % (name ns))
          regexes)))

(defn run-all-tests
  "Runs all tests in the project or a subset of them, depending upon the supplied options.

  options is a PersistentMap with the valid keys:
  :ns-inclusions - seq of regexes to match namespaces against in a logical or.
  :ns-exclusions - seq of regexes to match namespaces against in a logical or. When
  both :ns-exclusions and :ns-inclusions are present, the former takes
  precedence over the later.
  :test-paths - a vector of test paths (strings) to restrict the test
  execution. Defaults to all known test paths declared in the
  project."
  [options]
  (let [{:keys [ns-exclusions ns-inclusions test-exclusions test-inclusions test-paths]
         :or   {test-paths (project-info/get-test-paths)}} options
        exclude-test?                                      (when test-exclusions (test-filter test-exclusions))
        include-test?                                      (when test-inclusions (test-filter test-inclusions))
        namespaces                                         (-> (project-info/find-namespaces-in test-paths)
                                                               (cond->>
                                                                   ns-inclusions (filter (ns-filter ns-inclusions))
                                                                   ns-exclusions (remove (ns-filter ns-exclusions))))]
    (->> namespaces
         (map #(check-facts :ns % :exclude-test? exclude-test? :include-test? include-test?))
         merge-test-reports
         save-test-results!)))

(defn- non-passing-tests [[namespace results]]
  (let [non-passing-items (filter #(#{:error :fail} (:type %)) results)]
    (when (seq non-passing-items)
      (->> (map :source non-passing-items)
           (string/join (System/lineSeparator))
           (format "(do %s)")
           (list namespace)))))

(defn re-run-non-passing-tests
  "Runs only the tests which failed last time around."
  [_]
  (->> @test-results
       (keep non-passing-tests)
       (map #(check-facts :ns (first %) :source (second %)))
       merge-test-reports
       save-test-results!))
