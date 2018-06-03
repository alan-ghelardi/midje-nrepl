(ns midje-nrepl.middleware.test
  (:require [cider.nrepl.middleware.stacktrace :as stacktrace]
            [clojure.tools.nrepl.misc :refer [response-for]]
            [clojure.tools.nrepl.transport :as transport]
            [midje-nrepl.test-runner :as test-runner]
            [orchard.misc :as misc]))

(defn- send-report [{:keys [transport] :as message} report]
  (transport/send transport (response-for message (misc/transform-value report))))

(defn- test-ns-reply [{:keys [ns] :as message}]
  (let [namespace (symbol ns)
        report    (test-runner/run-tests-in-ns namespace)]
    (send-report message report)))

(defn- test-reply [{:keys [ns test-forms] :as message}]
  (let [namespace (symbol ns)
        report    (test-runner/run-test namespace test-forms)]
    (send-report message report)))

(defn- retest-reply [message]
  (->> (test-runner/re-run-failed-tests)
       (send-report message)))

(defn- test-stacktrace-reply [{:keys [index ns print-fn transport] :as message}]
  (let [namespace (symbol ns)
        exception (test-runner/get-exception-at namespace index)]
    (if exception
      (doseq [cause (stacktrace/analyze-causes exception print-fn)]
        (transport/send transport (response-for message cause)))
      (transport/send transport (response-for message :status :no-stacktrace)))))

(defn handle-test [{:keys [op transport] :as message}]
  (case op
    "midje-test-ns"         (test-ns-reply message)
    "midje-test"            (test-reply message)
    "midje-retest"          (retest-reply message)
    "midje-test-stacktrace" (test-stacktrace-reply message))
  (transport/send transport (response-for message :status :done)))