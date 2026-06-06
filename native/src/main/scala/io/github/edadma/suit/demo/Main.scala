package io.github.edadma.suit.demo

import io.github.edadma.vdom.*
import io.github.edadma.suit.*
import io.github.edadma.suit.dsl.*

// A small showcase of the layout engine: a vertical stack with a header bar, a row of
// three colour swatches separated by a flexible spacer, and a footer that toggles
// colour on click. It exercises the whole pipe — vdom builds the VNode tree,
// `SuitHostConfig` creates the RenderObjects, the constraint protocol lays them out,
// the frame loop hit-tests a pointer-press, the handler's `useState` write flows
// through the scheduler seam, the tree is marked dirty, and the loop repaints.
val App = view {
  val (lit, set, _) = useState(false)

  val swatch = (c: Color) => box(bg = c, width = 64, height = 64)()

  col(spacing = 12)(
    // Header: a full-width bar.
    box(bg = Color.rgb(0x222831), height = 48)(),

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

    // Footer: click to toggle its colour, proving the reactive path end to end.
    box(
      bg      = if lit then Color.rgb(0x51cf66) else Color.rgb(0x495057),
      height  = 40,
      onClick = _ => set(!lit),
    )(),
  )
}

@main def main(): Unit =
  Suit.run("suit — layout demo", 480, 320)(App())
