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
// positions each child by writing the child's `offset`. Each kind of object applies
// that one rule its own way — a box fills or wraps, a row distributes space along its
// main axis, padding insets its child — and the protocol composes those local choices
// into a whole layout with no global solver.
abstract class RenderObject:
  var parent: RenderObject | Null                 = null
  val children: mutable.ArrayBuffer[RenderObject] = mutable.ArrayBuffer.empty
  var offset: Offset                              = Offset.zero // position within the parent
  var size: Size                                  = Size.zero   // chosen during layout

  /** Flex factor read by an enclosing [[RenderFlex]]: `0` means inflexible (the object
    * takes its natural size), a positive value means it expands to claim that share of
    * the leftover space along the row/column's main axis. It is parent data — it only
    * means anything inside a flex — so it lives on every object rather than on a
    * dedicated wrapper, the way `spacer` and flexible children are expressed. */
  var flex: Int = 0

  /** Event handlers registered on this object, keyed by event name (e.g. `"click"`).
    * The runtime's input dispatch looks them up after a hit-test. */
  val handlers: mutable.Map[String, Any => Unit] = mutable.Map.empty

  /** Whether this object can take keyboard focus. A press routes focus to the nearest
    * focusable ancestor of the hit object (see [[FocusManager.pointerFocus]]), so the
    * widgets that handle keys (buttons, checkboxes, sliders, text fields) set this on
    * their outer object. */
  var focusable: Boolean = false

  /** Whether this object wants text-input events while focused. The runtime starts the
    * platform's text-input session when focus lands on an object with this set (and stops it
    * otherwise), so a text field's outer object sets it; plain focusable widgets leave it
    * false and only see key events. */
  var acceptsText: Boolean = false

  /** Whether this object (and its whole subtree) is transparent to hit-testing — Flutter's
    * `IgnorePointer`. A click passes straight through it to whatever is behind, so it claims
    * no pointer. A tooltip floating in the overlay sets this so it never intercepts the
    * clicks meant for the content it hovers over. */
  var ignorePointer: Boolean = false

  /** The pointer shape this object asks for while the cursor is over it, or `null` for "no
    * preference" — the resolver then keeps looking up the ancestor chain (see
    * [[PointerRouter.cursorAt]]), so a button sets [[Cursor.Pointer]] once and its inner label
    * inherits it. [[Cursor.Default]] is a real preference (force the arrow), distinct from `null`
    * (inherit). Only the native runtime reads it, to set the process-wide system cursor. */
  var cursor: Cursor | Null = null

  /** The payload this object carries as a drag source, or `null` when it is not draggable. A press over
    * an object with a non-null payload arms a drag (see [[PointerRouter]]); the value — whatever the app
    * put here — rides along and is handed to the drop target's `onDragOver`/`onDrop` as
    * [[DragEvent.payload]]. suit treats it opaquely, so it can be an id, a model object, anything. */
  var dragPayload: Any | Null = null

  /** Whether this object caches and repaints independently of the rest of the tree — a
    * **repaint boundary** (Flutter's `RepaintBoundary`). When only the content inside a
    * boundary changes, the runtime re-rasterises just that boundary's region and leaves
    * the rest of the window's pixels untouched, so an animating canvas does not force the
    * static UI around it to redraw. The root is a boundary (the whole window); a canvas is
    * a nested one. Boundary content is assumed to cover its bounds opaquely. */
  def isRepaintBoundary: Boolean = false

  /** Whether this boundary's content is driven imperatively rather than by the reconciler —
    * a **live surface** that must be re-rasterised on every frame the loop runs (a canvas
    * stepped by `useFrame`, whose painter reads mutable state the tree cannot see). Implies
    * [[isRepaintBoundary]]. A frame request ([[Repaint]]) re-rasterises every live surface. */
  def isLiveSurface: Boolean = false

  /** Set when this boundary's subtree changed since it last painted, so the next frame
    * re-rasterises it. Only meaningful on a [[isRepaintBoundary]]; [[markDirty]] sets it on
    * the nearest enclosing boundary. A fresh boundary starts dirty so it paints once. */
  var needsRepaint: Boolean = true

  /** The text-style values this object contributes to the cascade. A [[RenderText]]
    * descendant inherits any field set here unless a nearer ancestor or its own explicit
    * value overrides it (see [[TextStyleAttrs]]). The default carrier sets nothing, so an
    * object is transparent to the cascade until something gives it text attributes. */
  var textAttrs: TextStyleAttrs = TextStyleAttrs.empty

  /** Constraints down, size up. An implementation must set `size` and position any
    * children. */
  def layout(constraints: Constraints): Unit

  /** Paint this object at `origin` — its absolute top-left — then its children. The
    * base implementation paints children only; objects with their own appearance
    * override, draw themselves first, then call [[paintChildren]]. */
  def paint(canvas: Canvas, origin: Offset): Unit = paintChildren(canvas, origin)

  /** The clip this object imposes on its descendants, in absolute coordinates, or `None`
    * if it does not clip. A scroll view confines its content to its viewport, a box with
    * `clipContent` to its (rounded) rect; the partial-repaint path
    * ([[Compositor.repaintBoundary]]) replays these so a boundary nested inside them is
    * confined exactly as a full repaint confines it. Valid only after a layout pass. */
  def clipShape: Option[(Rect, BorderRadius)] = None

  protected def paintChildren(canvas: Canvas, origin: Offset): Unit =
    var i = 0
    while i < children.length do
      val child = children(i)
      child.paint(canvas, origin + child.offset)
      i += 1

  /** The first child, or null — the subject of the single-child layouts (box, padding,
    * sized box). */
  protected def soleChild: RenderObject | Null =
    if children.nonEmpty then children(0) else null

  /** This object's absolute top-left, summing `offset` up the parent chain to the root.
    * Input routing uses it to express a pointer's position in the receiving object's own
    * coordinate space. Valid only after a layout pass has positioned the tree. */
  def absoluteOffset: Offset =
    var acc                     = Offset.zero
    var n: RenderObject | Null = this
    while n != null do
      val r = n.asInstanceOf[RenderObject]
      acc = acc + r.offset
      n = r.parent
    acc

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

  /** Signal that the frame is stale. Walks to the root, marking the **nearest enclosing
    * repaint boundary** so only that region re-rasterises, and setting the root's live
    * "needs a frame" flag the loop reads. A change in the static UI reaches the root
    * boundary (a full repaint); a change inside a canvas stops at the canvas boundary (a
    * partial repaint of just its region). No needs-layout vs needs-paint distinction is
    * made yet — any change requests a fresh frame; splitting the two is a later
    * optimisation. */
  def markDirty(): Unit =
    var node: RenderObject | Null = this
    var boundaryMarked            = false
    while node != null do
      val r = node.asInstanceOf[RenderObject]
      if !boundaryMarked && r.isRepaintBoundary then
        r.needsRepaint = true
        boundaryMarked = true
      r match
        case root: RenderRoot => root.dirty = true
        case _                => ()
      node = r.parent

  /** The deepest RenderObject whose bounds contain `point` (absolute coordinates),
    * or null if `point` is outside this object. `origin` is this object's absolute
    * top-left. Children are tested topmost-first (later siblings paint last, so they
    * are on top); a hit returns self only when no child claims the point. */
  def hitTest(point: Offset, origin: Offset): RenderObject | Null =
    if ignorePointer then null
    else if !Rect.at(origin, size).contains(point.x, point.y) then null
    else
      var i = children.length - 1
      while i >= 0 do
        val child = children(i)
        val hit   = child.hitTest(point, origin + child.offset)
        if hit != null then return hit
        i -= 1
      this

