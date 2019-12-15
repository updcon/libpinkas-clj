(defproject updcon/libpinkas-clj "0.0.4"
  :description "A Clojure library to support registry operations for microservices"
  :url "https://github.com/updcon/libpinkas-clj"
  :license {:name "MIT"}
  :dependencies [[http-kit "2.3.0"]
                 [cheshire "5.9.0"]
                 [org.clojure/core.async "0.6.532"]]
  :profiles {:uberjar {:aot :all}
             :dev     {:dependencies [[org.clojure/clojure "1.10.1"]]}})
