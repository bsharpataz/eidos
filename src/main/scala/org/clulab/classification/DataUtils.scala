package org.clulab.classification

import java.io.File

import ai.lum.common.StringUtils._
import ai.lum.common.FileUtils._
import org.clulab.processors.Document
import org.clulab.processors.fastnlp.FastNLPProcessor
import org.clulab.utils.Serializer

object DataUtils {
  val proc = new FastNLPProcessor()
  // Data Loading
  def loadArticleData(labelFile: String, collectionDir: String): Seq[Article] = {
    val source = scala.io.Source.fromFile(labelFile)
    val lines = source.getLines().toArray
    source.close()
    for {
      line <- lines
      fields = line.split("\t").map(_.normalizeSpace)
      exists = fields(2)
      if exists == "" && fields.length > 12
      fp = mkFPLabel(fields(5))
      kws = fields(6)
      issue = fields(7)
      orientation = fields(8)
      orientationId = mkOrientationId(fields(9))
      title = fields(10)
      aid = fields(11)
      eid = fields(12)
      basename = s"${aid}_$eid"
      filename = s"$collectionDir/$basename.txt"
      _ = println(s"Loading article from $filename")
      fileText = getTextFromArticle(filename)
      if fileText.nonEmpty
      doc = proc.annotate(fileText.get, keepText = true)
      _ = println(s"Finished parsing $basename...")
    } yield new Article(basename, doc, fp, issue, orientationId)
  }

  def getTextFromArticle(fn: String): Option[String] = {
    val f = new File(fn)
    if (f.exists) {
      val source = scala.io.Source.fromFile(f)
      val text = source.getLines().toArray.map(_.trim).mkString(" ")
      source.close()
      Some(text)
    } else {
      None
    }
  }

  def mkFPLabel(s: String): Int = s match {
    case "0" => 0
    case "1" => 1
    case "." => 2
  }

  def mkOrientationId(s: String): Int = s match {
    case "0" => 0
    case "1" => 1
    case _ => -1
  }




  // Data Storage Classes
  /**
    *
    * @param id the aid and eid concatenated with _
    * @param document processors Document
    * @param fpLabel  1 = ForeignPolicy (FP), 0 = Domestic (Dom), 2 = None
    * @param issue  subclass, e.g. "Dom,corruption"
    * @param orientationLabel 1 or 0 indicating if anti-foreign or not (only applies to FP, where set to -1)
    */
  case class Article(id: String, document: Document, fpLabel: Int, issue: String, orientationLabel: Int)

  // Helper Class to facilitate serializing the mentions
  @SerialVersionUID(1L)
  class SerializedArticles(val mentions: Seq[Article]) extends Serializable {}
  object SerializedArticles {
    def load(file: File): Seq[Article] = Serializer.load[SerializedArticles](file).mentions
    def load(filename: String): Seq[Article] = Serializer.load[SerializedArticles](filename).mentions
  }

}