/** The styled container — suit's workhorse rectangle, the equivalent of Flutter's
  * `Container` or a SwiftUI view with a background. It paints a drop shadow, a (possibly
  * gradient, possibly rounded) background, its children, and an optional border, all at an
  * optional `opacity`; it insets its child by `padding`; and it sizes itself by a single
  * rule: an axis with an explicit `width`/`height` takes that value; otherwise the axis
  * **fills** the space offered when the parent dictates one (a tight constraint) and
  * **shrinks to its content** when the parent leaves it free (a loose constraint). That is
  * why a box at the root fills the window, while a box inside a row wraps its contents.
  *
  * The decoration is a general set of independent properties (a `Paint` background, a
  * `Paint` border, a corner radius, a shadow, an opacity), not a fixed widget look — the
  * styling system applies them to this one generic box. */
final class RenderBox extends RenderObject:
  var background: Paint | Null   = null
  var border: Paint | Null       = null
  var borderWidth: Double        = 0.0
  var borderRadius: BorderRadius = BorderRadius.zero
  var shadow: Shadow | Null      = null
  var opacity: Double            = 1.0
  var width: Option[Double]      = None
  var height: Option[Double]     = None
  var padding: EdgeInsets        = EdgeInsets.zero
  var clipContent: Boolean       = false

  // The size this box was at its previous layout, so a `resize` handler fires only when the size
  // actually changes — the push-based equivalent of a ResizeObserver, used by a widget whose
  // content depends on its own width (a soft-wrapping editor re-wraps when its pane is resized).
  private var lastSize: Size = Size.zero

  override def clipShape: Option[(Rect, BorderRadius)] =
    if clipContent then Some((Rect.at(absoluteOffset, size), borderRadius)) else None

  def layout(constraints: Constraints): Unit =
    val outer = constraints.tighten(width, height)
    // The child sizes to its content within the room left after padding, so the box
    // can wrap it; loosening turns the box's own "fill" pressure off for the child.
    val inner = outer.deflate(padding).loosen
    val content = soleChild match
      case ch: RenderObject =>
        ch.layout(inner)
        ch.offset = Offset(padding.left, padding.top)
        Size(ch.size.width + padding.horizontal, ch.size.height + padding.vertical)
      case null =>
        Size(padding.horizontal, padding.vertical)
    val natural = Size(width.getOrElse(content.width), height.getOrElse(content.height))
    size = outer.constrain(natural)

    // Notify a `resize` listener when the laid-out size changes. The callback typically schedules
    // a re-render (a `useState` setter), which vdom batches, so calling it here is safe.
    if size != lastSize then
      lastSize = size
      handlers.get("resize").foreach(_.apply(size))

  override def paint(canvas: Canvas, origin: Offset): Unit =
    val rect    = Rect.at(origin, size)
    val rounded = !borderRadius.isZero
    val faded   = opacity < 1.0

    // A translucent box composites itself and its children as one group, so overlapping
    // shapes inside it don't double-expose through the fade.
    if faded then canvas.pushOpacity(opacity)

    shadow match
      case s: Shadow => canvas.drawShadow(rect, borderRadius, s)
      case null      => ()

    background match
      case p: Paint => if rounded then canvas.fillRoundedRect(rect, borderRadius, p) else canvas.fillRect(rect, p)
      case null     => ()

    // With `clipContent`, children are confined to the box's (rounded) rect — overflow
    // hidden — so a long line or an absolutely-positioned child can't spill past the edges.
    // The border is drawn after, outside the clip, so it stays crisp.
    if clipContent then
      canvas.pushClip(rect, borderRadius)
      paintChildren(canvas, origin)
      canvas.popClip()
    else paintChildren(canvas, origin)

    border match
      case p: Paint if borderWidth > 0 =>
        if rounded then canvas.strokeRoundedRect(rect, borderRadius, p, borderWidth)
        else canvas.strokeRect(rect, p, borderWidth)
      case _ => ()

    if faded then canvas.popOpacity()

/** Forces a fixed size onto its child (SwiftUI's `.frame` / Flutter's `SizedBox`), and/or
  * caps it to a maximum (Flutter's `ConstrainedBox`). A fixed `width`/`height` is handed to the
  * child as a tight constraint; a `maxWidth`/`maxHeight` instead lowers the *maximum* the child
  * may take while leaving it free to be smaller, so the child sizes to its content up to that
  * cap (what bounds a dialog's width so its text wraps without forcing a tiny dialog wide). An
  * axis with neither passes the parent's constraint straight through. With no child it occupies
  * the resulting size. Unlike [[RenderBox]] it has no appearance — it is pure layout. */
final class RenderConstrained extends RenderObject:
  var width: Option[Double]     = None
  var height: Option[Double]    = None
  var maxWidth: Option[Double]  = None
  var maxHeight: Option[Double] = None

  def layout(constraints: Constraints): Unit =
    // Lower the available maximums by any cap (keeping the minimums valid), then tighten the
    // fixed axes within what's left.
    val capW = maxWidth.getOrElse(Double.PositiveInfinity)
    val capH = maxHeight.getOrElse(Double.PositiveInfinity)
    val capped = Constraints(
      constraints.minWidth.min(capW),
      constraints.maxWidth.min(capW),
      constraints.minHeight.min(capH),
      constraints.maxHeight.min(capH),
    )
    val c = capped.tighten(width, height)
    soleChild match
      case ch: RenderObject =>
        ch.layout(c)
        ch.offset = Offset.zero
        size = c.constrain(ch.size)
      case null =>
        size = c.constrain(Size.zero)

/** Insets its child by `padding`. The child is laid out in the parent's constraints
  * deflated by the insets (so a tight parent stays tight, minus the padding), placed
  * at the top-left inside the padding, and this object reports the child's size grown
  * by the insets. */
final class RenderPadding extends RenderObject:
  var padding: EdgeInsets = EdgeInsets.zero

  def layout(constraints: Constraints): Unit =
    val inner = constraints.deflate(padding)
    val childSize = soleChild match
      case ch: RenderObject =>
        ch.layout(inner)
        ch.offset = Offset(padding.left, padding.top)
        ch.size
      case null => Size.zero
    size = constraints.constrain(
      Size(childSize.width + padding.horizontal, childSize.height + padding.vertical),
    )

/** A z-ordered overlay: children are stacked back-to-front in declaration order and
  * each is positioned within the stack by `alignment`. The stack fills the space the
  * parent offers where that is bounded, otherwise it wraps its largest child. This is
  * also what `align` and `center` build — a stack of one child at a chosen alignment —
  * and it is the basis for modals, tooltips, and badges once portals layer on top. */
final class RenderStack(var alignment: Alignment = Alignment.topLeft) extends RenderObject:

  def layout(constraints: Constraints): Unit =
    val inner   = constraints.loosen
    var maxW    = 0.0
    var maxH    = 0.0
    var i       = 0
    while i < children.length do
      val ch = children(i)
      ch.layout(inner)
      maxW = maxW.max(ch.size.width)
      maxH = maxH.max(ch.size.height)
      i += 1
    val w = if constraints.maxWidth.isFinite then constraints.maxWidth else maxW
    val h = if constraints.maxHeight.isFinite then constraints.maxHeight else maxH
    size = constraints.constrain(Size(w, h))
    i = 0
    while i < children.length do
      val ch = children(i)
      ch.offset = alignment.inscribe(ch.size, size)
      i += 1

