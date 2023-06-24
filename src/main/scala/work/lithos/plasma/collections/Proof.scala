package work.lithos.plasma.collections

import org.bouncycastle.util.encoders.Hex

import org.ergoplatform.appkit.{ErgoType, ErgoValue}
import scorex.crypto.authds
import sigmastate.eval.Colls
import special.collection.Coll
import supertagged.@@

import java.lang

/**
 * A Proof represents a the parts of the tree that were reconstructed during the operation. It may be used to mirror
 * off-chain operations with their on-chain counterparts.
 * @param bytes Proof bytes
 */
case class Proof(bytes: Array[Byte]){
  lazy val ergoType: ErgoType[java.lang.Byte] = ErgoType.byteType()
  def ergoValue: ErgoValue[Coll[java.lang.Byte]] = ErgoValue.of(bytes)

  def toADProof: Array[Byte] @@ authds.SerializedAdProof.Tag = authds.SerializedAdProof @@ bytes

  /**
   * Slice the proof into multiple shards of equal length
   * @param shardSize Size of each proof shard, except for the last shard which may be smaller
   * @return Sequence of proof shards that may be appended to form the full proof
   */
  def slice(shardSize: Int): Seq[Proof] = {
    bytes.sliding(shardSize).map(Proof.apply).toSeq
  }

  override def toString: String = Hex.toHexString(bytes).toLowerCase
}
