package org.clulab.wm.eidos.groundings

import org.clulab.wm.eidos.attachments.{EidosAttachment, Property}
import org.clulab.wm.eidos.mentions.EidosMention
import org.clulab.wm.eidos.utils.Namer
import org.slf4j.LoggerFactory

object Aliases {
  type SingleGrounding = (Namer, Float)
  type MultipleGrounding = Seq[SingleGrounding]
  type Groundings = Map[String, OntologyGrounding]
}

case class OntologyGrounding(grounding: Aliases.MultipleGrounding = Seq.empty) {
  def nonEmpty: Boolean = grounding.nonEmpty

  def take(n: Int): Aliases.MultipleGrounding = grounding.take(n)
  def head: Aliases.SingleGrounding = grounding.head
}

trait OntologyGrounder {
  def groundOntology(mention: EidosMention): OntologyGrounding
}

trait MultiOntologyGrounder {
  def groundOntology(mention: EidosMention): Aliases.Groundings
}

class EidosOntologyGrounder(val name: String, domainOntology: DomainOntology, wordToVec: EidosWordToVec) extends OntologyGrounder {

  val conceptEmbeddings: Seq[ConceptEmbedding] =
    0.until(domainOntology.size).map { n =>
      new ConceptEmbedding(domainOntology.getNamer(n),
          wordToVec.makeCompositeVector(domainOntology.getValues(n)))
    }

  def groundOntology(mention: EidosMention): OntologyGrounding = {
    if (mention.odinMention.matches("Entity")) { // TODO: Store this string somewhere
      val canonicalName = mention.canonicalName
      val canonicalNameParts = canonicalName.split(" +")

      OntologyGrounding(wordToVec.calculateSimilarities(canonicalNameParts, conceptEmbeddings))
    }
    else
      OntologyGrounding()
  }
}

class PropertiesOntologyGrounder(name: String, domainOntology: DomainOntology, wordToVec: EidosWordToVec) extends EidosOntologyGrounder(name, domainOntology, wordToVec) {

  override def groundOntology(mention: EidosMention): OntologyGrounding = {
    if (mention.odinMention.matches("Entity")) { // TODO: Store this string somewhere
      val propertyAttachments = mention.odinMention.attachments.filter(a => a.isInstanceOf[Property])
      // These need to be sorted after retrieval from a set.  Otherwise the order differs and
      // eventual multiplication of floats in different orders produces different results.
      val propertyTokens = propertyAttachments.flatMap(EidosAttachment.getAttachmentWords).toArray.sorted

      OntologyGrounding(wordToVec.calculateSimilarities(propertyTokens, conceptEmbeddings))
    }
    else
      OntologyGrounding()
  }
}

object EidosOntologyGrounder {
  // Namespace strings for the different in-house ontologies we typically use
  val    UN_NAMESPACE = "un"
  val   WDI_NAMESPACE = "wdi"
  val   FAO_NAMESPACE = "fao"
  val  MESH_NAMESPACE = "mesh"
  val PROPS_NAMESPACE = "props"

  protected val logger = LoggerFactory.getLogger(this.getClass())

  def apply(name: String, domainOntology: DomainOntology, wordToVec: EidosWordToVec): EidosOntologyGrounder =
    name match {
      case PROPS_NAMESPACE => new PropertiesOntologyGrounder(name, domainOntology, wordToVec)
      case _ => new EidosOntologyGrounder(name, domainOntology, wordToVec)
    }
}
