(ns libpinkas-clj.core-test
  (:require [clojure.test :refer :all]
            [libpinkas-clj.core :refer :all]
            [clojure.core.async :refer [timeout <!!] :include-macros true]))

(defn- schema [id port]
  {:node    "TESTNODE-DEADB00C"
   :address "127.0.0.1"
   :service {
             :id      id
             :service "redis"
             :address "127.0.0.1"
             :port    port}
   })

(def ^:private path "http://localhost:8500/v1/catalog/")

(def s1 (service path (schema "redis1" 8080) :interval 10))
(def s2 (service path (schema "redis2" 8081)))

(deftest test-0-register-request
  (testing "do-valid-register-request"
    (let [status (register s1)]

      (is (= status true))
      (<!! (timeout 1000))

      (is (= (count (filter
                      #(= "redis1" (get % :ServiceID))
                      (discover s1))) 1)))
    (deregister s1)
    (deregister s2)
    (<!! (timeout 1000))))

(deftest test-1-register-request
  (testing "do-valid-register-and-deregister"

    (register s1)
    (<!! (timeout 1000))
    (is (= (count (filter
                    #(= "redis1" (get % :ServiceID))
                    (discover s1))) 1))
    (deregister s1)

    (is (= (count (filter
                    #(= "redis1" (get % :ServiceID))
                    (discover s1))) 0))

    (register s1)
    (is (= (count (filter
                    #(= "redis1" (get % :ServiceID))
                    (discover s1))) 1))
    (deregister s1)))

(deftest test-2-register-request
  (testing "do-valid-register-request"
    (register s1)
    (register s2)

    (<!! (timeout 1000))

    (is (= (count (filter
                    #(= "redis1" (get % :ServiceID))
                    (discover s2))) 1))

    (is (= (count (filter
                    #(= "redis2" (get % :ServiceID))
                    (discover s2))) 1))

    (is (= (count (filter
                    #(or (= "redis1" (get % :ServiceID)) (= "redis2" (get % :ServiceID)))
                    (discover s2))) 2))

    ;wait for more than the default life time for consul to remove the service
    ;to ensure register beat has happen
    (<!! (timeout 65000))

    (is (= (count (filter
                    #(or (= "redis1" (get % :ServiceID)) (= "redis2" (get % :ServiceID)))
                    (discover s2))) 2))

    (deregister s1)
    (deregister s2)
    (<!! (timeout 1000))))
