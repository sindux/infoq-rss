package code.rss

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import scala.xml.Elem
import scala.xml.Node
import scala.xml.transform.RewriteRule
import scala.xml.transform.RuleTransformer
import dispatch.Defaults.executor
import dispatch.Future
import dispatch.Http
import dispatch.as
import dispatch.enrichFuture
import dispatch.implyRequestHandlerTuple
import dispatch.url
import net.liftweb.http.S
import net.liftweb.http.rest.RestHelper
import net.liftweb.common.Logger
import scala.xml.Attribute
import net.liftweb.http.rest.RestContinuation
import scala.util.matching.Regex
import com.ning.http.client.Response

object infoq extends RestHelper with Logger {
  serve { 
    case "rss" :: "video" :: Nil Get req =>
      val req = httpRequest(as.xml.Elem)(Map.empty)("http://www.infoq.com/feed/presentations")
      val newRssFut = for {
          response <- req
          itemUrls = getItemUrls(response)
          itemPagesFut = itemUrls map httpRequest(as.String)(ipadUA)
          itemPagesFutAll = Future.sequence(itemPagesFut)
          itemPages <- itemPagesFutAll
          videoUrls = itemPages map parseUrl(videoRegex)
          thumbnailUrls = itemPages map parseUrl(thumbnailRegex) map addPrefix("http://www.infoq.com")
          enclosureRule = new AddEnclosure(
	          (itemUrls zip videoUrls).toMap,
	          (itemUrls zip thumbnailUrls).toMap)
          newRssFut = new RuleTransformer(enclosureRule).transform(response).head
      } yield newRssFut
      
      RestContinuation.async(reply => for (newRss <- newRssFut) yield reply(newRss))
  }

  val ipadUA = Map("User-Agent" -> "Mozilla/5.0(iPad; U; CPU iPhone OS 3_2 like Mac OS X; en-us) AppleWebKit/531.21.10 (KHTML, like Gecko) Version/4.0.4 Mobile/7B314 Safari/531.21.10")
  def httpRequest[T](retType: Response => T)(headers: Map[String, String])(uri: String): Future[T] = {
    val svc = url(uri) <:< headers
    Http(svc OK retType)
  }
    
  class AddEnclosure(videoUrls: Map[String, Option[String]], thumbNailUrls: Map[String, Option[String]]) extends RewriteRule {
	override def transform(node: Node): Seq[Node] = {
	  node match {
	    case <item>{children @ _*}</item> =>
	        val linkNode = children collect {case <link>{link}</link> => {link}}
	        val link = linkNode.head.text
	        val videoUrl = videoUrls.getOrElse(link, None)
	        val thumbnailUrl = thumbNailUrls.getOrElse(link, None)
	        val vidTag = for (url <- videoUrl) yield <enclosure url={url} type="video/mp4"/>
	        val thumbnailTag = for (url <- thumbnailUrl) yield <media:thumbnail url={url}/> // why I need this comment, otherwise scala infers as Option[NodeBuffer]
	        <item>{children ++ vidTag.getOrElse(Nil) ++ thumbnailTag.getOrElse(Nil)}</item>
	    case other => other
	  }
	}
  }
  
  def getItemUrls(rssFeed: Elem): Seq[String] = {
    rssFeed \ "channel" \ "item" \ "link" map (_.text)
  }
 
  val videoRegex = """(?s)<video poster=".*?".*?<source src="(.*?)"""".r
  val thumbnailRegex = """(?s)var slides = new Array\('(.*?)'""".r

  def addPrefix(prefix: String)(str: Option[String]): Option[String] = {
    for (s <- str) yield prefix + s
  }
  
  def parseUrl(regex: Regex)(page: String): Option[String] = {
    val matches = regex findFirstMatchIn page
    for (m <- matches) yield m.group(1)
  }
}