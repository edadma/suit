package io.github.edadma.suit

import org.scalatest.funsuite.AnyFunSuite

// The box-blur convolution that softens a drop shadow. The runtime runs it over a Cairo image
// surface's native buffer; here it runs over a plain array through the same ByteSurface seam, so
// the arithmetic is pinned exactly. Pixels are premultiplied ARGB32 — four bytes b, g, r, a per
// pixel in little-endian order — but the blur treats the channels as independent values, which
// is all these checks exercise.
class BoxBlurSpec extends AnyFunSuite:

  private final class ArraySurface(val width: Int, val height: Int) extends ByteSurface:
    val stride            = width * 4
    val data              = new Array[Byte](stride * height)
    def get(o: Int): Int  = data(o) & 0xff
    def set(o: Int, v: Int): Unit = data(o) = v.toByte

    private def off(x: Int, y: Int) = y * stride + x * 4
    def alpha(x: Int, y: Int): Int  = get(off(x, y) + 3)
    def red(x: Int, y: Int): Int    = get(off(x, y) + 2)
    def green(x: Int, y: Int): Int  = get(off(x, y) + 1)
    def blue(x: Int, y: Int): Int   = get(off(x, y))
    def setAlpha(x: Int, y: Int, v: Int): Unit = set(off(x, y) + 3, v)
    def setRed(x: Int, y: Int, v: Int): Unit   = set(off(x, y) + 2, v)
    def fillAlpha(v: Int): Unit =
      for y <- 0 until height; x <- 0 until width do setAlpha(x, y, v)

  test("a non-positive radius or pass count leaves the buffer untouched"):
    val s = new ArraySurface(7, 7)
    s.setAlpha(3, 3, 255)
    BoxBlur.blur(s, 0, 3)
    assert(s.alpha(3, 3) == 255 && s.alpha(2, 3) == 0)
    BoxBlur.blur(s, 2, 0)
    assert(s.alpha(3, 3) == 255 && s.alpha(2, 3) == 0)

  test("a single impulse spreads into the surrounding window"):
    val s = new ArraySurface(7, 7)
    s.setAlpha(3, 3, 255)
    BoxBlur.blur(s, 1, 1)
    // One pass of radius 1 averages over a 3-wide window each way: 255/3 = 85 along the row,
    // then 85/3 = 28 down each column, so the impulse becomes a uniform 3x3 block of 28.
    for dy <- -1 to 1; dx <- -1 to 1 do assert(s.alpha(3 + dx, 3 + dy) == 28)
    // Nothing leaks past the window.
    assert(s.alpha(1, 3) == 0 && s.alpha(5, 3) == 0 && s.alpha(0, 0) == 0)

  test("the blur is symmetric about the impulse"):
    val s = new ArraySurface(9, 9)
    s.setAlpha(4, 4, 255)
    BoxBlur.blur(s, 2, 1)
    assert(s.alpha(2, 4) == s.alpha(6, 4)) // mirror across the column
    assert(s.alpha(4, 2) == s.alpha(4, 6)) // mirror across the row
    assert(s.alpha(3, 4) == s.alpha(5, 4))

  test("a larger radius spreads the impulse farther"):
    val s = new ArraySurface(11, 11)
    s.setAlpha(5, 5, 255)
    BoxBlur.blur(s, 2, 1)
    // Radius 2 reaches two pixels out and no farther in a single pass.
    assert(s.alpha(3, 5) > 0 && s.alpha(7, 5) > 0)
    assert(s.alpha(2, 5) == 0 && s.alpha(8, 5) == 0)

  test("a uniform interior is unchanged by the blur"):
    val s = new ArraySurface(15, 15)
    s.fillAlpha(200)
    BoxBlur.blur(s, 2, 3)
    // The window average of a constant field is the constant, so a pixel well clear of the
    // zero-padded border (radius*passes = 6 away) keeps its value exactly.
    assert(s.alpha(7, 7) == 200)

  test("channels blur independently"):
    val s = new ArraySurface(7, 7)
    s.setRed(3, 3, 255)
    BoxBlur.blur(s, 1, 1)
    // The red impulse spreads exactly as an alpha one would, and the other channels stay zero —
    // no cross-channel bleed.
    assert(s.red(3, 3) == 28 && s.red(2, 3) == 28)
    assert(s.alpha(3, 3) == 0 && s.green(3, 3) == 0 && s.blue(3, 3) == 0)
