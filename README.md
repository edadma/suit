# Suit

A retained-mode widget toolkit, written in Scala 3 as a reference implementation that translates cleanly to [Sysl](../trisc/sysl-reference.md).

You build a tree of widget objects, set their properties, attach event handler closures, and hand the root to the toolkit. Suit owns layout, rendering, and event dispatch.

The Scala source deliberately avoids language features that have no Sysl equivalent, so each Scala file maps almost line-for-line to a `.lsysl` file. The Swing renderer is the only platform-specific piece and is replaced with a SLIX/DrawEngine renderer in the Sysl port.

## Sysl-translatable features used

- Plain `class` (mutable widget) and `case class` (value type) with primitive fields → `struct`
- `sealed trait` + `final class` hierarchy → sysl tagged-union enum (or per-struct dispatch)
- `enum` with no fields → simple sysl `enum`
- `var` and `val` (no `lazy val`)
- `def` with explicit parameter and return types
- Default parameters, named arguments
- Closures used for event handlers (sysl supports closures)
- `if/else`, `while`, `for (i <- 0 until n)`
- Pattern matching on sealed hierarchies and primitives
- `scala.collection.mutable.ArrayBuffer` (= sysl `[]T` slice + `append`)
- `Array[T]` of fixed size (= sysl `[n]T`)
- Methods on classes that just touch their own fields (= sysl `Struct.method`); no virtual dispatch — algorithms over Widget pattern-match externally

## Features deliberately avoided

- `given` / `using` / implicit conversions / implicit classes
- Type classes, higher-kinded types, opaque types
- For-comprehensions and monadic combinators
- `lazy val`, by-name parameters
- Tuple field access (`._1`, `._2`); use named struct fields
- `Option`/`Either` chaining with `map`/`flatMap`/`getOrElse`
- Inheritance beyond a single sealed hierarchy
- Anonymous PartialFunctions
- String interpolation beyond `s"..."`

## Run

```
sbt run
```
