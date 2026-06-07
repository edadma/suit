---
title: "Widgets"
weight: 2
---

```scala
import io.github.edadma.suit.widgets.*
```

A small library of reusable controls composed from the DSL primitives and vdom hooks. A
widget is an ordinary vdom component, so its interaction state (hover, pressed) lives in
`useState` and survives re-renders, and it reconciles in place exactly like an application
component. Each widget is purely declarative output over `box` / `text` / `row` / `stack`;
the render tree, layout, and input routing underneath give it pixels and behaviour.

[= note =]
A scrolling viewport is the [`scrollView`](/reference/dsl/#scrollview) DSL primitive (its
scroll position lives on the render object, so it is a primitive rather than a composed
widget).
[= /note =]

[= note =]
The widgets **animate**. Hover and press tints fade rather than snap, the checkbox mark
scales and fades as it ticks, and the slider thumb glides toward its value — all driven by
[`useTransition`](/guide/motion/) over the runtime's frame clock. With motion settled, every
value lands exactly on its target, so the animation is invisible to tests.
[= /note =]

## Button

```scala
val Button: Component2[String, () => Unit]
```

A push button: a labelled, focusable rectangle that calls `onPressed` when clicked (a press
and release on the button) or activated from the keyboard (Space or Enter while focused). It
tints on hover and while held.

```scala
Button("Increment", () => setCount(count + 1))
```

## Checkbox

```scala
val Checkbox: Component2[Boolean, Boolean => Unit]
```

A small focusable square that toggles, calling `onChange` with the new state on a click or
on Space while focused. It is **controlled** — it draws the `checked` it is given and never
holds the value itself, so the parent owns the state. The check is a filled inner square (no
glyph-font dependency).

```scala
val (checked, setChecked, _) = useState(false)
Checkbox(checked, setChecked)
```

## Slider

```scala
val Slider: Component2[Double, Double => Unit]
```

A horizontal slider over the range `0..1`: a full-width track with a draggable thumb. It is
**controlled** — it renders the `value` it is given and reports a new value through
`onChange` on a press, a drag, or the arrow keys while focused. The new value comes from the
press position in the slider's own coordinate space (`local.x / size.width`), which is why
the handlers live on the outer track, not the thumb (see
[pointer capture](/guide/input/#pointer-capture-drag)).

```scala
val (level, setLevel, _) = useState(0.4)
box(width = 240)(
  Slider(level, setLevel),
)
```

## TextField

```scala
val TextField: Component2[String, String => Unit]
```

A single-line text field — a focusable, bordered box that edits a string. It is
**controlled**: it renders the `value` it is given and reports edits through `onChange`, so
the parent owns the text. While focused it receives typed characters (the runtime opens the
platform's text-input session for it) and editing keys:

- **Backspace** / **Delete** remove before / after the caret (or the selection).
- **Left** / **Right** / **Home** / **End** move the caret; hold **Shift** to extend a selection.
- **Ctrl+A** selects all.
- A **click** places the caret at the nearest character boundary; a **drag** selects a range.
- Typing or a deletion replaces the current selection.

Caret and selection geometry come from measuring text prefixes through the installed
`TextMeasurer`, so positions are exact (and JVM-testable). The content is clipped to the
field.

```scala
val (name, setName, _) = useState("")
col(crossAxisAlignment = CrossAxisAlignment.Stretch)(
  TextField(name, setName),
)
```

[= note =]
Give the field a definite width — put it in a `Stretch` column or a fixed-width `box` — so it
fills the row; like the other controlled widgets it does not impose a width of its own. The
caret is currently solid (not blinking) and the view does not yet scroll to follow a caret
past the right edge; both are planned refinements.
[= /note =]

## Theme

```scala
case class Theme(
    primary, primaryHover, primaryActive, onPrimary: Color,
    surface, surfaceText, border, accent, track: Color,
    radius, spacing, textSize: Double,
)

object Theme:
  val default: Theme   // the stock dark-blue theme

def ThemeProvider(theme: Theme)(children: VNode*): VNode
def useTheme()(using Hooks): Theme
```

Styling is a **general system**, not baked into the widgets. A `Theme` is a record of palette
and metric tokens; the built-in widgets read it through `useTheme()` and paint from whatever
the nearest enclosing `ThemeProvider` supplies (or `Theme.default` if there is none). Swap one
record at the top of the tree and every control below restyles — no widget code is touched.

```scala
val violet = Theme.default.copy(
  primary       = Color.rgb(0x9775fa),
  primaryHover  = Color.rgb(0xb197fc),
  primaryActive = Color.rgb(0x845ef7),
  radius        = 10.0,
)

ThemeProvider(violet)(
  col(spacing = 16)(
    Button("Save", onSave),
    Checkbox(on, setOn),
  ),
)
```

## A controlled-widget example

Because `Checkbox` and `Slider` are controlled, the pattern is always the same: hold the
value in `useState`, render the widget with it, and pass the setter as `onChange`.

```scala
val App = view {
  val (on, setOn, _)       = useState(true)
  val (level, setLevel, _) = useState(0.5)

  col(spacing = 16)(
    row(crossAxisAlignment = CrossAxisAlignment.Center, spacing = 8)(
      Checkbox(on, setOn),
      text(if on then "on" else "off", color = Color.white),
    ),
    text(s"${(level * 100).toInt}%", color = Color.white),
    box(width = 240)(Slider(level, setLevel)),
  )
}
```
