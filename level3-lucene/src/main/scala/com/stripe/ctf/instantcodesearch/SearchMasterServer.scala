package com.stripe.ctf.instantcodesearch

import com.twitter.util.Future
import org.jboss.netty.handler.codec.http.HttpResponseStatus
import org.jboss.netty.util.CharsetUtil.UTF_8
import com.twitter.util.FuturePool
import java.util.concurrent.Executors
import org.jboss.netty.handler.codec.http.HttpResponse
import org.jboss.netty.handler.codec.http.DefaultHttpResponse
import org.jboss.netty.buffer.ChannelBuffers.copiedBuffer
import org.jboss.netty.handler.codec.http.HttpVersion

class SearchMasterServer(port: Int, id: Int) extends AbstractSearchServer(port, id) {
  val NumNodes = 3

  @volatile var indexed = false
  @volatile var luceneIndex: LuceneIndex = _

  def this(port: Int) { this(port, 0) }

  val clients = (1 to NumNodes)
    .map { id => new SearchServerClient(port + id, id)}
    .toArray

  override def isIndexed() = {
    val responsesF = Future.collect(clients.map {client => client.isIndexed()})
    val successF = responsesF.map {responses => responses.forall { response =>

        (response.getStatus() == HttpResponseStatus.OK
          && response.getContent.toString(UTF_8).contains("true"))
      }
    }
    successF.map {success =>
      if (success) {
        successResponse()
      } else {
        errorResponse(HttpResponseStatus.BAD_GATEWAY, "Nodes are not indexed")
      }
    }.rescue {
      case ex: Exception => Future.value(
        errorResponse(HttpResponseStatus.BAD_GATEWAY, "Nodes are not indexed")
      )
    }
  }

  override def healthcheck() = {
    val responsesF = Future.collect(clients.map {client => client.healthcheck()})
    val successF = responsesF.map {responses => responses.forall { response =>
        response.getStatus() == HttpResponseStatus.OK
      }
    }
    successF.map {success =>
      if (success) {
        successResponse()
      } else {
        errorResponse(HttpResponseStatus.BAD_GATEWAY, "All nodes are not up")
      }
    }.rescue {
      case ex: Exception => Future.value(
        errorResponse(HttpResponseStatus.BAD_GATEWAY, "All nodes are not up")
      )
    }
  }

  override def index(path: String) = {
    System.err.println(
      "[master] Requesting " + NumNodes + " nodes to index path: " + path
    )

    val responses = Future.collect(clients.map {client => client.index(path)})
    responses.map {_ => successResponse()}
  }

  override def query(q: String) = {
    val f = Future.collect(clients.map(_.query(q)))
    f map combineResponses
  }

  def combineResponses(responses: Seq[HttpResponse]): HttpResponse = {
    val combined = responses.flatMap(extractContent).toSet.toList
    querySuccessResponseFromString(combined)
  }

  def querySuccessResponseFromString(results: List[String]): HttpResponse = {
    val response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
    val filtered = results.filter(_.trim().length() > 0)
    val resultString = if(filtered.isEmpty) {
      "[]"
    } else {
      filtered
      .map {r => "\"" + r + "\""}
      .mkString("[", ",\n", "]")
    }

    val content = "{\"success\": true,\n \"results\": " + resultString + "}"
    response.setContent(copiedBuffer(content, UTF_8))
    response
  }

  val UTF8 = java.nio.charset.Charset.forName("UTF-8")

  def extractContent(res: HttpResponse): Seq[String] = {
    res.getContent().toString(UTF8).split("\n")
  }
}
