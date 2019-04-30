(ns libpinkas-clj.core
  (:require [org.httpkit.client :as http]
            [cheshire.core :as json]
            [clojure.string :as s]
            [clojure.core.async
             :refer [timeout go-loop <! >!]
             :include-macros true]))

(defonce ^:private reject-repo (atom #{}))
(defonce ^:private deref-timeout 2000)

(defn- +with [f hsh]
  (f hsh @reject-repo))

(def ^:private remove-> (partial +with remove))
(def ^:private ifsome? (partial +with some))

(defprotocol Service
  (discover [this])
  (register [this])
  (deregister [this]))

(defn- with-ctx [path loc]
  (if (clojure.string/ends-with? path "/")
    (str path loc)
    (str path "/" loc)))

(defn- keyword->capital [x]
  (let [words (s/split (name x) #"-")
        capitalwords (map #(s/capitalize %) words)]
    (s/join capitalwords)))

(defn- transform [orig]
  (clojure.walk/postwalk (fn [k] (if (keyword? k) (keyword->capital k) k))
                         orig))

(defn- deref-with-default [ref]
  (deref ref deref-timeout false))

(defn- with-http
  ([]
   (fn [x] (http/request {:url x})))
  ([b]
   (fn [x] (http/request {:method :put
                          :url    x
                          :body   (json/generate-string b)}))))

(defn- exec [path loc m]
  (when-let [resp (-> path
                      (with-ctx loc)
                      m
                      deref-with-default)]
    (let [body (:body resp)]
      (when-not (empty? body)
        (json/parse-string body true)))))

(defn- with-hash [val inner]
  (hash (get-in val inner)))

(defn service [path info & ops]

  (reify Service
    (discover [this]
      (exec path (str "service/" (-> info :service :service)) (with-http)))

    (register [this]
      (let [hsh (with-hash info [:service :id])
            {:keys [interval] :or {interval 10}} ops
            beat (fn [] (exec path "register" (with-http (transform info))))
            added (beat)]
        (swap! reject-repo (fn [_] (remove-> #(= hsh %))))
        (when added (go-loop []
                      (<! (timeout (* interval 1000)))
                      (when (not (ifsome? #(= hsh %)))
                        (beat)
                        (recur))))
        added))

    (deregister [this]
      (swap! reject-repo #(conj % (with-hash info [:service-id])))
      (exec path "deregister" (with-http (transform info))))))
