---
title: "Input"
weight: 3
---

Input is the bridge from raw device events to the render tree's handlers. The runtime polls
SDL for mouse, wheel, and keyboard events and feeds them to a set of **routers**; the
routers hit-test the tree (for pointer events) or consult the focus owner (for key events)
and fire the matching handler. Like layout, all of this is pure Scala — it needs only
`hitTest`, the parent chain, and the handler maps — so the dispatch model is unit-tested on
the JVM.

## Bubbling

Dispatch bubbles. A hit-test lands on the *deepest* object under the cursor, but handlers
are usually registered on a composite's **outer** object — a button's frame, a slider's
track — while the cursor actually sits over an inner decoration with no handler of its own.

So an event walks up the parent chain from the hit to the **nearest ancestor with a handler
for that event**, and fires there. This is the analogue of DOM event bubbling, in the
single-listener-per-object form suit uses.

The event's coordinates are resolved against the **receiving** object, not the raw hit:

```scala
final case class PointerEvent(position: Offset, local: Offset, size: Size, button: Int = 0):
  def x: Double; def y: Double            // absolute (window) coordinates
  def localX: Double; def localY: Double  // relative to the receiving object's top-left
```

- `position` is the absolute window coordinate.
- `local` is that point relative to the receiving object's top-left, and `size` is that
  object's size.

Together they let a handler work in its own coordinate space — a slider maps
`local.x / size.width` to a fraction, measured against its *whole track* no matter which
inner pixel was hit.

## Pointer capture (drag)

A press **captures** the pointer at the hit object. While captured, every subsequent move
goes to the capturing object — even after the cursor leaves its bounds — until the button
comes up. This is what makes dragging work: a slider keeps receiving `mousemove` while you
drag past its edge. During a capture drag, `button` carries the held button so a handler
can tell a drag from a plain hover (`if e.button != 0 then …`).

A `click` fires on button-up **only if the press and the release resolve to the same click
handler** — i.e. the release landed on the widget the press started on. Pressing a button
and releasing off it does not click.

## Hover

Hover is tracked per **owner**, not per pixel. The router finds the nearest ancestor that
takes part in hover (has a `mouseenter` or `mouseleave` handler) and fires `mouseleave` /
`mouseenter` only when that owner *changes* — so moving the cursor across a button's inner
label does not spam enter/leave, and a widget styles itself on hover cleanly.

## Keyboard focus

`FocusManager` owns which object currently has keyboard focus:

- A **press** routes focus to the nearest **focusable** ancestor of the hit object (the
  target itself if it is focusable), or clears focus if the press landed on nothing
  focusable. Clicking a widget takes focus; clicking empty space drops it.
- Focus changes fire `focus` / `blur` handlers (no payload) so a widget can re-style itself.
- `KeyRouter` delivers `keydown` / `keyup` only to the focused object.

Mark an object focusable in the DSL with `focusable = true`; the built-in widgets set it on
their outer object.

## Tab traversal

The runtime intercepts **Tab** (and **Shift+Tab**) before key routing and moves focus through
the focusable objects rather than delivering the key to the focused widget. The order is the
tree's **document order** — a depth-first walk, each object before its children — and it wraps
at the ends: Tab past the last focusable returns to the first, Shift+Tab past the first goes to
the last. With nothing focused, Tab takes the first focusable and Shift+Tab the last.

This is `FocusManager.focusNext(root, backward)` over `FocusManager.focusables(root)`, both
pure, so traversal order is unit-tested on the JVM. Because Tab is consumed by traversal, a
focused `TextField` does not receive it as input — it advances focus, as in a web form.

```scala
final case class KeyEvent(scancode: Int, repeat: Boolean = false, shift: Boolean = false, ctrl: Boolean = false)
```

`scancode` is the **physical** key as a standard USB-HID usage code — a stable
cross-platform numbering, not an SDL detail. The `Key` object names the common ones:

```scala
Key.A  // letters are USB-HID 4..29 (a..z); only the ones widgets use are named
Key.Enter  Key.Escape  Key.Backspace  Key.Tab  Key.Space
Key.Left   Key.Right   Key.Up         Key.Down
Key.Home   Key.End     Key.Delete     Key.PageUp  Key.PageDown
```

`repeat` is true for the auto-repeat events a held key produces. `shift` / `ctrl` report the
modifiers held at the time — the runtime fills them from the event's modifier bitmask — which
is how a text field tells a caret move from a selection extend, or recognises Ctrl+A.

