package org.clulab.wm.eidos.apps

import java.io.PrintWriter

import com.typesafe.config.{Config, ConfigFactory}
import org.clulab.odin.{ExtractorEngine, Mention}
import org.clulab.processors.{Document, Processor, Sentence}
import org.clulab.processors.fastnlp.FastNLPProcessor
import org.clulab.struct.Counter
import org.clulab.utils.Configured
import org.clulab.wm.eidos.utils.FileUtils

object FreqAnalysis extends App with Configured {

  val config = ConfigFactory.load("eidos")
  override def getConf: Config = config

  val inputDir = getArgString("apps.inputDirectory", None)
  val outputDir = getArgString("apps.outputDirectory", None)
  val inputExtension = getArgString("apps.inputFileExtension", None)
  val files = FileUtils.findFiles(inputDir, inputExtension)

  val proc = new FastNLPProcessor()

  val myRule =
    """
      |- name: chunky
      |  type: token
      |  label: NP
      |  pattern: |
      |    [chunk='B-NP'][chunk='I-NP']*
    """.stripMargin

  val engine = ExtractorEngine(myRule)

  def getChunks(doc: Document, engine: ExtractorEngine): Seq[Mention] = {
    engine.extractFrom(doc)
  }

  def mkPartialAnnotation(proc: Processor, text: String): Document = {
    val doc = proc.mkDocument(text, true)
    proc.tagPartsOfSpeech(doc)
    proc.chunking(doc)
    doc.clear()
    doc
  }

  // For each file in the input directory:
  val chunksFromFiles = for {
    file <- files.par
      // 1. Open corresponding output file and make all desired exporters
      //println(s"Extracting from ${file.getName}")
      // 2. Get the input file contents
      text = FileUtils.getTextFromFile(file)
      // 3. Parse text
      doc = mkPartialAnnotation(proc, text)
      // 4. Extract NP chunks
      chunks <- getChunks(doc, engine)
    } yield chunks

  val counter = new Counter[String]
  chunksFromFiles.seq.foreach(m => counter.incrementCount(m.text.toLowerCase))
  val pw = new PrintWriter(s"$outputDir/unigram_counter.txt")
  counter.saveTo(pw)
  pw.close()

}


