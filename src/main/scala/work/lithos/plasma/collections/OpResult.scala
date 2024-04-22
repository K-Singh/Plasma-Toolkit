package work.lithos.plasma.collections


import org.ergoplatform.appkit.{ErgoType, ErgoValue}
import org.ergoplatform.sdk.JavaHelpers.JByteRType
import org.ergoplatform.sdk.JavaHelpers.UniversalConverter
import sigma.Colls
import sigma.Coll
import work.lithos.plasma.ByteConversion

import java.lang
import scala.util.Try
import org.ergoplatform.appkit.scalaapi.Iso

/**
 * A wrapper class that holds the result of applying an operation on a certain key or key-value pair.
 * @param tryOp A `Try[ Option[V] ]`. In general the `Try` must always evaluate to `Success`, while the option may evaluate
 *              to `None` or `Some[V]` depending on the operation.
 * @param converter The ByteConversion needed to serialize type `V` into a byte array
 * @tparam V The Value associated with the `PlasmaMap` that the operation was performed on
 */
case class OpResult[V](tryOp: Try[Option[V]])(implicit converter: ByteConversion[V]) {
  lazy val ergoType: ErgoType[java.lang.Byte] = ErgoType.byteType()

  /**
   * Shorthand for opResult.tryOp.get.get
   * Will throw an exception if either the Try or the Option failed to return successfully.
   * @return
   * The underlying value returned by the operation
   */
  def get: V = tryOp.get.get

  def ergoValue: ErgoValue[Coll[java.lang.Byte]] = {
    ErgoValue.of(Colls.fromArray(
      converter.convertToBytes(tryOp.getOrElse(throw new NoResultException).getOrElse(throw new NoResultException)))
      .map(Iso.isoByte.toJava)
      , ergoType)
  }

  def toHexString: Option[String] = {
    tryOp.map(o => o.map(r => converter.toHexString(r).toLowerCase)).toOption.flatten
  }
}
