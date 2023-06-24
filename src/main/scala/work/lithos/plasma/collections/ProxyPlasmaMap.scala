package work.lithos.plasma.collections

import Operations.{BatchOperation, DeleteBatch, InsertBatch, LookupBatch, UpdateBatch}

import org.bouncycastle.util.encoders.Hex
import scorex.crypto.authds.ADDigest
import scorex.crypto.authds.avltree.batch._
import scorex.crypto.hash.{Blake2b256, Digest32}
import sigmastate.AvlTreeFlags
import work.lithos.plasma.{ByteConversion, PlasmaParameters}

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.util.Try

/**
 * The `ProxyPlasmaMap` is used to prevent unnecessary writes on a Local DB. All operations may only be performed
 * after the Map has been initiated. Once initiated, the map performs operations on a temporary Plasma Map, while keeping
 * track of operations within an internal Queue. Once `commitChanges()` is called, all operations within the Queue are
 * automatically applied to the real `LocalPlasmaMap`, causing storage to be updated.
 * @param store The VersionedAVLStorage used to persist the AVL state. Must use a 32-byte digest.
 * @param flags AvlTreeFlags associated with this PlasmaMap
 * @param params PlasmaParameters used to apply certain settings to this PlasmaMap
 * @tparam K the Key type associated with this PlasmaMap
 * @tparam V the Value type associated with this PlasmaMap
 */
