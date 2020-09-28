(ns libpinkas-clj.core
  (:require [org.httpkit.client :as http]
            [cheshire.core :refer [generate-string parse-string]]
            [clojure.string :as s]
            [clojure.walk :refer [postwalk]]
            [clojure.core.async
             :refer [timeout go-loop <! >!]
             :include-macros true]))

(defonce ^:private reject-repo (atom #{}))
(defonce ^:private deref-timeout 2000)
(defonce ^:private default-interval 10)

(defn- +with [f hsh]
  (f hsh @reject-repo))

(def ^:private remove-> (partial +with remove))
(def ^:private ifsome? (partial +with some))

(defprotocol Service
  (describe [this])
  (discover [this])
  (register [this])
  (deregister [this]))

(defn- with-ctx [path loc]
  (if (s/ends-with? path "/")
    (str path loc)
    (str path "/" loc)))

(defn- keyword->capital [x]
  (let [words (s/split (name x) #"-")
        capitalwords (map #(s/capitalize %) words)]
    (s/join capitalwords)))

(defn- transform [orig]
  (postwalk (fn [k] (if (keyword? k) (keyword->capital k) k))
            orig))

(defn- deregister [orig]
  (-> orig
      (select-keys [:node :address])
      (assoc :serviceid (-> orig :service :id))))

(defn- deref-with-default [ref]
  (deref ref deref-timeout false))

(defn- with-http
  ([]
   (fn [^String x] (http/request {:url x})))
  ([b]
   (fn [^String x] (http/request {:method :put
                                  :url    x
                                  :body   (generate-string b)}))))


(def ^:private transform-http
  (comp with-http transform))

(def ^:private transform-deregister
  (comp transform-http deregister))

(defn- exec [^String path ^String loc m]
  (when-let [resp (-> path
                      (with-ctx loc)
                      m
                      deref-with-default)]
    (let [body (:body resp)]
      (when-not (empty? body)
        (parse-string body true)))))

(defn- with-hash [val inner]
  (hash (get-in val inner)))

(defn service [path info & ops]

  (reify Service
    (describe [_]
      {:info info
       :hash (with-hash info [:service :id])
       :path path
       :ops  ops})

    (discover [_]
      (exec path (str "service/" (-> info :service :service)) (with-http)))

    (register [_]
      (let [hsh (with-hash info [:service :id])
            {:keys [interval] :or {interval default-interval}} ops
            beat (fn [] (exec path "register" (transform-http info)))
            added (beat)]
        (swap! reject-repo (fn [_] (remove-> #(= hsh %))))
        (when added (go-loop []
                      (<! (timeout (* interval 1000)))
                      (when (not (ifsome? #(= hsh %)))
                        (beat)
                        (recur))))
        added))

    (deregister [_]
      (swap! reject-repo #(conj % (with-hash info [:service :id])))
      (exec path "deregister" (transform-deregister info)))))
