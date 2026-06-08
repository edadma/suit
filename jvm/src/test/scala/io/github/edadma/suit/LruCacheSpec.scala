package io.github.edadma.suit

import org.scalatest.funsuite.AnyFunSuite
import scala.collection.mutable

// Headless tests for the bounded LRU cache behind the shadow-surface cache. The eviction policy
// is the part that matters (a wrong policy thrashes the costly blur it exists to avoid), so it is
// pinned here off-device against a plain Int->String cache with an eviction log.
class LruCacheSpec extends AnyFunSuite:

  test("a miss builds and caches; a hit returns without rebuilding"):
    var builds = 0
    val c      = new LruCache[Int, String](2)
    assert(c.getOrElseUpdate(1, { builds += 1; "a" }) == "a")
    assert(c.getOrElseUpdate(1, { builds += 1; "a" }) == "a")
    assert(builds == 1) // the second call was a hit
    assert(c.size == 1)

  test("exceeding capacity evicts the least-recently-used entry"):
    val evicted = mutable.ArrayBuffer.empty[String]
    val c       = new LruCache[Int, String](2, evicted += _)
    c.getOrElseUpdate(1, "a")
    c.getOrElseUpdate(2, "b")
    c.getOrElseUpdate(3, "c") // over capacity → evicts the oldest (1 -> "a")
    assert(evicted.toList == List("a"))
    assert(!c.contains(1))
    assert(c.contains(2) && c.contains(3))

  test("a hit refreshes recency so it is not the next evicted"):
    val evicted = mutable.ArrayBuffer.empty[String]
    val c       = new LruCache[Int, String](2, evicted += _)
    c.getOrElseUpdate(1, "a")
    c.getOrElseUpdate(2, "b")
    c.get(1)                  // touch 1 → now 2 is the least-recently-used
    c.getOrElseUpdate(3, "c") // evicts 2, not 1
    assert(evicted.toList == List("b"))
    assert(c.contains(1) && c.contains(3) && !c.contains(2))

  test("clear runs onEvict for every value and empties the cache"):
    val evicted = mutable.ArrayBuffer.empty[String]
    val c       = new LruCache[Int, String](4, evicted += _)
    c.getOrElseUpdate(1, "a")
    c.getOrElseUpdate(2, "b")
    c.clear()
    assert(evicted.toSet == Set("a", "b"))
    assert(c.size == 0)

  test("capacity must be at least one"):
    assertThrows[IllegalArgumentException](new LruCache[Int, String](0))
