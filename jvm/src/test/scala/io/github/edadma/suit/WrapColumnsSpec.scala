package io.github.edadma.suit

import org.scalatest.funsuite.AnyFunSuite

// Tests for the offset-preserving word-wrap behind the soft-wrapping editor. A monospace fake
// measurer (10px per char) makes the break columns exact. The invariant that matters for an
// editor is that the segments reconstruct the line — no character is dropped at a break.
class WrapColumnsSpec extends AnyFunSuite:

  private val mono: TextMeasurer = (s, _) => Size(s.length * 10.0, 16.0)
  private val style              = TextStyle(size = 16.0)

  private def cols(line: String, maxW: Double): Vector[Int] =
    RenderText.wrapColumns(line, style, maxW, mono)

  /** The segments named by a break-column vector, reconstructed from the line. */
  private def segments(line: String, starts: Vector[Int]): Vector[String] =
    starts.indices.toVector.map { j =>
      line.substring(starts(j), if j + 1 < starts.length then starts(j + 1) else line.length)
    }

  test("a line that fits is a single row"):
    assert(cols("abc", 100) == Vector(0))

  test("an empty line is a single row"):
    assert(cols("", 100) == Vector(0))

  test("an unbounded or non-positive width never wraps"):
    assert(cols("anything at all here", Double.PositiveInfinity) == Vector(0))
    assert(cols("anything at all here", 0.0) == Vector(0))

  test("it breaks greedily at a space, keeping the space on the row that ends"):
    // "hello"=50 fits a 50px row; "hello world" does not, so it breaks before "world".
    val starts = cols("hello world", 50)
    assert(starts == Vector(0, 6))
    assert(segments("hello world", starts) == Vector("hello ", "world"))

  test("it packs as many words as fit before breaking"):
    val starts = cols("ab cd ef", 50) // "ab cd"=50 fits, "ab cd ef" does not
    assert(starts == Vector(0, 6))
    assert(segments("ab cd ef", starts) == Vector("ab cd ", "ef"))

  test("a word wider than the row is hard-broken by character"):
    val starts = cols("verylongword", 30) // 3 chars per row
    assert(starts == Vector(0, 3, 6, 9))
    assert(segments("verylongword", starts) == Vector("ver", "ylo", "ngw", "ord"))

  test("the segments always reconstruct the line exactly"):
    val line   = "the quick brown fox jumps over the lazy dog"
    val starts = cols(line, 95)
    assert(segments(line, starts).mkString == line)
