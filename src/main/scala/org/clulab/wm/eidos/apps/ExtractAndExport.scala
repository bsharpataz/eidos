package org.clulab.wm.eidos.apps

import java.io.PrintWriter

import ai.lum.common.StringUtils._
import com.typesafe.config.{Config, ConfigFactory}
import ai.lum.common.StringUtils._
import org.clulab.utils.Serializer
import org.clulab.odin.{Attachment, EventMention, Mention, State}
import org.clulab.serialization.json.stringify
import org.clulab.utils.Configured
import org.clulab.wm.eidos.attachments._
import org.clulab.wm.eidos.groundings.EidosOntologyGrounder
import org.clulab.wm.eidos.mentions.{EidosEventMention, EidosMention}
import org.clulab.wm.eidos.{AnnotatedDocument, EidosSystem}
import org.clulab.wm.eidos.serialization.json.JLDCorpus
import org.clulab.wm.eidos.utils.FileUtils
import org.clulab.wm.eidos.utils.FileUtils.{findFiles, printWriterFromFile}
import org.clulab.wm.eidos.utils.GroundingUtils.{getBaseGrounding, getGroundingsString}

import scala.collection.mutable.ArrayBuffer


/**
  * App used to extract mentions from files in a directory and produce the desired output format (i.e., jsonld, mitre
  * tsv or serialized mentions).  The input and output directories as well as the desired export formats are specified
  * in eidos.conf (located in src/main/resources).
  */
object ExtractAndExport extends App with Configured {

  def getExporter(exporterString: String, filename: String, topN: Int): Exporter = {
    exporterString match {
      case "jsonld" => JSONLDExporter(printWriterFromFile(filename + ".jsonld"), reader)
      case "mitre" => MitreExporter(printWriterFromFile(filename + ".mitre.tsv"), reader, filename, topN)
      case "serialized" => SerializedExporter(filename)
      case _ => throw new NotImplementedError(s"Export mode $exporterString is not supported.")
    }
  }

  val config = ConfigFactory.load("eidos")
  override def getConf: Config = config

  val inputDir = getArgString("apps.inputDirectory", None)
  val outputDir = getArgString("apps.outputDirectory", None)
  val inputExtension = getArgString("apps.inputFileExtension", None)
  val exportAs = getArgStrings("apps.exportAs", None)
  val topN = getArgInt("apps.groundTopN", Some(5))

  val files = findFiles(inputDir, inputExtension)
  val reader = new EidosSystem()

  // For each file in the input directory:
  files.par.foreach { file =>
    // 1. Open corresponding output file and make all desired exporters
    println(s"Extracting from ${file.getName}")
    val exporters = exportAs.map(getExporter(_, s"$outputDir/${file.getName}", topN))
    // 2. Get the input file contents
    val text = FileUtils.getTextFromFile(file)
    // 3. Extract causal mentions from the text
    val annotatedDocuments = Seq(reader.extractFromText(text, filename = Some(file.getName)))
    // 4. Export to all desired formats
    exporters.foreach(_.export(annotatedDocuments))
  }

}


trait Exporter {
  def export(annotatedDocuments: Seq[AnnotatedDocument]): Unit
}

// Helper classes for facilitating the different export formats
case class JSONLDExporter (pw: PrintWriter, reader: EidosSystem) extends Exporter {
  override def export(annotatedDocuments: Seq[AnnotatedDocument]): Unit = {
    val corpus = new JLDCorpus(annotatedDocuments, reader)
    val mentionsJSONLD = corpus.serialize()
    pw.println(stringify(mentionsJSONLD, pretty = true))
    pw.close()
  }
}

