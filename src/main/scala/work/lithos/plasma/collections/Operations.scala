package work.lithos.plasma.collections

object Operations {

  sealed trait BatchOperation[K, V]

  case class InsertBatch[K, V](keyVals: Seq[(K, V)]) extends BatchOperation[K, V]
  case class UpdateBatch[K, V](keyVals: Seq[(K, V)]) extends BatchOperation[K, V]
  case class LookupBatch[K, V](keys: Seq[K]) extends BatchOperation[K, V]
  case class DeleteBatch[K, V](keys: Seq[K]) extends BatchOperation[K, V]
}
