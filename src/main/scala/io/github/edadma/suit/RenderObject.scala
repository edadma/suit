package io.github.edadma.suit

import scala.collection.mutable

// The render layer vdom deliberately leaves to its host: a retained tree of objects
// that lay themselves out, paint, and answer hit-tests. vdom's reconciler creates
// and mutates these through `SuitHostConfig` — every opaque host node the reconciler
// holds is really a RenderObject. This is Flutter's RenderObject and SwiftUI's
// layout node; it is the one layer suit builds, with vdom providing everything above.
//
// The layout contract is the constraint-negotiation protocol (see [[Constraints]]):
// `layout` receives the constraints a parent imposes, sets this object's `size`, and
// positions each child by writing the child's `offset`. The skeleton's layout is
// intentionally trivial (a box fills the constraints it is handed); the real
// protocol — rows, columns, spacers, padding, alignment — arrives with the layout
// engine.
abstract class RenderObject:
  var parent: RenderObject | Null               = null
  val children: mutable.ArrayBuffer[RenderObject] = mutable.ArrayBuffer.empty
  var offset: Offset                            = Offset.zero // position within the parent
  var size: Size                                = Size.zero   // chosen during layout

  /** Event handlers registered on this object, keyed by event name (e.g. `"click"`).
    * The runtime's input dispatch looks them up after a hit-test. */
  val handlers: mutable.Map[String, Any => Unit] = mutable.Map.empty

  /** Constraints down, size up. An implementation must set `size` and position any
    * children. */
  def layout(constraints: Constraints): Unit

  /** Paint this object at `origin` — its absolute top-left — then its children. The
    * base implementation paints children only; objects with their own appearance
    * override, draw themselves first, then call [[paintChildren]]. */
  def paint(canvas: Canvas, origin: Offset): Unit = paintChildren(canvas, origin)

  protected def paintChildren(canvas: Canvas, origin: Offset): Unit =
    var i = 0
    while i < children.length do
      val child = children(i)
      child.paint(canvas, origin + child.offset)
      i += 1

  // --- tree edits (driven by the reconciler via SuitHostConfig) --------------

  def insertChild(child: RenderObject, before: RenderObject | Null): Unit =
    child.parent = this
    before match
      case b: RenderObject =>
        val i = children.indexOf(b)
        if i < 0 then children += child else children.insert(i, child)
      case null => children += child
    markDirty()

  def removeChild(child: RenderObject): Unit =
    children -= child
    child.parent = null
    markDirty()

  /** Signal that the frame is stale. Propagates up to the root, which holds the flag
    * the frame loop reads. The skeleton makes no distinction between needs-layout and
    * needs-paint — any change requests a fresh frame; splitting the two is a later
    * optimisation. */
  def markDirty(): Unit =
    val p = parent
    if p != null then p.markDirty()

  /** The deepest RenderObject whose bounds contain `point` (absolute coordinates),
    * or null if `point` is outside this object. `origin` is this object's absolute
    * top-left. Children are tested topmost-first (later siblings paint last, so they
    * are on top); a hit returns self only when no child claims the point. */
  def hitTest(point: Offset, origin: Offset): RenderObject | Null =
    if !Rect.at(origin, size).contains(point.x, point.y) then null
    else
      var i = children.length - 1
      while i >= 0 do
        val child = children(i)
        val hit   = child.hitTest(point, origin + child.offset)
        if hit != null then return hit
        i -= 1
      this

/** A rectangle with an optional background colour. With no explicit size it expands
  * to fill the constraints it is handed — Flutter's behaviour for a coloured box with
  * no fixed size. Explicit sizing, padding, and borders come with the DSL. */
final class RenderBox extends RenderObject:
  var background: Color | Null = null

  def layout(constraints: Constraints): Unit =
    size = constraints.biggest
    var i = 0
    while i < children.length do
      val child = children(i)
      child.layout(Constraints.tight(size))
      child.offset = Offset.zero
      i += 1

  override def paint(canvas: Canvas, origin: Offset): Unit =
    background match
      case c: Color => canvas.fillRect(Rect.at(origin, size), c)
      case null     => ()
    paintChildren(canvas, origin)

/** Text. A real implementation measures and paints glyphs via sdl3_ttf; until that
  * lands it carries its string and occupies no space. */
final class RenderText(var text: String) extends RenderObject:
  def layout(constraints: Constraints): Unit             = size = Size.zero
  override def paint(canvas: Canvas, origin: Offset): Unit = ()

/** A non-visual node that only holds a position in the sibling order — vdom anchors
  * fragments, portals, and empty renders on one. Zero size, never painted, never a
  * hit-test target. */
final class RenderAnchor(val label: String) extends RenderObject:
  def layout(constraints: Constraints): Unit                              = size = Size.zero
  override def paint(canvas: Canvas, origin: Offset): Unit                = ()
  override def hitTest(point: Offset, origin: Offset): RenderObject | Null = null

/** The top of the render tree — the window surface. It holds the live "needs a frame"
  * flag that the runtime's loop reads; any [[RenderObject.markDirty]] in the tree
  * bubbles here and sets it. It lays out its single child tight to the window size. */
final class RenderRoot(var windowSize: Size) extends RenderObject:
  var dirty: Boolean = true // paint once on startup

  override def markDirty(): Unit = dirty = true

  def layout(constraints: Constraints): Unit =
    size = windowSize
    var i = 0
    while i < children.length do
      val child = children(i)
      child.layout(Constraints.tight(size))
      child.offset = Offset.zero
      i += 1
