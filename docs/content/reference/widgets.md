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
These widgets are **pointer- and keyboard-driven only**. A text field needs text-input
support in the SDL binding, and a scroll view needs renderer clip-rects; until those land
in [sdl3](https://sdl3.edadma.dev/), `TextField` and `ScrollView` are deliberately absent
rather than approximated. The focus and keyboard infrastructure they need already exists.
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

## Theme

```scala
object Theme:
  val primary, primaryHover, primaryActive, onPrimary: Color
  val surface, border, accent, track: Color
```

The default palette the built-in widgets paint with — a small dark-on-accent theme. It is
just a set of named `Color`s; applications can ignore it and pass their own, but the widgets
read from it so a stock control looks consistent.

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
