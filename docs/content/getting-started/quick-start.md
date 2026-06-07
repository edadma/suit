---
title: "Quick Start"
weight: 2
---

A complete suit program: define a component with hooks, lay it out with the DSL, and run
it in a native window.

## A counter

```scala
import io.github.edadma.suit.*
import io.github.edadma.suit.dsl.*
import io.github.edadma.suit.widgets.*

// A component is an ordinary vdom view: its state lives in hooks and survives
// re-renders, and it reconciles in place when state changes.
val App = view {
  val (count, setCount, _) = useState(0)

  col(spacing = 16, mainAxisAlignment = MainAxisAlignment.Center, crossAxisAlignment = CrossAxisAlignment.Center)(
    text(s"count: $count", size = 24, color = Color.white),
    row(spacing = 8)(
      Button("−", () => setCount(count - 1)),
      Button("+", () => setCount(count + 1)),
    ),
  )
}

@main def main(): Unit =
  Suit.run("counter", 320, 200)(App())
```

Run it with `sbt suitNative/run` (the call blocks on the frame loop for the window's
lifetime). `sbt suitNative/nativeLink` just produces the binary without launching it.

## How a frame happens

`Suit.run` opens the SDL window, installs the vdom host and the scheduler seams, mounts
your app, and runs the frame loop. A state change flows like this:

1. An event handler calls a `useState` setter.
2. vdom enqueues a re-render on the microtask seam.
3. The loop drains the queue: the reconciler re-renders and **mutates the render tree in
   place**.
4. The change bubbles a *dirty* flag to the root.
5. The next loop iteration repaints — and **only** then, so an idle UI costs nothing but
   event polling.

You never call "render" or "repaint" yourself; you change state and the tree follows.

## Laying things out

The DSL mirrors the constraint-layout primitives. A quick taste:

```scala
// A header bar over a flexible body — the body expands to fill the leftover height.
col()(
  box(bg = Color.rgb(0x222831), height = 48, padding = EdgeInsets.all(12))(
    text("My App", size = 20, color = Color.white),
  ),
  box(flex = 1, padding = EdgeInsets.all(20))(
    text("Body fills the rest.", color = Color.white),
  ),
)
```

`spacer()` eats leftover space (the replacement for flex-grow); `flex = n` on a child makes
it claim a share of the leftover main-axis space; `stack`/`align`/`center` position
children by fractional alignment. See the **[layout guide](/guide/layout/)** for the full
model and the **[DSL reference](/reference/dsl/)** for every builder.

## Choosing a font

Text is rendered through Cairo. `Suit.run` uses the **Inter** font bundled into the binary by
default — no installed font required, identical on every OS. Pass `fontPath` to load a
specific TrueType/OpenType file through FreeType instead:

```scala
Suit.run("my app", 640, 480, fontPath = "/path/to/MyFont.ttf")(App())
```

## Handling input

Widgets are pointer- and keyboard-driven out of the box. To handle raw input yourself, the
`box` builder takes typed handlers:

```scala
box(
  width = 100, height = 100, bg = Color.rgb(0x4dabf7),
  focusable   = true,
  onClick     = e => println(s"clicked at ${e.localX}, ${e.localY}"),
  onMouseEnter = _ => println("hover in"),
  onMouseLeave = _ => println("hover out"),
  onKeyDown   = e => if e.scancode == Key.Space then println("space"),
)()
```

See the **[input guide](/guide/input/)** for bubbling, pointer capture, and focus.
