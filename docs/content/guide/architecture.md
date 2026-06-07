---
title: "Architecture"
weight: 1
---

suit is one layer of a three-layer system, the same division of labour Flutter draws
between its *widgets*, its *elements*, and its *render objects*. Two of those layers
already exist in **vdom**; suit builds the third.

## Three trees

```
 Widgets / VNodes     ── what you write: col(...)(box(...), text(...))      ← vdom
 Elements             ── the reconciled, stateful instance tree            ← vdom
 RenderObjects        ── layout, paint, hit-test                           ← suit
```

- **The VNode tree** is the declarative description your component returns. It is cheap and
  rebuilt freely. vdom's DSL produces it; suit's DSL (`box`, `row`, `col`, …) is just a
  vocabulary of VNodes whose tags name render-object kinds.
- **The element tree** is vdom's reconciler at work: it diffs successive VNode trees, holds
  hook state (`useState`), and decides the minimal set of host mutations. This is entirely
  vdom — suit adds nothing here.
- **The RenderObject tree** is suit. Each node lays itself out, paints itself, and answers
  hit-tests. The reconciler creates and mutates these nodes through a `HostConfig`; every
  opaque host node it holds is really a `RenderObject`.

vdom is *host-agnostic*: the same reconciler and hooks drive the browser DOM in
[riposte](https://github.com/edadma/riposte) and the screen here. The only thing that
changes per host is the `HostConfig` binding and the leaf layer underneath it.

## The host binding

`SuitHostConfig` is the single seam where vdom meets suit's renderer — the analogue of
riposte's DOM host config, but creating retained render objects instead of DOM nodes. The
reconciler calls it to create elements, edit the tree, set properties, and register
listeners:

```scala
def createElement(tag: String, namespace: String | Null): AnyRef = tag match
  case "box"      => new RenderBox
  case "row"      => new RenderFlex(Axis.Horizontal)
  case "col"      => new RenderFlex(Axis.Vertical)
  case "padding"  => new RenderPadding
  case "sizedBox" => new RenderConstrained
  case "stack"    => new RenderStack
  case "text"     => new RenderText("")
  case _          => new RenderBox
```

Properties travel as **typed values**, not strings. vdom's `PropValue` channel carries a
`Color`, an `EdgeInsets`, an `Alignment`, or a layout enum with its real type straight to
the matching render-object field; nothing is stringified and re-parsed. DOM-shaped
`HostConfig` methods (`setStyle` with CSS, `setInnerHtml`, namespaces) have no meaning on a
pixel canvas and are no-ops.

## What a RenderObject does

Every render object implements three jobs:

```scala
abstract class RenderObject:
  def layout(constraints: Constraints): Unit                    // size self, position children
  def paint(canvas: Canvas, origin: Offset): Unit               // draw back-to-front
  def hitTest(point: Offset, origin: Offset): RenderObject|Null // deepest object under a point
```

The kinds map one-to-one onto the DSL: `RenderBox` (styled container), `RenderFlex` (row /
column), `RenderPadding`, `RenderConstrained` (sized box), `RenderStack` (z-stack /
align / center), `RenderText`, plus a `RenderRoot` at the top and a `RenderAnchor` for
vdom's fragment/portal/empty placeholders.

## The paint seam

Painting goes through a `Canvas` trait — the boundary between *what* to draw and *how*:

```scala
trait Canvas:
  def fillRect(rect: Rect, color: Color): Unit
  def strokeRect(rect: Rect, color: Color, width: Double): Unit
  def fillCircle(center: Offset, radius: Double, color: Color): Unit
  def line(a: Offset, b: Offset, width: Double, color: Color): Unit
  def drawText(origin: Offset, text: String, style: TextStyle): Unit
```

On Native, `SdlCanvas` drives [SDL3](https://sdl3.edadma.dev/). In tests, `RecordingCanvas`
captures the same calls so paint output can be asserted on. The same trait split lets
`TextMeasurer` size text off-device (a deterministic fake in tests, an SDL3_ttf-backed
measurer at runtime), which is what keeps the whole layout pass JVM-testable.

## Why JVM-testable matters

The layout engine, the render tree, and the geometry are **pure Scala with no SDL
dependency** — they live in `shared/` and cross to a JVM target whose only purpose is
tests. So `sbt suitJVM/test` exercises real layout, paint (against `RecordingCanvas`), and
input routing with no window and no native toolchain, the same way vdom's reconciler is
tested headlessly. This is the whole reason the layout engine was kept FFI-free: no Yoga,
no C solver, just a recursive constraint negotiation you can step through in a debugger.

## The runtime

`Suit.run` is the Native entry point. It owns the SDL window and renderer, installs the
host binding and the two scheduler seams (a microtask queue for re-renders, a macrotask
queue for passive effects), mounts the app, and runs the frame loop. The loop is the bridge
between vdom's React-style batched updates and a game-style render loop: vdom never paints
on its own — it enqueues work, the loop drains it, the tree is marked dirty, and the loop
repaints **only when something changed**.

One rendering detail is worth knowing: the frame is composited into a persistent off-screen
target texture and that whole texture is blitted to the window each iteration. Drawing
directly to the window's drawable and presenting every frame flickers on macOS/Metal (each
present latches a drawable from a rotating pool that may not be the frame just drawn); a
target texture removes that race and also fixes the first-frame blank.