case class MitreExporter (pw: PrintWriter, reader: EidosSystem, filename: String, topN: Int) extends Exporter {
  override def export(annotatedDocuments: Seq[AnnotatedDocument]): Unit = {
    // Header
    pw.println(header())
    annotatedDocuments.foreach(printTableRows(_, pw, filename, reader))
    pw.close()
  }

  def header(): String = {
    "file\tsentence_id\taid\teid\tnews_id\ttitle\tdate\tevent_type\tactor\tactor_number\ttheme\tlocations\tsentence_text\trule_name"
  }


  def printTableRows(annotatedDocument: AnnotatedDocument, pw: PrintWriter, filename: String, reader: EidosSystem): Unit = {
    val allOdinMentions = annotatedDocument.eidosMentions.map(_.odinMention)
    val mentionsToPrint = annotatedDocument.eidosMentions.filter(m => reader.releventEdge(m.odinMention, State(allOdinMentions)))

    for (mention <- mentionsToPrint) {

      val odinMention = mention.odinMention

      val sentence_id = mention.odinMention.sentence
      val aid = ""
      val eid = ""
      val newsid = ""
      val title = ""
      val date = ""

      val actor = headTextOrElse(odinMention.arguments.get("actor"), "")
      val theme = headTextOrElse(odinMention.arguments.get("theme"), "")
      val actorNumber = actorNumberOrElse(odinMention.arguments.get("actor"), "")

      val trigger_txt = triggerTextOrElse(odinMention, "")
      val relation_norm = mention.label // i.e., "Protest" or "Demand"

      val locations = locationOrElse(odinMention, "")

      val foundBy = odinMention.foundBy

      val evidence = mention.odinMention.sentenceObj.getSentenceText.normalizeSpace

      val info = Seq(filename, sentence_id, aid, eid, newsid, title, date, relation_norm, actor, actorNumber, theme, locations, evidence, foundBy)

      val row = info.mkString("\t") + "\n"
      pw.print(row)

    }
  }


  def headTextOrElse(seq: Option[Seq[Mention]], default: String) = {
    if (seq.isEmpty) default
    else if (seq.get.isEmpty) default
    else seq.get.head.text.normalizeSpace
  }

  def actorNumberOrElse(seq: Option[Seq[Mention]], default: String) = {
    if (seq.isEmpty) default
    else if (seq.get.isEmpty) default
    else {
      seq.get.head.attachments.filter(_.isInstanceOf[Quantification]).map(_.asInstanceOf[TriggeredAttachment].trigger).mkString(", ")
    }
  }

  def triggerTextOrElse(m: Mention, default: String): String = {
    m match {
      case em: EventMention => em.trigger.text.normalizeSpace
      case _ => default
    }
  }

  def locationOrElse(m: Mention, default: String): String = {
    val ms = m +: m.arguments.values.flatten.toSeq.distinct
    val locations = ms.flatMap(m => m.attachments.filter(_.isInstanceOf[Location]))
    locations.mkString(", ")
  }
}


case class SerializedExporter (filename: String) extends Exporter {
  override def export(annotatedDocuments: Seq[AnnotatedDocument]): Unit = {
    val odinMentions = annotatedDocuments.flatMap(ad => ad.odinMentions)
    Serializer.save[SerializedMentions](new SerializedMentions(odinMentions), filename + ".serialized")
  }
}

// Helper Class to facilitate serializing the mentions
class SerializedMentions(val mentions: Seq[Mention]) extends Serializable {}
object SerializedMentions {
  def load(filename: String): Seq[Mention] = Serializer.load[SerializedMentions](filename).mentions
}






case class EntityInfo(m: EidosMention, topN: Int = 5) {
  val text = m.odinMention.text
  val norm = getBaseGrounding(m)
  val modifier = ExporterUtils.getModifier(m)
  val polarity = ExporterUtils.getPolarity(m)
  val un = getGroundingsString(m, EidosOntologyGrounder.UN_NAMESPACE, topN)
  val fao = getGroundingsString(m, EidosOntologyGrounder.FAO_NAMESPACE, topN)
  val wdi = getGroundingsString(m, EidosOntologyGrounder.WDI_NAMESPACE, topN)

  def toTSV(): String = Seq(text, norm, modifier, polarity).map(_.normalizeSpace).mkString("\t")

  def groundingToTSV() = Seq(un, fao, wdi).map(_.normalizeSpace).mkString("\t")


}

object ExporterUtils {



  def getModifier(mention: EidosMention): String = {
    def quantHedgeString(a: Attachment): Option[String] = a match {
      case q: Quantification => Some(f"Quant(${q.trigger.toLowerCase})")
      case h: Hedging => Some(f"Hedged(${h.trigger.toLowerCase})")
      case n: Negation => Some(f"Negated(${n.trigger.toLowerCase})")
      case _ => None
    }

    val attachments = mention.odinMention.attachments.map(quantHedgeString).toSeq.filter(_.isDefined)

    val modifierString = attachments.map(a => a.get).mkString(", ")
    modifierString
  }

  //fixme: not working -- always ;
  def getPolarity(mention: EidosMention): String = {
    val sb = new ArrayBuffer[String]
    val attachments = mention.odinMention.attachments
    val incTriggers = attachments.filter(a => a.isInstanceOf[Increase]).map(inc => inc.asInstanceOf[Increase].trigger)
    val decTriggers = attachments.filter(a => a.isInstanceOf[Decrease]).map(inc => inc.asInstanceOf[Decrease].trigger)
    for (t <- incTriggers) sb.append(s"Increase(${t})")
    for (t <- decTriggers) sb.append(s"Decrease(${t})")

    sb.mkString(", ")
  }

  def removeTabAndNewline(s: String) = s.replaceAll("(\\n|\\t)", " ")
}