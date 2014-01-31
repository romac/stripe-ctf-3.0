package com.stripe.ctf.instantcodesearch

import com.abahgat.suffixtree._
import collection.JavaConversions._

class Index(repoPath: String) {

  val index = scala.collection.mutable.Map[Int, List[(String, Int)]]()
  val suffixTree = new GeneralizedSuffixTree
  var counter = 1
  val indexMap = scala.collection.mutable.Map[String, Int]()

  def path() = repoPath

  def addFile(file: String, text: String): Unit = {
    text.split("\n").zipWithIndex.foreach { case (line, lineNo) =>
      line.split("[.,\\s+]").withFilter(_.length() > 0).foreach { word =>

        val idx = indexMap.get(word) match {
          case Some(idx) => idx
          case None =>
            val idx = counter
            suffixTree.put(word, idx)
            counter += 1
            idx
        }

        val entry = (file, lineNo + 1)
        val entries = index.getOrElse(idx, Nil)
        index += (idx -> (entry :: entries))
      }
    }
  }

  def apply(word: String): List[(String, Int)] = {
    val indexes = suffixTree.search(word)

    if(indexes != null) {
      indexes.map(x => (x: Int)).toList.flatMap(index(_))
    } else {
      Nil
    }
  }

}

