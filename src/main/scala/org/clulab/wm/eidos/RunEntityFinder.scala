package org.clulab.wm.eidos

import java.io.{File, FileWriter}

import org.clulab.processors.Processor
import org.clulab.processors.fastnlp.FastNLPProcessor
import org.clulab.serialization.json._

/**
  * Created by ajaynagesh on 3/19/18.
  */
object RunEntityFinder extends App {

  val UNDocsDirPath = "../UNDocs/unrep_new_doc_jsons"
  val UNDocsDir = new File(UNDocsDirPath)
  val reader = new EidosSystem()
  val filesList = UNDocsDir.listFiles() //.take(1000) //.take(20)
  val mentionsFile = new FileWriter(new File("../UNDocs/mentions.txt"))
  val canonicalMentionsFile = new FileWriter(new File("../UNDocs/canonical_mentions.txt"))
  val proc: Processor = new FastNLPProcessor()

  val allCanonicalAndMentionsPar = for (file <- filesList.par) yield { // filesList.zipWithIndex) yield { //

    val doc = JSONSerializer.toDocument(file)
    val text = doc.text.get
    val annotatedDocument = reader.extractFromText(text)
    val canonicalNames = annotatedDocument.eidosMentions.map(em => em.canonicalName)
    val mentionTexts = annotatedDocument.odinMentions.map(om => om.text)

    println(s"Completed parsing ${file.getName} ")

    mentionsFile.write(s"${mentionTexts.mkString("\n")}\n")
    canonicalMentionsFile.write(s"${canonicalNames.mkString("\n")}\n")
    mentionsFile.flush
    canonicalMentionsFile.flush

    (canonicalNames, mentionTexts)
  }
  println(s"Finished parsing all the documents")

//  val (allCanonicalMentions, allMentions) = allCanonicalAndMentionsPar.toList.unzip
//  println(s"Dumping the mentions and the canonical mentions ...")
//
//  for (m <- allCanonicalMentions.flatten){
//    canonicalMentionsFile.write(s"${m}\n")
//  }
  canonicalMentionsFile.close
//
//  for (m <- allMentions.flatten){
//    mentionsFile.write(s"${m}\n")
//  }
  mentionsFile.close
//  println("DONE ...")

}