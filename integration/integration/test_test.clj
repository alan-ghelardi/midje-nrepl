(ns integration.test-test
  (:require [clojure.java.io :as io]
            [clojure.string :as string]
            [integration.helpers :refer [send-message]]
            [matcher-combinators.matchers :as m]
            [matcher-combinators.midje :refer [match]]
            [midje.sweet :refer :all]))

(defn existing-file? [candidate]
  (-> candidate io/file .exists))

(defn read-line-from [file-path line]
  (-> (io/file file-path)
      slurp
      (string/split (re-pattern (System/lineSeparator)))
      (nth (dec line))))

(facts "about running tests"

       (fact "the REPL is up and running")
       (send-message {:op "eval" :code "(+ 2 1)"})
       => (match (list {:value "3"} {:status ["done"]}))

       (fact "runs all tests in the specified namespace"
             (send-message {:op "midje-test-ns" :ns "octocat.arithmetic-test"})
             => (match (list {:results (complement empty?)
                              :summary {:error 1 :fact 4 :fail 2 :ns 1 :pass 2 :skip 0 :test 5}}
                             {:status ["done"]})))

       (fact "when the ns is missing in the message, the middleware returns an error"
             (first (send-message {:op "midje-test-ns"}))
             => (match {:status (m/in-any-order ["error" "no-ns"])}))

       (fact "the test results contain a valid path to the file at which the tests are declared"
             (->> (send-message {:op "midje-test-ns" :ns "octocat.arithmetic-test"})
                  first
                  :results
                  :octocat.arithmetic-test
                  (map :file))
             => #(every? existing-file? %))

       (fact "by following the file and the line returned by a given test result,
it's possible to jump to the correct position of the test in question"
             (let [{:keys [file line]} (->> (send-message {:op "midje-test-ns" :ns "octocat.arithmetic-test"})
                                            first
                                            :results
                                            :octocat.arithmetic-test
                                            second)]
               (read-line-from file line)
               => #"\(\+ 2 3\) => 6"))

       (fact "re-runs tests that didn't pass in the previous execution"
             (send-message {:op "midje-retest"})
             => (match (list {:results (complement empty?)
                              :summary {:error 1 :fact 3 :fail 2 :ns 1 :pass 1 :skip 0 :test 4}}
                             {:status ["done"]})))

       (fact "runs the specified test"
             (send-message {:op         "midje-test"
                            :ns         "octocat.arithmetic-test"
                            :test-forms "(fact \"this is a crazy arithmetic\"
             (+ 2 3) => 6)"})
             => (match (list {:results
                              {:octocat.arithmetic-test
                               [{:context  ["this is a crazy arithmetic"]
                                 :index    0
                                 :ns       "octocat.arithmetic-test"
                                 :type     "fail"
                                 :expected "6\n"
                                 :actual   "5\n"
                                 :message  []}]}
                              :summary {:error 0 :fact 1 :fail 1 :ns 1 :pass 0 :skip 0 :test 1}}
                             {:status ["done"]})))

       (fact "when the parameters ns and/or test-forms are missing in the message,
the middleware returns an error"
             (first (send-message {:op "midje-test"}))
             => (match {:status (m/in-any-order ["error" "no-ns" "no-test-forms"])}))

       (fact "gets the stacktrace of the given erring test"
             (let [namespace          "octocat.arithmetic-test"
                   {:keys [ns index]} (->> (send-message {:op "midje-test-ns" :ns namespace})
                                           first
                                           (#(get-in % [:results (keyword namespace)]))
                                           (filter #(= "error" (:type %)))
                                           first)]
               (send-message {:op       "midje-test-stacktrace"
                              :ns       ns
                              :index    index
                              :print-fn "clojure.core/println"})
               => (match [{:class      "java.lang.ArithmeticException"
                           :message    "Divide by zero"
                           :stacktrace (complement empty?)}
                          {:status ["done"]}])))

       (fact "returns a `no-stacktrace` status when there is no stacktrace for the specified test"
             (send-message {:op       "midje-test-stacktrace"
                            :ns       "octocat.arithmetic-test"
                            :index    0
                            :print-fn "clojure.core/println"})
             => (match [{:status ["no-stacktrace"]}
                        {:status ["done"]}]))

       (fact "when the parameters ns, index and/or print-fn are missing in the message,
the middleware returns an error"
             (first (send-message {:op "midje-test-stacktrace"}))
             => (match {:status (m/in-any-order ["error" "no-ns" "no-index" "no-print-fn"])})))
