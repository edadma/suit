package io.github.edadma.suit.demo

import io.github.edadma.vdom.*
import io.github.edadma.suit.*
import io.github.edadma.suit.dsl.*
import io.github.edadma.suit.widgets.*

// A showcase of the widget library and the input model: a header caption, an
// interactive counter driven by a Button, a Checkbox bound to its own state, and a
// Slider whose value is reflected in a readout. It exercises the whole pipe — vdom
// builds the VNode tree, `SuitHostConfig` creates the RenderObjects, the installed
// measurer sizes the text, the constraint protocol lays everything out, the frame loop
// routes pointer, wheel, and keyboard events through the routers and the focus manager,
// the widgets' `useState` writes flow through the scheduler seam, the tree is marked
// dirty, and the loop repaints. Tab is not wired here, so click a control to focus it,
// then use the keyboard (Space/Enter on the button and checkbox, arrows on the slider).
val App = view {
  val (count, setCount, _)   = useState(0)
  val (checked, setChecked, _) = useState(false)
  val (level, setLevel, _)   = useState(0.4)

  col(spacing = 16)(
    // Header bar with a caption.
    box(bg = Color.rgb(0x222831), height = 48, padding = EdgeInsets.symmetric(horizontal = 16, vertical = 12))(
      text("suit — widgets & input", size = 20, color = Color.rgb(0xf1f3f5)),
    ),

    // Body: padded column of controls.
    box(flex = 1, padding = EdgeInsets.all(20))(
      col(spacing = 20)(
        row(crossAxisAlignment = CrossAxisAlignment.Center, spacing = 16)(
          Button("Increment", () => setCount(count + 1)),
          text(s"count: $count", size = 16, color = Color.rgb(0xf1f3f5)),
        ),
        row(crossAxisAlignment = CrossAxisAlignment.Center, spacing = 16)(
          Checkbox(checked, setChecked),
          text(if checked then "enabled" else "disabled", size = 16, color = Color.rgb(0xf1f3f5)),
        ),
        col(spacing = 8)(
          text(s"level: ${(level * 100).toInt}%", size = 16, color = Color.rgb(0xf1f3f5)),
          box(width = 240)(
            Slider(level, setLevel),
          ),
        ),
      ),
    ),
  )
}

@main def main(): Unit =
  Suit.run("suit — widgets & input demo", 480, 360)(App())
