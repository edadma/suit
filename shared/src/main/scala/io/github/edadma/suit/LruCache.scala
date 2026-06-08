package io.github.edadma.suit

import scala.collection.mutable

// A small bounded least-recently-used cache. suit uses it to hold expensive-to-build,
// rarely-changing render artifacts — the blurred shadow surfaces the Cairo backend would
// otherwise re-rasterise every frame — keyed by the inputs that determine their pixels, so a
// shape that recurs frame after frame is built once and reused.
//
// It is its own (pure) type so the eviction policy is unit-tested off-device, even though the
// values it ends up holding on the native backend are Cairo surfaces. `onEvict` lets the holder
// release a value pushed out of the cache (destroy the native surface); the default is a no-op
// for values that need no teardown.
final class LruCache[K, V](capacity: Int, onEvict: V => Unit = (_: V) => ()):
  require(capacity >= 1, "LruCache capacity must be >= 1")

  // Insertion order is recency order: the head is the least-recently-used entry, the last is the
  // most-recent. A hit re-inserts its entry at the end to mark it fresh.
  private val entries = mutable.LinkedHashMap.empty[K, V]

  def size: Int                 = entries.size
  def contains(key: K): Boolean = entries.contains(key)

  /** Look `key` up, marking it most-recently-used on a hit. */
  def get(key: K): Option[V] =
    entries.remove(key) match
      case Some(v) => entries.update(key, v); Some(v)
      case None    => None

  /** Return the value for `key`, building and inserting it with `create` on a miss. Either way
    * the entry is the most-recently-used afterward; a miss may evict the least-recent entries to
    * stay within capacity, calling `onEvict` on each. */
  def getOrElseUpdate(key: K, create: => V): V =
    get(key) match
      case Some(v) => v
      case None =>
        val v = create
        entries.update(key, v)
        while entries.size > capacity do
          val (k, evicted) = entries.head
          entries.remove(k)
          onEvict(evicted)
        v

  /** Drop everything, running `onEvict` on each value — for tearing the cache down. */
  def clear(): Unit =
    entries.valuesIterator.foreach(onEvict)
    entries.clear()
