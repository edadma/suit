package io.github.edadma.suit

// The display-density seam — the one number application code needs that the logical coordinate
// space deliberately hides.
//
// suit lays out, hit-tests, and reports every coordinate in logical units, pushing the
// difference between logical and physical pixels down to a single canvas scale (see
// [[DeviceSurface]]); a widget never has to know the display's density. But code that allocates
// its *own* pixel buffer — the backing image surface of a `surface(...)` widget, say — does need
// it, otherwise it draws at logical resolution and the blit upscales to a soft result on a HiDPI
// ("Retina") display.
//
// The runtime installs the window's scale here at startup; a headless or JVM context leaves the
// default of 1, so the same application code reads a sensible value off-device. It is a
// process-global injectable like the [[Repaint]] and scheduler seams, kept here so the shared
// code that reads it stays host-agnostic.
object DevicePixelRatio:
  /** The window's device-pixel scale on the horizontal axis (1.0 on a 1× display). */
  var scaleX: Double = 1.0

  /** The window's device-pixel scale on the vertical axis (1.0 on a 1× display). */
  var scaleY: Double = 1.0
