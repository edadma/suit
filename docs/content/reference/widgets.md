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
`TextMeasurer`, so positions are exact (and JVM-testable). The content is clipped to the field
and **scrolls horizontally** to keep the caret in view once the text outgrows it. The caret
**blinks** while the field is focused and snaps solid for a full interval after any edit or
caret move, so it is steady the instant you type.

```scala
val (name, setName, _) = useState("")
col(crossAxisAlignment = CrossAxisAlignment.Stretch)(
  TextField(name, setName),
)
```

[= note =]
Give the field a definite width — put it in a `Stretch` column or a fixed-width `box` — so it
fills the row; like the other controlled widgets it does not impose a width of its own.
[= /note =]

## Switch

```scala
val Switch: Component2[Boolean, Boolean => Unit]
```

A pill-shaped toggle with a sliding thumb — the on/off counterpart to a `Checkbox`. It is
**controlled** — it renders the `on` it is given and reports the flip through `onChange` on a
click or on Space while focused. The thumb glides between the ends and the track fades between
the inactive groove and the accent.

```scala
val (live, setLive, _) = useState(true)
row(crossAxisAlignment = CrossAxisAlignment.Center, spacing = 8)(
  Switch(live, setLive),
  text(if live then "live" else "paused"),
)
```

## RadioGroup

```scala
val RadioGroup: Component3[Seq[(String, String)], String, String => Unit]
```

A single-select column of options, each a `(value, label)` pair. It is **controlled** — the
row whose value equals `selected` shows its dot, and clicking a row (or Space while it is
focused) reports that row's value through `onChange`.

```scala
val (size, setSize, _) = useState("m")
RadioGroup(Seq("s" -> "Small", "m" -> "Medium", "l" -> "Large"), size, setSize)
```

## Tabs

```scala
val Tabs: Component3[Seq[(String, String)], String, String => Unit]
```

A row of selectable headers, each a `(value, label)` pair. It is **controlled** — the tab
whose value equals `selected` is highlighted, and clicking a tab (or Space / Enter while it is
focused) reports that tab's value. Pair it with the caller's own switch on `selected` to swap
the panel below.

```scala
val (tab, setTab, _) = useState("overview")
col(crossAxisAlignment = CrossAxisAlignment.Stretch, spacing = 12)(
  Tabs(Seq("overview" -> "Overview", "status" -> "Status"), tab, setTab),
  tab match
    case "status" => Alert(AlertKind.Success, "All systems nominal.")
    case _        => text("An overview."),
)
```

## ProgressBar

```scala
val ProgressBar: Component[Double]
```

A determinate progress bar over `0..1`: a rounded track with an accent fill proportional to
its value. The fill animates toward the target, so a jump to a new value glides. It takes its
width from its parent.

```scala
ProgressBar(level) // 0.0 .. 1.0
```

## Badge

```scala
val Badge: Component[String]
```

A small rounded pill that labels or counts, painted in the theme's accent.

```scala
Badge(s"$count")
```

## Divider

```scala
val Divider: Component[Boolean]
```

A hairline separator in the theme's border colour. `Divider(false)` is a horizontal rule;
`Divider(true)` is a vertical one. It takes its length from the cross axis of its parent, so
put a horizontal divider in a stretched column and a vertical one in a stretched row.

```scala
col(crossAxisAlignment = CrossAxisAlignment.Stretch, spacing = 12)(
  text("above"),
  Divider(false),
  text("below"),
)
```

## Alert

```scala
enum AlertKind:
  case Info, Success, Warning, Danger

val Alert: Component2[AlertKind, String]
```

A callout — a tinted, bordered panel that draws attention to a message, coloured by its
`AlertKind` from the theme's status roles (`info` / `success` / `warning` / `danger`).

```scala
Alert(AlertKind.Success, "Saved your changes.")
Alert(AlertKind.Danger, "Could not connect.")
```

## Card

```scala
val Card: Container
```

