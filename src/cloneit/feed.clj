(ns cloneit.feed
  (:refer-clojure :exclude [resolve])
  (:use clojure.contrib.json
        cloneit.data)
  (:import [java.io BufferedReader InputStreamReader]
           [java.net URL]
           [org.joda.time DateTime]))

(def cred "") ; username:password

(def json (let [con (.openConnection (URL. "http://stream.twitter.com/1/statuses/sample.json"))]
            (.setRequestProperty con "Authorization" (str "Basic "
                                                          (.encode (sun.misc.BASE64Encoder.) (.getBytes cred))))
            (BufferedReader. (InputStreamReader. (.getInputStream con)))))

(def resolve (memoize (fn [url]
  (try
    (let [con (doto (.openConnection (URL. url))
                (.setInstanceFollowRedirects false)
                (.connect))
          loc (.getHeaderField con "Location")]
      (.close (.getInputStream con))
      (if loc loc url))
    (catch Exception _ url)))))

(future (doseq [tweet (repeatedly #(read-json json))
                url (:urls (:entities tweet))]
          (let [url (resolve (:url url))]
            (dosync
              (if (not (@data url))
                (alter data assoc url {:title (:text tweet)
                                       :date (DateTime.)
                                       :points 1
                                       :poster "pepijndevos"})
                (alter data update-in [url :points] inc))))))

