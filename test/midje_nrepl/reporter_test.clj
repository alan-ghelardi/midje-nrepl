(ns midje-nrepl.reporter-test
  (:require [clojure.java.io :as io]
            [matcher-combinators.matchers :as m]
            [matcher-combinators.midje :refer [match]]
            [midje-nrepl.reporter :as reporter]
            [midje.emission.api :refer [silently]]
            [midje.emission.state :as emission.state]
            [midje.sweet :refer :all]
            [midje.util.exceptions :as midje.exceptions])
  (:import java.time.Instant))

(def correct-fact-function (with-meta (constantly true)
                             #:midje {:guid            "d2cd94c3346922886e796da80ab99ab764ba30f9"
                                      :source          '(fact "this is inquestionable" 1 => 1)
                                      :namespace       'octocat.arithmetic-test
                                      :line            10
                                      :description     "this is inquestionable"
                                      :name            "this is inquestionable"
                                      :top-level-fact? true}))

(def wrong-fact-function (with-meta (constantly false)
                           #:midje {:guid            "3b649bc647f4a2e8410c2cf8f90828cc017ae13"
                                    :source          '(fact "this is wrong" 1 => 2)
                                    :namespace       'octocat.arithmetic-test
                                    :line            14
                                    :description     "this is wrong"
                                    :name            "this is wrong"
                                    :top-level-fact? true}))

(def impossible-fact-function (with-meta (constantly false)
                                #:midje {:guid            "1ced1998157d66cc34a347e593ded405d59ecfe2"
                                         :source          '(fact "this is impossible" (/ 10 0) => 0)
                                         :namespace       'octocat.arithmetic-test
                                         :line            20
                                         :description     "this is impossible"
                                         :name            "this is impossible"
                                         :top-level-fact? true}))

(def failing-tabular-fact-function (with-meta (constantly false)
                                     #:midje{:guid            "bc65e9bd20065ba5449ba7f0254e9907c5e9ffe5"
                                             :source          '(fact (+ 5 6) => 12)
                                             :namespace       'octocat.arithmetic-test
                                             :top-level-fact? false}))

(def failure-with-simple-mismatch {:actual               1
                                   :arrow                '=>
                                   :call-form            1
                                   :check-expectation    :expect-match
                                   :description          ["this is wrong"]
                                   :expected-result      2
                                   :expected-result-form 2
                                   :function-under-test  wrong-fact-function
                                   :position             ["arithmetic_test.clj" 15]
                                   :type                 :actual-result-did-not-match-expected-value})

(def arithmetic-exception (ArithmeticException. "Divid by zero"))

(def failure-with-unexpected-exception {:actual               (midje.exceptions/captured-throwable arithmetic-exception)
                                        :arrow                '=>
                                        :call-form            '(/ 10 0)
                                        :check-expectation    :expect-match
                                        :description          ["this is impossible"]
                                        :expected-result      0
                                        :expected-result-form 0
                                        :function-under-test  impossible-fact-function
                                        :position             ["arithmetic_test.clj" 20]
                                        :type                 :actual-result-did-not-match-expected-value})

(def failure-with-checker-notes {:actual               [1 2 3]
                                 :arrow                '=>
                                 :call-form            [1 2 3]
                                 :check-expectation    :expect-match
                                 :description          ["the rightmost isn't contained into the leftmost"]
                                 :expected-result      (contains [1 2 3 4])
                                 :expected-result-form '(contains [1 2 3 4])
                                 :function-under-test  (constantly false)
                                 :notes                '("Best match found: [1 2 3]")
                                 :position             ["colls_test.clj" 12]
                                 :type                 :actual-result-did-not-match-checker})

(def failure-with-prerequisit-error {:failures '({:actual-count         0
                                                  :expected-call        "(an-impure-function {:first-name \"John\", :last-name \"Doe\"})"
                                                  :expected-count       :default
                                                  :expected-result-form "(an-impure-function {:first-name \"John\", :last-name \"Doe\"})"
                                                  :position             ["arithmetic_test.clj" 15]})
                                     :position ["arithmetic_test.clj" 15]
                                     :type     :some-prerequisites-were-called-the-wrong-number-of-times})

