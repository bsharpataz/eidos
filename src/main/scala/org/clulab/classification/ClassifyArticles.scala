package org.clulab.classification

import edu.stanford.nlp.coref.hybrid.rf.RandomForest
import org.clulab.classification.DataUtils.{Article, SerializedArticles}
import org.clulab.classification.ClassificationUtils.articleLabel
import org.clulab.embeddings.word2vec.Word2Vec
import org.clulab.learning._
import org.clulab.odin.Mention
import org.clulab.struct.Counter
import org.clulab.utils.Serializer
import org.clulab.wm.eidos.groundings.{CompactWord2Vec, ConceptEmbedding, EidosWordToVec}

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.util.Random

object ClassifyArticles {


  var unigramCounts = new Counter[String]
  var unigramDocCounts = new Counter[String]

  lazy val w2v = new Word2Vec("/Users/bsharp/github/eidos/src/main/resources/org/clulab/wm/eidos/english/w2v/vectors.txt")

//  lazy val fastw2v = CompactWord2Vec("/Users/bsharp/github/eidos/cache/vectors.txt.serialized", resource = false, true)


  def countUnigrams(article: Article): Unit = {
    val unigrams = mkFeatures(article)
    for ((unigramFeature, ct) <- unigrams.toSeq) {
      unigramCounts.incrementCount(unigramFeature, ct)
      unigramDocCounts.incrementCount(unigramFeature, 1.0)
    }
  }


  def mkFeatures(article: Article, useTfidf: Boolean = false, freqThresh: Int = 0): Counter[String] = {
    val contentTags = Set("NN", "VB", "IN", "JJ")
    def isContent(t: String): Boolean = contentTags.exists(tag => t.startsWith(tag))

    val features = new Counter[String]
    val contentWords = new ArrayBuffer[String]

    // Unigram counts
    for (s <- article.document.sentences) {
      for (i <- s.words.indices) {
        val word = s.words(i)
        val lemma = s.lemmas.get(i)
        val tag = s.tags.get(i)
        if (isContent(tag)) {
          features.incrementCount(s"UNI_${lemma}_$tag")
          contentWords.append(word)
        }
      }
    }


    // tfidf
    if (useTfidf) {
      for (feature <- features.keySet) {
        val tf = features.getCount(feature)
        val df = unigramDocCounts.getCount(feature)
        features.setCount(feature, tf / df)
      }
    }

    // Freq filter
    for (feature <- features.keySet) {
      if (unigramCounts.getCount(feature) < freqThresh) {
        features.setCount(feature, 0.0)
      }
    }

    // FP similarities
    var maxMax = -100.0
    var minMax = 100.0

    var maxMin = -100.0
    var minMin = 100.0

    var maxAvg = -100.0
    var minAvg = 100.0

    var maxOverall = -100.0
    var minOverall = 100.0

    for (subtype <- ClassificationHelper.FP.indices) {
      val subtypePhrases = ClassificationHelper.FP(subtype)
      val (w2vFeatures, subMax, subMin, subAvg, subOverall) = mkW2VFeatures(contentWords, subtypePhrases, s"FP$subtype")
      // add features
      features += w2vFeatures
      // update running totals
      maxMax = Math.max(maxMax, subMax)
      minMax = Math.min(minMax, subMax)
      maxMin = Math.max(maxMin, subMin)
      minMin = Math.min(minMin, subMin)
      maxAvg = Math.max(maxAvg, subAvg)
      minAvg = Math.min(minAvg, subAvg)
      maxOverall = Math.max(maxOverall, subOverall)
      minOverall = Math.min(minOverall, subOverall)
    }
    // Add running totals
    var prefix = "FP"
    features.setCount(s"$prefix-maxMAX", maxMax)
    features.setCount(s"$prefix-minMAX", minMax)
    features.setCount(s"$prefix-maxMIN", maxMin)
    features.setCount(s"$prefix-minMIN", minMin)
    features.setCount(s"$prefix-maxAVG", maxAvg)
    features.setCount(s"$prefix-minAVG", minAvg)
    features.setCount(s"$prefix-maxOVERALL", maxOverall)
    features.setCount(s"$prefix-minOVERALL", minOverall)


    // Dom similarities
    maxMax = -100.0
    minMax = 100.0

    maxMin = -100.0
    minMin = 100.0

    maxAvg = -100.0
    minAvg = 100.0

    maxOverall = -100.0
    minOverall = 100.0

    for (subtype <- ClassificationHelper.DOM.indices) {
      val subtypePhrases = ClassificationHelper.DOM(subtype)
      val (w2vFeatures, subMax, subMin, subAvg, subOverall) = mkW2VFeatures(contentWords, subtypePhrases, s"DOM$subtype")
      // add features
      features += w2vFeatures
      // update running totals
      maxMax = Math.max(maxMax, subMax)
      minMax = Math.min(minMax, subMax)
      maxMin = Math.max(maxMin, subMin)
      minMin = Math.min(minMin, subMin)
      maxAvg = Math.max(maxAvg, subAvg)
      minAvg = Math.min(minAvg, subAvg)
      maxOverall = Math.max(maxOverall, subOverall)
      minOverall = Math.min(minOverall, subOverall)
    }
    // Add running totals
    prefix = "DOM"
    features.setCount(s"$prefix-maxMAX", maxMax)
    features.setCount(s"$prefix-minMAX", minMax)
    features.setCount(s"$prefix-maxMIN", maxMin)
    features.setCount(s"$prefix-minMIN", minMin)
    features.setCount(s"$prefix-maxAVG", maxAvg)
    features.setCount(s"$prefix-minAVG", minAvg)
    features.setCount(s"$prefix-maxOVERALL", maxOverall)
    features.setCount(s"$prefix-minOVERALL", minOverall)


    features
  }

