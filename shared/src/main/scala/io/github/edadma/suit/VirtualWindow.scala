package io.github.edadma.suit

// The windowing arithmetic behind the virtualized list — which item indices a scroll
// position makes visible, so a list of ten thousand rows only ever builds the handful
// under the viewport. It is a pure function of the scroll offset, the viewport height, and
// the (fixed) item extent, with no widget or device in sight, so the windowing is unit-
// tested directly and the [[widgets.virtualList]] component is left as thin wiring over it.

/** A half-open range of item indices `[first, last)` to instantiate, plus the pixel offset
  * at which the first of them should be drawn (its top relative to the viewport top — zero
  * or negative, since the first visible item usually starts just above the fold). */
final case class VirtualRange(first: Int, last: Int, offsetY: Double):
  def isEmpty: Boolean = first >= last
  def length: Int      = last - first

object VirtualWindow:

  /** The items visible (plus `overscan` extra above and below to cover sub-item scrolling
    * and a little look-ahead) when a list of `itemCount` items, each `itemExtent` pixels
    * tall, is scrolled by `scroll` pixels inside a `viewportH`-tall viewport.
    *
    * `scroll` is clamped to `[0, maxScroll]` first, so an over-scroll past the end still
    * yields a valid window. With a non-positive extent or an empty list the range is empty.
    * `offsetY` is where the first returned item's top sits relative to the viewport: it
    * folds the sub-item remainder and the overscan rows into a single translation the
    * caller applies to the whole windowed block, so partial scrolling is smooth. */
  def visibleRange(
      scroll:     Double,
      viewportH:  Double,
      itemExtent: Double,
      itemCount:  Int,
      overscan:   Int = 3,
  ): VirtualRange =
    if itemExtent <= 0.0 || itemCount <= 0 || viewportH <= 0.0 then VirtualRange(0, 0, 0.0)
    else
      val total     = itemCount * itemExtent
      val maxScroll  = math.max(0.0, total - viewportH)
      val s          = math.max(0.0, math.min(scroll, maxScroll))
      val firstFull  = math.floor(s / itemExtent).toInt
      val first      = math.max(0, firstFull - overscan)
      val visibleN   = math.ceil(viewportH / itemExtent).toInt + 1
      val last       = math.min(itemCount, firstFull + visibleN + overscan)
      // The first returned item starts `first * itemExtent` from the content top; relative to
      // the viewport (which is scrolled down by `s`) that is this much above the fold.
      val offsetY = first * itemExtent - s
      VirtualRange(first, last, offsetY)

  /** The furthest a list of `itemCount` items of `itemExtent` each can scroll inside a
    * `viewportH`-tall viewport — the overflow, or zero when it all fits. */
  def maxScroll(viewportH: Double, itemExtent: Double, itemCount: Int): Double =
    math.max(0.0, itemCount * itemExtent - viewportH)