/** A scrolling viewport over a single child that may be larger than it along one axis.
  * The viewport fills the room its parent offers; the content is laid out free along the
  * scroll axis (so it takes its natural extent, however tall or wide) and constrained to
  * the viewport across it. A scroll position translates the content, which is clipped to
  * the viewport so the overflow does not paint over neighbours.
  *
  * Scroll position is state held here on the render object, not in the component tree —
  * the browser model, where an element remembers how far it is scrolled across re-renders
  * (the reconciler retains this object, so the offset survives). The object handles its
  * own `wheel` events: it registers a wheel handler on itself that scrolls along its axis,
  * so dropping a [[RenderScroll]] under the pointer router is all that wheel scrolling
  * needs — no application wiring. */
final class RenderScroll(var axis: Axis = Axis.Vertical) extends RenderObject:
  /** How far the content is scrolled on each axis, in pixels from the start. Each is kept within
    * `[0, maxScroll{X,Y}]` by [[scrollByX]]/[[scrollByY]] and `layout`. A single-axis viewport
    * leaves the unused offset at zero. */
  var offsetX: Double = 0.0
  var offsetY: Double = 0.0

  /** Scroll on both axes at once — the content may overflow horizontally and vertically together,
    * each scrolled independently with its own bar. Off by default, when only `axis` scrolls. The
    * global [[Axis]] stays two-valued: a both-ways viewport is a property here, not a third
    * direction (so flex and the splitter, which are inherently single-axis, never see it). */
  var biaxial: Boolean = false

  private var contentW: Double = 0.0
  private var contentH: Double = 0.0

  /** Whether a visible scrollbar is painted over the viewport. Off by default — the plain
    * `scrollView` is wheel-only; the `scrollArea` widget switches it on and supplies themed
    * colours through the props below. A biaxial viewport paints one bar per overflowing axis. */
  var scrollbar: Boolean           = false
  var scrollbarThumb: Color | Null = null
  var scrollbarTrack: Color | Null = null
  var scrollbarThickness: Double   = 8.0

  override def clipShape: Option[(Rect, BorderRadius)] =
    Some((Rect.at(absoluteOffset, size), BorderRadius.zero))

  private def isVertical: Boolean = axis == Axis.Vertical
  private def canScrollX: Boolean = biaxial || axis == Axis.Horizontal
  private def canScrollY: Boolean = biaxial || axis == Axis.Vertical

  /** The furthest the content can scroll on each axis: the amount by which it overflows the
    * viewport, or zero when it fits. */
  def maxScrollX: Double = math.max(0.0, contentW - size.width)
  def maxScrollY: Double = math.max(0.0, contentH - size.height)

  // The single-axis surface the original API exposed, kept intact for callers and tests that work
  // one direction at a time: `scrollOffset`/`maxScroll`/`scrollBy` all act on whichever axis is
  // active (the `axis` direction), so a plain vertical or horizontal viewport behaves exactly as
  // before while the per-axis fields above carry the biaxial case.
  def scrollOffset: Double = if isVertical then offsetY else offsetX
  // Routed through the same setters the wheel and the scrollbar use, so an assignment is clamped to
  // the scrollable range, re-places the content, repaints, and reports the move — rather than
  // leaving the field and the view disagreeing until something else happened to trigger a layout.
  def scrollOffset_=(v: Double): Unit = if isVertical then setOffsetY(v) else setOffsetX(v): Unit
  def maxScroll: Double = if isVertical then maxScrollY else maxScrollX

  // A wheel notch moves the view by this many pixels — a fixed step rather than the raw
  // wheel delta (which SDL reports as a small ±1-per-notch float), so one notch scrolls a
  // readable amount regardless of the platform's wheel granularity. Each axis takes its own
  // delta, so a biaxial viewport scrolls vertically and horizontally from the one wheel.
  // The event is claimed only when the view actually moved, so a viewport sitting at its limit
  // (or one that does not scroll on the wheel's axis at all) lets the wheel chain to the view
  // outside it rather than swallowing it.
  handlers("wheel") = e =>
    val s       = e.asInstanceOf[ScrollEvent]
    val beforeY = offsetY
    val beforeX = offsetX
    if canScrollY then scrollByY(-s.deltaY * RenderScroll.WheelStep)
    if canScrollX then scrollByX(-s.deltaX * RenderScroll.WheelStep)
    if offsetY != beforeY || offsetX != beforeX then s.consume()

  // Dragging a scrollbar thumb. A press is claimed only when it lands on a thumb (so a press
  // anywhere else in the viewport flows to the content untouched); the capture the pointer router
  // takes on that press then routes the drag's moves here even once the cursor leaves the thin
  // thumb. The cursor's travel maps to scroll travel by the content/track ratio.
  private var dragVertical    = false
  private var dragHorizontal  = false
  private var dragStartCoord  = 0.0
  private var dragStartScroll = 0.0

  handlers("mousedown") = e =>
    val ev = e.asInstanceOf[PointerEvent]
    if scrollbar then
      thumbRectFor(vertical = true) match
        case r: Rect if r.contains(ev.position.x, ev.position.y) =>
          dragVertical    = true
          dragStartCoord  = ev.position.y
          dragStartScroll = offsetY
        case _ =>
          thumbRectFor(vertical = false) match
            case r: Rect if r.contains(ev.position.x, ev.position.y) =>
              dragHorizontal  = true
              dragStartCoord  = ev.position.x
              dragStartScroll = offsetX
            case _ => ()

  handlers("mousemove") = e =>
    if dragVertical then dragTo(e.asInstanceOf[PointerEvent].position.y, vertical = true)
    else if dragHorizontal then dragTo(e.asInstanceOf[PointerEvent].position.x, vertical = false)

  handlers("mouseup") = _ => { dragVertical = false; dragHorizontal = false }

  // A visible scrollbar claims presses on its band, so the bar drives the scroll (thumb drag) rather
  // than the press falling through to the content that fills the viewport beneath it. Only a bar that
  // is actually shown — an axis with something to scroll — claims; elsewhere the point flows to the
  // content as usual.
  override def hitTest(point: Offset, origin: Offset): RenderObject | Null =
    if ignorePointer then null
    else if !Rect.at(origin, size).contains(point.x, point.y) then null
    else
      if scrollbar then
        if thumbRectFor(vertical = true) != null &&
          point.x >= origin.x + size.width - scrollbarThickness
        then return this
        if thumbRectFor(vertical = false) != null &&
          point.y >= origin.y + size.height - scrollbarThickness
        then return this
      var i = children.length - 1
      while i >= 0 do
        val child = children(i)
        val hit   = child.hitTest(point, origin + child.offset)
        if hit != null then return hit
        i -= 1
      this

  // Map the dragged cursor coordinate to a scroll offset on the given axis and apply it.
  private def dragTo(coord: Double, vertical: Boolean): Unit =
    thumbMetricsFor(vertical) match
      case Some((len, _)) =>
        val view   = if vertical then size.height else size.width
        val maxS   = if vertical then maxScrollY else maxScrollX
        val travel = view - len
        val next   = if travel > 0 then clamp(dragStartScroll + (coord - dragStartCoord) * (maxS / travel), maxS) else 0.0
        if vertical then setOffsetY(next) else setOffsetX(next)
      case None => ()

  // The thumb's length and start offset for one axis: sized to the visible fraction of the
  // content (never below a grabbable minimum) and positioned in proportion to the scroll offset.
  // None when that axis has nothing to scroll.
  private def thumbMetricsFor(vertical: Boolean): Option[(Double, Double)] =
    val view = if vertical then size.height else size.width
    val maxS = if vertical then maxScrollY else maxScrollX
    val off  = if vertical then offsetY else offsetX
    if maxS <= 0 || view <= 0 then None
    else
      val content = view + maxS
      val len     = math.max(RenderScroll.MinThumbLength, math.min(view, view * view / content))
      val travel  = view - len
      Some((len, (off / maxS) * travel))

  // One axis's thumb rectangle in absolute coordinates, or null when the bar is off or that axis
  // fits. The vertical bar rides the right edge; the horizontal bar rides the bottom.
  private def thumbRectFor(vertical: Boolean): Rect | Null =
    if !scrollbar then null
    else if vertical && !canScrollY then null
    else if !vertical && !canScrollX then null
    else
      thumbMetricsFor(vertical) match
        case Some((len, start)) =>
          val o = absoluteOffset
          if vertical then Rect(o.x + size.width - scrollbarThickness, o.y + start, scrollbarThickness, len)
          else Rect(o.x + start, o.y + size.height - scrollbarThickness, len, scrollbarThickness)
        case None => null

  /** The active axis's thumb rectangle in absolute coordinates, or null when the bar is off or the
    * content fits. (Biaxial viewports also expose the other axis's bar internally.) */
  def scrollbarThumbRect: Rect | Null = thumbRectFor(isVertical)

  // Notify a `scroll` listener when the view actually moves, however it moved — wheel, scrollbar
  // drag, or a caller setting the offset. Mirrors the `resize` notification: the callback typically
  // schedules a re-render, which vdom batches, so calling it from here is safe.
  private def notifyScrolled(): Unit =
    handlers.get("scroll").foreach(_.apply(Offset(offsetX, offsetY)))

  private def setOffsetX(v: Double): Boolean =
    val n = clamp(v, maxScrollX)
    if n != offsetX then { offsetX = n; placeChild(); markDirty(); notifyScrolled(); true } else false

  private def setOffsetY(v: Double): Boolean =
    val n = clamp(v, maxScrollY)
    if n != offsetY then { offsetY = n; placeChild(); markDirty(); notifyScrolled(); true } else false

  /** Scroll horizontally by `delta` px (positive toward the content's right), clamped to
    * `[0, maxScrollX]`; returns whether the offset moved. */
  def scrollByX(delta: Double): Boolean = setOffsetX(offsetX + delta)

  /** Scroll vertically by `delta` px (positive toward the content's bottom), clamped to
    * `[0, maxScrollY]`; returns whether the offset moved. */
  def scrollByY(delta: Double): Boolean = setOffsetY(offsetY + delta)

  /** Scroll the active (single) axis by `delta` px, clamped to its range; returns whether it
    * moved. The original single-axis entry point, now a thin alias over the per-axis scrolls. */
  def scrollBy(delta: Double): Boolean = if isVertical then scrollByY(delta) else scrollByX(delta)

  private def clamp(v: Double, maxS: Double): Double = math.max(0.0, math.min(v, maxS))

  // The content is positioned by negating each scroll offset, so the ordinary paint and
  // hit-test walks (both of which use a child's `offset`) translate with the scroll for
  // free — no special-casing in either pass.
  private def placeChild(): Unit =
    soleChild match
      case ch: RenderObject => ch.offset = Offset(-offsetX, -offsetY)
      case null             => ()

  def layout(constraints: Constraints): Unit =
    // Free along each scrollable axis (content may overflow there), bounded across a fixed axis
    // to the viewport.
    val childConstraints =
      Constraints(
        0,
        if canScrollX then Double.PositiveInfinity else constraints.maxWidth,
        0,
        if canScrollY then Double.PositiveInfinity else constraints.maxHeight,
      )
    val childSize = soleChild match
      case ch: RenderObject => ch.layout(childConstraints); ch.size
      case null             => Size.zero

    // The viewport fills the space offered; an unbounded axis (no scroll room defined)
    // falls back to the content's extent, which simply disables scrolling on that axis.
    val w = if constraints.maxWidth.isFinite then constraints.maxWidth else childSize.width
    val h = if constraints.maxHeight.isFinite then constraints.maxHeight else childSize.height
    size = constraints.constrain(Size(w, h))

    contentW = childSize.width
    contentH = childSize.height
    // Content may have shrunk since the last scroll; re-clamp before placing it.
    offsetX = clamp(offsetX, maxScrollX)
    offsetY = clamp(offsetY, maxScrollY)
    placeChild()

  override def paint(canvas: Canvas, origin: Offset): Unit =
    canvas.pushClip(Rect.at(origin, size), BorderRadius.zero)
    paintChildren(canvas, origin)
    if scrollbar then
      if canScrollY then paintScrollbar(canvas, origin, vertical = true)
      if canScrollX then paintScrollbar(canvas, origin, vertical = false)
    canvas.popClip()

  // A bar rides the trailing edge of its axis — the right edge for the vertical bar, the bottom for
  // the horizontal one — over the content (already clipped to the viewport). It paints only when
  // that axis has something to scroll, so an axis whose content fits shows no bar.
  private def paintScrollbar(canvas: Canvas, origin: Offset, vertical: Boolean): Unit =
    thumbMetricsFor(vertical) match
      case None => ()
      case Some((len, start)) =>
        val t      = scrollbarThickness
        val radius = BorderRadius.all(t / 2)
        val (trackRect, thumbRect) =
          if vertical then
            (
              Rect(origin.x + size.width - t, origin.y, t, size.height),
              Rect(origin.x + size.width - t, origin.y + start, t, len),
            )
          else
            (
              Rect(origin.x, origin.y + size.height - t, size.width, t),
              Rect(origin.x + start, origin.y + size.height - t, len, t),
            )
        scrollbarTrack match
          case c: Color => canvas.fillRoundedRect(trackRect, radius, Solid(c))
          case null     => ()
        scrollbarThumb match
          case c: Color => canvas.fillRoundedRect(thumbRect, radius, Solid(c))
          case null     => ()