## Text input

Editing keys are scancodes, but the **characters** they sit between are a separate stream:
the layout-resolved Unicode a key press produces (so a shifted or composed character arrives
as the actual string). The runtime opens the platform text-input session whenever focus lands
on an object that sets `acceptsText = true`, and delivers each typed run as a `TextInputEvent`
to that object's `onTextInput` handler via `TextRouter`.

```scala
final case class TextInputEvent(text: String)
```

This is what `TextField` is built on: editing keys come through `onKeyDown`, the typed
characters through `onTextInput`.

## Wheel

A wheel turn bubbles a `ScrollEvent` to the nearest `wheel` handler at the cursor.

```scala
final case class ScrollEvent(position: Offset, deltaX: Double, deltaY: Double):
  def consume(): Unit
  def consumed: Boolean
```

Positive `deltaY` is a downward/away scroll, matching SDL's convention.

### Chaining

Unlike the other pointer events, a wheel event does not stop at the first handler that
sees it: it **chains**. The nearest scrollable under the cursor is offered the event, and
if it does not claim it, the event passes on to the next scrollable ancestor.

A handler claims the event by calling `consume()`. The built-in views claim it only when
they actually moved — so a view already at its end, or a list too short to scroll, leaves
the wheel unclaimed and the view outside it scrolls instead. Without this a cursor resting
on a short inner list would silently kill the page scroll under it, which reads to a user
as the window having frozen.

This mirrors what a browser does with an exhausted inner scroll: the page keeps scrolling.

A custom `onWheel` that handles the wheel itself — a zoom, say — should `consume()` it, or
the scroll view it sits inside will act on the same turn:

```scala
box(onWheel = e => {
  zoom += e.deltaY * 0.1
  e.consume() // stop it here; do not also scroll the page
})(...)
```

## Cursor

A widget names the pointer shape shown while the cursor is over it with the `cursor` prop,
a value of the `Cursor` enum:

```scala
box(cursor = Cursor.Pointer)(...) // the hand, over anything clickable
```

`Cursor` is a *semantic* set — `Pointer`, `Text`, `Crosshair`, `Move`, `NotAllowed`,
`Progress`, `Wait`, and the four resize arrows `ResizeEW` / `ResizeNS` / `ResizeNESW` /
`ResizeNWSE` — that the native runtime maps to the platform's own system cursors, so a link
reads as the OS hand and a text field as its I-beam.

The shape **resolves like the text-style cascade**: the runtime shows the `Cursor` of the
nearest object at or above whatever is under the pointer that names one, so a `Button` sets
`Cursor.Pointer` once and its inner label inherits it. A widget that names no cursor (the
default) leaves the shape to its surroundings; with nothing named anywhere, the arrow shows.
`Cursor.Default` is a real preference — it forces the arrow, overriding an inherited shape —
distinct from naming nothing, which inherits.

During a drag the shape sticks to the widget the press started on, so a splitter keeps its
resize arrow while the pointer strays off the thin gutter. The runtime re-resolves every
frame, so a shape also follows a layout change under a still pointer, not only a move.

The built-in widgets already carry sensible cursors: `Button` / `Checkbox` / `Slider` show
the hand, `TextField` / `TextArea` the I-beam, and a `splitter` its resize arrow.

## Handlers on box

All of this is reached through the typed handlers on the `box` builder:

```scala
box(
  focusable    = true,
  cursor       = Cursor.Pointer,               // pointer shape while hovered
  onClick      = (e: PointerEvent) => ...,
  onMouseDown  = (e: PointerEvent) => ...,
  onMouseUp    = (e: PointerEvent) => ...,
  onMouseMove  = (e: PointerEvent) => ...,
  onMouseEnter = (e: PointerEvent) => ...,
  onMouseLeave = (e: PointerEvent) => ...,
  onWheel      = (e: ScrollEvent)     => ...,
  onKeyDown    = (e: KeyEvent)        => ...,
  onKeyUp      = (e: KeyEvent)        => ...,
  acceptsText  = true,                          // open text input while focused
  onTextInput  = (e: TextInputEvent)  => ...,
  onFocus      = () => ...,
  onBlur       = () => ...,
)(children*)
```

The [widget library](/reference/widgets/) is built entirely on these — reading the
`Button`, `Checkbox`, and `Slider` sources is the best way to see the model in practice.
