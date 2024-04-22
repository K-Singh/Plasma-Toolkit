package work.lithos.plasma.other_tests

import com.google.common.primitives.Longs
import work.lithos.plasma.ByteConversion.convertsLongKey
import org.bouncycastle.util.encoders.Hex
import org.ergoplatform.appkit.impl.ErgoTreeContract
import org.ergoplatform.appkit.{Address, Eip4Token, InputBox, Parameters}
import org.scalatest.funsuite.AnyFunSuite
import scorex.crypto.authds.avltree.batch.VersionedLDBAVLStorage
import scorex.crypto.hash.{Blake2b256, Digest32}
import scorex.db.LDBVersionedStore
import sigma.data.AvlTreeFlags
import work.lithos.plasma.PlasmaParameters
import work.lithos.plasma.collections.ProxyPlasmaMap
import work.lithos.plasma.other_tests.PlasmaMapLevelDBSuite.{TestLong, mockData}

import java.util.logging.FileHandler
import scala.jdk.CollectionConverters.seqAsJavaListConverter
import scala.util.Try

class ProxyMapSuite extends AnyFunSuite {
  var swayStore: LDBVersionedStore = _
  var avlStorage: VersionedLDBAVLStorage[Digest32] = _
  var map: ProxyPlasmaMap[Long, TestLong] = _
  var lookUpBox: InputBox = _
  var digestString: String = _


  test("Create ProxyPlasmaMap") {
    println("Creating ProxyPlasmaMap")
    swayStore = new LDBVersionedStore(FileHelper.getRandomTempDir, 10)
    avlStorage = new VersionedLDBAVLStorage[Digest32](swayStore, PlasmaParameters.default.toNodeParams)(Blake2b256)
    map = new ProxyPlasmaMap[Long, TestLong]( avlStorage, AvlTreeFlags.AllOperationsAllowed, PlasmaParameters.default)
    println(s"Digest ${Hex.toHexString(map.digest)}")
  }

  test("Throw exception when uninitiated"){
    println("Checking that map is uninitiated")
    require(Try(map.insert(mockData: _*)).isFailure, "Uninitiated map did not throw exception!")
  }

  test("Initiate map"){
    println("Initiating ProxyMap")
    map.initiate()
  }

  test("Insert into initiated map") {
    println("Inserting data into initiated map")
    val result = map.insert(mockData.slice(0, 100): _*)
   // println(s"Proof: ${result.proof}")
    println(s"Persistent Digest: ${Hex.toHexString(map.digest)}")
    println(s"Temporary Digest: ${Hex.toHexString(map.getTempMap.get.digest)}")
   // println(s"Manifest: ${localMap.toPlasmaMap.makeManifest.toHexString}")
    println(s"Persistent Tree height: ${map.prover.height}")
    println(s"Temporary Tree height: ${map.getTempMap.get.prover.rootNodeHeight}")
    require(Hex.toHexString(map.localMap.digest) != Hex.toHexString(map.getTempMap.get.digest), "Digests were equal despite" +
      " not un-committed changes!")
  }

  test("Commit changes to persistent storage"){
    println("Committing changes to persistent storage")
    val tempDigest = map.getTempMap.get.digest
    map.commitChanges()
    println(s"Persistent Digest: ${Hex.toHexString(map.digest)}")
    println(s"Temporary Digest: ${Hex.toHexString(tempDigest)}")
    require(Hex.toHexString(map.digest) == Hex.toHexString(tempDigest), "Digests were not equal despite" +
      " not committing changes!")
  }

  test("Validate that temp map was destroyed"){
    println("Validating temp map was destroyed")
    require(map.getTempMap.isEmpty, "Temp map was not destroyed after committing changes!")
  }

  test("Re-initiate map"){
    println("Re-initiating map")
    map.initiate()
    println(s"Persistent Digest: ${Hex.toHexString(map.digest)}")
    println(s"Temporary Digest: ${Hex.toHexString(map.getTempMap.get.digest)}")
    require(map.getTempMap.get.toString == map.toString, "Re-initiated map did not have correct digest")
  }

  test("Update map"){
    println("Updating existing data in map")
    val updates = mockData.slice(0, 100).map(m => m._1 -> m._2.copy(i = m._2.i + 1))
    map.update(updates: _*)
    digestString = map.getTempMap.get.toString
    println(s"Persistent Digest: ${Hex.toHexString(map.digest)}")
    println(s"Temporary Digest: ${Hex.toHexString(map.getTempMap.get.digest)}")
  }

  test("Insert additional data into map"){
    println("Inserting additional data into map")
    val inserts = mockData.slice(101, 200)
    map.insert(inserts: _*)
    println(s"Persistent Digest: ${Hex.toHexString(map.digest)}")
    println(s"Temporary Digest: ${Hex.toHexString(map.getTempMap.get.digest)}")
    println(s"Post-Update Digest: ${digestString}")
  }

  test("Commit only the update"){
    println("Committing update changes")
    map.commitNextOperation()
    println(s"Persistent Digest: ${Hex.toHexString(map.digest)}")
    println(s"Temporary Digest: ${Hex.toHexString(map.getTempMap.get.digest)}")
    println(s"Post-Update Digest: ${digestString}")

    require(digestString == Hex.toHexString(map.localMap.digest), "Persistent digest did not match update digest!")
    require(map.getTempMap.isDefined, "Temporary map was destroyed after committing one change!")
  }

  test("Drop insert changes by destroying temp map"){
    println("Dropping un-committed insert changes")
    map.dropChanges()
    println(s"Persistent Digest: ${Hex.toHexString(map.digest)}")
    println(s"Post-Update Digest: ${digestString}")

    require(map.toString == digestString, "Digest changed despite dropping changes!")
  }

  test("Ensure temp map is destroyed"){
    println("Validating that temporary map is destroyed")
    require(map.getTempMap.isEmpty, "Temp map not destroyed despite dropping changes")
  }

//  test("Convert to PlasmaMap"){
//    val map = localMap.toPlasmaMap
//    val manifest = map.getManifest(255)
//    println(s"Digest: ${manifest.digestString}")
//    val manString = manifest.toHexStrings
//    println(s"Manifest: ${manString._1}")
//
//    for(s <- manString._2) println(s"SubTree: ${s}")
//
//    println(s"Num subtrees: ${manString._2.length}")
//  }


}



