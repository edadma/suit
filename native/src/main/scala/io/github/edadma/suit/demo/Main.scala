package io.github.edadma.suit.demo

import io.github.edadma.suit.*
import io.github.edadma.suit.dsl.*
import io.github.edadma.suit.widgets.*

// A showcase of the styling foundation and the input model: a gradient header, rounded
// cards with drop shadows holding an interactive counter (Button), a Checkbox bound to its
// own state, and a Slider whose value is reflected in a readout. It exercises the whole
// pipe — vdom builds the VNode tree, `SuitHostConfig` creates the RenderObjects, the
// installed measurer sizes the text, the constraint protocol lays everything out, the
// frame loop routes pointer, wheel, and keyboard events through the routers and the focus
// manager, the widgets' `useState` writes flow through the scheduler seam, the tree is
// marked dirty, and the loop repaints. Tab is not wired here, so click a control to focus
// it, then use the keyboard (Space/Enter on the button and checkbox, arrows on the slider).

private val ink     = Color.rgb(0xf1f3f5)
private val muted   = Color.rgb(0xadb5bd)
private val cardBg  = Color.rgb(0x2b3035)

/** A rounded, shadowed panel — the styling foundation applied as reusable chrome rather
  * than a baked-in widget. */
private def card(children: VNode*): VNode =
  box(
    bg      = cardBg,
    radius  = 12,
    shadow  = Shadow(color = Color(0, 0, 0, 110), offset = Offset(0, 4), blur = 12),
    padding = EdgeInsets.all(20),
  )(children*)

val App = view {
  val (count, setCount, _)     = useState(0)
  val (checked, setChecked, _) = useState(false)
  val (level, setLevel, _)     = useState(0.4)

  col(spacing = 16)(
    // Header bar: a horizontal gradient with a title.
    box(
      bg      = LinearGradient(
        Seq(ColorStop(0, Color.rgb(0x4dabf7)), ColorStop(1, Color.rgb(0x9775fa))),
        begin = Alignment.centerLeft,
        end   = Alignment.centerRight,
      ),
      height  = 56,
      padding = EdgeInsets.symmetric(horizontal = 20, vertical = 14),
    )(
      text("suit — styling & input", size = 22, color = Color.rgb(0x0b1418)),
    ),

    // Body: a padded column of cards.
    box(flex = 1, padding = EdgeInsets.all(20))(
      col(spacing = 16)(
        card(
          row(crossAxisAlignment = CrossAxisAlignment.Center, spacing = 16)(
            Button("Increment", () => setCount(count + 1)),
            text(s"count: $count", size = 16, color = ink),
          ),
        ),
        card(
          row(crossAxisAlignment = CrossAxisAlignment.Center, spacing = 16)(
            Checkbox(checked, setChecked),
            text(if checked then "enabled" else "disabled", size = 16, color = ink),
          ),
        ),
        card(
          col(spacing = 10)(
            text(s"level: ${(level * 100).toInt}%", size = 16, color = muted),
            box(width = 260)(
              Slider(level, setLevel),
            ),
          ),
        ),
      ),
    ),
  )
}

@main def main(): Unit =
  Suit.run("suit — styling & input demo", 520, 420)(App())
