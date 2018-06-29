package org.clulab.wm.eidos

import java.net.URL
import java.nio.file.Paths

import com.typesafe.config.{Config, ConfigFactory}
import org.clulab.odin._
import org.clulab.processors.fastnlp.FastNLPProcessor
import org.clulab.processors.{Document, Processor, Sentence}
import org.clulab.sequences.LexiconNER
import org.clulab.utils.Configured
import org.clulab.wm.eidos.attachments.Score
import org.clulab.wm.eidos.entities.EidosEntityFinder
import org.clulab.wm.eidos.groundings._
import org.clulab.wm.eidos.groundings.Aliases.Groundings
import org.clulab.wm.eidos.groundings.EidosOntologyGrounder.{FAO_NAMESPACE, UN_NAMESPACE, WDI_NAMESPACE}
import org.clulab.wm.eidos.mentions.EidosMention
import org.clulab.wm.eidos.utils._
import org.slf4j.LoggerFactory
import org.clulab.wm.eidos.document.EidosDocument
import org.clulab.timenorm.TemporalCharbasedParser

case class AnnotatedDocument(var document: Document, var odinMentions: Seq[Mention], var eidosMentions: Seq[EidosMention])

/**
  * A system for text processing and information extraction
  */
class EidosSystem(val config: Config = ConfigFactory.load("eidos")) extends Configured with StopwordManaging with MultiOntologyGrounder with AdjectiveGrounder {
  def this(x: Object) = this() // Dummy constructor crucial for Python integration
//  val proc: Processor = new CluProcessor()//new FastNLPProcessor() // TODO: Get from configuration file soon
  val proc: Processor = new FastNLPProcessor() // TODO: Get from configuration file soon

  var debug = true // Allow external control with var

  override def getConf: Config = config

  var word2vec = getArgBoolean(getFullName("useW2V"), Some(false)) // Turn this on and off here
  // This isn't intended to be (re)loadable.  This only happens once.
  protected val wordToVec = EidosWordToVec(
    word2vec,
    getPath("wordToVecPath", "/org/clulab/wm/eidos/w2v/vectors.txt"),
//    getPath("wordToVecPath", "/org/clulab/wm/eidos/w2v/glove.840B.300d.txt"), // NOTE: Moving to GLoVE vectors
    getArgInt(getFullName("topKNodeGroundings"), Some(10))
  )

  protected def getFullName(name: String) = EidosSystem.PREFIX + "." + name

  protected def getPath(name: String, defaultValue: String): String = {
    val path = getArgString(getFullName(name), Option(defaultValue))

    EidosSystem.logger.info(name + ": " + path)
    path
  }

  class LoadableAttributes(
    // These are the values which can be reloaded.  Query them for current assignments.
    val entityFinder: EidosEntityFinder,
    val domainParams: DomainParams,
    val adjectiveGrounder: AdjectiveGrounder,
    val actions: EidosActions,
    val engine: ExtractorEngine,
    val ner: LexiconNER,
    val stopwordManager: StopwordManager,
    val ontologyGrounders: Seq[EidosOntologyGrounder],
    val timenorm: Option[TemporalCharbasedParser]
  )

  object LoadableAttributes {
    def   masterRulesPath: String = getPath(  "masterRulesPath", "/org/clulab/wm/eidos/grammars/master.yml")
    def  quantifierKBPath: String = getPath( "quantifierKBPath", "/org/clulab/wm/eidos/quantifierKB/gradable_adj_fullmodel.kb")
    def domainParamKBPath: String = getPath("domainParamKBPath", "/org/clulab/wm/eidos/quantifierKB/domain_parameters.kb")
    def    quantifierPath: String = getPath(   "quantifierPath",  "org/clulab/wm/eidos/lexicons/Quantifier.tsv")
    def   entityRulesPath: String = getPath(  "entityRulesPath", "/org/clulab/wm/eidos/grammars/entities/grammar/entities.yml")
    def    avoidRulesPath: String = getPath(   "avoidRulesPath", "/org/clulab/wm/eidos/grammars/avoidLocal.yml")
    def      taxonomyPath: String = getPath(     "taxonomyPath", "/org/clulab/wm/eidos/grammars/taxonomy.yml")
    def     stopwordsPath: String = getPath(    "stopWordsPath", "/org/clulab/wm/eidos/filtering/stops.txt")
    def   transparentPath: String = getPath(  "transparentPath", "/org/clulab/wm/eidos/filtering/transparent.txt")