object RenderScroll:
  /** Pixels scrolled per wheel notch. */
  val WheelStep: Double = 40.0

  /** The shortest the thumb is allowed to get, so it stays grabbable over very long content. */
  val MinThumbLength: Double = 24.0

/** The overlay layer — a full-window host for portaled content (dialogs, menus, tooltips)
  * that must paint above the application and be hit before it. It sits as the last child of
  * the [[RenderRoot]], so it paints on top and is hit-tested first, and it lays each child
  * out tight to its own (window) size, like the root, so a modal's scrim fills the window.
  *
  * Crucially it is **transparent to hit-testing wherever it has no content**: it never claims
  * a point for itself, only forwards to a child that does. An empty overlay therefore does
  * not swallow the application's input — only real overlay content (an open modal's scrim, a
  * menu) intercepts — which is what lets it sit permanently in front of the whole app. */
final class RenderOverlay extends RenderObject:
  def layout(constraints: Constraints): Unit =
    val w = if constraints.maxWidth.isFinite then constraints.maxWidth else 0.0
    val h = if constraints.maxHeight.isFinite then constraints.maxHeight else 0.0
    size = constraints.constrain(Size(w, h))
    var i = 0
    while i < children.length do
      val child = children(i)
      child.layout(Constraints.tight(size))
      child.offset = Offset.zero
      i += 1

  override def hitTest(point: Offset, origin: Offset): RenderObject | Null =
    var i = children.length - 1
    while i >= 0 do
      val child = children(i)
      val hit   = child.hitTest(point, origin + child.offset)
      if hit != null then return hit
      i -= 1
    null

/** Absolute pixel placement within a layer. It fills the space the parent offers (so it covers
  * a full-window overlay), lays its single child out **loose** — at the child's natural size,
  * not clamped to the room left after the offset — and places it at `(dx, dy)`. The child may
  * overflow the parent, which is intended: an overlay positions a menu or tooltip at a screen
  * point and a positioner that shrank the child to fit would hide the very overflow the caller
  * flips or slides to avoid. Used by the positioned overlays to anchor content to a trigger. */
