(ns infoq-rss.handler
  (:require [clojure.core.async :refer [promise-chan go <! >!] :as a]
            [compojure.core :refer [defroutes GET]]
            [net.cgrand.enlive-html :refer [xml-resource html-resource select at
                                            text html append do-> emit*
                                            transformation content] :as h]
            [org.httpkit.client :as http])
  (:use org.httpkit.server))

(def ipad-ua
  {:user-agent "Mozilla/5.0 (iPad; CPU OS 9_0_1 like Mac OS X) AppleWebKit/601.1.46 (KHTML, like Gecko) Version/9.0 Mobile/13A404 Safari/601.1"})

(defn- http-get [url]
  (let [ch (promise-chan)]
    (http/get url ipad-ua #(go (>! ch %)))
    ch))

(def select-1 (comp first select))

(defn- scrape [html-page]
  (let [page (html-resource (java.io.StringReader. html-page))]
    {:vid-url (str "http:" (-> (select-1 page [:video :source]) :attrs :src))
     :vid-length (select-1 page [:.videolength2 text])
     :pdf (let [pdf (select-1 page [:#pdfForm])
                filename (select-1 pdf [:input])]
            (str "http://www.infoq.com" (-> pdf :attrs :action) "?"
                 (-> filename :attrs :name) "=" (-> filename :attrs :value)))
     :thumbnail (second (re-find #"var slides = new Array\('(.*?)'" html-page))}))

(defn- mod-desc [desc detail]
  (let [snip (h/html-snippet desc)
        imgs (h/select snip [:img])]
    (emit* (concat (h/html [:a {:href (:pdf detail)} "Download PDF"]
                           [:p (str "Length: " (:vid-length detail))])
                   (at snip [:img] nil)  ;; move <img> tag to the end
                   imgs))))

(defn- enrich [rss-xml links-details]
  (at rss-xml [:channel :item]
      (fn [node]
        (let [link (select-1 node [:link text])
              detail (links-details link)
              desc (-> (select-1 node [:description text])
                       (mod-desc detail))
              append-enclosure (append (html [:enclosure
                                              {:url (:vid-url detail)
                                               :type "video/mp4"}]))
              upd-desc (transformation [:description] (content desc))
              append-thumbnail (append (html [:media:thumbnail
                                              {:url (:thumbnail detail)}]))]
          ((do-> append-enclosure upd-desc #_append-thumbnail) node)))))

(defn- infoq-rss []
  (a/<!!
   (go (let [rss (-> (http-get "http://www.infoq.com/feed/presentations")
                     <! :body java.io.StringReader. xml-resource)
             links (->> (select rss [:channel :item :link])
                        (map text))
             links-scraped (->> (map http-get links)
                                (a/map vector)
                                <!
                                (pmap #(scrape (:body %))))
             links-details (zipmap links links-scraped)
             final (enrich rss links-details)]
         {:status 200
          :headers {"Content-Type" "application/xml"}
          :body (emit* final)}))))

(defroutes all-routes
  (GET "/infoq/rss" [] (infoq-rss)))

(run-server #'all-routes {:port 8080})

(comment
  (def rss (-> (http-get "http://www.infoq.com/feed/presentations")
               a/<!! :body java.io.StringReader. xml-resource))
  (def links (->> (select rss [:channel :item :link])
                  (map text)))
  (def page-1 (:body (a/<!! (http-get (first links)))))
  (def page-1-detail (scrape page-1))
  (def links-details (zipmap (take 1 links) [page-1-detail]))
  (apply str (emit* (enrich rss links-details)))
  )
