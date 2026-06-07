package io.github.edadma.suit

// The styling value types — the vocabulary boxes are decorated with, and the values the
// paint seam ([[Canvas]]) speaks in. These are pure data (no SDL, no Cairo), so the whole
// styling model lays out and "paints" against a `RecordingCanvas` on the JVM, the same way
// the layout engine does. The guiding principle is the DOM's: styling is a general system
// of composable values applied to a generic box, never behaviour baked into a widget.

/** How an area is filled. The paint seam generalises a flat [[Color]] to this so a
  * background, a border, or a fill can be a gradient as easily as a solid colour. A
  * `Color` converts to a `Solid` implicitly (see the given below), so anywhere a `Paint`
  * is expected a plain colour still reads naturally. */
sealed trait Paint

/** A single flat colour — the common case, and what a [[Color]] converts to. */
final case class Solid(color: Color) extends Paint

/** One stop in a gradient: a `color` placed at `offset` (0 at the gradient's start, 1 at
  * its end) along the gradient's axis. */
final case class ColorStop(offset: Double, color: Color)

/** A linear gradient. `begin` and `end` are box-relative [[Alignment]]s — the gradient
  * runs from the point `begin` names in the filled rectangle to the point `end` names —
  * so the same gradient describes a vertical sheen (`topCenter`→`bottomCenter`, the
  * default) in a box of any size. Stops are colour-positioned along that axis. */
final case class LinearGradient(
    stops: Seq[ColorStop],
    begin: Alignment = Alignment.topCenter,
    end:   Alignment = Alignment.bottomCenter,
) extends Paint

/** A radial gradient centred at the box-relative [[Alignment]] `center`, growing to
  * `radius` expressed as a fraction of the box's larger dimension (so `0.5` reaches about
  * the nearer edge of a square). Stops run from the centre (offset 0) outward. */
final case class RadialGradient(
    stops:  Seq[ColorStop],
    center: Alignment = Alignment.center,
    radius: Double    = 0.5,
) extends Paint

object Paint:
  /** A flat colour is the degenerate paint. Having the conversion as a `given` lets a
    * `Color` flow into any `Paint` position — `box(bg = Color.rgb(0x1e1e1e))`, a stroke,
    * a fill — without callers wrapping it in `Solid` by hand. */
  given Conversion[Color, Paint] = Solid(_)

/** Per-corner corner radii for a rounded box. All four default to zero (a sharp
  * rectangle); [[BorderRadius.all]] sets a uniform radius. The paint backend clamps each
  * radius to half the box's smaller dimension so a large radius degrades to a stadium/pill
  * rather than overlapping. */
final case class BorderRadius(topLeft: Double, topRight: Double, bottomRight: Double, bottomLeft: Double):
  def isZero: Boolean = topLeft == 0 && topRight == 0 && bottomRight == 0 && bottomLeft == 0

object BorderRadius:
  val zero: BorderRadius              = BorderRadius(0, 0, 0, 0)
  def all(r: Double): BorderRadius    = BorderRadius(r, r, r, r)

  /** Round only the top corners (e.g. a tab or a card header). */
  def top(r: Double): BorderRadius    = BorderRadius(r, r, 0, 0)

  /** Round only the bottom corners. */
  def bottom(r: Double): BorderRadius = BorderRadius(0, 0, r, r)

/** A drop shadow behind a box — the cue for elevation. The backend renders it for real:
  * it draws the box's shape into an offscreen surface and softens it with a Gaussian-like
  * blur (Cairo has no native blur, so the softening is a separable box blur over the
  * surface's pixels). This value names the look: the shadow `color` (its alpha is the
  * strength), the `offset` it is cast by, how far the soft edge `blur` spreads, and an
  * optional `spread` that grows the shape before blurring. */
final case class Shadow(
    color:  Color  = Color(0, 0, 0, 90),
    offset: Offset = Offset(0, 2),
    blur:   Double = 8.0,
    spread: Double = 0.0,
)