final class RenderPositioned(var dx: Double = 0.0, var dy: Double = 0.0) extends RenderObject:
  def layout(constraints: Constraints): Unit =
    val inner = constraints.loosen
    soleChild match
      case ch: RenderObject =>
        ch.layout(inner)
        ch.offset = Offset(dx, dy)
      case null => ()
    val w = if constraints.maxWidth.isFinite then constraints.maxWidth else 0.0
    val h = if constraints.maxHeight.isFinite then constraints.maxHeight else 0.0
    size = constraints.constrain(Size(w, h))

/** A row or column — the main layout primitive. Children are laid end to end along the
  * **main axis** (horizontal for a row, vertical for a column) and sized across the
  * **cross axis**. Inflexible children (flex 0) take their natural main size; the
  * leftover main space is split among the flexible children in proportion to their
  * `flex`. `mainAxisAlignment` distributes any remaining slack, `crossAxisAlignment`
  * places (or stretches) each child across, `spacing` inserts a fixed gap between
  * adjacent children, and `mainAxisSize` chooses whether the flex fills its parent or
  * shrinks to its children. This replaces flexbox with one explicit two-pass rule. */
final class RenderFlex(val axis: Axis) extends RenderObject:
  var mainAxisAlignment: MainAxisAlignment   = MainAxisAlignment.Start
  var crossAxisAlignment: CrossAxisAlignment = CrossAxisAlignment.Start
  var mainAxisSize: MainAxisSize             = MainAxisSize.Max
  var spacing: Double                        = 0.0

  private def isRow                          = axis == Axis.Horizontal
  private def mainOf(s: Size): Double        = if isRow then s.width else s.height
  private def crossOf(s: Size): Double       = if isRow then s.height else s.width
  private def sizeOf(main: Double, cross: Double): Size =
    if isRow then Size(main, cross) else Size(cross, main)
  private def offsetOf(main: Double, cross: Double): Offset =
    if isRow then Offset(main, cross) else Offset(cross, main)

  def layout(constraints: Constraints): Unit =
    val maxMain  = if isRow then constraints.maxWidth else constraints.maxHeight
    val maxCross = if isRow then constraints.maxHeight else constraints.maxWidth
    val bounded  = maxMain.isFinite
    val n        = children.length
    val gaps     = if n > 0 then spacing * (n - 1) else 0.0

    // Cross constraint for a child: a stretch alignment forces the full (bounded) cross
    // extent; otherwise the child is free to take its natural cross size up to the max.
    def childConstraints(mainMin: Double, mainMax: Double): Constraints =
      val stretch  = crossAxisAlignment == CrossAxisAlignment.Stretch && maxCross.isFinite
      val crossMin = if stretch then maxCross else 0.0
      if isRow then Constraints(mainMin, mainMax, crossMin, maxCross)
      else Constraints(crossMin, maxCross, mainMin, mainMax)

    // First pass: lay out the inflexible children at their natural main size. When the
    // main axis is unbounded there is nothing to distribute, so flexible children are
    // treated as inflexible too.
    var usedMain  = 0.0
    var totalFlex = 0
    var i         = 0
    while i < n do
      val ch = children(i)
      if ch.flex > 0 && bounded then totalFlex += ch.flex
      else
        ch.layout(childConstraints(0.0, if bounded then maxMain else Double.PositiveInfinity))
        usedMain += mainOf(ch.size)
      i += 1

    // Second pass: hand each flexible child a tight main constraint equal to its share
    // of the space left after the inflexible children and the fixed gaps.
    if totalFlex > 0 && bounded then
      val free = (maxMain - usedMain - gaps).max(0.0)
      i = 0
      while i < n do
        val ch = children(i)
        if ch.flex > 0 then
          val share = free * ch.flex / totalFlex
          ch.layout(childConstraints(share, share))
          usedMain += share
        i += 1

    // Resolve own size: main extent fills the parent (Max) or wraps the children
    // (Min); cross extent stretches or wraps the widest child.
    var maxChildCross = 0.0
    i = 0
    while i < n do
      maxChildCross = maxChildCross.max(crossOf(children(i).size))
      i += 1
    val childrenMain = usedMain + gaps
    val mainExtent =
      if mainAxisSize == MainAxisSize.Max && bounded then maxMain
      else if bounded then childrenMain.min(maxMain)
      else childrenMain
    val crossExtent =
      if crossAxisAlignment == CrossAxisAlignment.Stretch && maxCross.isFinite then maxCross
      else maxChildCross
    size = constraints.constrain(sizeOf(mainExtent, crossExtent))

    // Position the children. The slack left over after the children and their fixed
    // gaps is distributed per the main-axis alignment as a leading offset plus an
    // extra gap between each pair.
    val slack = (mainOf(size) - childrenMain).max(0.0)
    val (leading, extra) = mainAxisAlignment match
      case MainAxisAlignment.Start        => (0.0, 0.0)
      case MainAxisAlignment.End          => (slack, 0.0)
      case MainAxisAlignment.Center       => (slack / 2, 0.0)
      case MainAxisAlignment.SpaceBetween => (0.0, if n > 1 then slack / (n - 1) else 0.0)
      case MainAxisAlignment.SpaceAround  => val g = if n > 0 then slack / n else 0.0; (g / 2, g)
      case MainAxisAlignment.SpaceEvenly  => val g = slack / (n + 1); (g, g)

    val crossSize = crossOf(size)
    var cursor    = leading
    i = 0
    while i < n do
      val ch        = children(i)
      val crossSpan = crossOf(ch.size)
      val crossPos = crossAxisAlignment match
        case CrossAxisAlignment.Start | CrossAxisAlignment.Stretch => 0.0
        case CrossAxisAlignment.End                                => crossSize - crossSpan
        case CrossAxisAlignment.Center                             => (crossSize - crossSpan) / 2
      ch.offset = offsetOf(cursor, crossPos)
      cursor += mainOf(ch.size) + spacing + extra
      i += 1

/** A run of text. It sizes itself by asking the installed [[TextMeasurer]] how big its
  * string is in its [[TextStyle]] — the measurement seam that keeps layout off-device —
  * and paints through the canvas's `drawText`, which the SDL backend rasterises and the
  * recording backend captures.
  *
  * By default it lays out as a single line, the historical behaviour. With `maxLines` other
  * than 1 (and `softWrap` on) it greedily word-wraps to the width the parent allows, breaking
  * a word that is itself wider than the line; explicit `\n`s always start a new line.
  * `maxLines` caps the line count (0 = unlimited), `overflow` decides whether the dropped
  * tail is silently clipped or marked with an ellipsis, and `align` positions each line
  * horizontally within the measured block. The broken lines are computed once during layout
  * and replayed by paint, so the two passes never disagree about where a line sits. */