A surface panel — a themed, rounded, shadowed container that groups related content. It is
pure chrome (no state, no interaction), so it takes children directly and pads them by the
theme's spacing.

```scala
Card(
  col(spacing = 8)(
    text("Title"),
    text("Some grouped content."),
  ),
)
```

## Dialog

```scala
def Dialog(
    open:         Boolean,
    onClose:      () => Unit,
    maskClosable: Boolean = true,
    exitMs:       Int     = 200,
    width:        Double  = 420,
)(children: VNode*): VNode
```

A modal dialog — content centred above a dimming scrim that takes over the window until
dismissed. It is **controlled**: the caller owns `open` and is told to close through
`onClose`, which fires on a click on the scrim (when `maskClosable`), the **Escape** key, or
anything the caller wires inside the body (a Close button).

The dialog is **portaled into the overlay layer**, so it escapes any clipping or scrolling of
the place that opened it and always paints on top — the `Dialog` node can sit anywhere in the
tree. Opening moves focus into the dialog and **traps Tab** within it; Escape closes it from
anywhere inside; closing **restores focus** to whatever held it before. The scrim and card
fade in and the dialog stays mounted through its close animation (`exitMs`) before unmounting,
via `usePresence` + `useTransition`.

The content is **capped to `width` pixels** — sized to its content but never wider — so body
text has a width to wrap into. Remember `text` is single-line by default, so pass it `maxLines`
other than 1 (e.g. `maxLines = 0` for unlimited) to wrap. Pass `width = Double.NaN` to leave the
card uncapped (a long single line then grows it unbounded).

The overlay layer is provided by `Suit.run`; with none available (outside a running app) the
dialog renders nothing. A headless test wires its own through `OverlayContext` — see
`DialogSpec`.

```scala
val (open, setOpen, _) = useState(false)

col(spacing = 16)(
  Button("Open dialog", () => setOpen(true)),
  Dialog(open, () => setOpen(false))(
    col(spacing = 16)(
      text("A modal dialog", size = 18),
      text("It dims the rest and traps focus until dismissed."),
      row(mainAxisAlignment = MainAxisAlignment.End)(
        Button("Close", () => setOpen(false)),
      ),
    ),
  ),
)
```

## Menu

```scala
def Menu(
    open:      Boolean,
    onClose:   () => Unit,
    anchor:    Ref[RenderObject | Null],
    exitMs:    Int       = 150,
    width:     Double    = 180,
    placement: Placement = Placement(),
)(items: VNode*): VNode

val MenuItem: Component2[String, () => Unit]
```

A dropdown menu anchored to a trigger. Like the dialog it is **controlled** — the caller owns
`open` and is told to close through `onClose` — but it is **positioned**: it portals into the
overlay layer and floats just below the trigger, flipping above it near the bottom edge and
sliding left to stay on-screen.

Give the trigger a `ref` and hand the *same* ref to `Menu` as `anchor`, so the menu can read the
trigger's on-screen rectangle:

```scala
val (open, setOpen, _) = useState(false)
val anchor             = useRef[RenderObject | Null](null)

row(spacing = 16)(
  box(ref = anchor)(
    Button("Options", () => setOpen(true)),
  ),
  Menu(open, () => setOpen(false), anchor)(
    MenuItem("Rename", () => setOpen(false)),
    MenuItem("Delete", () => setOpen(false)),
  ),
)
```

A click anywhere outside the menu dismisses it (a transparent full-window catcher, not a
dimming scrim), as does Escape; opening traps Tab within the menu and restores focus on close.
Fill it with `MenuItem`s — focusable rows that call their `onSelect` on a click or on Space /
Enter while focused, and highlight on hover. Wire each `onSelect` to do the action and close the
menu.

