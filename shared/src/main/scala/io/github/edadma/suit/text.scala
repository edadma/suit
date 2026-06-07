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

/** How a run of text is drawn: its point `size` and `color`. This is the *resolved*
  * style — both fields concrete — that the measurement and paint seams receive. A
  * [[RenderText]] computes it from its own explicit values overlaid on whatever it
  * inherits (see [[TextStyleAttrs]]). Font family and weight are a single global choice
  * for now (the runtime opens one font file); richer styling layers on here later
  * without changing the layout contract. */
final case class TextStyle(size: Double = 16.0, color: Color = Color(0, 0, 0))

object TextStyle:
  val default: TextStyle = TextStyle()

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
final case class TextStyleAttrs(size: Option[Double] = None, color: Option[Color] = None)

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