final class RenderText(var text: String) extends RenderObject:
  /** This node's own explicit overrides. A field left `None` is inherited from the
    * nearest ancestor that sets it (see [[resolvedStyle]]); a field set here wins over
    * anything inherited. */
  var explicitSize: Option[Double]         = None
  var explicitColor: Option[Color]         = None
  var explicitWeight: Option[Int]          = None
  var explicitFamily: Option[FontFamily]   = None

  /** Per-line horizontal placement within the laid-out block. */
  var align: TextAlign = TextAlign.Left

  /** Maximum number of lines to lay out; 1 keeps the run single-line (no wrapping), and 0
    * means unlimited. Wrapping only happens when this is not 1, `softWrap` is on, and the
    * width is bounded. */
  var maxLines: Int = 1

  /** Whether the dropped tail (a too-wide line, or lines past `maxLines`) is clipped or
    * trimmed and marked with an ellipsis. */
  var overflow: TextOverflow = TextOverflow.Clip

  /** Whether soft word-wrapping at the available width is allowed. Off means the run breaks
    * only at explicit `\n`s. */
  var softWrap: Boolean = true

  /** The lines produced by the last layout, in paint order, and the height of one line. */
  private var lines: Vector[String] = Vector.empty
  private var lineHeight: Double    = 0.0

  /** The concrete style to measure and paint with: this node's explicit values overlaid
    * on the cascade. For each property, the first source that provides it wins —
    * this node, then each ancestor's [[RenderObject.textAttrs]] from nearest to root —
    * and anything still unset falls back to [[TextStyle.default]]. */
  def resolvedStyle: TextStyle =
    var size   = explicitSize
    var color  = explicitColor
    var weight = explicitWeight
    var n: RenderObject | Null = parent
    while n != null && (size.isEmpty || color.isEmpty || weight.isEmpty) do
      val a = n.asInstanceOf[RenderObject].textAttrs
      if size.isEmpty then size = a.size
      if color.isEmpty then color = a.color
      if weight.isEmpty then weight = a.weight
      n = n.asInstanceOf[RenderObject].parent
    TextStyle(
      size.getOrElse(TextStyle.default.size),
      color.getOrElse(TextStyle.default.color),
      weight.getOrElse(TextStyle.default.weight),
      explicitFamily.getOrElse(TextStyle.default.family),
    )

  def layout(constraints: Constraints): Unit =
    val style = resolvedStyle
    val m     = TextMeasurer.installed
    lineHeight = m.measure("", style).height
    val maxW = constraints.maxWidth

    val raw =
      if maxLines != 1 && softWrap && maxW.isFinite then RenderText.wrap(text, style, maxW, m)
      else if maxLines == 1 then Vector(text)
      else text.split("\n", -1).toVector

    val limit     = if maxLines <= 0 then Int.MaxValue else maxLines
    val kept      = raw.take(limit)
    val truncated = raw.length > kept.length

    lines =
      if overflow == TextOverflow.Ellipsis && maxW.isFinite then
        if truncated then
          if kept.isEmpty then Vector(RenderText.ellipsize("", style, maxW, m))
          else kept.init :+ RenderText.ellipsize(kept.last, style, maxW, m)
        else kept.map(l => if m.measure(l, style).width > maxW then RenderText.ellipsize(l, style, maxW, m) else l)
      else kept

    val w = if lines.isEmpty then 0.0 else lines.iterator.map(l => m.measure(l, style).width).max
    size = constraints.constrain(Size(w, lineHeight * math.max(lines.length, 1)))

  override def paint(canvas: Canvas, origin: Offset): Unit =
    val style = resolvedStyle
    val m     = TextMeasurer.installed
    var i     = 0
    while i < lines.length do
      val line = lines(i)
      if line.nonEmpty then
        val lw = m.measure(line, style).width
        val dx = align match
          case TextAlign.Left   => 0.0
          case TextAlign.Center => (size.width - lw) / 2.0
          case TextAlign.Right  => size.width - lw
        canvas.drawText(Offset(origin.x + dx, origin.y + i * lineHeight), line, style)
      i += 1

object RenderText:
  /** Greedy word-wrap of `text` to `maxW`, honouring explicit `\n`s as hard breaks and
    * hard-breaking any single word that is itself wider than a line. Pure (drives only the
    * measurer), so the line-breaking is JVM-testable against a deterministic measurer. */
  private[suit] def wrap(text: String, style: TextStyle, maxW: Double, m: TextMeasurer): Vector[String] =
    val out        = Vector.newBuilder[String]
    val paragraphs = text.split("\n", -1)
    var pi         = 0
    while pi < paragraphs.length do
      wrapParagraph(paragraphs(pi), style, maxW, m, out)
      pi += 1
    out.result()

  private def wrapParagraph(
      p:     String,
      style: TextStyle,
      maxW:  Double,
      m:     TextMeasurer,
      out:   scala.collection.mutable.Builder[String, Vector[String]],
  ): Unit =
    if p.isEmpty then out += ""
    else
      var current = ""
      for w <- p.split(" ") if w.nonEmpty do
        if current.isEmpty then current = startWord(w, style, maxW, m, out)
        else
          val candidate = current + " " + w
          if m.measure(candidate, style).width <= maxW then current = candidate
          else
            out += current
            current = startWord(w, style, maxW, m, out)
      if current.nonEmpty then out += current

  /** Begin a fresh line with `w`. If `w` fits it becomes the running line; otherwise it is
    * hard-broken into width-sized chunks, all but the last emitted, the last returned. */
  private def startWord(
      w:     String,
      style: TextStyle,
      maxW:  Double,
      m:     TextMeasurer,
      out:   scala.collection.mutable.Builder[String, Vector[String]],
  ): String =
    if m.measure(w, style).width <= maxW then w
    else
      val sb = new StringBuilder
      var i  = 0
      while i < w.length do
        val ch = w.charAt(i)
        if sb.nonEmpty && m.measure(sb.toString + ch, style).width > maxW then
          out += sb.toString
          sb.setLength(0)
        sb.append(ch)
        i += 1
      sb.toString

  /** Greedy word-wrap of a single logical `line` (no `\n`s) to `maxW`, returned as the **start
    * columns** of each visual row rather than as strings. Unlike [[wrap]], this preserves every
    * character offset — the space at a break stays on the row that ends, so the segments
    * `line.substring(starts(i), starts(i+1))` reconstruct the line exactly. That exactness is what
    * lets a soft-wrapping editor map a caret column to a (row, x) and back. The result always
    * begins with `0`; an empty line, or an unbounded/non-positive width, yields a single row
    * `Vector(0)`. A word wider than the row is hard-broken by character, as in [[wrap]]. Pure
    * (measurer-driven), so it is JVM-testable against a deterministic measurer. */
  private[suit] def wrapColumns(line: String, style: TextStyle, maxW: Double, m: TextMeasurer): Vector[Int] =
    if line.isEmpty || !maxW.isFinite || maxW <= 0.0 then Vector(0)
    else
      val starts   = Vector.newBuilder[Int]
      starts += 0
      val n        = line.length
      var rowStart = 0
      def fits(from: Int, to: Int): Boolean = m.measure(line.substring(from, to), style).width <= maxW

      // Hard-break a word `[from0, we)` that cannot fit a fresh row into width-sized chunks,
      // emitting a start at each break and leaving `rowStart` at the final chunk's start.
      def hardBreak(from0: Int, we: Int): Unit =
        var from = from0
        var i    = from + 1
        while i < we do
          if !fits(from, i + 1) then
            starts += i
            rowStart = i
            from = i
          i += 1

      var wi = 0
      while wi < n do
        if line.charAt(wi) == ' ' then wi += 1
        else
          var we = wi
          while we < n && line.charAt(we) != ' ' do we += 1
          // The word is `[wi, we)`. Keep it on the current row while the row still fits; otherwise
          // break before it (its leading space stays on the row that ends), hard-breaking a word
          // that cannot fit a row of its own.
          if fits(rowStart, we) then ()
          else if wi == rowStart then hardBreak(rowStart, we)
          else
            starts += wi
            rowStart = wi
            if !fits(wi, we) then hardBreak(wi, we)
          wi = we
      starts.result()

  /** The longest prefix of `line` such that `prefix + "…"` fits `maxW`, with `…` appended —
    * or just `…` if not even one character fits. */
  private[suit] def ellipsize(line: String, style: TextStyle, maxW: Double, m: TextMeasurer): String =
    val ell = "…"
    var s   = line
    while s.nonEmpty && m.measure(s + ell, style).width > maxW do s = s.substring(0, s.length - 1)
    if s.isEmpty then ell else s + ell

