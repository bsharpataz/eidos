package org.clulab.wm.eidos.apps

import java.io.{File, PrintWriter}

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
import org.clulab.wm.eidos.utils.Closer.AutoCloser
import org.clulab.wm.eidos.utils.{FileUtils, GroundingUtils}
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
      case "jsonld" => JSONLDExporter(FileUtils.printWriterFromFile(filename + ".jsonld"), reader)
      case "mitre" => MitreExporter(FileUtils.printWriterFromFile(filename + ".mitre.tsv"), reader, filename, topN)
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
  val files = FileUtils.findFiles(inputDir, inputExtension)
  val reader = new EidosSystem()

  // For each file in the input directory:
  files.par.foreach { file =>
    // 1. Open corresponding output file and make all desired exporters
    println(s"Extracting from ${file.getName}")
    // 2. Get the input file contents
    val text = FileUtils.getTextFromFile(file)
    // 3. Extract causal mentions from the text
    val annotatedDocuments = Seq(reader.extractFromText(text, filename = Some(file.getName)))
    // 4. Export to all desired formats
    exportAs.foreach { format =>
      (getExporter(format, s"$outputDir/${file.getName}", topN)).autoClose { exporter =>
        exporter.export(annotatedDocuments)
      }
    }
  }
}

trait Exporter {
  def export(annotatedDocuments: Seq[AnnotatedDocument]): Unit
  def close(): Unit
}

// Helper classes for facilitating the different export formats
case class JSONLDExporter(pw: PrintWriter, reader: EidosSystem) extends Exporter {
  override def export(annotatedDocuments: Seq[AnnotatedDocument]): Unit = {
    val corpus = new JLDCorpus(annotatedDocuments, reader)
    val mentionsJSONLD = corpus.serialize()
    pw.println(stringify(mentionsJSONLD, pretty = true))
  }

  override def close(): Unit = Option(pw).map(_.close())
}