    def    unOntologyPath: String = getPath(   "unOntologyPath", "/org/clulab/wm/eidos/ontologies/un_ontology.yml")
    def   wdiOntologyPath: String = getPath(  "wdiOntologyPath", "/org/clulab/wm/eidos/ontologies/wdi_ontology.yml")
    def   faoOntologyPath: String = getPath(      "faoOntology", "/org/clulab/wm/eidos/ontologies/fao_variable_ontology.yml")

    def timeNormModelPath: String = getPath("timeNormModelPath", "/org/clulab/wm/eidos/models/timenorm_model.hdf5")

    // These are needed to construct some of the loadable attributes even though it isn't a path itself.
    def ontologies: Seq[String] = getArgStrings(getFullName("ontologies"), Some(Seq.empty))
    def maxHops: Int = getArgInt(getFullName("maxHops"), Some(15))

    protected def domainOntologies: Seq[DomainOntology] =
        if (!word2vec)
          Seq.empty
        else
          ontologies.map {
            _ match {
              case name @ UN_NAMESPACE  =>  UNOntology(name,  unOntologyPath, proc)
              case name @ WDI_NAMESPACE => WDIOntology(name, wdiOntologyPath, proc)
              case name @ FAO_NAMESPACE => FAOOntology(name, faoOntologyPath, proc)
              case name @ _ => throw new IllegalArgumentException("Ontology " + name + " is not recognized.")
            }
          }

    def apply(): LoadableAttributes = {
      // Reread these values from their files/resources each time based on paths in the config file.
      val masterRules = FileUtils.getTextFromResource(masterRulesPath)
      val actions = EidosActions(taxonomyPath)
      val ontologyGrounders = domainOntologies.map(EidosOntologyGrounder(_, wordToVec))
      val timenormResource: URL = getClass.getResource(timeNormModelPath)
      val timenorm: Option[TemporalCharbasedParser] =
          if (timenormResource == null) None
          else {
            // See https://stackoverflow.com/questions/6164448/convert-url-to-normal-windows-filename-java/17870390
            val file = Paths.get(timenormResource.toURI()).toFile().getAbsolutePath()
            // timenormResource.getFile() won't work for Windows, probably because Hdf5Archive is
            //     public native void openFile(@StdString BytePointer var1, ...
            // and needs native representation of the file.
            Some(new TemporalCharbasedParser(file))
          }

      new LoadableAttributes(
        EidosEntityFinder(entityRulesPath, avoidRulesPath, maxHops = maxHops),
        DomainParams(domainParamKBPath),
        EidosAdjectiveGrounder(quantifierKBPath),
        actions,
//        ExtractorEngine(masterRules, actions, actions.mergeAttachments), // ODIN component
        ExtractorEngine(masterRules, actions, actions.globalAction), // ODIN component
        LexiconNER(Seq(quantifierPath), caseInsensitiveMatching = true), //TODO: keep Quantifier...
        StopwordManager(stopwordsPath, transparentPath),
        ontologyGrounders,
        timenorm
      )
    }
  }

  var loadableAttributes = LoadableAttributes()

  // These public variables are accessed directly by clients which
  // don't know they are loadable and which had better not keep copies.
  def domainParams = loadableAttributes.domainParams
  def engine = loadableAttributes.engine
  def ner = loadableAttributes.ner
  def timenorm = loadableAttributes.timenorm

  def reload() = loadableAttributes = LoadableAttributes()

  // Annotate the text using a Processor and then populate lexicon labels
  def annotate(text: String, keepText: Boolean = true, documentCreationTime: Option[String] = None): Document = {
    val oldDoc = proc.annotate(text, true) // Formerly keepText, must now be true
    val doc = EidosDocument(oldDoc, keepText, documentCreationTime)
    doc.sentences.foreach(addLexiconNER)
    doc.parseTime(timenorm)
    doc
  }

