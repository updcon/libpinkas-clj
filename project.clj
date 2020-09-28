(defproject updcon/libpinkas-clj "0.0.5"
  :description "A Clojure library to support registry operations for microservices"
  :url "https://github.com/updcon/libpinkas-clj"
  :license {:name "MIT"}
  :dependencies [[http-kit "2.5.0"]
                 [cheshire "5.10.0"]
                 [org.clojure/core.async "1.3.610"]]
  :profiles {:uberjar {:aot :all}
             :dev     {:dependencies [[org.clojure/clojure "1.10.1"]]}})
