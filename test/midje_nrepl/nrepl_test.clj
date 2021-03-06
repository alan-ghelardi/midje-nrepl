(ns midje-nrepl.nrepl-test
  (:require [cider.nrepl :as cider]
            [matcher-combinators.midje :refer [match]]
            [midje-nrepl.middleware.fake :as fake]
            [midje-nrepl.misc :as misc]
            [midje-nrepl.nrepl
             :refer
             [defmiddleware middleware-vars-expected-by-wrap-inhibit-tests]]
            [midje.sweet :refer :all]
            [nrepl.middleware :as middleware]
            [nrepl.middleware.interruptible-eval :as eval]
            [nrepl.transport :as transport]
            [refactor-nrepl.middleware :as refactor-nrepl]))

(defmiddleware wrap-greeting
  {:handles {"greeting"
             {:doc "Sends a generic greeting to the user."}
             "personal-greeting"
             {:doc      "Sends a personal greeting to the user."
              :requires {"first-name" "The first name of the user."
                         "last-name"  "The last name of the user."}}}}
  'midje-nrepl.middleware.fake/handle-greeting)

(defmiddleware wrap-simple-delegation
  {:handles {"delegate"
             {:doc "Simply delegates to the higher handler, by assoc'ying a `::delegated` key into the message"}}}
  'midje-nrepl.middleware.fake/handle-simple-delegation)

(def fake-handler #(assoc % ::I-am-fake true))

(facts "about defining middleware functions"

       (fact "the middleware contains a descriptor assoc'ed into its meta"
             (meta #'wrap-greeting)
             => (match {::middleware/descriptor
                        {:handles {"greeting"
                                   {:doc "Sends a generic greeting to the user."}}}}))

       (fact "when the middleware is called with a message that doesn't match its op,
it simply calls the base handler"
             (-> (wrap-greeting fake-handler)
                 (apply [{:op "eval" :code "(+ 1 2)"}]))
             =>                 {:op         "eval"
                                 :code       "(+ 1 2)"
                                 ::I-am-fake true})

       (fact "when the message's op matches the middleware's op,
it replies to the message"
             (-> (wrap-greeting fake-handler)
                 (apply [{:op "greeting"}]))
             => {:op       "greeting"
                 :greeting "Hello!"})

       (fact "the handler function can take two parameters; the message and the base handler"
             (-> (wrap-simple-delegation fake-handler)
                 (apply [{:op "delegate"}]))
             => (match {:op               "delegate"
                        ::I-am-fake       true
                        ::fake/delegated? true}))

       (tabular (fact "returns an error when required parameters are missing"
                      (-> (wrap-greeting identity)
                          (apply [(merge {:op "personal-greeting" :transport ..transport..} ?message)])) => irrelevant
                      (provided
                       (transport/send ..transport.. {:status ?status}) => irrelevant))
                ?message                                      ?status
                {:first-name "John"}                #{:done :error :no-last-name}
                {:last-name "Doe"}               #{:done :error :no-first-name}
                {} #{:done :error :no-first-name :no-last-name})

       (fact "calls the middleware normally when all required parameters are provided"
             (-> (wrap-greeting fake-handler)
                 (apply [{:op "personal-greeting" :first-name "John" :last-name "Doe"}]))
             => (match {:greeting "Hello John Doe!"}))

       (fact "returns a set of vars representing middleware functions in a lower level from `wrap-inhibit-tests`;
`refactor-nrepl.middleware/wrap-refactor` is included in this set because `refactor-nrepl` is at the classpath"
             (middleware-vars-expected-by-wrap-inhibit-tests)
             => #{#'eval/interruptible-eval #'cider/wrap-refresh #'refactor-nrepl/wrap-refactor})

       (fact "when `refactor-nrepl` isn't present at the classpat,
`refactor-nrepl.middleware/wrap-refactor` isn't returned"
             (middleware-vars-expected-by-wrap-inhibit-tests)
             => #{#'eval/interruptible-eval #'cider/wrap-refresh}
             (provided
              (misc/dependency-in-classpath? "refactor-nrepl") => false)))
