package work.lithos.plasma

import com.google.common.primitives.Longs
import org.bouncycastle.util.encoders.Hex
import org.ergoplatform.appkit.Address
import org.ergoplatform.sdk.ErgoId
import scorex.crypto.authds.{ADDigest, ADKey, ADValue}
import sigmastate.Values
import sigmastate.serialization.ErgoTreeSerializer
import supertagged.@@

trait ByteConversion[T] {

  def convertToBytes(t: T): Array[Byte]
  def convertFromBytes(bytes: Array[Byte]): T

  def toKey(t: T): PlasmaKey = PlasmaKey(convertToBytes(t))
  def toVal(t: T): PlasmaVal = PlasmaVal(convertToBytes(t))

  def toADKey(t: T):    ADKey    = ADKey @@ convertToBytes(t)
  def toADVal(t: T):    ADValue  = ADValue @@ convertToBytes(t)
  def toADDigest(t: T): ADDigest = ADDigest @@ convertToBytes(t)

  def ofKey(pKey: PlasmaKey): T = convertFromBytes(pKey.key)
  def ofVal(pVal: PlasmaVal): T = convertFromBytes(pVal.value)

  def toHexString(t: T): String = Hex.toHexString(convertToBytes(t))

}

object ByteConversion {
  implicit val convertsString: ByteConversion[String] = new ByteConversion[String] {
    override def convertToBytes(t: String): Array[Byte] = Hex.decode(t)

    override def convertFromBytes(bytes: Array[Byte]): String = Hex.toHexString(bytes)
  }

  /**
   * Converts a `Long` into a `Array[Byte]` with a length of 32 bytes. The first 8 bytes represent the serialized `Long`,
   * while the remaining 24 bytes are simply 0's
   */
  implicit val convertsLongKey: ByteConversion[Long] = new ByteConversion[Long] {
    override def convertToBytes(t: Long): Array[Byte] = Longs.toByteArray(t) ++ Array.fill(24)(0.toByte)

    override def convertFromBytes(bytes: Array[Byte]): Long = Longs.fromByteArray(bytes.slice(0, 8))
  }

  /**
   * Converts a `Long` into the standard `Array[Byte]` of length 8.
   */
  implicit val convertsLongVal: ByteConversion[Long] = new ByteConversion[Long] {
    override def convertToBytes(t: Long): Array[Byte] = Longs.toByteArray(t)

    override def convertFromBytes(bytes: Array[Byte]): Long = Longs.fromByteArray(bytes)
  }

  implicit val convertsId: ByteConversion[ErgoId] = new ByteConversion[ErgoId] {
    override def convertToBytes(t: ErgoId): Array[Byte] = t.getBytes

    override def convertFromBytes(bytes: Array[Byte]): ErgoId = new ErgoId(bytes)
  }

  implicit val convertsErgoTree: ByteConversion[Values.ErgoTree] = new ByteConversion[Values.ErgoTree] {
    override def convertToBytes(t: Values.ErgoTree): Array[Byte] = t.bytes

    override def convertFromBytes(bytes: Array[Byte]): Values.ErgoTree = ErgoTreeSerializer.DefaultSerializer.deserializeErgoTree(bytes)
  }

  implicit val convertsArrBytes: ByteConversion[Array[Byte]] = new ByteConversion[Array[Byte]] {
    override def convertToBytes(t: Array[Byte]): Array[Byte] = t

    override def convertFromBytes(bytes: Array[Byte]): Array[Byte] = bytes
  }

}



