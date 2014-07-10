package code.rss

import net.liftweb.http.S
import net.liftweb.http.rest.RestHelper
import dispatch._
import Defaults._
import net.liftweb.common.Full
import net.liftweb.http.XmlResponse
import net.liftweb.http.PlainTextResponse
import sun.net.www.content.text.PlainTextInputStream
import scala.xml.XML
import scala.xml.Elem
import scala.xml.Node
import scala.xml.transform.RuleTransformer
import scala.xml.transform.RewriteRule
import net.liftweb.util.HttpHelpers
import net.liftweb.util.Helpers
import net.liftweb.http.NotFoundResponse
import net.liftweb.http.RedirectResponse

object infoq extends RestHelper {
  def httpRequest(uri: String): Future[Elem] = {
    val svc = url(uri)
    Http(svc OK as.xml.Elem)
  }

  def httpRequest2(uri: String): Future[String] = {
    val svc = url(uri) <:< Map("User-Agent" -> "Mozilla/5.0(iPad; U; CPU iPhone OS 3_2 like Mac OS X; en-us) AppleWebKit/531.21.10 (KHTML, like Gecko) Version/4.0.4 Mobile/7B314 Safari/531.21.10")
    Http(svc OK as.String)
  }
    
  class AddEnclosure(hostName: String) extends RewriteRule {
	override def transform(node: Node): Seq[Node] = {
	  node match {
	    case <item>{children @ _*}</item> =>
	        val link = children collect {case <link>{link}</link> => {link}}
	        val url = hostName + Helpers.urlEncode(link.head.text)
	        <item>{children ++ <enclosure url={url} type="video/mp4"/>}</item>
	    case other => other
	  }
	}
  }
  
  serve { 
    case "rss" :: "video" :: Nil Get req =>
      val req = httpRequest("http://www.infoq.com/feed/presentations")
      val response = req()
      val enclosureRule = new AddEnclosure("http://" + S.hostName + ":8080/mp4/")
      val newResponse = new RuleTransformer(enclosureRule).transform(response).head
      newResponse
      
    case "mp4" :: url Get req =>
      val req = httpRequest2(url.head)
      val response = req()
      val vidRegex = """(?s)<video poster="(.*?)".*?<source src="(.*?)"""".r
      val matches = vidRegex findFirstMatchIn response
      val src = for {
        m <- matches
      } yield m.group(2)
      
      src match {
        case Some(url) => RedirectResponse(url)
        case _ => NotFoundResponse("Cannot find video")
      }
  }
}