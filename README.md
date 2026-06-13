# suit

![Maven Central](https://img.shields.io/maven-central/v/io.github.edadma/suit_native0.5_3)
[![Last Commit](https://img.shields.io/github/last-commit/edadma/suit)](https://github.com/edadma/suit/commits)
![GitHub](https://img.shields.io/github/license/edadma/suit)
![Scala Version](https://img.shields.io/badge/Scala-3.8.4-blue.svg)
![Scala Native Version](https://img.shields.io/badge/Scala_Native-0.5.12-blue.svg)

A declarative, reactive UI toolkit for [Scala Native](https://scala-native.org/) that
renders with [Cairo](https://www.cairographics.org/) on top of
[SDL3](https://sdl3.edadma.dev/). suit pairs the host-agnostic
[**vdom**](https://github.com/edadma/riposte) core — a React-shaped reconciler and hooks —
with a Flutter-style **constraint-layout** engine and a retained render tree. Write
components with `useState`, lay them out with `col` / `row` / `box` / `text`, and let state
drive the screen. In short: *Flutter in Scala Native*.

> **Status:** in active development, not yet published to Maven Central. Build and run from
> the repository checkout (see [Documentation](#documentation)).

## Documentation

Full documentation — getting started, the three-tree architecture, the constraint-layout
model, the input system, and the DSL and widget reference — is at
**[suit.edadma.dev](https://suit.edadma.dev/)** (source in [`docs/`](docs/)). This README is
a quick taste; the site is the reference.

## The idea

suit is one layer of a three-layer system, the same split Flutter draws between its widgets,
elements, and render objects:

| Layer            | What it is                                   | Provided by |
|------------------|----------------------------------------------|-------------|
| VNodes / widgets | what you write: `col(...)(box(...), text())` | vdom        |
| Elements         | the reconciled, stateful instance tree       | vdom        |
| RenderObjects    | layout, paint, hit-test                      | **suit**    |

vdom is host-agnostic — the same core drives the browser DOM in
[riposte](https://github.com/edadma/riposte) and the screen here. suit supplies only the
rendering layer underneath — drawn with Cairo, presented through SDL3.

## Why constraint layout, not flexbox

The model Flutter and SwiftUI both adopted:

> **Constraints go down, sizes come up, the parent sets positions.**

A parent hands each child a range of allowed sizes; the child picks its own size within that
range; the parent positions it. One recursive rule — no monolithic solver, no guessing which
flex property wins. The whole engine is pure Scala with **no SDL dependency**, so layout,
the render tree, and geometry are unit-tested headlessly on the JVM against a recording
canvas.

## Widgets

A small library of controls — all theme-driven and animated, composed from the DSL
primitives: buttons, checkboxes, switches, radio groups, sliders, tabs, progress bars,
badges, dividers, alerts, cards, tooltips, menus, modal dialogs, and a resizable `splitter`.
Text entry comes in a
single-line `TextField` and a multi-line `TextArea`, and large data sets are served by a
**virtualized data grid** — `dataTable` over a `virtualList` that only builds the rows under
the viewport, so a thousand-row result stays smooth. See the
[widget reference](https://suit.edadma.dev/reference/widgets/).

For custom drawing there are two escape hatches: `canvas` hands your routine suit's `Canvas`
each frame (a chart, a game, a sketch pad), and `surface` wraps an **application-owned image
surface** you draw into yourself with the full underlying graphics API (raw Cairo on Native) and
re-blit on demand via a `SurfaceHandle` — the retained route for content suit's `Canvas` doesn't
cover. See the [DSL reference](https://suit.edadma.dev/reference/dsl/).

## A counter

```scala
import io.github.edadma.suit.*
import io.github.edadma.suit.dsl.*
import io.github.edadma.suit.widgets.*

val App = view {
  val (count, setCount, _) = useState(0)

  col(spacing = 16, mainAxisAlignment = MainAxisAlignment.Center, crossAxisAlignment = CrossAxisAlignment.Center)(
    text(s"count: $count", size = 24, color = Color.white),
    Button("Increment", () => setCount(count + 1)),
  )
}

@main def main(): Unit =
  Suit.run("counter", 320, 200)(App())
```

## Building

suit depends on a [riposte](https://github.com/edadma/riposte) checkout next to it (for the
`vdom` source dependency) and on the system SDL3 and Cairo libraries.

```bash
# native libraries (macOS / Homebrew)
brew install sdl3 cairo

# both repos side by side
git clone https://github.com/edadma/riposte.git
git clone https://github.com/edadma/suit.git

sbt suitJVM/test     # headless layout/paint/input suite (no window, no native toolchain)
sbt suitNative/run   # build and launch the widget demo
```

## License

ISC.

Bundles the [Inter](https://rsms.me/inter/) font (Inter 18pt Regular) under the SIL Open
Font License — see [`fonts/OFL.txt`](fonts/OFL.txt).
