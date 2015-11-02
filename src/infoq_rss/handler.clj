(ns infoq-rss.handler
  (:require [clojure.core.async :refer [promise-chan go <! >!] :as a]
            [clojure.xml :as xml]
            [compojure.core :refer [defroutes GET]]
            [net.cgrand.enlive-html :as h]
            [org.httpkit.client :as http])
  (:use org.httpkit.server))

(def ipad-ua
  {:user-agent "Mozilla/5.0 (iPad; CPU OS 7_0 like Mac OS X) AppleWebKit/537.51.1 (KHTML, like Gecko) Version/7.0 Mobile/11A465 Safari/9537.53"})

(defn http-get [url]
  (let [ch (promise-chan)]
    (http/get url ipad-ua #(go (>! ch %)))
    ch))

(defn infoq-rss []
  (let [rss (a/<!! (http-get "http://www.infoq.com/feed/presentations"))
        rss-xml (-> rss :body java.io.StringReader. h/xml-resource)
        items (->> (h/select rss-xml [:channel :item :link])
                   (map h/text))
        articles-chs (map http-get items)
        articles (a/<!! (a/map vector articles-chs))
        item-details (pmap #(let [html-page (:body %)
                                  page (h/html-resource (java.io.StringReader. html-page))]
                              {:vid-url (str "http:"
                                             (-> (h/select page [:video :source])
                                                 first :attrs :src))
                               :vid-length (-> (h/select page [:.videolength2 h/text])
                                               first)
                               :pdf (let [pdf (-> (h/select page [:#pdfForm]) first)
                                          filename (-> (h/select pdf [:input]) first)]
                                      (str "http://www.infoq.com"
                                           (-> pdf :attrs :action) "?"
                                           (-> filename :attrs :name) "="
                                           (-> filename :attrs :value)))
                               :thumbnail (second (re-find #"var slides = new Array\('(.*?)'" html-page))})
                           articles)
        combined (zipmap items item-details)
        final (h/at rss-xml
                    [:channel :item]
                    (fn [node]
                      (let [link (first (h/select node [:link h/text]))
                            detail (combined link)
                            append-enclosure (h/append (h/html [:enclosure
                                                                {:url (:vid-url detail)
                                                                 :type "video/mp4"}]))
                            append-thumbnail (h/append (h/html [:media:thumbnail
                                                                {:url (:thumbnail detail)}]))
                            description (first (h/select node [:description h/text]))]
                        ((h/do-> append-enclosure #_append-thumbnail)
                         node))))]
    {:status 200
     :headers {"Content-Type" "application/xml"}
     :body (apply str (h/emit* final))}))

(defroutes all-routes
  (GET "/infoq/rss" [] (infoq-rss)))

(run-server #'all-routes {:port 8080})
