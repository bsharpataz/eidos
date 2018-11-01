package org.clulab.wm.eidos.context

import org.clulab.wm.eidos.utils.Sourcer
import org.deeplearning4j.nn.graph.ComputationGraph
import org.deeplearning4j.nn.modelimport.keras.KerasModelImport
import org.nd4j.linalg.factory.Nd4j

import scala.collection.mutable
import scala.collection.mutable.ListBuffer

object Geo_disambiguate_parser {
  val INT2LABEL = Map(0 -> "I-LOC", 1 -> "B-LOC", 2 -> "O")
  val UNKNOWN_TOKEN = "UNKNOWN_TOKEN"
  val TIME_DISTRIBUTED_1 = "time_distributed_1"
}

class Geo_disambiguate_parser(modelPath: String, word2IdxPath: String, loc2geonameIDPath: String) {

  protected val network: ComputationGraph = KerasModelImport.importKerasModelAndWeights(modelPath, false)

  lazy protected val word2int: mutable.Map[String, Int] = readDict(word2IdxPath)
  lazy protected val loc2geonameID: mutable.Map[String, Int] = readDict(loc2geonameIDPath) // provide path of geoname dict file having geonameID with max population

  protected def readDict(dictPath: String): mutable.Map[String, Int] = {
    val source = Sourcer.sourceFromResource(dictPath)
    val dict = mutable.Map.empty[String, Int]

    source.getLines.foreach { line =>
      val words = line.split(' ')

      dict += (words(0).toString -> words(1).toInt)
    }
    source.close()
    dict
  }

  def createFeatures(words: Array[String]): Array[Float] = {
    val unknown = word2int(Geo_disambiguate_parser.UNKNOWN_TOKEN)
    val features = words.map(word2int.getOrElse(_, unknown).toFloat)

    features
  }

  def generateLabels(word_features: Array[Float]): Array[String] = {
    val word_input = Nd4j.create(word_features.toArray)
    network.setInput(0, word_input)

    val results = network.feedForward()
    val output = results.get(Geo_disambiguate_parser.TIME_DISTRIBUTED_1)
    val label_predictions: Array[Array[Float]] =
        if (output.shape()(0) == 1) Array(output.toFloatVector())
        else output.toFloatMatrix()

    // TODO: Why does this happen?
    if (label_predictions.size != word_features.size)
      println("Not working")
    else
      println("Working fine")

    def argmax[T](values: Array[T]): Int = {
      // This goes through the values twice, but at least doesn't create extra objects.
      val max = values.max

      values.indexWhere(_ == max)
    }

    label_predictions.map(prediction => Geo_disambiguate_parser.INT2LABEL(argmax(prediction)))
  }

  def makeLocationPhrases(word_labels: Array[String], words_text: Array[String],
      Start_offset: Array[Int], End_offset: Array[Int]): List[GeoPhraseID] = {
    var locations = new ListBuffer[GeoPhraseID]
    var location_phrase = ""
    var start_phrase_char_offset = 0

    def addLocation(index: Int) = {
      val prettyLocationPhrase = location_phrase.replace('_', ' ')
      val geoNameId = loc2geonameID.get(location_phrase.toLowerCase)

      // TODO: Figure this out
      // The word at index has ended already, therefore use index - 1.
      locations += GeoPhraseID(prettyLocationPhrase, geoNameId, start_phrase_char_offset, End_offset(index - 1))
    }

    for ((label, index) <- word_labels.zipWithIndex) {
      if (label == "B-LOC") {
        if (location_phrase.nonEmpty)
          addLocation(index)
        location_phrase = words_text(index)  // initializing the location phrase with current word
        start_phrase_char_offset = Start_offset(index)
      }
      else if (label == "I-LOC") {
        if (location_phrase.nonEmpty)
          location_phrase += ("_" + words_text(index))
        else {
          start_phrase_char_offset = Start_offset(index)  // this case means that we are getting I-LOC but there was no B-LOC before this step.
          location_phrase = words_text(index)
        }
      }
      else if (label == "O") {
        if (location_phrase.nonEmpty)
          addLocation(index)
        location_phrase = ""
      }
    }
    locations.toList
  }
}
