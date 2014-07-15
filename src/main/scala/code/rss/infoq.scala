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

object infoq extends RestHelper with Logger {
  serve { 
    case "rss" :: "video" :: Nil Get req =>
      val req = httpRequest("http://www.infoq.com/feed/presentations")
      val newRssFut = for {
          response <- req
          itemUrls = getItemUrls(response)
          itemPagesFut = itemUrls map httpRequest2
          itemPagesFutAll = Future.sequence(itemPagesFut)
          itemPages <- itemPagesFutAll
          videoUrls = itemPages map getVideoUrl
          thumbnailUrls = itemPages map getThumbnail
          enclosureRule = new AddEnclosure(
	          (itemUrls zip videoUrls).toMap,
	          (itemUrls zip thumbnailUrls).toMap)
          newRssFut = new RuleTransformer(enclosureRule).transform(response).head
      } yield newRssFut
      
      RestContinuation.async(reply => for (newRss <- newRssFut) yield reply(newRss))
  }

  def httpRequest(uri: String): Future[Elem] = {
    val svc = url(uri)
    Http(svc OK as.xml.Elem)
  }
  
  def httpRequest2(uri: String): Future[String] = {
    val svc = url(uri) <:< Map("User-Agent" -> "Mozilla/5.0(iPad; U; CPU iPhone OS 3_2 like Mac OS X; en-us) AppleWebKit/531.21.10 (KHTML, like Gecko) Version/4.0.4 Mobile/7B314 Safari/531.21.10")
    Http(svc OK as.String)
  }
    
  class AddEnclosure(videoUrls: Map[String, Option[String]], thumbNailUrls: Map[String, Option[String]]) extends RewriteRule {
	override def transform(node: Node): Seq[Node] = {
	  node match {
	    case <item>{children @ _*}</item> =>
	        val linkNode = children collect {case <link>{link}</link> => {link}}
	        val link = linkNode.head.text
	        val videoUrl = videoUrls get link
	        val thumbnailUrl = thumbNailUrls get link
	        val vidTag = videoUrl match {
	          case Some(Some(url)) => <enclosure url={url} type="video/mp4"/>
	          case _ => Nil
	        }
	        val thumbnailTag = thumbnailUrl match {
	          case Some(Some(url)) => <media:thumbnail url={url}/>
	          case _ => Nil
	        }
	        <item>{children ++ vidTag}</item>
	    case other => other
	  }
	}
  }
  
  def getItemUrls(rssFeed: Elem): Seq[String] = {
    rssFeed \ "channel" \ "item" \ "link" map (_.text)
  }
  
  def getVideoUrl(page: String): Option[String] = {
    val vidRegex = """(?s)<video poster="(.*?)".*?<source src="(.*?)"""".r
    val matches = vidRegex findFirstMatchIn page
    matches match {
      case Some(url) => Some(url.group(2))
      case _ => None
    }
  }
  
  def getThumbnail(page: String): Option[String] = {
    val vidRegex = """(?s)var slides = new Array\('(.*?)'""".r
    val matches = vidRegex findFirstMatchIn page
    matches match {
      case Some(url) => Some("http://www.infoq.com" + url.group(1))
      case _ => None
    }
  }
}