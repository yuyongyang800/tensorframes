package org.tensorframes

import org.apache.spark.sql.types.NumericType
import org.tensorflow.framework.TensorShapeProto
import scala.collection.JavaConverters._
import org.tensorframes.Shape.DimType


/**
 * The shape of a tensor.
 * @param ds
 */
class Shape private (private val ds: Array[DimType]) extends Serializable {
  final val dims: IndexedSeq[DimType] = ds

  final def numDims = ds.length

  def hasUnknown: Boolean = ds.contains(Shape.UNKNOWN)

  override def toString: String =
    ds.map(x => if (x == Shape.UNKNOWN) { "?" } else {x.toString}).mkString("[",",","]")

  /**
   * Return a shape with an extra leading dimension.
   * @param x the dimension to add as the new head.
   */
  def prepend(x: DimType): Shape = Shape(x +: ds)

  def prepend(x: Int): Shape = Shape(x.toLong +: ds)

  /**
   * A shape with the first dimension dropped.
   */
  def tail: Shape = Shape(ds.tail)

  /**
   * Checks that this shape could be used as a more precise description of the other shape.
   */
  def checkMorePreciseThan(other: Shape): Boolean = {
    if (dims.size != other.dims.size) {
      return false
    }
    dims.zip(other.dims).forall { case (a, b) => b == Shape.UNKNOWN || b == a }
  }

  override def equals(that: Any): Boolean =
    that match {
      case that: Shape => that.ds.sameElements(ds)
      case _ => false
    }

  override def hashCode: Int = {
    var res: Long = 1
    ds.foreach(x => res += res * 31 + x)
    res.toInt
  }

  private[tensorframes] def toProto: TensorShapeProto = {
    val b = TensorShapeProto.newBuilder()
    dims.foreach { d =>
      b.addDimBuilder().setSize(d).build()
    }
    b.build()
  }
}

object Shape {
  type DimType = Long
  private val UNKNOWN: DimType = -1L
  val Unknown: Int = -1

  def empty: Shape = Shape()

  private[tensorframes] def apply(s: Array[Long]): Shape = {
    s.foreach(x => require(x >= -1, s"$s should not contain values <= -2"))
    new Shape(s.toArray)
  }

  def apply(i: Int): Shape = Shape(Array(i.toLong))

  def apply(is: Int*): Shape = Shape(is.map(_.toLong).toArray)

  private[tensorframes] def from(shape: TensorShapeProto): Shape = {
    Shape(shape.getDimList.asScala.map(_.getSize).toArray)
  }
}


/**
 * SparkTF information. This is the information generally required to work on a tensor.
 * @param shape
 * @param dataType
 */
// TODO(tjh) the types supported by TF are much richer (uint8, etc.) but it is not clear
// if they all map to a Catalyst memory representation
// TODO(tjh) support later basic structures for sparse types?
case class SparkTFColInfo(
    shape: Shape,
    dataType: NumericType) extends Serializable

/**
 * Exception thrown when the user requests tensors of high order.
 * @param s
 */
case class HighDimException(s: Shape)
  extends Exception(s"Shape $s is too high - tensorframes only supports dimensions <= 1 (vectors)")
