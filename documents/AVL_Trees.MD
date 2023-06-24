# AVL Trees / Plasma In ErgoScript: Basics, Tips, and Design Patterns

In this document we'll go through the basics of how AVL Trees may be used in ErgoScript contracts,
along with some tricks and design patterns that can help while implementing them.
If you have your own design patterns or ideas related to AVL Trees, feel free to make a pull request
and add to this document!

# Basics

What exactly is Plasma? Plasma involves the usage of an *Authenticated Dictionary*, essentially a
hash-based data structure that keeps track of the changes made to it so that *any* change made to the dictionary
is visible. Dictionaries use a pattern of *keys* and *values*. Each key is unique, and providing
a key to the tree allows one to receive the value associated with that unique key. In Ergo, *Authenticated Dictionaries* are built using a data structure called an *AVL Tree*.
If you have prior experience with Merkle Trees, then Ergo's implementation of AVL Trees will be quite similar,
except that AVL Trees are much more efficient. With that in mind, it should also be noted that AVL Trees
in Ergo use Blake2b256 hashing to construct the tree. This means ALL keys in the tree must be 32 bytes.


As stated above, Authenticated Dictionaries keep track of the changes made to them. What this means is that every unique state of the AVL Tree has some unique value associated
with it, and this value can be used to determine information about the tree, the data stored inside of it,
and whether changes have been made to it. This value is called the *digest*, and it represents
the current state of the keys and values present within the tree. By keeping track of the digest, you can
verify whether a tree's state has changed or not.

For example, let's say a tree is made, and operations
are performed on it until it's digest equals the following value:
`c44054a97379f4607b41e6b20cff4b5e266d892221666d5f1a1faec78fc8a7ec09`. Then a few months later,
I want to check if the tree has been untouched since the time it was interacted with. I can verify that no
information in my tree has changed by simply verifying that the tree's current digest is equal to the digest above.


Another important point about digests is that they are simply Blake2b256 hashes. This means each digest is simply 32 bytes,
but it can hold data about the *entire* tree. You can therefore store hundreds of thousands of bytes of data
within the tree, all of which can be uniquely represented by the single 32 byte digest string.