  def mkW2VFeatures(unigrams: Seq[String], categoryStrings: Seq[String], categoryPrefix: String): (Counter[String], Double, Double, Double, Double) = {
    val features = new Counter[String]
    val unigramsSet = unigrams.toSet

    val categoryWords = categoryStrings.flatMap(_.split(" "))
    val categoryWordsSet = categoryWords.toSet

    // Max to each category
    val max = w2v.maxSimilarity(unigramsSet, categoryWordsSet)
    features.setCount(s"$categoryPrefix-MAX", max)

    // Min to each category
    val min = w2v.minSimilarity(unigramsSet, categoryWordsSet)
    features.setCount(s"$categoryPrefix-MIN", min)

    // Avg to each category
    val avg = w2v.avgSimilarity(unigrams,  categoryWords)
    features.setCount(s"$categoryPrefix-AVG", avg)

    // Overall to each category
    val overall = w2v.textSimilarity(unigrams, categoryWords)
    features.setCount(s"$categoryPrefix-OVERALL", overall)

    (features, max, min, avg, overall)
  }


  def mkDatum(a: Article, useTfidf: Boolean = false, freqThresh: Int = 0, hierarch: Boolean, level: Option[Int], finegrained: Boolean): RVFDatum[Int, String] = {
    val features = mkFeatures(a, useTfidf, freqThresh)
    val label = articleLabel(a, finegrained) //ClassificationUtils.getLabel(a, hierarch, level, finegrained)
    new RVFDatum[Int, String](label, features)
  }