(def failure-with-table-bindings {:description          ["some crazy additions" nil]
                                  :arrow                '=>
                                  :call-form            (+ 5 6)
                                  :expected-result-form 12
                                  :check-expectation    :expect-match
                                  :midje/table-bindings {'?x 5 '?y 6 '?result 12}
                                  :type                 :actual-result-did-not-match-expected-value
                                  :expected-result      12
                                  :actual               11
                                  :position             ["octocat.arithmetic-test" 51]})

(def arithmetic-test-file (io/file "/home/john-doe/dev/octocat/test/octocat/arithmetic_test.clj"))

(def expected-file? #(= % arithmetic-test-file))

(binding [reporter/*report* (atom {})]
  (facts "about internal reporter functions"
         (against-background
          (before :contents (silently (require 'octocat.arithmetic-test))))

         (fact "resets the report atom"
               (reporter/reset-report! (the-ns 'octocat.arithmetic-test) arithmetic-test-file)
               @reporter/*report* => (match (m/equals {:testing-ns 'octocat.arithmetic-test
                                                       :file       expected-file?
                                                       :results    {}
                                                       :summary    {:check 0 :error 0 :fact 0 :fail 0 :ns 0 :pass 0 :to-do 0}})))

         (fact "when Midje starts checking a top level fact,
it stores its description in the report atom"
               (reporter/starting-to-check-top-level-fact correct-fact-function)
               @reporter/*report* => (match {:top-level-description ["this is inquestionable"]}))

         (fact "when Midje start checking a fact,
it stores the information about this fact in the report map"
               (reporter/starting-to-check-fact correct-fact-function)
               @reporter/*report* => (match {:current-test
                                             {:context ["this is inquestionable"]
                                              :ns      'octocat.arithmetic-test
                                              :file    expected-file?
                                              :line    10
                                              :source  "(fact \"this is inquestionable\" 1 => 1)"}}))

         (fact "when the test passes,
it stores the corresponding test result in the report atom"
               (reporter/pass)
               @reporter/*report* => (match {:results {'octocat.arithmetic-test
                                                       [{:context ["this is inquestionable"]
                                                         :index   0
                                                         :ns      'octocat.arithmetic-test
                                                         :file    expected-file?
                                                         :line    10
                                                         :source  "(fact \"this is inquestionable\" 1 => 1)"
                                                         :type    :pass}]}}))

         (fact "when Midje finishes a top-level fact,
it dissoc's some keys from the report atom"
               (reporter/finishing-top-level-fact correct-fact-function)
               (keys @reporter/*report*) => (match (m/in-any-order [:results :summary :testing-ns :file])))

         (fact "when the test fails,
it stores the corresponding test result in the report atom"
               (reporter/starting-to-check-top-level-fact wrong-fact-function)
               (reporter/starting-to-check-fact wrong-fact-function)
               (reporter/fail failure-with-simple-mismatch)
               @reporter/*report* => (match {:results {'octocat.arithmetic-test
                                                       [{:context ["this is inquestionable"]
                                                         :index   0
                                                         :ns      'octocat.arithmetic-test
                                                         :file    expected-file?
                                                         :line    10
                                                         :source  "(fact \"this is inquestionable\" 1 => 1)"
                                                         :type    :pass}
                                                        {:context  ["this is wrong"]
                                                         :index    1
                                                         :ns       'octocat.arithmetic-test
                                                         :file     expected-file?
                                                         :line     15
                                                         :source   "(fact \"this is wrong\" 1 => 2)"
                                                         :expected "2\n"
                                                         :actual   "1\n"
                                                         :message  '()
                                                         :type     :fail}]}})
               (reporter/finishing-top-level-fact wrong-fact-function))

         (fact "when the test throws an unexpected exception,
it is interpreted as an error in the test report"
               (reporter/starting-to-check-top-level-fact impossible-fact-function)
               (reporter/starting-to-check-fact impossible-fact-function)
               (reporter/fail failure-with-unexpected-exception)
               @reporter/*report* => (match {:results {'octocat.arithmetic-test
                                                       [{:context ["this is inquestionable"]
                                                         :index   0
                                                         :ns      'octocat.arithmetic-test
                                                         :file    expected-file?
                                                         :line    10
                                                         :source  "(fact \"this is inquestionable\" 1 => 1)"
                                                         :type    :pass}
                                                        {:context  ["this is wrong"]
                                                         :index    1
                                                         :ns       'octocat.arithmetic-test
                                                         :file     expected-file?
                                                         :line     15
                                                         :source   "(fact \"this is wrong\" 1 => 2)"
                                                         :expected "2\n"
                                                         :actual   "1\n"
                                                         :message  '()
                                                         :type     :fail}
                                                        {:context  ["this is impossible"]
                                                         :index    2
                                                         :ns       'octocat.arithmetic-test
                                                         :file     expected-file?
                                                         :line     20
                                                         :source   "(fact \"this is impossible\" (/ 10 0) => 0)"
                                                         :expected "0\n"
                                                         :error    #(= arithmetic-exception %)
                                                         :type     :error}]}})
               (reporter/finishing-top-level-fact impossible-fact-function))

         (fact "future facts are interpreted as work to do in the report"
               (reporter/future-fact ["TODO"] ["arithmetic_test.clj" 25])
               @reporter/*report* => (match {:results {'octocat.arithmetic-test
                                                       [{:context ["this is inquestionable"]
                                                         :index   0
                                                         :ns      'octocat.arithmetic-test
                                                         :file    expected-file?
                                                         :line    10
                                                         :source  "(fact \"this is inquestionable\" 1 => 1)"
                                                         :type    :pass}
                                                        {:context  ["this is wrong"]
                                                         :index    1
                                                         :ns       'octocat.arithmetic-test
                                                         :file     expected-file?
                                                         :line     15
                                                         :source   "(fact \"this is wrong\" 1 => 2)"
                                                         :expected "2\n"
                                                         :actual   "1\n"
                                                         :message  '()
                                                         :type     :fail}
                                                        {:context  ["this is impossible"]
                                                         :index    2
                                                         :ns       'octocat.arithmetic-test
                                                         :file     expected-file?
                                                         :line     20
                                                         :source   "(fact \"this is impossible\" (/ 10 0) => 0)"
                                                         :expected "0\n"
                                                         :error    #(= arithmetic-exception %)
                                                         :type     :error}
                                                        {:context ["TODO"]
                                                         :index   3
                                                         :line    25
                                                         :type    :to-do}]}}))

         (fact "treats failing tabular facts properly"
               (reporter/starting-to-check-top-level-fact failing-tabular-fact-function)
               (reporter/starting-to-check-fact failing-tabular-fact-function)
               (reporter/fail failure-with-table-bindings)
               @reporter/*report* => (match {:results {'octocat.arithmetic-test
                                                       [{:context ["this is inquestionable"]
                                                         :index   0
                                                         :ns      'octocat.arithmetic-test
                                                         :file    expected-file?
                                                         :line    10
                                                         :source  "(fact \"this is inquestionable\" 1 => 1)"
                                                         :type    :pass}
                                                        {:context  ["this is wrong"]
                                                         :index    1
                                                         :ns       'octocat.arithmetic-test
                                                         :file     expected-file?
                                                         :line     15
                                                         :source   "(fact \"this is wrong\" 1 => 2)"
                                                         :expected "2\n"
                                                         :actual   "1\n"
                                                         :message  '()
                                                         :type     :fail}
                                                        {:context  ["this is impossible"]
                                                         :index    2
                                                         :ns       'octocat.arithmetic-test
                                                         :file     expected-file?
                                                         :line     20
                                                         :source   "(fact \"this is impossible\" (/ 10 0) => 0)"
                                                         :expected "0\n"
                                                         :error    #(= arithmetic-exception %)
                                                         :type     :error}
                                                        {:context ["TODO"]
                                                         :index   3
                                                         :line    25
                                                         :type    :to-do}
                                                        {:actual   "11\n"
                                                         :context  ["some crazy additions"
                                                                    "With table substitutions:"
                                                                    "?x 5"
                                                                    "?y 6"
                                                                    "?result 12"]
                                                         :expected "12\n"
                                                         :file     expected-file?
                                                         :line     51
                                                         :index    4
                                                         :message  ()
                                                         :ns       'octocat.arithmetic-test
                                                         :source   "(fact (+ 5 6) => 12)"
                                                         :type     :fail}]}})
               (reporter/finishing-top-level-fact failing-tabular-fact-function))

         (fact "every test result, except when it represents a future fact, stores information about when the test started and finished"
               (->> @reporter/*report*
                    :results
                    vals
                    first
                    (remove #(= (:type %) :to-do))
                    (every? (fn [{:keys [started-at finished-at]}]
                              (and (instance? Instant started-at)
                                   (instance? Instant finished-at)))))
               => true)

         (fact "future facts don't contain the neither the `:started-at` nor the `finished-at` keys"
               (->> @reporter/*report*
                    :results
                    vals
                    first
                    (filter #(= (:type %) :to-do))
                    first
                    keys)
               => (match (m/in-any-order [:index :context :line :type])))

         (fact "summarizes test results, by computing the counters for each category"
               (reporter/summarize-test-results!)
               @reporter/*report* => (match {:summary {:check 5
                                                       :error 1
                                                       :fact  4
                                                       :fail  2
                                                       :ns    1
                                                       :pass  1
                                                       :to-do 1}}))

         (tabular (fact "prettifies expected and/or actual values when they are present in the failure map"
                        (reporter/prettify-expected-and-actual-values (merge {:context ["this is a test"]} ?failure-map))
                        => (merge {:context ["this is a test"]} ?result))
                  ?failure-map             ?result
                  {}                       {}
                  {:expected 1}            {:expected "1\n"}
                  {:actual 2}              {:actual "2\n"}
                  {:expected 1 :actual 3}  {:expected "1\n" :actual "3\n"})

         (tabular (fact "explains the failures according to the failure-map's type"
                        (reporter/explain-failure ?failure-map) => ?result)
                  ?failure-map                    ?result
                  failure-with-simple-mismatch    {:expected 2 :actual 1 :message '()}
                  failure-with-checker-notes      {:expected '(contains [1 2 3 4]) :actual [1 2 3] :message '("        Best match found: [1 2 3]")}
                  failure-with-prerequisit-error  {:message '("These calls were not made the right number of times:" "    (an-impure-function {:first-name \"John\", :last-name \"Doe\"}) [expected at least once, actually never called]")})))

(facts "about the `with-in-memory-reporter` macro"

       (fact "evaluates the forms with the reporter in context and returns the
       resulting report map"
             (reporter/with-in-memory-reporter {:ns *ns* :file (io/file *file*)}
               (fact "I'm pretty sure about that"
                     1 => 1))
             => (match {:results
                        {'midje-nrepl.reporter-test [{:context ["I'm pretty sure about that"]
                                                      :index   0
                                                      :ns      'midje-nrepl.reporter-test
                                                      :file    #(= (str %) *file*)
                                                      :line    number?}]}
                        :summary {:check 1 :error 0 :fail 0 :ns 1 :pass 1 :to-do 0}}))

       (fact "catches any compilation error raised inside the macro's body and returns it as an error in the report map"
             (reporter/with-in-memory-reporter {:ns *ns* :file (io/file *file*)}
               (eval '(boom!)))
             => (match {:results
                        {'midje-nrepl.reporter-test
                         [{:context ["midje-nrepl.reporter-test: namespace couldn't be loaded"]
                           :error   #(instance? RuntimeException %)
                           :index   0
                           :ns      'midje-nrepl.reporter-test
                           :file    #(= (str %) *file*)
                           :line    number?
                           :type    :error}]}
                        :summary {:check 0 :error 1 :fail 0 :ns 1 :pass 0 :to-do 0}})))

(fact "outside the `with-in-memory-reporter` macro, the report remains unchanged"
      reporter/*report* => nil)

(defn attempt-to-check-wrong-fact  [last-attempt?]
  (fact "this is wrong, I know"
        1 => 2)
  (when-not last-attempt?
    (emission.state/output-counters:set:midje-failures! 0)))

(facts "about Selvage support"

       (fact "when Midje counters are manipulated after a fact being
        checked (e.g. like in a retry flow), the reporter resolves possible
        divergences in the results"
             (reporter/with-in-memory-reporter {:ns *ns* :file (io/file *file*)}
               (fact "yes, this is obvious"
                     1 => 1)
               (attempt-to-check-wrong-fact false)
               (attempt-to-check-wrong-fact false)
               (attempt-to-check-wrong-fact true))
             => (match {:results
                        {'midje-nrepl.reporter-test
                         [{:context ["yes, this is obvious"]
                           :ns      'midje-nrepl.reporter-test
                           :type    :pass}
                          {:context  ["this is wrong, I know"]
                           :index    1
                           :ns       'midje-nrepl.reporter-test
                           :type     :fail
                           :expected "2\n"
                           :actual   "1\n"}]}
                        :summary {:check 2 :error 0 :fact 2 :fail 1 :ns 1 :pass 1 :to-do 0}})))