/** A leaf that paints a scalable vector image. It sizes to its explicit `width`/`height` when
  * given, otherwise to the document's intrinsic size, each clamped to the constraints; with
  * neither it collapses to nothing. Painting hands the image and its laid-out rectangle to the
  * canvas, which scales the vectors to fill it (see [[Canvas.drawSvg]]). It is a leaf for
  * hit-testing — give an interactive icon a surrounding `box` with the handlers. */
final class RenderSvg(var image: SvgImage | Null) extends RenderObject:
  var width:  Option[Double] = None
  var height: Option[Double] = None

  def layout(constraints: Constraints): Unit =
    val natural = image match
      case img: SvgImage => img.intrinsicSize.getOrElse(Size.zero)
      case null          => Size.zero
    val w = width.getOrElse(natural.width)
    val h = height.getOrElse(natural.height)
    size = constraints.constrain(Size(w, h))

  override def paint(canvas: Canvas, origin: Offset): Unit =
    image match
      case img: SvgImage => canvas.drawSvg(img, Rect.at(origin, size))
      case null          => ()

/** A leaf that paints a raster (bitmap) image. It sizes to its explicit `width`/`height` when
  * given, otherwise to the image's own pixel size, each clamped to the constraints. Painting hands
  * the image and its laid-out rectangle to the canvas, which scales the pixels to fill it (see
  * [[Canvas.drawImage]]). Like [[RenderSvg]] it is a leaf for hit-testing — wrap an interactive
  * image in a `box` with the handlers. */
final class RenderImage(var image: RasterImage | Null) extends RenderObject:
  var width:  Option[Double] = None
  var height: Option[Double] = None

  def layout(constraints: Constraints): Unit =
    val natural = image match
      case img: RasterImage => Size(img.width.toDouble, img.height.toDouble)
      case null             => Size.zero
    val w = width.getOrElse(natural.width)
    val h = height.getOrElse(natural.height)
    size = constraints.constrain(Size(w, h))

  override def paint(canvas: Canvas, origin: Offset): Unit =
    image match
      case img: RasterImage => canvas.drawImage(img, Rect.at(origin, size))
      case null             => ()

/** A handle that lets application code ask a [[dsl.surface]] widget to re-blit after it has
  * drawn new pixels into the image surface it supplied. Create one, pass it to the widget, and
  * call [[repaint]] after each redraw. Before the widget mounts (and after it unmounts) the call
  * is a harmless no-op, so a handle held across a widget's lifetime never dangles. */
final class SurfaceHandle:
  private[suit] var target: RenderSurface | Null = null

  /** Request that the surface's current pixels be copied to the screen on the next frame. It
    * marks only the widget's own repaint boundary dirty — so just that region is re-rasterised and
    * the static UI around it is left untouched — and requests a frame. */
  def repaint(): Unit =
    target match
      case s: RenderSurface => s.markDirty()
      case null             => ()

/** A leaf that wraps an application-owned image surface and blits it to the screen — the
  * companion to [[RenderCanvas]] for code that would rather drive a real drawing surface itself,
  * with the full underlying graphics API, than issue suit's [[Canvas]] primitives. The
  * application creates the surface, draws into it on its own schedule, and calls
  * [[SurfaceHandle.repaint]] to have the new pixels composited.
  *
  * Unlike [[RenderCanvas]] it is **not** a live surface: it re-blits only when its handle is
  * poked, not every frame, so a static richly-drawn panel costs one copy per change rather than a
  * re-rasterise at frame rate. It is a repaint boundary, so that copy repaints just its region and
  * leaves the rest of the window untouched.
  *
  * It sizes to its explicit `width`/`height` when given, otherwise to the surface's own pixel
  * size, each clamped to the constraints. For a sharp result on a HiDPI display, size the surface
  * in device pixels (see [[DevicePixelRatio]]) and pass logical `width`/`height`: the blit then
  * lands the surface's pixels one-to-one on the display. */
final class RenderSurface(var image: RasterImage | Null) extends RenderObject:
  /** A surface is a repaint boundary so a redraw re-blits just its region, not the whole scene.
    * It is not a *live* surface, though — it does not re-rasterise every frame; only an explicit
    * [[SurfaceHandle.repaint]] (or a full-scene repaint) draws it again. */
  override def isRepaintBoundary: Boolean = true

  var width:  Option[Double]       = None
  var height: Option[Double]       = None
  var handle: SurfaceHandle | Null = null

  def layout(constraints: Constraints): Unit =
    val natural = image match
      case img: RasterImage => Size(img.width.toDouble, img.height.toDouble)
      case null             => Size.zero
    val w = width.getOrElse(natural.width)
    val h = height.getOrElse(natural.height)
    size = constraints.constrain(Size(w, h))

  override def paint(canvas: Canvas, origin: Offset): Unit =
    image match
      case img: RasterImage => canvas.drawImage(img, Rect.at(origin, size))
      case null             => ()

/** A leaf that shows a video frame — the one thing suit does not rasterise.
  *
  * It paints no pixels of the frame at all. It fills its rectangle with `background` and then
  * punches a transparent hole ([[Canvas.clearRect]]) exactly where the frame belongs; the runtime
  * blits the [[VideoLayer]]'s texture into that hole from underneath, and the UI layer composites
  * over it. So the frame never touches Cairo, never gets colour-converted or scaled on the CPU,
  * and a new frame costs nothing here at all — no repaint, no relayout, not even a dirty flag.
  * The next present simply shows the new texture. See `video.scala` for why.
  *
  * The background is what shows in the letterbox bars when the frame's shape does not match the
  * rectangle's ([[VideoFit.Contain]]), which is why it defaults to black rather than to
  * transparent: bars are part of the picture the editor is judging.
  *
  * It sizes to its explicit `width`/`height` when given, otherwise **fills** what the parent
  * offers (an unbounded axis collapses to nothing), like [[RenderCanvas]] — a preview monitor
  * takes its pane. It is a leaf for the tree but not for input: give it handlers and a
  * click-to-scrub surface works. */
