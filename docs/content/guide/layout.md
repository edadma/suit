---
title: "Layout"
weight: 2
---

suit lays out with the protocol Flutter and SwiftUI both adopted — two greenfield
frameworks that independently rejected flexbox for the same idea:

> **Constraints go down, sizes come up, the parent sets positions.**

A parent hands each child a `Constraints` (the range of sizes it may take); the child
chooses its own `Size` within that range; the parent then positions the child by writing
its offset. One rule, applied recursively — no monolithic multi-property solver.

## Constraints

`Constraints` is a min/max range on each axis:

```scala
final case class Constraints(minWidth: Double, maxWidth: Double, minHeight: Double, maxHeight: Double)
```

The **tight vs loose** distinction is the load-bearing one:

- An axis is **tight** when its min equals its max — the parent is *dictating* that
  dimension ("be exactly this wide"). `Constraints.tight(size)` forces both axes.
- An axis is **loose** when its min is 0 — the child may be as small as its natural size,
  up to the max ("be as big as you need, no larger"). `Constraints.loose(size)` and the
  `.loosen` method drop the minimums to zero.

That single distinction is what later expresses "fill the leftover space" versus "shrink to
fit" — the basis of spacers and flexible children.

Useful operations:

```scala
c.constrain(size)             // clamp a size into the range
c.tighten(width, height)      // force one/both axes to an explicit extent (clamped)
c.deflate(insets)             // shrink the room by padding on each side
c.loosen                      // min → 0, keep max (turn "fill" into "at most")
c.biggest / c.smallest        // the largest / smallest allowed size
```

## How each primitive sizes itself

Every render object applies the one rule its own way. Knowing each makes layouts
predictable.

### box

A styled container. An axis with an explicit `width`/`height` takes that value; otherwise
the axis **fills** the space offered under a tight constraint and **shrinks to its content**
under a loose one.

> A `box` at the root fills the window; the same `box` inside a `row` wraps its contents —
> the difference is entirely the constraint its parent handed it.

It lays its single child in the room left after `padding` (loosened, so the child sizes to
content), places it inside the padding, and reports the child's size grown by the insets.

### row / col (RenderFlex)

Children are laid end to end along the **main axis** (horizontal for `row`, vertical for
`col`) and sized across the **cross axis**, in two passes:

1. **Inflexible children** (`flex = 0`) are laid out at their natural main size.
2. The leftover main space, after the inflexible children and the fixed `spacing` gaps, is
   divided among the **flexible children** in proportion to their `flex`; each gets a tight
   main constraint equal to its share.

Then:

- `mainAxisSize` — `Max` fills the parent along the main axis, `Min` wraps the children.
- `mainAxisAlignment` — distributes any remaining slack: `Start` / `End` / `Center` clump
  the children; `SpaceBetween` / `SpaceAround` / `SpaceEvenly` spread the gap.
- `crossAxisAlignment` — places each child across: `Start` / `End` / `Center`, or `Stretch`
  to force every child to the full cross extent.

When the main axis is *unbounded* (e.g. a row inside a horizontally-loose parent) there is
nothing to distribute, so flexible children are treated as inflexible.

### spacer

A flexible empty gap — the replacement for flex-grow. Inside a row or column it eats
leftover space in proportion to its `flex`, pushing its siblings apart. It is just a `box`
with no appearance and a flex factor.

### sizedBox (RenderConstrained)

Forces a fixed size onto its child (SwiftUI's `.frame`, Flutter's `SizedBox`). Each given
axis becomes a tight constraint on the child; an unset axis passes the parent's constraint
straight through. It has no appearance — pure layout.

### padding

Insets its child by `padding` on each side: the child is laid out in the parent's
constraints deflated by the insets, placed at the top-left inside the padding, and this
object reports the child's size grown by the insets.

### stack / align / center

A z-ordered overlay: children stack back-to-front in declaration order, each positioned by
an `Alignment`. The stack fills bounded space, otherwise wraps its largest child. `align`
is a one-child stack at a chosen alignment; `center` is `align(Alignment.center)`. This is
the basis for modals, tooltips, and badges once portals layer on top.

### text

Sizes itself by asking the installed `TextMeasurer` how big its string is in its
`TextStyle`, clamped into the parent's constraints. Layout is single-line for now.

## Alignment

`Alignment(x, y)` is a fractional coordinate independent of box size: `(-1, -1)` is the
top-left corner, `(0, 0)` the centre, `(1, 1)` the bottom-right. The same value positions a
child in a container of any size — exactly what stacks, centring, and a slider thumb need.
Named constants cover the nine compass points (`Alignment.topLeft`, `.center`,
`.bottomRight`, …).

## A worked example

```scala
col()(                                            // fills the window (tight from root)
  box(bg = header, height = 48)(                  // fixed-height bar
    text("Title", color = Color.white),
  ),
  row(flex = 1)(                                  // body claims the leftover height
    box(width = 200, bg = sidebar)(),             // fixed-width sidebar
    box(flex = 1, bg = content)(),                // content fills the rest of the width
  ),
)
```

The column gets a tight constraint from the root, so it fills the window. The header takes
its fixed 48px; the row, with `flex = 1`, claims the remaining height. Inside the row the
sidebar takes its fixed 200px and the content box, with `flex = 1`, fills what's left.
