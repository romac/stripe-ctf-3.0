package com.stripe.ctf.instantcodesearch

import com.twitter.util.{Future, Promise, FuturePool}
import com.twitter.concurrent.Broker
import org.jboss.netty.handler.codec.http.{HttpResponse, HttpResponseStatus}
import org.jboss.netty.handler.codec.http.DefaultHttpResponse
import org.jboss.netty.handler.codec.http.HttpVersion
import org.jboss.netty.buffer.ChannelBuffers.copiedBuffer
import org.jboss.netty.util.CharsetUtil.UTF_8


class SearchServer(port : Int, id : Int) extends AbstractSearchServer(port, id) {

  val NumNodes = 3

  case class Query(q : String, broker : Broker[SearchResult])
  var searcher: Searcher = _
  @volatile var index: Index = _
  @volatile var searcherCreated = false
  @volatile var indexed = false

  override def healthcheck() = {
    Future.value(successResponse())
  }

  override def isIndexed() = {
    if (indexed) {
      Future.value(successResponse())
    }
    else {
      Future.value(errorResponse(HttpResponseStatus.OK, "Not indexed"))
    }
  }
  override def index(path: String) = {
    val indexer = new Indexer(path, NumNodes, id)

    FuturePool.unboundedPool {
      System.err.println("[node #" + id + "] Indexing path: " + path)
      index = indexer.index()
      indexed = true
    }

    Future.value(successResponse())
  }

  override def query(q: String) = {
    System.err.println("[node #" + id + "] Searching for: " + q)
    handleSearch(q)
  }

  private def initSearcher(): Searcher = {
    synchronized {
      if(!searcherCreated) {
        searcher = new Searcher(index)
        searcherCreated = true
      }
      searcher
    }
  }

  def handleSearch(q: String): Future[HttpResponse] = {
    if(!searcherCreated) {
      initSearcher()
    }

    val searches = new Broker[Query]()
    searches.recv foreach { q =>
      FuturePool.unboundedPool {searcher.search(q.q, q.broker)}
    }

    val matches = new Broker[SearchResult]()
    val err = new Broker[Throwable]
    searches ! new Query(q, matches)

    val promise = Promise[HttpResponse]
    var results = List[Match]()

    matches.recv foreach { m =>
      m match {
        case m : Match =>
          results = m :: results
        case Done() =>
          promise.setValue(querySuccessResponse(results))
      }
    }

    promise
  }

  override def querySuccessResponse(results: List[Match]): HttpResponse = {
    val response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
    val resultString = results
      .map { r => r.path + ":" + r.line }
      .mkString("\n")

    response.setContent(copiedBuffer(resultString, UTF_8))
    response
  }
}