final class RenderVideo(var layer: VideoLayer | Null) extends RenderObject:
  var width:       Option[Double] = None
  var height:      Option[Double] = None
  var fit:         VideoFit       = VideoFit.Contain
  var background:  Color          = Color.black

  /** The frame's pixel aspect ratio — the displayed width of one stored pixel over its height.
    * 1 for square-pixel formats; anything else for anamorphic or SD sources, where ignoring it
    * shows people too thin or too wide. See [[VideoGeometry.place]]. */
  var pixelAspect: Double = 1.0

  def layout(constraints: Constraints): Unit =
    val w = width.getOrElse(if constraints.maxWidth.isFinite then constraints.maxWidth else 0.0)
    val h = height.getOrElse(if constraints.maxHeight.isFinite then constraints.maxHeight else 0.0)
    size = constraints.constrain(Size(w, h))

  /** Where this widget's frame is read from and where it lands, in the window — the rectangles
    * the runtime blits between. Empty rectangles until a layer with a decoded frame is attached,
    * so the runtime skips a widget that has nothing to show yet. This is the *unclipped* mapping;
    * the runtime blits [[clippedPlacement]] so a video inside a scroll cannot spill past it. */
  def placement: (Rect, Rect) =
    layer match
      case l: VideoLayer =>
        VideoGeometry.place(fit, Rect.at(absoluteOffset, size), l.frameWidth, l.frameHeight, pixelAspect)
      case null => (Rect(0, 0, 0, 0), Rect(0, 0, 0, 0))

  /** [[placement]] confined to every clip its ancestors impose, so the GPU blit is bounded
    * exactly as Cairo bounds the hole this widget punches — a preview inside a scroll viewport
    * (or a clipped card) stays within it instead of the texture bleeding over the chrome. `dst`
    * is intersected with each clipping ancestor's rect and `src` is narrowed by the same fraction
    * so the still-visible slice of the frame maps correctly; an empty overlap yields empty
    * rectangles, and the runtime skips the layer. A clip's corner radius is not applied to the
    * blit — the visible rectangle keeps square corners. */
  def clippedPlacement: (Rect, Rect) =
    val (src, dst) = placement
    if src.width <= 0 || src.height <= 0 || dst.width <= 0 || dst.height <= 0 then (src, dst)
    else
      var clipped                = dst
      var n: RenderObject | Null = parent
      while n != null do
        val r = n.asInstanceOf[RenderObject]
        r.clipShape match
          case Some((rect, _)) => clipped = clipped.intersect(rect)
          case None            => ()
        n = r.parent
      if clipped.width <= 0 || clipped.height <= 0 then (Rect(0, 0, 0, 0), Rect(0, 0, 0, 0))
      else
        val sx = src.width / dst.width
        val sy = src.height / dst.height
        val croppedSrc = Rect(
          src.x + (clipped.x - dst.x) * sx,
          src.y + (clipped.y - dst.y) * sy,
          clipped.width * sx,
          clipped.height * sy,
        )
        (croppedSrc, clipped)

  // The hole is punched against the origin actually being painted at, not against the absolute
  // offset, so a partial repaint of this widget lands it in the same place a full frame does.
  override def paint(canvas: Canvas, origin: Offset): Unit =
    val bounds = Rect.at(origin, size)
    canvas.fillRect(bounds, Solid(background))
    layer match
      case l: VideoLayer =>
        val (_, dst) = VideoGeometry.place(fit, bounds, l.frameWidth, l.frameHeight, pixelAspect)
        if dst.width > 0 && dst.height > 0 then canvas.clearRect(dst)
      case null => ()

/** A direct drawing surface — the toolkit's analogue of an HTML `<canvas>`. It hands the
  * application the very [[Canvas]] suit's own widgets paint through, so a custom drawing (a
  * chart, a game, a physics simulation) issues the same primitives the rest of the UI does and
  * is just as testable against a [[RecordingCanvas]] off-device.
  *
  * It sizes to its explicit `width`/`height` when given, otherwise **fills** the space the
  * parent offers (an unbounded axis collapses to nothing), so a bare canvas expands to its
  * region. It is a leaf for the render tree — it has no children — but not for input: give it
  * pointer or key handlers and an interactive surface (a sim you can click into) works.
  *
  * Painting brackets the application's `painter` with a clip to the widget's bounds and a
  * translation to its top-left, so the app draws in a **local** coordinate space (origin at the
  * widget, `0..width` x `0..height`) and cannot spill past its edges. The painter is re-invoked
  * on every repaint; to animate, advance application state and request a frame (see `useFrame`),
  * which marks the tree dirty and repaints. */
final class RenderCanvas extends RenderObject:
  /** A canvas is a repaint boundary, and a *live* one: its painter reads mutable application
    * state the reconciler never sees, so a frame request re-rasterises it and nothing else. */
  override def isRepaintBoundary: Boolean = true
  override def isLiveSurface: Boolean     = true

  /** The application's draw routine: given the canvas and the widget's size (in its own local
    * coordinate space), it issues the drawing for the current frame. Defaults to a no-op so an
    * un-wired canvas simply paints nothing. */
  var painter: (Canvas, Size) => Unit = (_, _) => ()
  var width:  Option[Double]          = None
  var height: Option[Double]          = None

  def layout(constraints: Constraints): Unit =
    val w = width.getOrElse(if constraints.maxWidth.isFinite then constraints.maxWidth else 0.0)
    val h = height.getOrElse(if constraints.maxHeight.isFinite then constraints.maxHeight else 0.0)
    size = constraints.constrain(Size(w, h))

  override def paint(canvas: Canvas, origin: Offset): Unit =
    canvas.pushClip(Rect.at(origin, size), BorderRadius.zero)
    canvas.pushTranslate(origin.x, origin.y)
    painter(canvas, size)
    canvas.popTranslate()
    canvas.popClip()

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
  /** The root is the outermost repaint boundary: the whole window. Its layer is the live
    * surface the runtime presents, so [[needsRepaint]] here means "re-rasterise the whole
    * scene". */
  override def isRepaintBoundary: Boolean = true

  /** A frame is needed — the flag the runtime's loop reads each iteration. Distinct from
    * [[needsRepaint]]: a canvas tick sets `dirty` (run a frame) and the canvas's own
    * `needsRepaint` (re-rasterise just it), leaving the root's `needsRepaint` clear. */
  var dirty: Boolean = true // paint once on startup

  def layout(constraints: Constraints): Unit =
    size = windowSize
    var i = 0
    while i < children.length do
      val child = children(i)
      child.layout(Constraints.tight(size))
      child.offset = Offset.zero
      i += 1

  /** The nested repaint boundaries whose subtree changed since they last painted — the
    * regions a partial frame must re-rasterise. A dirty boundary is not descended into: its
    * own repaint already covers everything below it (including deeper boundaries). The root
    * itself is never included; a dirty root means a full repaint, handled separately. */
  def dirtyBoundaries: List[RenderObject] =
    val out = mutable.ListBuffer.empty[RenderObject]
    def walk(n: RenderObject): Unit =
      var i = 0
      while i < n.children.length do
        val c = n.children(i)
        if c.isRepaintBoundary && c.needsRepaint then out += c
        else walk(c)
        i += 1
    walk(this)
    out.toList

  /** Every video widget in the tree that has a frame to show, in paint order (back to front),
    * each with the source and destination rectangles the runtime should blit between.
    *
    * The runtime collects these fresh each frame rather than during paint, because the two run on
    * different schedules: a present happens every iteration, while Cairo repaints only when the
    * tree is dirty. Harvesting during paint would lose every layer on a frame where nothing was
    * re-rasterised — which, for a video playing over a still UI, is nearly all of them. Layout
    * runs every frame, so the absolute offsets read here are current.
    *
    * Widgets whose placement is empty (no layer attached, or no size yet) are left out, so the
    * runtime blits only what can actually be drawn. */
  def videoLayers: List[(VideoLayer, Rect, Rect)] =
    val out = mutable.ListBuffer.empty[(VideoLayer, Rect, Rect)]
    def walk(n: RenderObject): Unit =
      n match
        case v: RenderVideo =>
          v.layer match
            case l: VideoLayer =>
              val (src, dst) = v.clippedPlacement
              if dst.width > 0 && dst.height > 0 && src.width > 0 && src.height > 0 then
                out += ((l, src, dst))
            case null => ()
        case _ => ()
      var i = 0
      while i < n.children.length do
        walk(n.children(i))
        i += 1
    walk(this)
    out.toList

  /** Mark every live surface in the tree as needing repaint. A frame request from an
    * imperative animation ([[Repaint]]) cannot say which canvas advanced, so each live
    * surface re-rasterises on the next frame; the static UI (not a live surface) is left
    * cached. */
  def invalidateLiveSurfaces(): Unit =
    def walk(n: RenderObject): Unit =
      if n.isLiveSurface then n.needsRepaint = true
      var i = 0
      while i < n.children.length do
        walk(n.children(i))
        i += 1
    walk(this)

  /** Clear the repaint flag on every boundary in the tree (including the root) after a full
    * frame has re-rasterised the whole scene. */
  def clearRepaintFlags(): Unit =
    def walk(n: RenderObject): Unit =
      if n.isRepaintBoundary then n.needsRepaint = false
      var i = 0
      while i < n.children.length do
        walk(n.children(i))
        i += 1
    walk(this)
