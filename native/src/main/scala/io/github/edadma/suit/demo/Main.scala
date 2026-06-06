package io.github.edadma.suit.demo

import io.github.edadma.vdom.*
import io.github.edadma.suit.*
import io.github.edadma.suit.dsl.*

// A small showcase of the layout engine and text: a vertical stack with a header that
// labels itself, a row of three colour swatches separated by a flexible spacer, and a
// footer button that toggles colour on click and brightens on hover. It exercises the
// whole pipe — vdom builds the VNode tree, `SuitHostConfig` creates the RenderObjects,
// the installed measurer sizes the text, the constraint protocol lays everything out,
// the frame loop routes pointer events through `PointerRouter`, the handlers' `useState`
// writes flow through the scheduler seam, the tree is marked dirty, and the loop
// repaints — now drawing glyphs through sdl3_ttf.
val App = view {
  val (lit, setLit, _)         = useState(false)
  val (hover, setHover, _)     = useState(false)

  val swatch = (c: Color) => box(bg = c, width = 64, height = 64)()

  col(spacing = 12)(
    // Header: a bar with a centred caption.
    box(bg = Color.rgb(0x222831), height = 48, padding = EdgeInsets.symmetric(horizontal = 16, vertical = 12))(
      text("suit — text & input", size = 20, color = Color.rgb(0xf1f3f5)),
    ),

    // Body grows to fill the leftover vertical space; pad it, then lay swatches in a
    // row with a spacer shoving the last one to the right edge.
    box(flex = 1, padding = EdgeInsets.all(16))(
      row(crossAxisAlignment = CrossAxisAlignment.Center, spacing = 12)(
        swatch(Color.rgb(0xff6b6b)),
        swatch(Color.rgb(0xffd43b)),
        spacer(),
        swatch(Color.rgb(0x4dabf7)),
      ),
    ),

    // Footer: click to toggle its colour, hover to brighten — proving pointer routing
    // and the reactive path end to end. Its label reflects the toggle state.
    box(
      bg           = footerColor(lit, hover),
      height       = 44,
      padding      = EdgeInsets.symmetric(horizontal = 16, vertical = 12),
      onClick      = _ => setLit(!lit),
      onMouseEnter = _ => setHover(true),
      onMouseLeave = _ => setHover(false),
    )(
      text(if lit then "lit — click to dim" else "dim — click to light", size = 16, color = Color.rgb(0xf8f9fa)),
    ),
  )
}

private def footerColor(lit: Boolean, hover: Boolean): Color =
  (lit, hover) match
    case (true, true)   => Color.rgb(0x69db7c)
    case (true, false)  => Color.rgb(0x51cf66)
    case (false, true)  => Color.rgb(0x5c636a)
    case (false, false) => Color.rgb(0x495057)

@main def main(): Unit =
  Suit.run("suit — text & input demo", 480, 320)(App())
