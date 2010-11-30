(defproject cloneit "1.0.0"
  :description      "Cloning Reddit using Clojure"
  :url              "http://www.bestinclass.dk"
  :library-path     "lib/"
  :namespaces       [cloneit]
  :main             cloneit
  :dependencies     [[org.clojure/clojure "1.2.0"]
		     [org.clojure/clojure-contrib "1.2.0"]
		     [compojure "0.5.3"]
                     [joda-time "1.6"]
                     [hiccup "0.3.1"]
                     [ring/ring-core "0.3.5"]
                     [ring/ring-jetty-adapter "0.3.5"]])
