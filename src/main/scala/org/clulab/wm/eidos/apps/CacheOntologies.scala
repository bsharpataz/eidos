package org.clulab.wm.eidos.apps

import java.io.File

import com.typesafe.config.{ConfigFactory, ConfigValueFactory}
import org.clulab.wm.eidos.EidosSystem
import org.clulab.wm.eidos.groundings.EidosOntologyGrounder.{FAO_NAMESPACE, UN_NAMESPACE, WDI_NAMESPACE}
import org.clulab.wm.eidos.groundings._

object CacheOntologies extends App {

  val config = ConfigFactory.load("eidos")
      .withValue("EidosSystem.useW2V", ConfigValueFactory.fromAnyRef(false, "Don't use vectors when caching ontologies."))
  val reader = new EidosSystem(config)
  val loadableAttributes = reader.LoadableAttributes

  val ontologies: Seq[String] = loadableAttributes.ontologies
  if (ontologies.isEmpty)
    throw new RuntimeException("No ontologies were specified, please check the config file.")

  val cacheDir: String = loadableAttributes.cacheDir
  val proc = reader.proc

  val domainOntologies = ontologies.map {
    _ match {
      case name @ UN_NAMESPACE  =>  UNOntology(name, loadableAttributes.unOntologyPath,  cacheDir, proc, loadSerialized = false)
      case name @ WDI_NAMESPACE => WDIOntology(name, loadableAttributes.wdiOntologyPath, cacheDir, proc, loadSerialized = false)
      case name @ FAO_NAMESPACE => FAOOntology(name, loadableAttributes.faoOntologyPath, cacheDir, proc, loadSerialized = false)
      case name @ _ => throw new IllegalArgumentException("Ontology " + name + " is not recognized.")
    }
  }

  println(s"Saving ontologies to $cacheDir...")
  new File(cacheDir).mkdirs()
  domainOntologies.foreach(ont => ont.save(DomainOntology.serializedPath(ont.name, cacheDir)))
  println(s"Finished serializing ${domainOntologies.length} ontologies.")

  val filenameIn = loadableAttributes.wordToVecPath
  val filenameOut = EidosWordToVec.makeCachedFilename(cacheDir, loadableAttributes.wordToVecPath)
  println(s"Saving vectors to $filenameOut...")
  val word2Vec = CompactWord2Vec(filenameIn, resource = true, cached = false)
  word2Vec.save(filenameOut)
  println(s"Finished serializing vectors.")
}
