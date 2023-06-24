package work.lithos.plasma

import scorex.crypto.authds.avltree.batch.NodeParameters

/**
 * Parameters that can be applied to a `PlasmaMap`
 * @param keySize Size of the key, in bytes
 * @param valueSizeOpt Optional value size to set for the `PlasmaMap`
 */
case class PlasmaParameters(keySize: Int, valueSizeOpt: Option[Int]){
  def toNodeParams: NodeParameters = {
    NodeParameters(keySize, valueSizeOpt, keySize)
  }
}

object PlasmaParameters {
  /**
   * Default `PlasmaParameters`, 32 byte key sizes and an unrestricted value size
   */
  lazy val default: PlasmaParameters = PlasmaParameters(32, None)
}