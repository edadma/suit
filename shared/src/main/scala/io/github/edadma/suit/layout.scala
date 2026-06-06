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
    Size(constrainWidth(size.width), constrainHeight(size.height))

  /** Clamp a width into `[minWidth, maxWidth]`. */
  def constrainWidth(w: Double): Double = w.max(minWidth).min(maxWidth)

  /** Clamp a height into `[minHeight, maxHeight]`. */
  def constrainHeight(h: Double): Double = h.max(minHeight).min(maxHeight)

  /** The largest size these constraints allow. */
  def biggest: Size = Size(maxWidth, maxHeight)

  /** The smallest size these constraints allow. */
  def smallest: Size = Size(minWidth, minHeight)

  def isTight: Boolean = minWidth == maxWidth && minHeight == maxHeight

  /** Drop the minimums to zero, keeping the maximums — turns "fill me" into "be at
    * most this big". A child that should size to its content (rather than expand) is
    * laid out under the loosened form of its parent's constraints. */
  def loosen: Constraints = Constraints(0, maxWidth, 0, maxHeight)

  /** Shrink the available room by `insets` on each side, never below zero. The space a
    * padded child is given is its parent's constraints deflated this way. */
  def deflate(insets: EdgeInsets): Constraints =
    val maxW = (maxWidth - insets.horizontal).max(0)
    val maxH = (maxHeight - insets.vertical).max(0)
    Constraints(
      minWidth.min(maxW),
      maxW,
      minHeight.min(maxH),
      maxH,
    )

  /** Tighten one or both axes to an explicit extent, each clamped to stay within the
    * existing range. Unset axes are left untouched. This is how a sized box imposes a
    * fixed width/height while still respecting the room its parent gave it. */
  def tighten(width: Option[Double] = None, height: Option[Double] = None): Constraints =
    val (minW, maxW) = width match
      case Some(w) => val c = constrainWidth(w); (c, c)
      case None    => (minWidth, maxWidth)
    val (minH, maxH) = height match
      case Some(h) => val c = constrainHeight(h); (c, c)
      case None    => (minHeight, maxHeight)
    Constraints(minW, maxW, minH, maxH)

object Constraints:

  /** Constraints that force exactly `size` on both axes. */
  def tight(size: Size): Constraints =
    Constraints(size.width, size.width, size.height, size.height)

  /** Constraints that allow anything up to `size` on both axes. */
  def loose(size: Size): Constraints =
    Constraints(0, size.width, 0, size.height)

// The vocabulary a flex layout (row/column) is configured with. These mirror the
// SwiftUI/Flutter stack model: a main axis along which children are laid end to end,
// and a cross axis perpendicular to it.

/** Which way a [[RenderFlex]] stacks its children. */
enum Axis:
  case Horizontal, Vertical

/** How leftover space along the main axis is distributed once every child has its
  * size. `Start`/`End`/`Center` clump the children and leave the gap at one end or
  * split; the `Space*` values spread the gap between and around them. */
enum MainAxisAlignment:
  case Start, End, Center, SpaceBetween, SpaceAround, SpaceEvenly

/** How each child is placed on the cross axis within the flex's cross extent.
  * `Stretch` forces every child to the full cross extent; the others position a
  * child that is smaller than the extent. */
enum CrossAxisAlignment:
  case Start, End, Center, Stretch

/** Whether a flex shrinks to wrap its children along the main axis (`Min`) or expands
  * to fill the room its parent offered (`Max`). */
enum MainAxisSize:
  case Min, Max