  def main(args: Array[String]): Unit = {
    // Load the data
    val wdir = "/Users/bsharp/data/protests"
//    val collectionDir = s"$wdir/all_files_aid"
//    val labelFile = "/Users/bsharp/data/protests/randomsample300_01282019.txt"
//    val articles = DataUtils.loadArticleData(labelFile, collectionDir)
//    println(s"Loaded ${articles.length} articles...")
    val serialized = s"$wdir/serializedRandom_v1.ser"
//    println(s"serializing to $serialized")
//    Serializer.save[SerializedArticles](new SerializedArticles(articles), serialized)

    // Options
    val useTfidf = true
    val freqThresh = 20
    val lower = -1.0
    val upper = 1.0
    val hierarchical = false // not in use!
    val level = None  // not in use!
    val finegrained = true
    val numLabels = if (finegrained) 5 else 3

    assert(!hierarchical) // currently deprecated

    // Load the serialized articles
    val articles = SerializedArticles.load(serialized)
    articles.foreach(countUnigrams)
    val datums = articles.map(mkDatum(_, useTfidf, freqThresh, hierarchical, level, finegrained))
    println(s"Finished loading ${articles.length} serialized articles.")

//    // Split into train and test
    Random.setSeed(6)
    val shuffled = Random.shuffle(datums)
//    val nTrain = 200
//    val (train, test) = Random.shuffle(articles).splitAt(nTrain)
//    train.foreach(countUnigrams)
//    println(s"There are ${train.length} training instances and ${test.length} testing instances")




    // Cross-Validation
    val numFolds = 10
    val folds = shuffled.grouped(Math.floor(datums.length / numFolds).toInt + 1).toArray
    val accuracies = new ArrayBuffer[Double]
    for (testFold <- 0 until numFolds) {
      println(s"Beginning fold $testFold of ${folds.length}...")

      // reset counters
      unigramCounts = new Counter[String]
      unigramDocCounts = new Counter[String]
      // Make train data
      val trainDataset = new RVFDataset[Int, String]()
      for {
        (fold, i) <- folds.zipWithIndex
        if i != testFold
        datum <- fold
      } trainDataset += datum
      println(s"This fold has ${trainDataset.size} training instances and ${folds(testFold).length} test instances.")
      val scaleRange = Datasets.svmScaleRVFDataset[Int, String](trainDataset, lower, upper)
      // Train
      println("Training classifier...")
//      val classifier = new RFClassifier[Int, String](numTrees = 20, maxTreeDepth = 10)
      val classifier = new LibSVMClassifier[Int, String](LinearKernel)
//      val classifier = new LogisticRegressionClassifier[Int, String]()
      classifier.train(trainDataset)
      // Make test datums
      val testDatums = for {
        instance <- folds(testFold)
        feats = instance.featuresCounter
        rescaled = Datasets.svmScaleDatum[String](feats, scaleRange, lower, upper)
      } yield new RVFDatum[Int, String](instance.label, rescaled)
      // Evaluate
      println("Evaluating...")
      val accuracy = ClassificationUtils.evaluate(classifier, testDatums, numLabels, hierarchical, level)
      println("===========================================")
      println(s"Fold $testFold Accuracy: $accuracy")
      accuracies.append(accuracy)
    }
    println(s"\nAccuracies: ${accuracies.mkString(", ")}")
    println(s"OVERALL: ${accuracies.sum / numFolds.toDouble}")





    // Make a dataset from the train articles
//    val trainDataset = new RVFDataset[Int, String]()
//    for (instance <- train) {
//      val datum = mkDatum(instance, useTfidf, freqThresh)
//      trainDataset += datum
//    }
//    println(s"Added ${trainDataset.size} instances to train dataset.")
//    Serializer.save[RVFDataset[Int, String]](trainDataset, s"$serialized.train.ser")
//    println("trainset serialized.")
//
//    val scaleRange = Datasets.svmScaleRVFDataset[Int, String](trainDataset, lower, upper)
//
//    // Train the classifier
////    val classifier = new LibSVMClassifier[Int, String](LinearKernel)
////    val classifier = new LogisticRegressionClassifier[Int, String]()
////    val classifier = new L1LogisticRegressionClassifier[Int, String]()
//    val classifier = new RFClassifier[Int, String](numTrees = 20, maxTreeDepth = 10)
//    classifier.train(trainDataset)
//
//    val testDatums = for {
//      instance <- test
//      feats = mkFeatures(instance, useTfidf, freqThresh)
//      rescaled = Datasets.svmScaleDatum[String](feats, scaleRange, lower, upper)
//    } yield new RVFDatum[Int, String](instance.fpLabel, rescaled)
//
//    val accuracy = ClassificationUtils.evaluate(classifier, testDatums, trainDataset.numLabels)
//    println("===========================================")
//    println(s"Overall Accuracy: $accuracy")
//



  }
}
