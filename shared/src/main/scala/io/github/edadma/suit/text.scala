package io.github.edadma.suit

// Text styling and the measurement seam.
//
// Painting text is a backend job — it needs real font files and a glyph rasteriser,
// which only the SDL host has — but *laying out* text is part of the constraint pass,
// which suit keeps off-device so the layout engine stays JVM-testable. The two needs
// are reconciled by abstracting measurement behind a trait: layout asks the installed
// `TextMeasurer` how big a string will be, the runtime installs an SDL-backed measurer,
// and tests install a deterministic fake. This mirrors the `Canvas` seam (how to draw)
// and `vdom.Host.config` (the installed host) — one global, swapped per environment.

/** How a run of text is drawn: its point `size`, `color`, and `weight`. This is the
  * *resolved* style — every field concrete — that the measurement and paint seams receive. A
  * [[RenderText]] computes it from its own explicit values overlaid on whatever it inherits
  * (see [[TextStyleAttrs]]). `weight` is a CSS-style numeric weight (100–900) driven through
  * the font's variable `wght` axis by the backend; a backend with only a fixed face ignores
  * it. Font family is still a single global choice; richer styling layers on here later
  * without changing the layout contract. */
final case class TextStyle(size: Double = 16.0, color: Color = Color(0, 0, 0), weight: Int = FontWeight.Normal)

object TextStyle:
  val default: TextStyle = TextStyle()

/** Standard CSS numeric font weights, for naming the value that flows into a font's variable
  * `wght` axis. The axis is continuous, so any value in range is valid; these are the common
  * stops. */
object FontWeight:
  val Thin       = 100
  val ExtraLight = 200
  val Light      = 300
  val Normal     = 400
  val Medium     = 500
  val SemiBold   = 600
  val Bold       = 700
  val ExtraBold  = 800
  val Black      = 900

/** How each line of a multi-line (or single-line) run is positioned horizontally within
  * the width the layout gave the text. `Left` is the default and the historical behaviour;
  * `Center`/`Right` shift each line by the slack between its measured width and the box. */
enum TextAlign:
  case Left, Center, Right

/** What happens when text cannot fit the space it is allowed — too wide for one line, or
  * more wrapped lines than `maxLines`. `Clip` simply stops (the box's own clipping, if any,
  * hides the rest); `Ellipsis` trims the last visible line and appends `…` to mark that
  * content was dropped. */
enum TextOverflow:
  case Clip, Ellipsis

/** A *partial* text style for the render-tree cascade. Any field left `None` is
  * inherited from the nearest ancestor that sets it, the way `color`/`font-size` cascade
  * in CSS. A container carries one of these ([[RenderObject.textAttrs]]); a [[RenderText]]
  * resolves an effective [[TextStyle]] by walking its ancestors and falling back to
  * [[TextStyle.default]] for anything no one set. Keeping inheritance in the render tree
  * (not at DSL-build time) is what makes it composable and JVM-testable. */
final case class TextStyleAttrs(
    size: Option[Double] = None,
    color: Option[Color] = None,
    weight: Option[Int] = None,
)

object TextStyleAttrs:
  /** The neutral carrier: inherits everything, overrides nothing. Containers default to
    * it so an object that sets no text style is transparent to the cascade. */
  val empty: TextStyleAttrs = TextStyleAttrs()

/** Measures text without drawing it — the off-device half of text support. An
  * implementation returns the pixel size a single line of `text` occupies in `style`,
  * so a [[RenderText]] can size itself during layout. */
trait TextMeasurer:
  def measure(text: String, style: TextStyle): Size

object TextMeasurer:
  /** The fallback measurer: every string measures to nothing. It lets a UI with text
    * lay out (degraded, not crashing) before a real measurer is installed, the way the
    * scheduler seams default to no-ops. */
  val zero: TextMeasurer = (_, _) => Size.zero

  /** The measurer layout consults. The runtime installs an SDL-backed one before the
    * first frame; tests install a deterministic fake. */
  var installed: TextMeasurer = zero
