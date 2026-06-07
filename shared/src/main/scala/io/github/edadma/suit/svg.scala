package io.github.edadma.suit

// SVG support rides the same seam everything else does. librsvg (the renderer) is native-only,
// but the render tree, the DSL, and the Canvas trait all live in `shared` and stay JVM-testable,
// so the loaded document is represented here by an abstract handle. The native loader (`Svg`)
// produces a Cairo-backed implementation that carries a real librsvg handle, `CairoCanvas` knows
// how to draw that one, and a headless test supplies its own lightweight stand-in.

/** A backend-loaded SVG document, ready to paint. Held abstractly by the render tree and the
  * DSL so they remain platform-neutral; obtain one from the platform loader (`Svg.fromString` /
  * `Svg.fromFile` on Native). */
trait SvgImage:
  /** The document's intrinsic size in pixels, if it declares one (absolute `width`/`height` on
    * the root `<svg>`). A [[RenderSvg]] given no explicit size falls back to this. */
  def intrinsicSize: Option[Size]