case class MitreExporter(pw: PrintWriter, reader: EidosSystem, filename: String, topN: Int) extends Exporter {
  override def export(annotatedDocuments: Seq[AnnotatedDocument]): Unit = {
    // Header
    pw.println(header())
    annotatedDocuments.foreach(printTableRows(_, pw, filename, reader))
  }

  override def close(): Unit = Option(pw).map(_.close())

  def header(): String = {
    "file\tsentence_id\taid\teid\tnews_id\ttitle\tdate\tevent_type\thedge_neg\tactor\tactor_number\tactor_location\ttheme\ttheme_actor\ttheme grounding\tsentence_text\trule_name"
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

      val actors = odinMention.arguments.get("actor")
      val actor = headTextOrElse(actors, "")
      val actorNumber = actorNumberOrElse(odinMention.arguments.get("actor"), "")
      val actorLocation = locationOrElse(actors, "")

      val themes = odinMention.arguments.get("theme")
      val theme = headTextOrElse(themes, "")
      val themeActor = themeActorOrElse(themes, "")
      val themeGrounded = themeGroundingOrElse(mention.eidosArguments.get("theme"), "")

      val trigger_txt = triggerTextOrElse(odinMention, "")
      val relation_norm = mention.label // i.e., "Protest" or "Demand"

      val hedgedNegatedStatus = hedgedOrNegated(odinMention)

//      val locations = locationOrElse(odinMention, "")

      val foundBy = odinMention.foundBy

      val evidence = mention.odinMention.sentenceObj.getSentenceText.normalizeSpace

      val info = Seq(filename, sentence_id, aid, eid, newsid, title, date, relation_norm, hedgedNegatedStatus, actor, actorNumber, actorLocation, theme, themeActor, themeGrounded, evidence, foundBy)

      val row = info.mkString("\t") + "\n"
      pw.print(row)

    }
  }


  def headTextOrElse(seq: Option[Seq[Mention]], default: String) = {
    if (seq.isEmpty) default
    else if (seq.get.isEmpty) default
    else seq.get.head.text.normalizeSpace
  }

  def actorNumberOrElse(seq: Option[Seq[Mention]], default: String): String = {
    if (seq.isEmpty) default
    else if (seq.get.isEmpty) default
    else {
      seq.get.head.attachments.filter(_.isInstanceOf[Quantification]).map(_.asInstanceOf[TriggeredAttachment].trigger).mkString(", ").normalizeSpace
    }
  }

  def triggerTextOrElse(m: Mention, default: String): String = {
    m match {
      case em: EventMention => em.trigger.text.normalizeSpace
      case _ => default
    }
  }


  // Locations in the Actor
  def locationOrElse(ms: Option[Seq[Mention]], default: String): String = {
    if (ms.isDefined) {
      ms.get.map(m => locationOrElse(m, default)).mkString(", ").normalizeSpace
    } else default
  }
  def locationOrElse(m: Mention, default: String): String = {
    val ms = m +: m.arguments.values.flatten.toSeq.distinct
    val locations = ms.flatMap(m => m.attachments.filter(_.isInstanceOf[Location]))
    if (locations.nonEmpty) locations.mkString(", ") else default
  }

  // Locations and ORGS in the theme
  def themeActorOrElse(themes: Option[Seq[Mention]], default: String): String = {
    if (themes.isDefined) {
      themes.get.map(theme => themeActorOrElse(theme, default)).mkString(", ").normalizeSpace
    } else default
  }
  def themeActorOrElse(theme: Mention, default: String): String = {
    val ts = theme +: theme.arguments.values.flatten.toSeq.distinct
    val locations = ts.flatMap(m => m.attachments.filter(_.isInstanceOf[Location]))
    val organizations = ts.flatMap(m => m.attachments.filter(_.isInstanceOf[Organization]))
    val actors = locations ++ organizations
    if (actors.nonEmpty) actors.mkString(", ") else default
  }

  // Locations and ORGS in the theme
  def themeGroundingOrElse(themes: Option[Seq[EidosMention]], default: String): String = {
    if (themes.isDefined) {
      themes.get.map(theme => themeGroundingOrElse(theme, default)).mkString(", ").normalizeSpace
    } else default
  }
  def themeGroundingOrElse(theme: EidosMention, default: String): String = {
    val ts = theme +: theme.eidosArguments.values.flatten.toSeq.distinct
    val groundings = ts.map(m => GroundingUtils.getBaseGrounding(m))
    if (groundings.nonEmpty) groundings.mkString("; ") else default
  }

  def hedgedOrNegated(m: Mention): String = {
    val h = m.attachments.filter(_.isInstanceOf[Hedging]).map(_.asInstanceOf[TriggeredAttachment].trigger)
    val hedge = if (h.nonEmpty) s"Hedged(${h.mkString(", ")})" else ""
    val n = m.attachments.filter(_.isInstanceOf[Negation]).map(_.asInstanceOf[TriggeredAttachment].trigger)
    val neg = if (n.nonEmpty) s"Negated(${n.mkString(", ")})" else ""

    (hedge + neg).normalizeSpace
  }

}

case class SerializedExporter(filename: String) extends Exporter {
  override def export(annotatedDocuments: Seq[AnnotatedDocument]): Unit = {
    val odinMentions = annotatedDocuments.flatMap(ad => ad.odinMentions)
    Serializer.save[SerializedMentions](new SerializedMentions(odinMentions), filename + ".serialized")
  }

  override def close(): Unit = ()
}

// Helper Class to facilitate serializing the mentions
@SerialVersionUID(1L)
class SerializedMentions(val mentions: Seq[Mention]) extends Serializable {}
object SerializedMentions {
  def load(file: File): Seq[Mention] = Serializer.load[SerializedMentions](file).mentions 
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
    val incTriggers = attachments.collect{ case inc: Increase => inc.trigger}
    val decTriggers = attachments.collect{ case dec: Decrease => dec.trigger}
    for (t <- incTriggers) sb.append(s"Increase(${t})")
    for (t <- decTriggers) sb.append(s"Decrease(${t})")

    sb.mkString(", ")
  }

  def removeTabAndNewline(s: String) = s.replaceAll("(\\n|\\t)", " ")
}