(NOTE: Digests in ErgoScript are actually 32 bytes + 1 byte for the tree height. In general,
the AvlTree ErgoScript type handles this for you, so it's not too much of an issue)


## Serialization and Proofs

[ErgoScript LangSpec AVL Tree Methods](https://github.com/ScorexFoundation/sigmastate-interpreter/blob/develop/docs/LangSpec.md#avltree)

When dealing with keys and values in an AVL Tree, it's always important to remember that actual types
are not stored. All keys and values stored in an AVL Tree are simply byte arrays. This is made clear in the Plasma Library
due to the necessity of the `ByteConversion` class. In ErgoScript, you can also see that the majority of the relevant methods for AVL Trees involve
usage of the `Coll[Byte]` type. 

Another important point in regard to AVL Trees is the necessity of *Proofs*. Proofs represent parts of the tree
that were accessed while performing some sort of operation. In the Plasma library, all operations performed
on the tree return a proof. This is extremely important! Why? Because using just a Proof and the digest,
one can simulate any operations done on the tree in the on-chain ErgoScript code. Using just a small
32 byte footprint (the digest), the information for the operation, and an associated proof, you can perform
complex operations on-chain that allow you to mirror an on-chain tree's state with your off-chain tree's state.

## Operations
There are four main operations that exist in both on-chain and off-chain AVL Trees. 

Off-chain:
- insert: Insert a sequence of Key-Value pairs into the tree (AKA PlasmaMap)
- update: Update an existing key by providing it as a pair with its new value
- delete: Delete a key from the tree, using just the key itself
- lookUp: Lookup the value associated with a key, using just the key itself

Each off-chain operation returns the proof needed to perform the same operation on-chain.

The same operations are possible in ErgoScript, with slightly different names.

On-chain:
- insert: Insert a collection of `(Coll[Byte], Coll[Byte])`. Element 1 of the pair represents new unique serialized key, while element two represents the serialized value associated with the key.
- update: Update key-value pairs by providing a collection of `(Coll[Byte], Coll[Byte])`. Element 1 represents a serialized key that already exists in the tree. Element 2 represents the new value to associate with the key.
- remove: Remove key-value pairs from the tree by providing a collection of `Coll[Byte]`, where each `Coll[Byte]` is a serialized key that already exists in the tree.
- getMany: Using a provided collection of `Coll[Byte]`s representing serialized keys, find the values associated with the keys in the tree. You may use `.get()` to obtain just a single value using a single key.

Other operations technically exist in the off-chain code like `InsertOrUpdate`. This operation is not provided in `Plasma-Toolkit` because no analogous 
operation exists in ErgoScript. As stated earlier, ALL on-chain operations require a tree proof in order to be performed correctly.
The tree proof provided MUST be the same proof you obtained from your off-chain code while performing the same operation. (With one exception we will go into later).
Providing an invalid tree proof will cause your contract to fail in all circumstances, so be sure to use
the right proof inside your ErgoScript. Also remember that each operation (other than lookups / getMany ) changes the state of the tree,
and therefore change the tree's digest upon successful application. 

## Example Script 1:
Here is a simple script verifying that all keys given in context var 0 exist in the tree in R4
```scala
val keyExistScript: String =
    """
      | {
      |   val tree  = SELF.R4[AvlTree].get
      |   val keys  = getVar[Coll[Coll[Byte]]](0).get
      |   val proof = getVar[Coll[Byte]](1).get
      |   
      |   val keysExist = tree.getMany(keys, proof).forall{
      |     (o: Option[Coll[Byte]]) =>
      |       o.isDefined
      |   }
      |   
      |   sigmaProp(keysExist)
      | }
      |""".stripMargin
```

## Example Script 2:
Here is a simple script verifying that key-values are inserted into a tree, and that the outputted box
holds the tree's next state after applying the insertion.
```scala
val insertWithNextStateScript: String =
    """
      | {
      |   val tree     = SELF.R4[AvlTree].get
      |   val keyVals  = getVar[Coll[ (Coll[Byte], Coll[Byte]) ]](0).get
      |   val proof    = getVar[Coll[Byte]](1).get
      |   
      |   val nextTree: Option[AvlTree] = tree.insert(keyVals, proof)
      |   
      |   // This will fail if the operation failed or the proof is incorrect due to calling .get on the Option
      |   val outputDigest: Coll[Byte] = nextTree.get.digest 
      |   
      |   val insertionPerformed = OUTPUTS(0).R4[AvlTree].get.digest == outputDigest
      |   val sameScript = OUTPUTS(0).propositionBytes == SELF.propositionBytes
      |   
      |   sigmaProp(sameScript && insertionPerformed)
      | }
      |""".stripMargin
```

# Tips and Info

Here are some important tips and info that are relevant when dealing with AVL Trees.

Computational cost of performing AVL Tree operations is quite low! You can lookup hundreds
of values for very little computation in your contract. In fact, the biggest limiting factor
for operations is information size (Size of key or key-value pair collections used in operations)
and proof size. Proof size can be especially large, as it grows in relation to the size of your tree.
For example, one operation I performed on-chain required an 8000 byte size proof! This is quite big,
the proof alone wouldn't even fit in a box's register (Due to the 4096 box size limit). There
are however, a few ways I've found to deal with this.

For one, always use *Context Variables* when dealing with AVL Trees. Context variables are inserted
into the box at the time of the transaction. Since they are not tied to the box itself, the storage
limit is no longer the 4096 bytes box limit, but instead the 95kb transaction size limit. This in itself
is a huge increase in the amount of data that can be interacted with. By using context vars, you can feasibly
perform operations on huge trees.

Another important point is that AVL Trees automatically sort the nodes inside them. What this means
is that your *worst case* proof size is always equal to your operation but performed on random key-values.
Any bias at all inside of your operation reduces the size of the proof. In fact, you can create very
efficient proof sizes by performing operations on sorted key-values. Because the key-values are pre-sorted,
the proof needed is very small (Less of the tree needs to be traversed, because the key-values needed
are already right next to each other). One simple method to do this in Scala is the following:

```scala
import org.ergoplatform.sdk.ErgoId
implicit val ergoIdKeys: Seq[ErgoId] // Example for key collection type, can be anything as long as it can be serialized to bytes

// These sorted keys will allow for efficient proof generation when doing lookups
val sortedKeys = ergoIdKeys.sortBy(id => BigInt.apply(id.getBytes))
```

Finally, one other extremely important fact is that you can use the **same** proof for both lookups
and updates involving the same keys. This is extremely useful,
by requiring only one proof you can greatly decrease the amount of data space used in your transaction!

# Design Patterns
# COMING SOON :)
