package org.clulab.wm.eidos.groundings

import java.io.{FileInputStream, FileOutputStream, ObjectOutputStream}
import java.util.IdentityHashMap

import org.clulab.utils.ClassLoaderObjectInputStream
import org.clulab.wm.eidos.utils.Namer

import scala.collection.JavaConverters._
import scala.collection.mutable.{HashMap => MutableHashMap}
import scala.collection.mutable.ArrayBuffer

class CompactNamerData(val nodeStrings: Array[String], val leafIndexes: Array[Int], val branchIndexes: Array[Int])

class CompactNamer(protected val n: Int, data: CompactNamerData) extends Namer {

  protected def parentName(n: Int, stringBuilder: StringBuilder): Unit = {
    if (n >= 0) {
      val index = n * CompactDomainOntology.indexWidth
      val parentOffset = data.branchIndexes(index + CompactDomainOntology.parentOffset)
      val nameOffset = data.branchIndexes(index + CompactDomainOntology.nameOffset)

      parentName(parentOffset, stringBuilder)
      stringBuilder.append(data.nodeStrings(nameOffset))
      stringBuilder.append(DomainOntology.SEPARATOR)
    }
  }

  def name: String = {
    val stringBuilder = new StringBuilder()
    val index = n * CompactDomainOntology.indexWidth
    val parentOffset = data.leafIndexes(index + CompactDomainOntology.parentOffset)
    val nameOffset = data.leafIndexes(index + CompactDomainOntology.nameOffset)

    parentName(parentOffset, stringBuilder)
    stringBuilder.append(data.nodeStrings(nameOffset))
    stringBuilder.result()
  }
}

class CompactDomainOntology(protected val leafStrings: Array[String], protected val leafStringIndexes: Array[Int], protected val leafStartIndexes: Array[Int],
  protected val nodeStrings: Array[String], protected val leafIndexes: Array[Int], protected val branchIndexes: Array[Int]) extends DomainOntology {

  def size: Integer = leafIndexes.length / CompactDomainOntology.indexWidth

  protected val namerData = new CompactNamerData(nodeStrings, leafIndexes, branchIndexes)

  // This is done so that other data can be thrown away
  def getNamer(n: Integer): Namer = new CompactNamer(n, namerData)

  def getValues(n: Integer): Array[String] = {
    val start =  leafStartIndexes(n)
    val stop = leafStartIndexes(n + 1)

    start.until(stop).toArray.map(n => leafStrings(leafStringIndexes(n)))
  }

  def save(filename: String): Unit = {
    val objectOutputStream = new ObjectOutputStream(new FileOutputStream(filename))

    objectOutputStream.writeObject(leafStrings.mkString("\n"))
    objectOutputStream.writeObject(leafStringIndexes)
    objectOutputStream.writeObject(leafStartIndexes)
    objectOutputStream.writeObject(nodeStrings.mkString("\n"))
    objectOutputStream.writeObject(leafIndexes)
    objectOutputStream.writeObject(branchIndexes)
    objectOutputStream.close()
  }
}

object CompactDomainOntology {
  val indexWidth = 2
  val parentOffset = 0
  val nameOffset = 1

  def load(filename: String): CompactDomainOntology = {
    val objectInputStream = new ClassLoaderObjectInputStream(this.getClass.getClassLoader, new FileInputStream(filename))

    // This is so that text can be abandoned at the end of the block, before the array is read.
    def splitText(text: String): Array[String] = {
      val arrayBuffer = new ArrayBuffer[String]()
      val stringBuilder = new StringBuilder
      var count = 0

      for (i <- 0 until text.length) {
        val c = text(i)

        if (c == '\n') {
          arrayBuffer += stringBuilder.result()
          count += 1
          stringBuilder.clear()
        }
        else
          stringBuilder.append(c)
      }
      arrayBuffer += stringBuilder.result()
      arrayBuffer.toArray
    }

    val leafStrings = splitText(objectInputStream.readObject().asInstanceOf[String])
    val leafStringIndexes = objectInputStream.readObject().asInstanceOf[Array[Int]]
    val leafStartIndexes = objectInputStream.readObject().asInstanceOf[Array[Int]]
    val nodeStrings = splitText(objectInputStream.readObject().asInstanceOf[String])
    val leafIndexes = objectInputStream.readObject().asInstanceOf[Array[Int]]
    val branchIndexes = objectInputStream.readObject().asInstanceOf[Array[Int]]

    objectInputStream.close()
    new CompactDomainOntology(leafStrings, leafStringIndexes, leafStartIndexes, nodeStrings, leafIndexes, branchIndexes)
  }

  class CompactDomainOntologyBuilder(treeDomainOntology: TreeDomainOntology) {

