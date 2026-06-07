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

  /** Signal that the frame is stale. Propagates up to the root, which holds the flag
    * the frame loop reads. No distinction is made yet between needs-layout and
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

    paintChildren(canvas, origin)

    border match
      case p: Paint if borderWidth > 0 =>
        if rounded then canvas.strokeRoundedRect(rect, borderRadius, p, borderWidth)
        else canvas.strokeRect(rect, p, borderWidth)
      case _ => ()

    if faded then canvas.popOpacity()

/** Forces a fixed size onto its child (SwiftUI's `.frame` / Flutter's `SizedBox`).
  * Each given axis is handed to the child as a tight constraint; an axis left unset
  * passes the parent's constraint straight through. With no child it simply occupies
  * the requested size. Unlike [[RenderBox]] it has no appearance — it is pure layout. */
final class RenderConstrained extends RenderObject:
  var width: Option[Double]  = None
  var height: Option[Double] = None

  def layout(constraints: Constraints): Unit =
    val c = constraints.tighten(width, height)
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
  * recording backend captures. Layout is single-line; the measured size is clamped into
  * the constraints the parent imposed. */
final class RenderText(var text: String) extends RenderObject:
  /** This node's own explicit overrides. A field left `None` is inherited from the
    * nearest ancestor that sets it (see [[resolvedStyle]]); a field set here wins over
    * anything inherited. */
  var explicitSize: Option[Double] = None
  var explicitColor: Option[Color] = None

  /** The concrete style to measure and paint with: this node's explicit values overlaid
    * on the cascade. For each property, the first source that provides it wins —
    * this node, then each ancestor's [[RenderObject.textAttrs]] from nearest to root —
    * and anything still unset falls back to [[TextStyle.default]]. */
  def resolvedStyle: TextStyle =
    var size  = explicitSize
    var color = explicitColor
    var n: RenderObject | Null = parent
    while n != null && (size.isEmpty || color.isEmpty) do
      val a = n.asInstanceOf[RenderObject].textAttrs
      if size.isEmpty then size = a.size
      if color.isEmpty then color = a.color
      n = n.asInstanceOf[RenderObject].parent
    TextStyle(size.getOrElse(TextStyle.default.size), color.getOrElse(TextStyle.default.color))

  def layout(constraints: Constraints): Unit =
    size = constraints.constrain(TextMeasurer.installed.measure(text, resolvedStyle))

  override def paint(canvas: Canvas, origin: Offset): Unit =
    if text.nonEmpty then canvas.drawText(origin, text, resolvedStyle)

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