  protected def addLexiconNER(s: Sentence) = {
    for {
      (lexiconNERTag, i) <- ner.find(s).zipWithIndex
      if lexiconNERTag != EidosSystem.NER_OUTSIDE
    } s.entities.get(i) = lexiconNERTag
  }

  // MAIN PIPELINE METHOD
  def extractFromText(text: String, keepText: Boolean = true, returnAllMentions: Boolean = false, documentCreationTime: Option[String] = None): AnnotatedDocument = {
    val doc = annotate(text, keepText, documentCreationTime)
    val odinMentions = extractFrom(doc)
    //println(s"\nodinMentions() -- entities : \n\t${odinMentions.map(m => m.text).sorted.mkString("\n\t")}")
    val cagRelevant = if (returnAllMentions) odinMentions else keepCAGRelevant(odinMentions)
    val eidosMentions = EidosMention.asEidosMentions(cagRelevant, loadableAttributes.stopwordManager, this)

    new AnnotatedDocument(doc, cagRelevant, eidosMentions)
  }

  def extractEventsFrom(doc: Document, state: State): Vector[Mention] = {
    val res = engine.extractFrom(doc, state).toVector
    loadableAttributes.actions.keepMostCompleteEvents(res, State(res)).toVector
  }

  def extractFrom(doc: Document): Vector[Mention] = {
    // get entities
    val entities: Seq[Mention] = loadableAttributes.entityFinder.extractAndFilter(doc).toVector

    // filter entities which are entirely stop or transparent
    //println(s"In extractFrom() -- entities : \n\t${entities.map(m => m.text).sorted.mkString("\n\t")}")
    // Becky says not to filter yet
    //val filtered = loadableAttributes.ontologyGrounder.filterStopTransparent(entities)
    val filtered = entities
    //println(s"\nAfter filterStopTransparent() -- entities : \n\t${filtered.map(m => m.text).sorted.mkString("\n\t")}")

    val events = extractEventsFrom(doc, State(filtered)).distinct
    // Note -- in main pipeline we filter to only CAG relevant after this method.  Since the filtering happens at the
    // next stage, currently all mentions make it to the webapp, even ones that we filter out for the CAG exports
    //val cagRelevant = keepCAGRelevant(events)

    events
    //cagRelevant.toVector
  }


  def populateSameAsRelations(ms: Seq[Mention]): Seq[Mention] = {

    // Create an UndirectedRelation Mention to contain the sameAs grounding information
    def sameAs(a: Mention, b: Mention, score: Double): Mention = {
      // Build a Relation Mention (no trigger)
      new CrossSentenceMention(
        labels = Seq("SameAs"),
        anchor = a,
        neighbor = b,
        arguments = Seq(("node1", Seq(a)), ("node2", Seq(b))).toMap,
        document = a.document,  // todo: change?
        keep = true,
        foundBy = s"sameAs-${EidosSystem.SAME_AS_METHOD}",
        attachments = Set(Score(score)))
    }

    // n choose 2
    val sameAsRelations = for {
      (m1, i) <- ms.zipWithIndex
      m2 <- ms.slice(i+1, ms.length)
      score = wordToVec.calculateSimilarity(m1, m2)
    } yield sameAs(m1, m2, score)

    sameAsRelations
  }

  // Old version
  def oldKeepCAGRelevant(mentions: Seq[Mention]): Seq[Mention] = {
    // 1) These will be "Causal" and "Correlation" which fall under "Event"
    val cagEdgeMentions = mentions.filter(m => EidosSystem.CAG_EDGES.contains(m.label))
    // 2) and these will be "Entity", without overlap from above.
    val entityMentions = mentions.filter(m => m.matches("Entity") && m.attachments.nonEmpty)
    // 3) These last ones may overlap with the above or include mentions not in the original list.
    val argumentMentions: Seq[Mention] = cagEdgeMentions.flatMap(_.arguments.values.flatten)
    // Put them all together.
    val goodMentions = cagEdgeMentions ++ entityMentions ++ argumentMentions
    // To preserve order, avoid repeats, and not allow anything new in the list, filter the original.
    val relevantMentions = mentions.filter(m => goodMentions.exists(m.eq))

    relevantMentions
  }

