package work.lithos.plasma.collections

/**
 * A ProvenResult represents the two components returned after every PlasmaMap operation
 * @param response A sequence of `OpResult[V]`, which holds the resulting Values outputted from the operation.
 *                 NOTE: Some operations like insertions may return `OpResult(Success(None))` upon successful application.
 * @param proof The `Proof` associated with the applied operation.
 * @tparam V The Value type associated with the PlasmaMap the operation was applied to
 */
case class ProvenResult[V](response: Seq[OpResult[V]], proof: Proof)