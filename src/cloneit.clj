(ns cloneit
  ;(:gen-class)
  (:use
     compojure.core
     hiccup.core
     hiccup.page-helpers
     hiccup.form-helpers
     ring.util.response
     ring.adapter.jetty
     ring.middleware.session)
  (:import (org.joda.time DateTime Duration Period)))

(defmacro this-file [] (str "src/" *file*))
(def data  (ref {"http://www.bestinclass.dk" {:title "Best in Class" :points 1 :date (DateTime.) :poster "LauJensen"}}))
(def users (ref {"lau.jensen@bestinclass.dk" {:username "LauJensen" :password "way2secret"}}))
(def online-users (ref {}))

(def formatter
     (.toPrinter (doto (org.joda.time.format.PeriodFormatterBuilder.)
		   .appendDays    (.appendSuffix " day "    " days ")
		   .appendHours   (.appendSuffix " hour "   " hours ")
		   .appendMinutes (.appendSuffix " minute " " minutes ")
		   .appendSeconds (.appendSuffix " second " " seconds "))))

(defn pprint [stamp]
  (let [retr   (StringBuffer.)
	period (Period. (Duration. stamp (DateTime.)))]
    (.printTo formatter retr period (java.util.Locale. "US"))
    (str retr)))

(defn pick [m & ks] (map #(m %) ks))

(defn invalid-url? [url]
  (or (empty? url)
      (not (try (java.net.URL. url) (catch Exception e nil)))))

(defn invalid-email? [x] false)

(defn with-head [session title & body]
  (html
   [:head
    [:title title]
    (include-css "/styles/reddit.css")]
   [:body
    (if-let [user (@online-users (:id session))]
      [:div#user (:username user) (link-to "/logout/" "(Log out)")]
      [:div#user (link-to "/login/" "(Log in)")])
    body]))

(defn add-link [session [title url]]
  (redirect
   (cond
    (invalid-url? url) "/new/?msg=Invalid URL"
    (empty? title)     "/new/?msg=Invalid Title"
    (@data url)        "/new/?msg=Link already submitted"
    :else
    (dosync
     (alter data assoc url {:title title :date (DateTime.) :points 1
			    :poster (:username (@online-users (:id session)))})
     "/"))))

(defn render-links [keyfn cmp]
  (for [link (sort-by keyfn cmp @data)]
    (let [[url {:keys [title points date poster]}] link]
      [:li
       (link-to url title)
       [:span (format " Posted by %s, %s ago. %d %s "
		      poster (pprint date) points "points")]
       (link-to (str "/up/" url)   "Up")
       (link-to (str "/down/" url) "Down")])))

(defn reddit-new-link [session msg]
  (with-head "Reddit.Clojure - Submit to our authority"
    [:h1 "Reddit.Clojure - Submit a new link"]
    [:h3 "Submit a new link"]
    (when msg [:p {:style "color: red;"} msg])
    (form-to [:post "/new/"]
     [:input {:type "Text" :name "url" :value "http://" :size 48 :title "URL"}]
     [:input {:type "Text" :name "title" :value "" :size 48 :title "Title"}]
     (submit-button "Add link"))
    (link-to "/" "Home")))

(defn add-user [session-id [email user password]]
  (redirect
   (cond
    (invalid-email? email) "/register/?msg=Invalid email"
    :else
    (dosync
     (if (@users email)
       "/register/?msg=Email already registered"
       (do
	 (alter users assoc email {:username user :password password})
	 (alter online-users assoc session-id (@users email))
	 "/"))))))

(defn registration-form [session msg]
  (with-head session "Reddit.Clojure - Registration form"   
    [:h1 "Registration"]
    (when msg [:h4 msg])
    (form-to [:post "/register/"]
	     [:table	      
	      (for [field ["Email" "Username" "Password"]]
		[:tr
		 [:td field]
		 [:td (text-field field)]])]
	     (submit-button "Sign up"))))

(defn login-user [session [email password]]
  (redirect 
   (if-let [user (@users email)]
     (if (= password (:password user))
       (dosync
	(alter online-users assoc (:id session) user)
	"/")
       "/login/?msg=Bad username/password combo")
    "/login/?msg=User does not exist")))

(defn logout-user [{:keys [id]}]
  (dosync
   (alter online-users dissoc id))
  (redirect "/"))

(defn login-form [session msg]
  (with-head session "Reddit.Clojure - Login screen"   
    [:h1 "Login"]
    (when msg [:h4 msg])
    (form-to [:post "/login/"]
	     [:table
	      [:tr [:td "email"]   [:td (text-field "email") ]]
	      [:tr [:td "password"][:td (password-field "psw") ]]]
	     (submit-button "Login"))))

(defn reddit-home [session]
  (with-head session "Reddit.Clojure"
    [:h1 "Reddit.Clojure"]
    [:h3 (format "In exactly %d lines of gorgeous Clojure" 100)]
	     ;		 (->> (this-file) reader line-seq count))]
    (link-to "/" "Refresh")  (link-to "/new/" "Add Link")
    [:h1 "Highest ranking list"]
    [:ol (render-links #(:points (val %))  >)]
    [:h1 "Latest link"]  
    [:ol (render-links #(.getMillis (Duration. (:date (val %)) (DateTime.))) >)]))

(defn rate [url mfn]
  (dosync
   (when (@data url) (alter data update-in [url :points] mfn)))
  (redirect "/"))

(defroutes reddit
  (GET "/"
       {session :session}
       (reddit-home session))
  (GET "/new/*"
       {{:strs [id] :as session} :session {:strs [msg]} :params}
       (if (@online-users id)
         (reddit-new-link session msg)
         (redirect "/register/")))
  (POST "/new/"
        {session :session {:strs [title url]} :params}
        (add-link session [title url]))
  (GET "/up/*"
       {{wild "*"} :params}
       (rate wild inc))
  (GET "/down/*"
       {{wild "*"} :params}
       (rate wild dec))
  (GET "/login/*"
       {session :session {:strs [msg]} :params}
       (login-form session msg))
  (POST "/login/"
        {session :session {:strs [email psw]} :params}
        (login-user session [email psw]))
  (GET "/logout/"
       {session :session}
       (logout-user  session))
  (GET "/register/*"
       {session :session {:strs [msg]} :params}
       (registration-form session msg))
  (POST "/register/"
        {{:strs [id]} :session {:strs [Email Username Password]} :params}
        (add-user id [Email Username Password]))
  (GET "/styles/*"
       {{wild "*"}:params}
       (file-response (str "res/" wild)))
  (ANY "*" [] 404))

(comment (defn -main [& args]
  (run-server {:port 8080} "/*" (->> reddit wrap-session servlet))))

(run-jetty (wrap-session reddit) {:port 8080})
