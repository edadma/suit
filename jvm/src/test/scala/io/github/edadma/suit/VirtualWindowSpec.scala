package io.github.edadma.suit

import org.scalatest.funsuite.AnyFunSuite

// Tests for the virtualization windowing math — which item indices a scroll position makes
// visible. This is the guarantee that a huge list only builds a small window; the widget is
// thin wiring over it.
class VirtualWindowSpec extends AnyFunSuite:

  test("an unscrolled list shows the top window plus overscan below"):
    // 1000 items of 20px in a 200px viewport: 10 fit, +1 partial, + overscan.
    val r = VirtualWindow.visibleRange(scroll = 0, viewportH = 200, itemExtent = 20, itemCount = 1000, overscan = 3)
    assert(r.first == 0)
    assert(r.last == 0 + (200 / 20) + 1 + 3) // ceil(viewport/extent)+1 visible + overscan
    assert(r.offsetY == 0.0)
    assert(r.length < 1000) // the whole point: not all 1000

  test("scrolling advances the first visible index and offsets the block by the remainder"):
    val r = VirtualWindow.visibleRange(scroll = 50, viewportH = 200, itemExtent = 20, itemCount = 1000, overscan = 3)
    // firstFull = floor(50/20) = 2; first = 2 - overscan = 0 (clamped); offsetY = first*20 - 50 = -50
    assert(r.first == 0)
    assert(r.offsetY == -50.0)

  test("deeper scrolling keeps the window small and the offset within one item"):
    val r = VirtualWindow.visibleRange(scroll = 1000, viewportH = 200, itemExtent = 20, itemCount = 1000, overscan = 3)
    val firstFull = 50 // floor(1000/20)
    assert(r.first == firstFull - 3)
    // the first item's top relative to the viewport is within [-extent*(overscan+1), 0]
    assert(r.offsetY <= 0.0 && r.offsetY >= -20.0 * 4)
    assert(r.length <= (200 / 20) + 1 + 2 * 3)

  test("over-scroll past the end is clamped to the last full window"):
    val r = VirtualWindow.visibleRange(scroll = 1_000_000, viewportH = 200, itemExtent = 20, itemCount = 1000)
    assert(r.last == 1000)        // never past the end
    assert(r.first >= 0)
    assert(r.first < 1000)

  test("a list that fits the viewport shows everything and cannot scroll"):
    val r = VirtualWindow.visibleRange(scroll = 0, viewportH = 500, itemExtent = 20, itemCount = 5)
    assert(r.first == 0 && r.last == 5)
    assert(VirtualWindow.maxScroll(500, 20, 5) == 0.0)

  test("degenerate inputs yield an empty window rather than crashing"):
    assert(VirtualWindow.visibleRange(0, 200, 0, 100).isEmpty)   // zero extent
    assert(VirtualWindow.visibleRange(0, 200, 20, 0).isEmpty)    // no items
    assert(VirtualWindow.visibleRange(0, 0, 20, 100).isEmpty)    // no viewport

  test("maxScroll is the content overflow"):
    assert(VirtualWindow.maxScroll(viewportH = 200, itemExtent = 20, itemCount = 1000) == 20.0 * 1000 - 200)