    protected def append(strings: MutableHashMap[String, Int], string: String): Unit =
       if (!strings.contains(string))
          strings.put(string, strings.size)

    protected def mkParentMap(): IdentityHashMap[OntologyBranchNode, (Int, Int)] = {
      // This is myIndex, parentIndex
      val parentMap: IdentityHashMap[OntologyBranchNode, (Int, Int)] = new IdentityHashMap()

      def append(parents: Seq[OntologyBranchNode]): Int =
          if (parents.nonEmpty)
            if (parentMap.containsKey(parents.head))
              parentMap.get(parents.head)._1
            else {
              val parentIndex = append(parents.tail) // Put root on top.
              val myIndex = parentMap.size
              parentMap.put(parents.head, (myIndex, parentIndex))
              myIndex
            }
          else
            -1

      0.until(treeDomainOntology.size).foreach { i =>
        append(treeDomainOntology.getParents(i))
      }
      parentMap
    }

    protected def mkLeafStringMap(): MutableHashMap[String, Int] = {
      val stringMap: MutableHashMap[String, Int] = new MutableHashMap()

      0.until(treeDomainOntology.size).foreach { i =>
        treeDomainOntology.getValues(i).foreach(append(stringMap, _))
      }
      stringMap
    }

    protected def mkNodeStringMap(parentMap: IdentityHashMap[OntologyBranchNode, (Int, Int)]): MutableHashMap[String, Int] = {
      val stringMap: MutableHashMap[String, Int] = new MutableHashMap()

      parentMap.keySet().asScala.foreach { ontologyBranchNode =>
        append(stringMap, ontologyBranchNode.escaped)
      }
      0.until(treeDomainOntology.size).foreach { i =>
        append(stringMap, treeDomainOntology.getNode(i).escaped)
      }
      stringMap
    }

    protected def mkLeafStringAndStartIndexes(leafStringMap: MutableHashMap[String, Int]): (Array[Int], Array[Int]) = {
      val stringIndexBuffer = new ArrayBuffer[Int]()
      val startIndexBuffer = new ArrayBuffer[Int]()

      0.until(treeDomainOntology.size).foreach { i =>
        startIndexBuffer += stringIndexBuffer.size
        treeDomainOntology.getValues(i).foreach { value =>
          stringIndexBuffer += leafStringMap(value)
        }
      }
      startIndexBuffer += stringIndexBuffer.size // extra
      (stringIndexBuffer.toArray, startIndexBuffer.toArray)
    }

    protected def mkLeafIndexes(parentMap: IdentityHashMap[OntologyBranchNode, (Int, Int)], stringMap: MutableHashMap[String, Int]): Array[Int] = {
      val indexBuffer = new ArrayBuffer[Int]()

      0.until(treeDomainOntology.size).foreach { i =>
        val node = treeDomainOntology.getNode(i)

        indexBuffer += parentMap.get(node.parent)._1 // parentOffset
        indexBuffer += stringMap(node.escaped) // nameOffset
      }
      indexBuffer.toArray
    }

    protected def mkBranchIndexes(parentMap: IdentityHashMap[OntologyBranchNode, (Int, Int)], stringMap: MutableHashMap[String, Int]): Array[Int] = {
      val indexBuffer = new ArrayBuffer[Int]()
      val keysAndValues: Array[(OntologyBranchNode, (Int, Int))] = parentMap.asScala.toArray.sortBy(_._2._1)

      keysAndValues.foreach { case (branchNode, (_, parentIndex)) =>
        indexBuffer += parentIndex // parentOffset
        indexBuffer += stringMap(branchNode.escaped) // nameOffset
      }
      indexBuffer.toArray
    }

    def build(): DomainOntology = {
      val parentMap: IdentityHashMap[OntologyBranchNode, (Int, Int)] = mkParentMap()
      val leafStringMap: MutableHashMap[String, Int] = mkLeafStringMap()
      val nodeStringMap: MutableHashMap[String, Int] = mkNodeStringMap(parentMap)
      val (leafStringIndexes, leafStartIndexes) = mkLeafStringAndStartIndexes(leafStringMap)
      val leafIndexes = mkLeafIndexes(parentMap, nodeStringMap)
      val branchIndexes = mkBranchIndexes(parentMap, nodeStringMap)

      def toArray(stringMap:MutableHashMap[String, Int]): Array[String] =
          stringMap.toArray.sortBy(_._2).map(_._1)

      val leafStrings: Array[String] = toArray(leafStringMap)
      val nodeStrings: Array[String] = toArray(nodeStringMap)

      new CompactDomainOntology(leafStrings, leafStringIndexes, leafStartIndexes, nodeStrings, leafIndexes, branchIndexes)
    }
  }
}