  // New version
  def keepCAGRelevant(mentions: Seq[Mention]): Seq[Mention] = {

    // 1) These will be "Causal" and "Correlation" which fall under "Event" if they have content
    val cagEdgeMentions = mentions.filter(m => releventEdge(m))

    // Should these be included as well?

    // 3) These last ones may overlap with the above or include mentions not in the original list.
    val cagEdgeArguments = cagEdgeMentions.flatMap(mention => mention.arguments.values.flatten.toSeq)
    // Put them all together.
    val releventEdgesAndTheirArgs = cagEdgeMentions ++ cagEdgeArguments
    // To preserve order, avoid repeats, and not allow anything new in the list, filter the original.
    mentions.filter(mention => isCAGRelevant(mention, cagEdgeMentions, cagEdgeArguments))
  }

  def isCAGRelevant(mention: Mention, cagEdgeMentions: Seq[Mention], cagEdgeArguments: Seq[Mention]): Boolean =
    // We're no longer keeping all modified entities
    //(mention.matches("Entity") && mention.attachments.nonEmpty) ||
      cagEdgeMentions.contains(mention) ||
      cagEdgeArguments.contains(mention)

  def releventEdge(m: Mention): Boolean = {
    m match {
      case tb: TextBoundMention => EidosSystem.CAG_EDGES.contains(tb.label)
      case rm: RelationMention => EidosSystem.CAG_EDGES.contains(rm.label)
      case em: EventMention => EidosSystem.CAG_EDGES.contains(em.label) && argumentsHaveContent(em)
      case _ => throw new UnsupportedClassVersionError()
    }
  }

  def argumentsHaveContent(mention: EventMention): Boolean = {
    val causes: Seq[Mention] = mention.arguments.getOrElse("cause", Seq.empty)
    val effects: Seq[Mention] = mention.arguments.getOrElse("effect", Seq.empty)

    if (causes.nonEmpty && effects.nonEmpty) // If it's something interesting,
    // then both causes and effects should have some content
      causes.exists(loadableAttributes.stopwordManager.hasContent(_)) && effects.exists(loadableAttributes.stopwordManager.hasContent(_))
    else
      true
  }

  /*
      Grounding
  */

  def containsStopword(stopword: String) =
    loadableAttributes.stopwordManager.containsStopword(stopword)

  def groundOntology(mention: EidosMention): Groundings =
      loadableAttributes.ontologyGrounders.map (ontologyGrounder =>
        (ontologyGrounder.name, ontologyGrounder.groundOntology(mention))).toMap

  def groundAdjective(quantifier: String): AdjectiveGrounding =
    loadableAttributes.adjectiveGrounder.groundAdjective(quantifier)

  /*
      Wrapper for using w2v on some strings
   */
  def stringSimilarity(string1: String, string2: String): Double = wordToVec.stringSimilarity(string1, string2)

  /*
     Debugging Methods
   */

  def debugPrint(str: String): Unit = if (debug) println(str)

  def debugMentions(mentions: Seq[Mention]): Unit = {
    if (debug) mentions.foreach(m => println(s" * ${m.text} [${m.label}, ${m.tokenInterval}]"))
  }
}

object EidosSystem {
  type Corpus = Seq[AnnotatedDocument]

  val logger = LoggerFactory.getLogger(this.getClass())

  val PREFIX: String = "EidosSystem"

  val EXPAND_SUFFIX: String = "expandParams"
  val SPLIT_SUFFIX: String = "splitAtCC"
  // Stateful Labels used by webapp
  val INC_LABEL_AFFIX = "-Inc"
  val DEC_LABEL_AFFIX = "-Dec"
  val QUANT_LABEL_AFFIX = "-Quant"
  val NER_OUTSIDE = "O"
  // Provenance info for sameAs scoring
  val SAME_AS_METHOD = "simple-w2v"

  // CAG filtering
  val CAG_EDGES = Set("Causal", "Correlation")
}
