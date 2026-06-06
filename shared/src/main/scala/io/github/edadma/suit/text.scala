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

/** How a run of text is drawn: its point `size` and `color`. Font family and weight
  * are a single global choice for now (the runtime opens one font file); richer styling
  * layers on here later without changing the layout contract. */
final case class TextStyle(size: Double = 16.0, color: Color = Color(0, 0, 0))

object TextStyle:
  val default: TextStyle = TextStyle()

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
