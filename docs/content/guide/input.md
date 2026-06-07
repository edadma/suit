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

```scala
final case class KeyEvent(scancode: Int, repeat: Boolean = false)
```

`scancode` is the **physical** key as a standard USB-HID usage code — a stable
cross-platform numbering, not an SDL detail. The `Key` object names the common ones:

```scala
Key.Enter  Key.Escape  Key.Backspace  Key.Tab  Key.Space
Key.Left   Key.Right   Key.Up         Key.Down
Key.Home   Key.End     Key.Delete     Key.PageUp  Key.PageDown
```

`repeat` is true for the auto-repeat events a held key produces. Text entry — the Unicode a
key press produces under the active layout — is a separate concern (text-input events), not
derived from scancodes here.

## Wheel

A wheel turn bubbles a `ScrollEvent` to the nearest `wheel` handler at the cursor.

```scala
final case class ScrollEvent(position: Offset, deltaX: Double, deltaY: Double)
```

Positive `deltaY` is a downward/away scroll, matching SDL's convention.

## Handlers on box

All of this is reached through the typed handlers on the `box` builder:

```scala
box(
  focusable    = true,
  onClick      = (e: PointerEvent) => ...,
  onMouseDown  = (e: PointerEvent) => ...,
  onMouseUp    = (e: PointerEvent) => ...,
  onMouseMove  = (e: PointerEvent) => ...,
  onMouseEnter = (e: PointerEvent) => ...,
  onMouseLeave = (e: PointerEvent) => ...,
  onWheel      = (e: ScrollEvent)  => ...,
  onKeyDown    = (e: KeyEvent)     => ...,
  onKeyUp      = (e: KeyEvent)     => ...,
  onFocus      = () => ...,
  onBlur       = () => ...,
)(children*)
```

The [widget library](/reference/widgets/) is built entirely on these — reading the
`Button`, `Checkbox`, and `Slider` sources is the best way to see the model in practice.
