package com.stripe.ctf.instantcodesearch

// import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.document.{Document, Field, StringField, TextField, IntField}
import org.apache.lucene.index.{DirectoryReader, IndexReader, IndexWriter, IndexWriterConfig}
import org.apache.lucene.queryparser.classic.{ParseException, QueryParser}
import org.apache.lucene.search.{IndexSearcher, Query, ScoreDoc, TopScoreDocCollector}
import org.apache.lucene.store.{Directory, RAMDirectory}
import org.apache.lucene.util.Version;

import collection.JavaConversions._

class LuceneIndex {

  val analyzer = new CaseSensitiveAnalyzer(Version.LUCENE_46)
  val index = new RAMDirectory()
  val config = new IndexWriterConfig(Version.LUCENE_46, analyzer)
  val writer = new IndexWriter(index, config)
  val queryParser = new QueryParser(Version.LUCENE_46, "text", analyzer)
  var closed = false

  queryParser.setLowercaseExpandedTerms(false)
  queryParser.setAllowLeadingWildcard(true)

  def addFile(file: String, text: String): Unit = {
    text.split("\n").zipWithIndex.foreach { case (line, lineNo) =>
      // println(s"Adding $file:${lineNo + 1}...")
      val doc = new Document
      doc add new IntField("line", lineNo + 1, Field.Store.YES)
      doc add new StringField("file", file, Field.Store.YES)
      doc add new TextField("text", line, Field.Store.NO)
      writer addDocument doc
    }
  }

  def query(word: String): Seq[Match] = {
    if(!closed) {
      writer.close()
      closed = true
    }
    val q = queryParser parse s"*$word*"
    val hitsPerPage = 100
    val reader = DirectoryReader open index
    val searcher = new IndexSearcher(reader)
    val collector = TopScoreDocCollector.create(hitsPerPage, true)
    searcher.search(q, collector)

    val hits = collector.topDocs().scoreDocs

    hits.map { hit =>
      val docId = hit.doc
      val doc = searcher.doc(docId)
      val (file, line) = (doc.get("file"), doc.get("line").toInt)
      // println(s"Found hit for $word in $file:$line")
      Match(file, line)
    }
  }

  def apply(word: String): Seq[Match] = {
    query(word)
  }

}

