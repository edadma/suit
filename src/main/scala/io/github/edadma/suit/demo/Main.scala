package io.github.edadma.suit.demo

import io.github.edadma.vdom.*
import io.github.edadma.suit.*
import io.github.edadma.suit.dsl.*

// The Phase-2 milestone in one component: a single box whose colour toggles on each
// click. It exercises the whole pipe end to end — vdom builds the VNode tree,
// `SuitHostConfig` creates a `RenderBox`, the frame loop hit-tests the pointer-press,
// the handler's `useState` write flows through the scheduler seam, the render tree is
// marked dirty, and the loop repaints with the new colour. If this works, every
// architectural assumption behind suit holds.
val App = view {
  val (on, set, _) = useState(false)
  box(
    bg      = if on then Color.rgb(0x4dabf7) else Color.rgb(0xff6b6b),
    onClick = _ => set(!on),
  )
}

@main def main(): Unit =
  Suit.run("suit — hello box", 480, 320)(App())
