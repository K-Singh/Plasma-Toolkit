package work.lithos.plasma.other_tests

import java.io.File
import scala.util.Random

object FileHelper {

  def getRandomTempDir: File = {
    val dir = java.nio.file.Files.createTempDirectory("avldb_test_" + Random.alphanumeric.take(15).mkString).toFile
    dir.deleteOnExit()
    dir
  }

}
