package io.github.edadma.suit

// The device-pixel geometry of a window — the bridge between the logical coordinate space the
// UI is laid out in and the physical pixels it must be rasterised into. On a HiDPI ("Retina")
// display the two differ: an 800×600 logical window has, say, a 1600×1200 pixel backbuffer at
// a 2× scale. suit keeps layout, hit-testing, and every coordinate the application sees in
// logical units, and pushes the difference down to a single canvas scale, so the same tree
// draws crisp on any display without the widgets knowing the display's density.
//
// This carries no Cairo or SDL types, so the (trivial but easy-to-get-wrong) ratio arithmetic
// is settled here and verified off-device; the runtime just consumes the result.
final case class DeviceSurface(width: Int, height: Int, scaleX: Double, scaleY: Double)

object DeviceSurface:
  /** Derive the backing-surface size and draw scale from a window's logical size and its actual
    * pixel size (what SDL reports as the window's size *in pixels*). The surface takes the pixel
    * dimensions — it is the real backbuffer — and the scale is the pixel-to-logical ratio on
    * each axis, so drawing the tree in logical coordinates through a canvas scaled by it lands
    * every edge on physical pixels. When the two sizes match (an ordinary 1× display) the scale
    * is 1 and the surface equals the window, leaving the non-HiDPI path byte-for-byte unchanged.
    * Degenerate inputs — a zero logical or pixel dimension, as can happen before a window is
    * shown — fall back to the logical size at scale 1 so there is always somewhere to draw. */
  def from(logicalWidth: Int, logicalHeight: Int, pixelWidth: Int, pixelHeight: Int): DeviceSurface =
    val lw = math.max(1, logicalWidth)
    val lh = math.max(1, logicalHeight)
    val pw = if pixelWidth > 0 then pixelWidth else lw
    val ph = if pixelHeight > 0 then pixelHeight else lh
    DeviceSurface(pw, ph, pw.toDouble / lw, ph.toDouble / lh)
