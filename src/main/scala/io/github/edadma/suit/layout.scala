package io.github.edadma.suit

// The layout protocol suit borrows from Flutter and SwiftUI — two greenfield
// frameworks that both rejected flexbox in favour of the same idea:
//
//   Constraints go down, sizes come up, the parent sets positions.
//
// A parent hands each child a `Constraints` (the range of sizes it may take); the
// child chooses its own `Size` within that range; the parent then positions the
// child by writing its offset. One rule, applied recursively — no monolithic
// multi-property solver.

/** The range of sizes a parent permits a child. A child must return a `Size` with
  * `minWidth <= w <= maxWidth` and `minHeight <= h <= maxHeight`.
  *
  * The *tight* vs *loose* distinction is load-bearing. An axis is **tight** when its
  * min equals its max — the parent is dictating that dimension ("be exactly this
  * wide"). It is **loose** when the min is 0 — the child may be as small as its
  * natural size, up to the max ("be as big as you need, no larger"). That single
  * distinction is what later expresses "fill the leftover space" versus "shrink to
  * fit", the basis of the spacer / flexible-child behaviour. */
final case class Constraints(minWidth: Double, maxWidth: Double, minHeight: Double, maxHeight: Double):

  /** Clamp `size` into this range. */
  def constrain(size: Size): Size =
    Size(
      size.width.max(minWidth).min(maxWidth),
      size.height.max(minHeight).min(maxHeight),
    )

  /** The largest size these constraints allow. */
  def biggest: Size = Size(maxWidth, maxHeight)

  /** The smallest size these constraints allow. */
  def smallest: Size = Size(minWidth, minHeight)

  def isTight: Boolean = minWidth == maxWidth && minHeight == maxHeight

object Constraints:

  /** Constraints that force exactly `size` on both axes. */
  def tight(size: Size): Constraints =
    Constraints(size.width, size.width, size.height, size.height)

  /** Constraints that allow anything up to `size` on both axes. */
  def loose(size: Size): Constraints =
    Constraints(0, size.width, 0, size.height)
