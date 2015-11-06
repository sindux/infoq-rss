(defproject infoq-rss "0.1.0-SNAPSHOT"
  :description "InfoQ Video RSS"
  :url "https://github.com/sindux/infoq-rss"
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [compojure "1.4.0"]
                 [http-kit "2.1.19"]
                 [org.clojure/core.async "0.2.371"]
                 [enlive "1.1.6"]]
  :plugins [[lein-ring "0.9.7"]]
  :ring {:handler infoq-rss.handler/app}
  :profiles {:uberjar {:main infoq-rss.handler
                       :aot :all}})
