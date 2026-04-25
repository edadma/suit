# Suit

A React-style declarative UI toolkit, written in Scala 3 as a reference implementation that translates cleanly to [Sysl](https://sysl.sh/).

You describe the UI as an immutable tree of `View` values; a reconciler diffs that tree against a live tree of mutable `Node` fibers, preserving per-component state across re-renders. Function components hold state via positional hooks (`useState`, `useReducer`, `useMemo`, `useCallback`, `useRef`, `useEffect`, `useLayoutEffect`, `useId`, `useContext`).

The Scala source deliberately avoids language features that have no Sysl equivalent, so each Scala file maps almost line-for-line to a `.lsysl` file. The Swing host is the only platform-specific piece; in the Sysl port it's replaced with a SLIX/DrawEngine renderer.

## Quick taste

```scala
import io.github.edadma.suit.*

private def Counter(label: String): Widget =
  component("Counter") { hooks =>
    val (count, setCount, updateCount) = hooks.useState(0)

    Stack(Axis.Horizontal, gap = 8, children = Array(
      Text(s"$label: $count"),
      Button("-", () => updateCount(_ - 1)),
      Button("+", () => updateCount(_ + 1)),
      Button("Reset", () => setCount(0)),
    ))
  }

object App:
  val engine = new Engine
  def view(): View =
    Stack(Axis.Vertical, padding = Insets.all(16), gap = 10, children = Array(
      Component(Counter("Score")),
      Component(Counter("Lives")),
    ))

  def main(args: Array[String]): Unit =
    engine.setRoot(view())
    new SwingHost("Demo", 480, 320, engine).show()
```

## What's in the box

**Widgets** — `Text`, `Button`, `Input`, `Checkbox`, `Stack` (vertical/horizontal flex), `Spacer`. All composable via `Stack` children + `Component(widget)` for custom widgets.

**Reconciler** — diffs an immutable `View` tree against a live `Node` tree. Reuses nodes by kind+position, supports keyed list reconciliation across reorders/inserts/removes, unmounts subtrees child-first so parent cleanups can still reference child state.

**Hooks** — full React-style positional state on per-component fibers:

| Hook | Behavior |
|---|---|
| `useState[T](initial)` | Returns `(value, setValue, updateFn)` so call sites can pick value-set or functional update with `_` to discard the unused setter |
| `useReducer(reducer, initial)` | Reducer-driven state on top of `useState` |
| `useMemo(compute, deps)` / `useCallback(fn, deps)` | Memoize across renders by deps array |
| `useRef[T](initial)` | Stable mutable cell that does not trigger re-renders |
| `useEffect(body, deps)` | Side effect that fires *after commit*, with cleanup before next run / on unmount |
| `useLayoutEffect(body, deps)` | Same shape but fires after layout, before render emits draw commands |
| `useId()` | Stable per-fiber unique string |
| `useContext(ctx)` | Reads the nearest `ctx.provide(value, child)` — no prop drilling |

**Refs forwarding** — `WithRef(ref, child)` wires a `Ref[Node]` to the produced node; `Engine.focusNode(node)` for imperative focus from a hook (e.g. auto-focus on mount).

**Other reconciler features** — `Keyed(key, child)` for list reconciliation, `Fragment(children)` for multi-child returns, `Context[T]` + `createContext(default)` + `ctx.provide(value, child)`.

**Event-driven repaint loop** — zero idle CPU. Repaints fire on input events, window resize, and active animations only. Caret blink is real-time-driven (no flicker on hover).

**Visual primitives** — RGBA colors with alpha, `Insets`, `Shadow` (for focus-ring glow), rounded-corner rect commands, time-based caret blink, horizontal text-field scrolling.

## Sysl-translatable features used

- Plain `class` (mutable Node / Widget) and `case class` (value types and View variants) → sysl `struct`
- `sealed trait` + `final case class` hierarchy → sysl tagged-union enum
- `def` with explicit parameter and return types; default parameters; named arguments; tuple destructuring
- Closures (sysl supports them — used for event handlers + render closures)
- Pattern matching on sealed hierarchies and primitives
- `if/else`, `while`, `for (i <- 0 until n)`
- `scala.collection.mutable.ArrayBuffer` ≈ sysl `[]T` slice + `append`
- `Array[T]` of fixed size ≈ sysl `[n]T`
- Methods on classes that touch their own fields ≈ sysl `Struct.method`. Algorithms across `Node` / `View` pattern-match externally — no virtual dispatch.

## Features deliberately avoided

- `given` / `using` / implicit conversions / implicit classes
- Type classes, higher-kinded types, opaque types
- For-comprehensions and monadic combinators (no `Option#map`/`flatMap` chaining etc.)
- `lazy val`, by-name parameters
- Anonymous-tuple field access (`._1`, `._2`); structs/case classes only
- Inheritance beyond a single sealed hierarchy
- Anonymous PartialFunctions

## Testing

A headless `TestHost` (`io.github.edadma.suit.testkit.TestHost`) drives the engine without any AWT, exposes event simulation (click, type, press, focus, hover), tree queries (`findText`, `findButton`, `findInput`, `findCheckbox`, generic `findAll`), and a controllable clock for time-driven behavior.

```
sbt test
```

The included `SuitSpec` (30 tests) exercises every widget, every hook, the reconciler's keyed/positional matching, effect timing, child-first unmount ordering, and ref forwarding. Adding a new feature without a regression-test entry is the easy way to break things silently — don't.

## Run the demo

```
sbt run
```

The demo exercises every widget, hooks-driven counters, a context-driven theme switch, a keyed list with shuffle/add/remove, useId-based ID fields, and an auto-focus input.

## Status

Working but pre-1.0. API may shift as the Sysl translation surfaces things that don't lower well.
