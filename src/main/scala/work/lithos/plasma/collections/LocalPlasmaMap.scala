package work.lithos.plasma.collections


import scorex.crypto.authds.avltree.batch.{BatchAVLProver, Insert, Lookup, PersistentBatchAVLProver, Remove, Update, VersionedAVLStorage}
import scorex.crypto.authds.{ADKey, ADValue}
import scorex.crypto.hash.{Blake2b256, Digest32}
import sigmastate.AvlTreeFlags
import supertagged.@@
import work.lithos.plasma.{ByteConversion, PlasmaParameters}

import java.io.File

/**
 * The `LocalPlasmaMap` represents an AVL Tree stored in a local persistent database. You can use `LocalPlasmaMap`'s to
 * keep track of a state after the process has already ended.
 * @param store The VersionedAVLStorage used to persist the AVL state. Must use a 32-byte digest.
 * @param flags AvlTreeFlags associated with this PlasmaMap
 * @param params PlasmaParameters used to apply certain settings to this PlasmaMap
 * @tparam K the Key type associated with this PlasmaMap
 * @tparam V the Value type associated with this PlasmaMap
 */
class LocalPlasmaMap[K, V](store: VersionedAVLStorage[Digest32], override val flags: AvlTreeFlags, override val params: PlasmaParameters)
                          (implicit val convertKey: ByteConversion[K], convertVal: ByteConversion[V]) extends LocalPlasmaBase[K, V]{
  override val storage: VersionedAVLStorage[Digest32] = store

  override val prover: PersistentBatchAVLProver[Digest32, Blake2b256.type] = {
    val avlProver = new BatchAVLProver[Digest32, Blake2b256.type](params.keySize, params.valueSizeOpt)
    PersistentBatchAVLProver.create(avlProver, store).getOrElse(throw new ProverCreationException)
  }

  override def insert(keyVals: (K, V)*): ProvenResult[V] = {
    val response = keyVals
      .map(kv =>
        OpResult (
          prover.performOneOperation(Insert(convertKey.toADKey(kv._1), convertVal.toADVal(kv._2)))
            .map(o => o.map(v => convertVal.convertFromBytes(v)))
        )
      )

    val proof = prover.generateProofAndUpdateStorage()
    ProvenResult(response, Proof(proof))
  }

  override def update(newKeyVals: (K, V)*): ProvenResult[V] = {
    val response = newKeyVals
      .map(kv =>
        OpResult (
          prover.performOneOperation(Update(convertKey.toADKey(kv._1), convertVal.toADVal(kv._2)))
            .map(o => o.map(v => convertVal.convertFromBytes(v)))
        )
      )
    val proof = prover.generateProofAndUpdateStorage()
    ProvenResult(response, Proof(proof))
  }

  override def delete(keys: K*): ProvenResult[V] = {
    val response = keys
      .map(k =>
        OpResult (
          prover.performOneOperation(Remove(convertKey.toADKey(k)))
            .map(o => o.map(v => convertVal.convertFromBytes(v)))
        )
      )
    val proof = prover.generateProofAndUpdateStorage()
    ProvenResult(response, Proof(proof))
  }

  override def lookUp(keys: K*): ProvenResult[V] = {
    val response = keys
      .map(k =>
        OpResult(
          prover.performOneOperation(Lookup(convertKey.toADKey(k)))
            .map(o => o.map(v => convertVal.convertFromBytes(v)))
        )
      )
    val proof = prover.generateProofAndUpdateStorage()
    ProvenResult(response, Proof(proof))
  }


  /**
   * Get the key-values currently associated with this PlasmaMap from persistent storage.
   *  NOTE: Not implemented yet
   * @return Sequence of Key Values from persistent storage
   */
  override def persistentItems: Seq[(K, V)] = {
    ???
  }

  /**
   * Returns persistent items as a Map
   * NOTE: Not implemented yet
   * @return Return mapping of keys to values
   */
  override def toMap: Map[K, V] = ???
}

object LocalPlasmaMap {
  def apply[K, V](store: VersionedAVLStorage[Digest32], flags: AvlTreeFlags, params: PlasmaParameters)
  (implicit convertKey: ByteConversion[K], convertVal: ByteConversion[V]): LocalPlasmaMap[K, V] = {
      new LocalPlasmaMap[K, V](store, flags, params)
  }
}
