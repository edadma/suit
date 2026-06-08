package io.github.edadma.suit

// Raster (bitmap) image support rides the same seam as SVG. The decoder (turbojpeg) and Cairo
// are native-only, but the render tree, the DSL, and the Canvas trait live in `shared` and stay
// JVM-testable, so a decoded image is represented here only by its pixel dimensions. The native
// loader (`Raster`) produces a Cairo-surface-backed implementation, `CairoCanvas` knows how to
// blit that one, and a headless test supplies its own lightweight stand-in.

/** A decoded bitmap image, ready to paint. Held abstractly by the render tree and the DSL so
  * they stay platform-neutral; obtain one from the platform loader (`Raster.fromFile` /
  * `Raster.fromBytes` on Native), which decodes JPEG through turbojpeg (PNG is handled separately by
  * Cairo). Unlike an SVG it has a definite pixel size — `width` x `height` — which a [[RenderImage]]
  * uses as its intrinsic size. */
trait RasterImage:
  /** The decoded image's width in pixels. */
  def width: Int

  /** The decoded image's height in pixels. */
  def height: Int
