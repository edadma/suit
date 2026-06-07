package io.github.edadma.suit

import org.scalatest.funsuite.AnyFunSuite

// The device-pixel ratio arithmetic that drives HiDPI rendering. The runtime that consumes it
// (Suit.run, native) sizes the Cairo backbuffer to `width`/`height` and scales the context by
// `scaleX`/`scaleY`; these checks pin the mapping the runtime can't be unit-tested against.
class DeviceSurfaceSpec extends AnyFunSuite:

  test("a 1x display leaves the surface equal to the window at scale 1"):
    assert(DeviceSurface.from(800, 600, 800, 600) == DeviceSurface(800, 600, 1.0, 1.0))

  test("a 2x display doubles the surface and scales drawing 2x"):
    assert(DeviceSurface.from(800, 600, 1600, 1200) == DeviceSurface(1600, 1200, 2.0, 2.0))

  test("a fractional ratio is carried through on each axis"):
    val d = DeviceSurface.from(800, 600, 1200, 900)
    assert(d.width == 1200 && d.height == 900)
    assert(d.scaleX == 1.5 && d.scaleY == 1.5)

  test("the two axes scale independently"):
    val d = DeviceSurface.from(100, 200, 200, 200)
    assert(d == DeviceSurface(200, 200, 2.0, 1.0))

  test("a zero pixel size (window not yet shown) falls back to the logical size at scale 1"):
    assert(DeviceSurface.from(640, 480, 0, 0) == DeviceSurface(640, 480, 1.0, 1.0))

  test("a degenerate zero logical dimension is clamped so the scale stays finite"):
    val d = DeviceSurface.from(0, 0, 100, 100)
    assert(d == DeviceSurface(100, 100, 100.0, 100.0))
