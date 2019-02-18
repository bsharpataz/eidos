package org.clulab.classification

//import edu.stanford.nlp.classify.RVFClassifier
import edu.stanford.nlp.classify.RVFClassifier
import org.clulab.classification.ClassifyArticles.mkFeatures
import org.clulab.classification.DataUtils.Article
import org.clulab.learning.{Classifier, Datasets, RVFDatum}
import org.clulab.struct.Counter

import scala.collection.mutable

object ClassificationUtils {

  def evaluate(classifier: Classifier[Int, String], test: Seq[RVFDatum[Int, String]], numLabels: Int, hierarchical: Boolean, level: Option[Int]): Double = {
    // Evaluate

    var overallCorrect = 0.0
    var overallTotal = 0.0

    val correct = new Counter[Int]
    val total = new Counter[Int]

    val predMatrix = new mutable.HashMap[Int, mutable.HashMap[Int, Double]]()
    for (i <- 0 until numLabels) {
      predMatrix(i) = new mutable.HashMap[Int, Double]()
      for (j <- 0 until numLabels) {
        predMatrix(i)(j) = 0.0
      }
    }

    for(datum <- test) {
      val pred = classifier.classOf(datum) //row
      predMatrix(pred)(datum.label) += 1.0
//      println(s"prediction: $pred\t${datum.label}")
      if(pred == datum.label) {
        correct.incrementCount(datum.label)
        overallCorrect += 1.0
      }
      total.incrementCount(datum.label)
      overallTotal += 1.0
    }

    println("DOM=0, FP=1, NA = 2")
    for (labelId <- 0 until numLabels) {
      val acc = correct.getCount(labelId) / total.getCount(labelId)
      // Report
      println(s"Acc label $labelId: $acc")
    }

    if (hierarchical){
      level match {
        case Some(1) => displayMatixHier1(predMatrix.toMap)
        case Some(2) => ???
        case _ => ???
      }
    } else {
//      displayMatix(predMatrix.toMap)
      displayMatixFG(predMatrix.toMap)
    }



    overallCorrect / overallTotal
  }

//  def getLabel(a: Article, hierarchical: Boolean, level: Option[Int]): Int = {
//    hierarchical match {
//      case false => a.fpLabel
//      case true =>
//        level.get match {
//          case 1 => hierarchLabel1(a.fpLabel)
//          case 2 => ???
//          case _ => ???
//        }
//    }
//  }
//
//  def getLabel(label: Int, hierarchical: Boolean, level: Option[Int], finegrained: Boolean): Int = {
//    hierarchical match {
//      case false => label
//      case true =>
//        level.get match {
//          case 1 => hierarchLabel1(label)
//          case 2 => ???
//          case _ => ???
//        }
//    }
//  }
//
//  def hierarchLabel1(label: Int): Int = label match {
//    case 0 => 1 // Dom = yes
//    case 1 => 1 // FP = yes
//    case 2 => 0 // NA = no
//  }


  def displayMatix(predMatrix: Map[Int, mutable.HashMap[Int, Double]]): Unit = {
    println("   \tDOM \t FP \t NA")
    println(s"DOM\t${predMatrix(0)(0)} \t${predMatrix(0)(1)}  \t${predMatrix(0)(2)}")
    println(s" FP\t${predMatrix(1)(0)}  \t${predMatrix(1)(1)}  \t${predMatrix(1)(2)}")
    println(s" NA\t${predMatrix(2)(0)}  \t${predMatrix(2)(1)}  \t${predMatrix(2)(2)}")
  }

  def displayMatixFG(predMatrix: Map[Int, mutable.HashMap[Int, Double]]): Unit = {
    println("   \tDOM \t FPConf \t FPTerr   \t FPOth  \t   NA")
    println(s"DOM \t${predMatrix(0)(0)} \t${predMatrix(0)(1)}  \t${predMatrix(0)(2)}   \t${predMatrix(0)(3)}  \t${predMatrix(0)(4)}")
    println(s"FPConf\t${predMatrix(1)(0)}  \t${predMatrix(1)(1)}  \t${predMatrix(1)(2)}   \t${predMatrix(1)(3)}  \t${predMatrix(1)(4)}")
    println(s"FPTerr\t${predMatrix(2)(0)}  \t${predMatrix(2)(1)}  \t${predMatrix(2)(2)}   \t${predMatrix(2)(3)}  \t${predMatrix(2)(4)}")
    println(s"FPOth\t${predMatrix(3)(0)}  \t${predMatrix(3)(1)}  \t${predMatrix(3)(2)}   \t${predMatrix(3)(3)}  \t${predMatrix(3)(4)}")
    println(s" NA \t${predMatrix(4)(0)}  \t${predMatrix(4)(1)}  \t${predMatrix(4)(2)}   \t${predMatrix(4)(3)}  \t${predMatrix(4)(4)}")
  }

  def displayMatixHier1(predMatrix: Map[Int, mutable.HashMap[Int, Double]]): Unit = {
    println("   \tNA    \tFP/DOM")
    println(s" NA   \t${predMatrix(0)(0)}    \t${predMatrix(0)(1)}")
    println(s"FP/DOM\t${predMatrix(1)(0)}     \t${predMatrix(1)(1)}")
  }

  def articleLabel(a: Article, finegrained: Boolean): Int = {
    if (true) {
      // Dom=1, FP:intlconflict=2, FP:territorial=3, FP:other=4, NA=0
      val r = a.issue.drop(1) match {  // drop to get rid of the " at the beginning
        case dom if dom.startsWith("Dom") => 1
        case fpConflict if fpConflict.contains("international conflict") => 2
        case fpTerritorial if fpTerritorial.contains("territorial") => 3
        case fpOther if fpOther.startsWith("FP") => 4
        case _ => 0 // NA
      }
      println(s"label: $r    (${a.issue}) [Dom=1, FP:intlconflict=2, FP:territorial=3, FP:other=4, NA=0]")
      r
    } else {
      // Dom=0, FP=1, NA=2
      a.fpLabel
    }
  }

}