class ProxyPlasmaMap[K, V](store: VersionedAVLStorage[Digest32], override val flags: AvlTreeFlags, override val params: PlasmaParameters)
                          (implicit val convertKey: ByteConversion[K], convertVal: ByteConversion[V]) extends LocalPlasmaBase[K, V]{
  override val storage: VersionedAVLStorage[Digest32] = store



  private var tempMap: Option[PlasmaMap[K, V]] = None
  /**
   * Get the underlying LocalPlasmaMap from this Proxy
   */
  val localMap: LocalPlasmaMap[K, V] = new LocalPlasmaMap[K, V](store, flags, params)
  private val opQueue: mutable.Queue[BatchOperation[K, V]] = mutable.Queue.empty[BatchOperation[K, V]]

  override val prover: PersistentBatchAVLProver[Digest32, Blake2b256.type] = localMap.prover

  /**
   * @return Current digest of the temporary map if it exists, otherwise returns the digest of the local map
   */
  override def digest: ADDigest = tempMap.map(_.prover.digest).getOrElse(prover.digest)
  /**
   * Initiates the ProxyMap, allowing operations to be performed on its newly created internal temp Map
   */
  def initiate(): Unit = {
    tempMap = Some(localMap.toPlasmaMap)
  }

  /**
   * Apply insertions to the temporary tree
   * @param keyVals Key-Value pairs to insert
   * @return ProvenResult of insertion
   */
  override def insert(keyVals: (K, V)*): ProvenResult[V] = {
    val mapProver = tempMap.map(_.prover).getOrElse(throw new UninitiatedProxyException)
    val response = keyVals
      .map(kv =>
        OpResult (
          mapProver.performOneOperation(Insert(convertKey.toADKey(kv._1), convertVal.toADVal(kv._2)))
            .map(o => o.map(v => convertVal.convertFromBytes(v)))
        )
      )
    opQueue.enqueue(InsertBatch(keyVals))
    val proof = mapProver.generateProof()
    ProvenResult(response, Proof(proof))
  }

  /**
   * Apply updates to the temporary tree
   * @param newKeyVals Key-Value pairs to update
   * @return ProvenResult of update
   */
  override def update(newKeyVals: (K, V)*): ProvenResult[V] = {
    val mapProver = tempMap.map(_.prover).getOrElse(throw new UninitiatedProxyException)
    val response = newKeyVals
      .map(kv =>
        OpResult (
          mapProver.performOneOperation(Update(convertKey.toADKey(kv._1), convertVal.toADVal(kv._2)))
            .map(o => o.map(v => convertVal.convertFromBytes(v)))
        )
      )
    opQueue.enqueue(UpdateBatch(newKeyVals))
    val proof = mapProver.generateProof()
    ProvenResult(response, Proof(proof))
  }

  /**
   * Apply deletions to the temporary tree
   * @param keys Keys to delete from the tree
   * @return ProvenResult of deletion
   */
  override def delete(keys: K*): ProvenResult[V] = {
    val mapProver = tempMap.map(_.prover).getOrElse(throw new UninitiatedProxyException)
    val response = keys
      .map(k =>
        OpResult (
          mapProver.performOneOperation(Remove(convertKey.toADKey(k)))
            .map(o => o.map(v => convertVal.convertFromBytes(v)))
        )
      )
    opQueue.enqueue(DeleteBatch(keys))
    val proof = mapProver.generateProof()
    ProvenResult(response, Proof(proof))
  }

  /**
   * Apply lookUps to the temporary tree
   *
   * NOTE: LookUps only apply to the temporary map. Use `localLookUp` to lookUp keys existing in the underlying `LocalPlasmaMap``
   * @param keys Keys to lookUp in the tree
   * @return ProvenResult of lookUp
   */
  override def lookUp(keys: K*): ProvenResult[V] = {
    val mapProver = tempMap.map(_.prover).getOrElse(throw new UninitiatedProxyException)
    val response = keys
      .map(k =>
        OpResult(
          mapProver.performOneOperation(Lookup(convertKey.toADKey(k)))
            .map(o => o.map(v => convertVal.convertFromBytes(v)))
        )
      )
    val proof = mapProver.generateProof()
    ProvenResult(response, Proof(proof))
  }

  def localLookUp(keys: K*): ProvenResult[V] = {
    localMap.lookUp(keys: _*)
  }

  /**
   * Commit changes made on the temporary map to the local map, updating storage in the process.
   * The temporary map is destroyed upon successful application of operations.
   */
  def commitChanges(): Seq[ProvenResult[V]] = {
    val map = tempMap.getOrElse(throw new UninitiatedProxyException)
    val resultBuffer = ArrayBuffer.empty[ProvenResult[V]]
    var opBatch = Try(opQueue.dequeue())
    while(opBatch.isSuccess){
      resultBuffer += performBatchOp(opBatch.get)
      opBatch = Try(opQueue.dequeue())
    }
    require(map.toString == localMap.toString, s"Temporary digest ${map.toString} and local digest ${localMap.toString} were not the same, was storage" +
      " modified by a separate process?")
    tempMap = None
    resultBuffer
  }

  /**
   * Commit the next operation in the Queue. This function does not destroy the temporary map.
   * @return ProvenResult of operation, which may be verified with results created on temporary map
   */
  def commitNextOperation(): ProvenResult[V] = {
    val opBatch = opQueue.dequeue()
    performBatchOp(opBatch)
  }

  private def performBatchOp(op: BatchOperation[K, V]): ProvenResult[V] = {
    op match {
      case InsertBatch(keyVals) => localMap.insert(keyVals: _*)
      case UpdateBatch(keyVals) => localMap.update(keyVals: _*)
      case LookupBatch(keys) =>    localMap.lookUp(keys: _*)
      case DeleteBatch(keys) =>    localMap.delete(keys: _*)
    }
  }

  /**
   * Returns the hexadecimal string representation of the digest of the underlying LocalPlasmaMap
   * @return
   */
  override def toString: String = {
    Hex.toHexString(localMap.digest)
  }

  /**
   * Returns a pair of (String, Option[String]) that contains the hexadecimal representation
   * of the local digest and the temporary digest if it exists.
   */
  def digestStrings: (String, Option[String]) = toString -> tempMap.map(_.digest).map(Hex.toHexString)

  /**
   * Returns the temporary map created after initiation. Returns None if the ProxyMap has not been initiated yet.
   */
  def getTempMap: Option[PlasmaMap[K, V]] = tempMap

  /**
   * Destroys the temporary map, causing any un-committed changes on it to be lost
   */
  def dropChanges(): Unit = {
    tempMap = None
  }

  /**
   * Get the key-values currently associated with this PlasmaMap from persistent storage.
   *
   * NOTE: - Not implemented
   *
   * @return Sequence of Key Values from persistent storage
   */
  override def persistentItems: Seq[(K, V)] = ???

  /**
   * Returns persistent items as a Map
   *
   * NOTE: - Not implemented
   * @return Return mapping of keys to values
   */
  override def toMap: Map[K, V] = ???
}

object ProxyPlasmaMap {
    def apply[K, V](store: VersionedAVLStorage[Digest32], flags: AvlTreeFlags, params: PlasmaParameters)
                   (implicit convertKey: ByteConversion[K], convertVal: ByteConversion[V]): ProxyPlasmaMap[K, V] = {
      new ProxyPlasmaMap[K, V](store, flags, params)
    }
}