Pass a `placement` to prefer a different side or add a gap (see [Placement](#placement) below);
the menu still flips and slides to stay on-screen.

## Placement

```scala
case class Placement(
    side:  PopoverSide  = PopoverSide.Below,  // Below | Above | Right | Left
    gap:   Double       = 0.0,
    align: PopoverAlign = PopoverAlign.Start, // Start | Center | End
)
```

How a positioned overlay (`Menu`, `Tooltip`) places itself against its trigger: the preferred
`side`, the `gap` in pixels between trigger and card, and the cross-axis `align`. It expresses a
*preference*, not a fixed position — the popover flips to the opposite side when the preferred one
would run off-screen, and slides along the cross axis to stay visible. The default — below, flush,
left-aligned — is the ordinary dropdown placement.

```scala
Menu(open, onClose, anchor, placement = Placement(side = PopoverSide.Above, gap = 6))(...)
Tooltip("Saved", placement = Placement(side = PopoverSide.Above))(text("Drafts"))
```

## Tooltip

```scala
def Tooltip(
    label:     String,
    delayMs:   Int       = 400,
    exitMs:    Int       = 120,
    placement: Placement = Placement(),
)(trigger: VNode*): VNode
```

A small label that appears beside its trigger on hover. Wrap the trigger as the child; the
tooltip attaches the hover tracking and an anchor itself, so callers wire nothing. It portals
into the overlay layer and floats just below the trigger (flipping and sliding to stay
on-screen), and is **click-through** — it never intercepts a click meant for what is underneath.
It shows after a short hover `delayMs` and fades on both ends. Pass a `placement` to prefer a
different side — a tooltip often reads better [above](#placement) its trigger.

```scala
Tooltip("Saved automatically.")(
  text("Drafts"),
)
```

[= note =]
Both `Menu` and `Tooltip` need the overlay layer that `Suit.run` provides; outside a running app
(or a test that does not wire one) the menu and the floating label render nothing — the tooltip's
trigger still shows. A headless test wires an overlay through `OverlayContext` — see `MenuSpec` /
`TooltipSpec`.
[= /note =]

## Theme

```scala
case class Theme(
    primary, primaryHover, primaryActive, onPrimary: Color,
    background, surface, surfaceText, border, accent, track: Color,
    info, success, warning, danger: Color,
    radius, spacing, textSize: Double,
    isDark: Boolean,
)

object Theme:
  val dark:        Theme   // stock dark-blue (the context default)
  val light:       Theme   // stock light-blue — the light counterpart to dark
  val violetDark:  Theme   // violet accent, dark scheme
  val violetLight: Theme   // violet accent, light scheme
  val default:     Theme   // == dark
  val builtIns: List[Theme] // [dark, light, violetDark, violetLight] — for a theme picker

def ThemeProvider(theme: Theme)(children: VNode*): VNode
def useTheme()(using Hooks): Theme
```

Styling is a **general system**, not baked into the widgets. A `Theme` is a record of palette
(`background` is the app body behind everything, `surface` an elevated panel on top of it;
the `info` / `success` / `warning` / `danger` status roles are what an `Alert` paints from) and
metric tokens, plus an `isDark` flag recording the colour scheme. The built-in widgets read it
through `useTheme()` and paint from whatever the nearest enclosing `ThemeProvider` supplies (or
`Theme.default` if there is none). Swap one record at the top of the tree and every control
below restyles — no widget code is touched.

There are four built-in themes — a blue and a violet accent, each in a light and a dark scheme.
Switching light/dark is just choosing which record to provide, so an app holds the choice in
state and a toggle flips it:

```scala
val (dark, setDark, _) = useState(true)
val theme = if dark then Theme.dark else Theme.light

ThemeProvider(theme)(
  box(bg = theme.background)(
    col(spacing = 16)(
      Switch(dark, setDark),   // flips the whole tree light/dark
      Button("Save", onSave),
      Checkbox(on, setOn),
    ),
  ),
)
```

A custom theme is a `copy` of any built-in — override the brand colours and keep the rest:

```scala
val brand = Theme.dark.copy(
  primary = Color.rgb(0x9775fa),
  accent  = Color.rgb(0x9775fa),
  radius  = 10.0,
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
