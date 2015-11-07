(ns infoq-rss.handler
  (:require [clojure.core.async :refer [promise-chan go <! >! go-loop timeout] :as a]
            [compojure.core :refer [defroutes GET]]
            [net.cgrand.enlive-html :refer [xml-resource html-resource select at
                                            text html append do-> emit*
                                            transformation content] :as h]
            [org.httpkit.client :as http]
            [org.httpkit.server :refer [run-server]])
  (:gen-class))

(def ipad-ua
  {:user-agent "Mozilla/5.0 (iPad; CPU OS 9_0_1 like Mac OS X) AppleWebKit/601.1.46 (KHTML, like Gecko) Version/9.0 Mobile/13A404 Safari/601.1"})

(defn time-evictor [expiry-ms check-interval-ms]
  (fn [mem]
    (go-loop []
      (<! (timeout check-interval-ms))
      (swap! mem #(apply dissoc % (for [[k {:keys [time]}] %
                                        :when (>= (System/currentTimeMillis)
                                                  (+ time expiry-ms))]
                                    k)))
      (recur))))

(defn memoize-async
  "Like memoize but for async function and with evictor."
  [evictor f]
  (let [mem (atom {})]
    (evictor mem)
    (fn [& args]
      (if-let [e (find @mem args)]
        (go (:result (val e)))
        (go (let [ret (<! (apply f args))]
              (swap! mem assoc args {:result ret :time (System/currentTimeMillis)})
              ret))))))

(defn- http-get* [url]
  (let [ch (promise-chan)]
    (http/get url ipad-ua #(go (>! ch %)))
    ch))

(def hour 3600000)
(def http-get (memoize-async (time-evictor (* 4 hour) (* 1 hour)) http-get*))

(def select-1 (comp first select))

(defn- scrape [html-page]
  (let [page (html-resource (java.io.StringReader. html-page))]
    {:vid-url (str "http:" (-> (select-1 page [:video :source]) :attrs :src))
     :duration (select-1 page [:.videolength2 text])
     :pdf (let [pdf (select-1 page [:#pdfForm])
                filename (select-1 pdf [:input])]
            (str "http://www.infoq.com" (-> pdf :attrs :action) "?"
                 (-> filename :attrs :name) "=" (-> filename :attrs :value)))
     :thumbnail (second (re-find #"var slides = new Array\('(.*?)'" html-page))}))

(defn- mod-desc [desc detail]
  (let [snip (h/html-snippet desc)
        imgs (h/select snip [:img])]
    (emit* (concat (h/html [:a {:href (:pdf detail)}
                            "Download PDF (Please login from browser first)"])
                   (at snip [:img] nil)  ;; move <img> tag to the end
                   imgs))))

(defn- enrich [rss-xml links-details]
  (at rss-xml
      [:rss] (h/set-attr "xmlns:media" "http://search.yahoo.com/mrss/"
                         "xmlns:itunes" "http://www.itunes.com/dtds/podcast-1.0.dtd")
      [:channel :item]
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
                                              {:url (:thumbnail detail)}]))
              append-duration (append (html [:itunes:duration
                                             (:duration detail)]))]
          ((do-> append-enclosure upd-desc append-thumbnail append-duration) node)))))

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
          :headers {"Content-Type" "application/rss+xml"}
          :body (emit* final)}))))

(defroutes all-routes
  (GET "/infoq/rss" [] (infoq-rss)))

(defn -main [& args]
  (run-server #'all-routes {:port 8080}))


(comment
  (def rss (-> (http-get "http://www.infoq.com/feed/presentations")
               a/<!! :body java.io.StringReader. xml-resource))
  (def links (->> (select rss [:channel :item :link])
                  (map text)))
  (def page-1 (:body (a/<!! (http-get (first links)))))
  (def page-1-detail (scrape page-1))
  (def links-details (zipmap (take 1 links) [page-1-detail]))
  (apply str (emit* (enrich rss links-details)))

  ;; time-evictor test
  (let [a (atom {})]
    ((time-evictor 2000 1000) a)  ;; expire after 2 secs; check every 1 sec
    (go-loop [i 0]
      (when (< i 10)
        (swap! a assoc i {:res i :time (System/currentTimeMillis)}))
      (println i @a)
      (<! (timeout 1000))
      (when (not-empty @a)
        (recur (inc i)))))

  ;; memoize-async test
  (defn slow-fn* [n] (go (<! (timeout 1000)) n))
  (def slow-fn (memoize-async (time-evictor 3000 1000) slow-fn*))
  (go-loop [i 0]
    (println i (time (<! (slow-fn -1))))
    (<! (timeout 1000))
    (when (< i 10)
      (recur (inc i)))))
