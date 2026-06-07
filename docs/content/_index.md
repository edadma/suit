---
title: suit
splash: true
heroTitle: A declarative UI toolkit for
heroHighlight: Scala Native
summary: Build native desktop UIs the way you build React components — declarative, reactive, composed from functions. suit pairs the vdom core (a React-shaped reconciler and hooks) with a Flutter-style constraint-layout engine that paints through SDL3. Write `col`, `row`, `box`, and `text`; let state drive the tree.
---

## What this is

**suit** is a declarative, reactive UI toolkit for [Scala Native](https://scala-native.org/).
It is the same idea Flutter draws between its framework and its rendering library, in two
existing pieces:

- **[vdom](https://github.com/edadma/riposte)** supplies the *framework* layer — the
  `VNode` model, the reconciler, and the hooks runtime (`useState`, components, context).
  It is host-agnostic: the same core drives the DOM in riposte and the screen here.
- **suit** supplies the *rendering* layer vdom deliberately leaves to its host — a
  retained tree of **RenderObjects** that lay themselves out, paint, and answer
  hit-tests — implemented over [SDL3](https://sdl3.edadma.dev/).

The result is "Flutter in Scala Native": components written with hooks, laid out by
constraint negotiation, rendered to a native window.

```scala
import io.github.edadma.vdom.*
import io.github.edadma.suit.*
import io.github.edadma.suit.dsl.*
import io.github.edadma.suit.widgets.*

val App = view {
  val (count, setCount, _) = useState(0)

  col(spacing = 16, mainAxisAlignment = MainAxisAlignment.Center)(
    text(s"count: $count", size = 24, color = Color.white),
    Button("Increment", () => setCount(count + 1)),
  )
}

@main def main(): Unit =
  Suit.run("counter", 320, 200)(App())
```

## Why constraint layout, not flexbox

suit's layout is the model Flutter and SwiftUI both adopted, deliberately *not* CSS
flexbox:

> **Constraints go down, sizes come up, the parent sets positions.**

A parent hands each child a range of allowed sizes; the child picks its own size within
that range; the parent positions it. One rule, applied recursively — no monolithic
multi-property solver, and no guessing which of a dozen flex properties wins. Because the
whole engine is pure Scala with no SDL dependency, the layout, the render tree, and the
geometry are **unit-tested headlessly on the JVM** against a recording canvas.

## What you get

- **A declarative DSL** — `box`, `row`, `col`, `text`, `spacer`, `stack`, `align`,
  `center`, `padding`, `sizedBox` — that emit vdom nodes.
- **Hooks** — `useState` and the rest of the vdom runtime, so a component's state survives
  re-renders and the tree reconciles in place.
- **An input model** with bubbling, pointer capture (drag), per-widget hover, keyboard
  focus, and wheel routing.
- **A small widget library** — `Button`, `Checkbox`, `Slider` — composed from the DSL.

## Where to go next

- **[Getting Started](/getting-started/)** — install Scala Native and SDL3, then run the demo.
- **[Guide](/guide/)** — the three-tree architecture, the constraint-layout protocol, and the input model.
- **[Reference](/reference/)** — the DSL builders and the widget library.
