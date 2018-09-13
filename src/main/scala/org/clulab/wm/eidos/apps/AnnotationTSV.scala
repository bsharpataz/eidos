package org.clulab.wm.eidos.apps

import java.io.PrintWriter
import java.util.{Calendar, Date}

import com.typesafe.config.{Config, ConfigFactory}
import org.clulab.odin.{EventMention, State}
import org.clulab.struct.{Counter, Lexicon}
import org.clulab.utils.Configured
import org.clulab.wm.eidos.mentions.EidosEventMention
import org.clulab.wm.eidos.{AnnotatedDocument, EidosSystem}
import org.clulab.wm.eidos.utils.FileUtils
import org.clulab.wm.eidos.utils.FileUtils.findFiles

object AnnotationTSV extends App with Configured {

  // Methods for Sheet 1
  def header(time: Date): String = {
    s"EIDOS Rule Annotation -- generated ${time}\nSource\tSentence ID\tFactor A Text\tFactor A Normalization\t" +
      "Factor A Modifiers\tFactor A Polarity\tRelation Text\tRelation Normalization\t" +
      "Relation Modifiers\tFactor B Text\tFactor B Normalization\tFactor B Modifiers\t" +
      "Factor B Polarity\tLocation\tTime\tEvidence\tExtraction Correct\tAnnotator\tRule"
  }
  // Rule is index 18 = R
  // Correct is 16 = P

  def mkTableRows(annotatedDocument: AnnotatedDocument, filename: String, reader: EidosSystem): (Seq[String], Counter[String]) = {
    val allMentions = annotatedDocument.odinMentions
    val mentionsToPrint = annotatedDocument.eidosMentions.filter(m => reader.releventEdge(m.odinMention, State(allMentions)))

    val ruleCounter = new Counter[String]

    val rows = for {
      mention <- mentionsToPrint

      source = filename
      sentence_id = mention.odinMention.sentence

      cause <- mention.asInstanceOf[EidosEventMention].eidosArguments("cause")
      factor_a_info = EntityInfo(cause)

      trigger = mention.odinMention.asInstanceOf[EventMention].trigger
      relation_txt = ExporterUtils.removeTabAndNewline(trigger.text)
      relation_norm = mention.label // i.e., "Causal" or "Correlation"
      relation_modifier = ExporterUtils.getModifier(mention) // prob none

      effect <- mention.asInstanceOf[EidosEventMention].eidosArguments("effect")
      factor_b_info = EntityInfo(effect)

      location = "" // I could try here..?
      time = ""
      evidence = ExporterUtils.removeTabAndNewline(mention.odinMention.sentenceObj.getSentenceText.trim)
      foundby = mention.odinMention.foundBy
      _ = ruleCounter.incrementCount(foundby) // a hack

      row = source + "\t" + sentence_id + "\t" +
        factor_a_info.toTSV() + "\t" +
        relation_txt + "\t" + relation_norm + "\t" + relation_modifier + "\t" +
        factor_b_info.toTSV() + "\t" +
        location + "\t" + time + "\t" + evidence + "\t" + " " + "\t" + " " + "\t" + foundby

    } yield row

    (rows, ruleCounter)
  }

  // Methods for Sheet 2
  // Sorted...
  def counterToRows(ruleCounter: Counter[String]): Seq[String] = {
    val total = ruleCounter.getTotal
    val rows = ruleCounter.toSeq
      .sortBy(- _._2)
      .zipWithIndex
      .map(ruleInfo => ruleRow(ruleInfo._1._1, ruleInfo._1._2, total, ruleInfo._2))
    rows :+ s"Grand Total\t$total"
  }

  def ruleRow(rule: String, count: Double, total: Double, i: Int): String = {
    val j = i + 2 // account for the header and 1-indexing of google sheets
    val percAll = count / total
    val numCorrect = "=SUMIF(rule_annotation!$S:$S,A" + j + ",rule_annotation!$Q:$Q)"
    val numIncorrect = s"=COUNTIFS(rule_annotation!S:S,A$j,rule_annotation!Q:Q," + """"<>1")"""
    val percCorr = s"=IF(D$j+E$j>0, D$j/(D$j+E$j), " + """"")"""
    val percCurated = s"=(D$j+E$j)/B$j"
    s"$rule\t$count\t$percAll\t$numCorrect\t$numIncorrect\t$percCorr\t$percCurated"
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
  val processed = for { file <- files.par
    // 1. Get the input file contents
    text = FileUtils.getTextFromFile(file)
    // 2. Extract causal mentions from the text
    annotatedDocument = reader.extractFromText(text)
    // 3. Process into table rows
    (rows, rulesFound) = mkTableRows(annotatedDocument, file.getName, reader)
  } yield (rows, rulesFound)

  val (rows, rulesCounters) = processed.seq.unzip

  val totalRulesFound = rulesCounters match {
    case first +: rest =>
      rest.foreach(first += _)
      first
    case _ => throw new RuntimeException("I expected for there to be some rule counters!")
  }

  val timestamp = Calendar.getInstance.getTime
  val sheet1 = FileUtils.printWriterFromFile(s"$outputDir/rule_annotation.tsv")
  val sheet2 = FileUtils.printWriterFromFile(s"$outputDir/rule_summary.tsv")

  // Sheet 1 -- Extraction Data
  sheet1.println(header(timestamp))
  rows.flatten.foreach(sheet1.println)
  // Sheet 2 -- Summary Statistics
  val sheet2Header = "RULE\tCOUNT of RULE\t% of all\tNum correct\tNum incorrect\t% correct\t% curated"
  sheet2.println(sheet2Header)
  val summaryRows = counterToRows(totalRulesFound)
  summaryRows.foreach(sheet2.println)

  sheet1.close()
  sheet2.close()

}
