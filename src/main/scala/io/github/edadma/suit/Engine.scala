package io.github.edadma.suit

import scala.collection.mutable.ArrayBuffer

// Engine drives a single rendering surface. Application code interacts with
// the engine through `setRoot(view)` — typically called once on mount and
// then again whenever the application's state changes. Inside `runFrame`,
// the engine reconciles any pending view, dispatches input events to the
// current Node tree, lays it out, and emits a fresh draw list.
//
// All cross-tree algorithms (measure / layout / event dispatch / render) are
// top-level functions that pattern-match on the concrete Node type. This is
// the shape sysl uses to dispatch on a tagged-union `Node` enum without OO
// virtual methods.
final class Engine:

  val theme:    Theme              = new Theme
  val input:    InputState         = new InputState
  val drawList: ArrayBuffer[DrawCommand] = ArrayBuffer.empty

  // The View currently committed and the Node tree that mirrors it.
  private[suit] var rootView:    View | Null = null
  private[suit] var rootNode:    Node | Null = null

  // A View handed in via setRoot but not yet reconciled. Reconciliation runs
  // at the start of each frame so it doesn't mutate the tree we're walking.
  private var pendingView: View | Null = null

  var focused:      Node | Null = null
  var width:        Int         = 0
  var height:       Int         = 0
  var frame:        Long        = 0
  var nowMs:        Long        = 0
  var caretEpochMs: Long        = 0

  var dirty:     Boolean = true
  var animating: Boolean = false

  // Counter of in-flight `useTransition` animations across the whole tree.
  // While > 0 the engine keeps requesting frames so the eased value can
  // step toward its target. Hooks increment on start and decrement when
  // the transition reaches its target value (or its component unmounts).
  private[suit] var animationCount: Int = 0

  // Wall-clock source. Tests override this to make caret-blink and any other
  // time-driven behaviour deterministic. Default reads the system clock.
  var clock: () => Long = () => System.currentTimeMillis()

  // Pluggable text-measurement function. Default is a hard-coded monospaced
  // estimate (`length * theme.charWidth`); the host overrides this with a
  // platform-native FontMetrics-style call so layout sees real glyph widths
  // and variable-width fonts work. Signature: (text, fontSize) → pixels.
  // Sysl analog: a function pointer set by the platform layer.
  var textMeasure: (String, Int) => Int = (s, _) => s.length * theme.charWidth

  // Re-entry guard. An event handler that calls back into runFrame during its
  // own dispatch would otherwise blow the stack; instead we no-op and let the
  // outer frame's phase-2 commit pick up whatever pendingView the handler set.
  private var inFrame: Boolean = false

  // Effects scheduled by `useEffect` during render. Drained by flushEffects
  // after the commit phase finishes — that's the React semantics: the body
  // never runs while the tree is still in flux.
  private val pendingEffects: ArrayBuffer[EffectCell] = ArrayBuffer.empty

  // Layout effects (scheduled by `useLayoutEffect`) fire synchronously after
  // layout but before render emits draw commands. That's where you put code
  // that needs to read measured bounds and adjust state before the user
  // sees the frame (e.g. positioning a tooltip relative to its anchor).
  private val pendingLayoutEffects: ArrayBuffer[EffectCell] = ArrayBuffer.empty

  // A stack of values per context, maintained while the reconciler walks
  // through ContextProvider nodes. `useContext(ctx)` returns the head; the
  // reconciler is responsible for matched push/pop.
  private val contextStack: scala.collection.mutable.HashMap[Context[?], List[Any]] =
    scala.collection.mutable.HashMap.empty

  // Portals registered against this engine. The list is maintained by the
  // reconciler (mount appends, unmount removes). Order matters: layout and
  // render iterate forward so later mounts paint on top; dispatch iterates
  // in reverse so the topmost portal sees events first.
  private[suit] val portalNodes: ArrayBuffer[PortalNode] = ArrayBuffer.empty

  // Application entry point — called once on mount and again whenever state
  // changes. The view is not reconciled immediately; reconciliation happens
  // at the start of the next runFrame so we don't mutate a tree mid-walk.
  def setRoot(view: View): Unit =
    pendingView = view
    dirty       = true

  def markDirty(): Unit = dirty = true

  // Re-reconcile against the currently-committed view. Used by hook setters
  // (`useState`, `useEffect`) and any other internal-state change path that
  // doesn't have a fresh top-level View handy.
  def requestRender(): Unit =
    if pendingView == null then pendingView = rootView
    dirty = true

  // ---- Effect scheduling -------------------------------------------------

  private[suit] def scheduleEffect(e: EffectCell): Unit =
    pendingEffects += e

  private[suit] def scheduleLayoutEffect(e: EffectCell): Unit =
    pendingLayoutEffects += e

  private def flushEffects(): Unit       = drainEffects(pendingEffects)
  private def flushLayoutEffects(): Unit = drainEffects(pendingLayoutEffects)

  private def drainEffects(queue: ArrayBuffer[EffectCell]): Unit =
    var i = 0
    while i < queue.length do
      val rec = queue(i)
      val old = rec.cleanup
      if old != null then
        rec.cleanup = null
        old()
      val body = rec.nextBody
      rec.nextBody = null
      if body != null then rec.cleanup = body()
      i = i + 1
    queue.clear()


  // ---- Context stack -----------------------------------------------------

  private[suit] def pushContext[T](ctx: Context[T], value: T): Unit =
    val stack = contextStack.getOrElse(ctx, Nil)
    contextStack.update(ctx, value :: stack)

  private[suit] def popContext[T](ctx: Context[T]): Unit =
    contextStack.get(ctx) match
      case Some(_ :: rest) => contextStack.update(ctx, rest)
      case _               => ()

  private[suit] def contextValue[T](ctx: Context[T]): T =
    contextStack.get(ctx) match
      case Some(v :: _) => v.asInstanceOf[T]
      case _            => ctx.default

  def needsFrame: Boolean = dirty || animating || input.hadEvents

  def resetCaret(): Unit = caretEpochMs = nowMs

  // Programmatically focus a node — typically obtained via a ref. Idempotent
  // and safe to call from a useLayoutEffect / useEffect on mount. Nodes that
  // aren't focusable are no-ops.
  def focusNode(n: Node): Unit =
    val prev = focused
    if prev ne n then
      if prev != null then setFocusFlag(prev, false)
      setFocusFlag(n, true)
      focused = n
      resetCaret()
      markDirty()

  private def setFocusFlag(n: Node, on: Boolean): Unit = n match
    case x: InputNode    => x.focused = on
    case x: CheckboxNode => x.focused = on
    case _               => ()

  // ----- frame -------------------------------------------------------------

  def runFrame(viewportW: Int, viewportH: Int): Unit =
    if inFrame then return
    inFrame = true
    try runFrameInner(viewportW, viewportH)
    finally inFrame = false

  private def runFrameInner(viewportW: Int, viewportH: Int): Unit =
    if width != viewportW || height != viewportH then dirty = true
    width  = viewportW
    height = viewportH
    nowMs  = clock()

    // Phase 1 — adopt any pending view from before this frame.
    commitPending()

    val root0 = rootNode
    if root0 != null then

      Engine.handleTabKey(this, root0)
      // Overlays go first so they get first dibs on input — a modal's
      // backdrop or dialog can claim a click before main-tree widgets see it.
      dispatchOverlays()
      Engine.dispatchEvents(this, root0)

      // Phase 2 — onClick handlers may have called setRoot during dispatch.
      // Reconcile again so layout/render run on the up-to-date Node tree.
      commitPending()

      val root1 = rootNode
      if root1 != null then
        Engine.layout(this, root1, Rect(0, 0, viewportW, viewportH))
        layoutOverlays(viewportW, viewportH)
        // Layout-effects fire after layout but before render — so they can
        // read measured bounds and adjust state before any pixels go out.
        flushLayoutEffects()
        drawList.clear()
        drawList += FillRect(Rect(0, 0, viewportW, viewportH), theme.bg)
        Engine.render(this, root1)
        renderOverlays()

    // Commit phase finished — now safe to fire useEffect bodies. They may
    // call setState, which schedules another reconcile for the next frame.
    flushEffects()

    input.clearFrameEdges()
    frame = frame + 1
    dirty = false
    // Caret-blink keeps the host ticking only while a text field is focused;
    // active useTransitions also keep frames coming until they settle.
    animating = focused.isInstanceOf[InputNode] || animationCount > 0
    // Active transitions need each next frame to actually re-reconcile so
    // their hook bodies re-run and step the eased value. requestRender()
    // can't reliably do this from inside a render (pendingView is still the
    // pre-commit view), so the engine seeds pendingView itself at end of
    // frame whenever animations are in flight.
    if animationCount > 0 && pendingView == null && rootView != null then
      pendingView = rootView

  private def dispatchOverlays(): Unit =
    var i = portalNodes.length - 1
    while i >= 0 do
      val ovl = portalNodes(i).overlay
      if ovl != null then Engine.dispatchEvents(this, ovl)
      i = i - 1

  private def layoutOverlays(w: Int, h: Int): Unit =
    val frame = Rect(0, 0, w, h)
    var i = 0
    while i < portalNodes.length do
      val ovl = portalNodes(i).overlay
      if ovl != null then Engine.layout(this, ovl, frame)
      i = i + 1

  private def renderOverlays(): Unit =
    var i = 0
    while i < portalNodes.length do
      val ovl = portalNodes(i).overlay
      if ovl != null then Engine.render(this, ovl)
      i = i + 1

  private def commitPending(): Unit =
    val pv = pendingView
    if pv != null then
      rootNode = Reconciler.reconcile(rootNode, pv, this)
      rootView = pv
      pendingView = null


// --- Algorithms over the Node tree ------------------------------------------

object Engine:

  // ----- measure ----------------------------------------------------------

  def measure(eng: Engine, n: Node): Size = n match
    case t: TextNode     => measureText(eng, t)
    case b: ButtonNode   => measureButton(eng, b)
    case i: InputNode    => measureInput(eng, i)
    case c: CheckboxNode => measureCheckbox(eng, c)
    case _: SpacerNode   => Size(0, 0)
    case i: ImageNode    => Size(i.view.width, i.view.height)
    case s: SliderNode   => Size(s.view.width, eng.theme.lineHeight + 4)
    case r: RadioNode    => measureRadio(eng, r)
    case s: StackNode    => measureStack(eng, s)
    case c: ComponentNode =>
      val ch = c.child
      if ch == null then Size(0, 0) else measure(eng, ch)
    case cp: ContextProviderNode =>
      val ch = cp.child
      if ch == null then Size(0, 0) else measure(eng, ch)
    case eb: ErrorBoundaryNode =>
      val ch = eb.child
      if ch == null then Size(0, 0) else measure(eng, ch)
    case sn: ScrollNode =>
      // Scroll's preferred size is its declared viewport height with the
      // child's natural width. The full content height shows through only
      // during layout/render.
      val ch = sn.child
      val cw = if ch == null then 0 else measure(eng, ch).w
      Size(cw, sn.view.height)
    case _: PortalNode => Size(0, 0)   // portals have no in-flow size
    case ap: AbsolutePositionNode =>
      val ch = ap.child
      if ch == null then Size(0, 0) else measure(eng, ch)
    case c: CenterNode =>
      val ch = c.child
      if ch == null then Size(0, 0) else measure(eng, ch)
    case b: BackdropNode =>
      val ch = b.child
      if ch == null then Size(0, 0) else measure(eng, ch)

  private def textWidth(eng: Engine, s: String): Int =
    eng.textMeasure(s, eng.theme.fontSize)

  private def measureText(eng: Engine, t: TextNode): Size =
    Size(textWidth(eng, t.view.content), eng.theme.lineHeight)

  private def measureButton(eng: Engine, b: ButtonNode): Size =
    Size(
      textWidth(eng, b.view.label) + eng.theme.btnPaddingX * 2,
      eng.theme.lineHeight         + eng.theme.btnPaddingY * 2,
    )

  private def measureInput(eng: Engine, i: InputNode): Size =
    Size(i.view.width, eng.theme.lineHeight + eng.theme.inputPaddingY * 2)

  private def measureCheckbox(eng: Engine, c: CheckboxNode): Size =
    val box = eng.theme.lineHeight
    Size(box + 6 + textWidth(eng, c.view.label), eng.theme.lineHeight)

  private def measureRadio(eng: Engine, r: RadioNode): Size =
    val box = eng.theme.lineHeight
    Size(box + 6 + textWidth(eng, r.view.label), eng.theme.lineHeight)

  private def measureStack(eng: Engine, s: StackNode): Size =
    val v = s.view
    var w = 0
    var h = 0
    var i = 0
    while i < s.children.length do
      val cs = measure(eng, s.children(i))
      v.axis match
        case Axis.Vertical =>
          if cs.w > w then w = cs.w
          h = h + cs.h
          if i > 0 then h = h + v.gap
        case Axis.Horizontal =>
          w = w + cs.w
          if i > 0 then w = w + v.gap
          if cs.h > h then h = cs.h
      i = i + 1
    Size(w + v.padding.horizontal, h + v.padding.vertical)


  // ----- layout -----------------------------------------------------------

  def layout(eng: Engine, n: Node, frame: Rect): Unit =
    n.bounds = frame
    n match
      case s: StackNode     => layoutStack(eng, s, frame)
      case c: ComponentNode =>
        val ch = c.child
        if ch != null then layout(eng, ch, frame)
      case cp: ContextProviderNode =>
        val ch = cp.child
        if ch != null then layout(eng, ch, frame)
      case eb: ErrorBoundaryNode =>
        val ch = eb.child
        if ch != null then layout(eng, ch, frame)
      case sn: ScrollNode =>
        // Honor the declared viewport height: even if the parent allocates
        // more space, our viewport stays at view.height. Bounds were set by
        // the outer `layout` call above; override the height here.
        val viewportH = math.min(frame.h, sn.view.height)
        sn.bounds = Rect(frame.x, frame.y, frame.w, viewportH)
        val ch = sn.child
        if ch != null then
          val cs = measure(eng, ch)
          sn.contentHeight = cs.h
          val maxScroll = (cs.h - viewportH).max(0)
          if sn.scrollY > maxScroll then sn.scrollY = maxScroll
          if sn.scrollY < 0           then sn.scrollY = 0
          // Child laid out at its full natural height, offset upward by scrollY.
          layout(eng, ch, Rect(frame.x, frame.y - sn.scrollY, frame.w, cs.h))
      case _: PortalNode => ()    // child is laid out separately by layoutOverlays
      case ap: AbsolutePositionNode =>
        val ch = ap.child
        if ch != null then
          val cs        = measure(eng, ch)
          val childRect = Rect(ap.view.x, ap.view.y, cs.w, cs.h)
          // Override our own bounds to match the child's positioned rect so
          // hit-testing (e.g. Backdrop's "click inside child?" check) sees
          // the right area instead of inheriting the parent's frame.
          ap.bounds = childRect
          layout(eng, ch, childRect)
      case cn: CenterNode =>
        val ch = cn.child
        if ch != null then
          val cs        = measure(eng, ch)
          val cx        = frame.x + (frame.w - cs.w) / 2
          val cy        = frame.y + (frame.h - cs.h) / 2
          val childRect = Rect(cx, cy, cs.w, cs.h)
          // Override our own bounds to the centered child rect (mirrors
          // AbsolutePositionNode) so a wrapping Backdrop's "click inside
          // child?" check sees the actual rendered area, not the full frame.
          cn.bounds = childRect
          layout(eng, ch, childRect)
      case b: BackdropNode =>
        val ch = b.child
        if ch != null then layout(eng, ch, frame)
      case _ => ()

  private def layoutStack(eng: Engine, s: StackNode, frame: Rect): Unit =
    val v       = s.view
    val innerX  = frame.x + v.padding.left
    val innerY  = frame.y + v.padding.top
    val innerW  = frame.w - v.padding.horizontal
    val innerH  = frame.h - v.padding.vertical

    var fixedTotal = 0
    var flexTotal  = 0
    var i = 0
    while i < s.children.length do
      val child = s.children(i)
      val flex  = flexOf(child)
      if flex > 0 then flexTotal = flexTotal + flex
      else
        v.axis match
          case Axis.Vertical   => fixedTotal = fixedTotal + measure(eng, child).h
          case Axis.Horizontal => fixedTotal = fixedTotal + measure(eng, child).w
      i = i + 1

    val gaps      = if s.children.length > 1 then (s.children.length - 1) * v.gap else 0
    val available = (v.axis match
      case Axis.Vertical   => innerH
      case Axis.Horizontal => innerW
    ) - fixedTotal - gaps
    val flexPool  = if available > 0 then available else 0

    var pos = v.axis match
      case Axis.Vertical   => innerY
      case Axis.Horizontal => innerX

    i = 0
    while i < s.children.length do
      val child = s.children(i)
      val flex  = flexOf(child)
      v.axis match
        case Axis.Vertical =>
          val ch =
            if flex > 0 && flexTotal > 0 then flexPool * flex / flexTotal
            else measure(eng, child).h
          layout(eng, child, Rect(innerX, pos, innerW, ch))
          pos = pos + ch
          if i < s.children.length - 1 then pos = pos + v.gap
        case Axis.Horizontal =>
          val cw =
            if flex > 0 && flexTotal > 0 then flexPool * flex / flexTotal
            else measure(eng, child).w
          layout(eng, child, Rect(pos, innerY, cw, innerH))
          pos = pos + cw
          if i < s.children.length - 1 then pos = pos + v.gap
      i = i + 1

  private def flexOf(n: Node): Int = n match
    case s: SpacerNode => s.view.flex
    case _             => 0


  // ----- event dispatch ---------------------------------------------------

  // Top-of-frame: handle Tab/Shift-Tab focus cycling before any per-widget
  // dispatch, so focusables don't intercept the Tab key for their own use.
  // Consumes the keyTab edge if a focusable is found.
  private[suit] def handleTabKey(eng: Engine, root: Node): Unit =
    if !eng.input.keyTab then return
    val list = scala.collection.mutable.ArrayBuffer.empty[Node]
    collectFocusables(root, list)
    if list.isEmpty then return
    val current = eng.focused
    val idx     = if current != null then list.indexOf(current) else -1
    val next =
      if eng.input.keyShiftDown then
        if idx <= 0 then list.length - 1 else idx - 1
      else
        if idx == list.length - 1 then 0 else idx + 1
    eng.focusNode(list(next))
    eng.input.keyTab = false   // consume — don't bubble to text fields etc.

  private def collectFocusables(n: Node, out: scala.collection.mutable.ArrayBuffer[Node]): Unit = n match
    case _: InputNode    => out += n
    case _: CheckboxNode => out += n
    case _: RadioNode    => out += n
    case _: SliderNode   => out += n
    case s: StackNode =>
      var i = 0
      while i < s.children.length do
        collectFocusables(s.children(i), out)
        i = i + 1
    case c: ComponentNode =>
      val ch = c.child; if ch != null then collectFocusables(ch, out)
    case cp: ContextProviderNode =>
      val ch = cp.child; if ch != null then collectFocusables(ch, out)
    case eb: ErrorBoundaryNode =>
      val ch = eb.child; if ch != null then collectFocusables(ch, out)
    case sn: ScrollNode =>
      val ch = sn.child; if ch != null then collectFocusables(ch, out)
    case p: PortalNode =>
      val ovl = p.overlay; if ovl != null then collectFocusables(ovl, out)
    case ap: AbsolutePositionNode =>
      val ch = ap.child; if ch != null then collectFocusables(ch, out)
    case c: CenterNode =>
      val ch = c.child; if ch != null then collectFocusables(ch, out)
    case b: BackdropNode =>
      val ch = b.child; if ch != null then collectFocusables(ch, out)
    case _ => ()


  def dispatchEvents(eng: Engine, n: Node): Unit = n match
    case b: ButtonNode    => dispatchButton(eng, b)
    case c: CheckboxNode  => dispatchCheckbox(eng, c)
    case r: RadioNode     => dispatchRadio(eng, r)
    case s: SliderNode    => dispatchSlider(eng, s)
    case i: InputNode     => dispatchInput(eng, i)
    case s: StackNode =>
      var k = 0
      while k < s.children.length do
        dispatchEvents(eng, s.children(k))
        k = k + 1
    case c: ComponentNode =>
      val ch = c.child
      if ch != null then dispatchEvents(eng, ch)
    case cp: ContextProviderNode =>
      val ch = cp.child
      if ch != null then dispatchEvents(eng, ch)
    case eb: ErrorBoundaryNode =>
      val ch = eb.child
      if ch != null then dispatchEvents(eng, ch)
    case sn: ScrollNode =>
      // Recurse first so an inner Scroll consumes the wheel before us.
      val ch = sn.child
      if ch != null then dispatchEvents(eng, ch)
      if eng.input.wheelDeltaY != 0f
        && sn.bounds.contains(eng.input.mouseX, eng.input.mouseY) then
        val before = sn.scrollY
        val maxScroll = (sn.contentHeight - sn.bounds.h).max(0)
        var next = sn.scrollY + eng.input.wheelDeltaY.toInt
        if next < 0           then next = 0
        if next > maxScroll   then next = maxScroll
        if next != before then
          sn.scrollY = next
          eng.markDirty()
        eng.input.wheelDeltaY = 0f   // consume
    case _: PortalNode => ()        // dispatched separately
    case ap: AbsolutePositionNode =>
      val ch = ap.child
      if ch != null then dispatchEvents(eng, ch)
    case c: CenterNode =>
      val ch = c.child
      if ch != null then dispatchEvents(eng, ch)
    case b: BackdropNode =>
      val ch = b.child
      if ch != null then dispatchEvents(eng, ch)
      // Outside-click → onBackdropClick. Either way, consume the press so it
      // doesn't bleed through to the main tree below.
      if eng.input.mousePressed
        && b.bounds.contains(eng.input.mouseX, eng.input.mouseY) then
        val childInside = ch != null
          && ch.bounds.contains(eng.input.mouseX, eng.input.mouseY)
        if !childInside then b.view.onBackdropClick()
        eng.input.mousePressed  = false
        eng.input.mouseReleased = false
    case _ => ()

  private def dispatchButton(eng: Engine, b: ButtonNode): Unit =
    val inside = b.bounds.contains(eng.input.mouseX, eng.input.mouseY)
    b.hover = inside && b.view.enabled
    if !b.view.enabled then
      b.pressed = false
    else if inside && eng.input.mousePressed then
      b.pressed = true
    else if b.pressed && eng.input.mouseReleased then
      b.pressed = false
      if inside then b.view.onClick()

  private def dispatchCheckbox(eng: Engine, c: CheckboxNode): Unit =
    val inside = c.bounds.contains(eng.input.mouseX, eng.input.mouseY)
    c.hover = inside && c.view.enabled
    if eng.input.mousePressed then
      if inside && c.view.enabled then
        focusWidget(eng, c)
        c.view.onToggle(!c.view.checked)
      else if c.focused then
        c.focused = false
        if eng.focused == c then eng.focused = null
    if c.focused && c.view.enabled && (eng.input.keySpace || eng.input.keyEnter) then
      c.view.onToggle(!c.view.checked)

  private def dispatchRadio(eng: Engine, r: RadioNode): Unit =
    val inside = r.bounds.contains(eng.input.mouseX, eng.input.mouseY)
    r.hover = inside && r.view.enabled
    if eng.input.mousePressed then
      if inside && r.view.enabled then
        focusWidget(eng, r)
        if !r.view.selected then r.view.onSelect()
      else if r.focused then
        r.focused = false
        if eng.focused == r then eng.focused = null
    if r.focused && r.view.enabled && (eng.input.keySpace || eng.input.keyEnter) then
      if !r.view.selected then r.view.onSelect()

  private def dispatchSlider(eng: Engine, s: SliderNode): Unit =
    val inside = s.bounds.contains(eng.input.mouseX, eng.input.mouseY)
    s.hover = inside && s.view.enabled

    if eng.input.mousePressed then
      if inside && s.view.enabled then
        focusWidget(eng, s)
        s.dragging = true
        emitSliderValue(eng, s)
      else if s.focused then
        s.focused = false
        if eng.focused == s then eng.focused = null

    if s.dragging && eng.input.mouseDown && s.view.enabled then
      emitSliderValue(eng, s)

    if eng.input.mouseReleased then s.dragging = false

    if s.focused && s.view.enabled then
      if eng.input.keyLeft then
        eng.input.keyLeft = false
        if s.view.value > s.view.min then s.view.onChange(s.view.value - 1)
      if eng.input.keyRight then
        eng.input.keyRight = false
        if s.view.value < s.view.max then s.view.onChange(s.view.value + 1)
      if eng.input.keyHome then
        eng.input.keyHome = false
        if s.view.value != s.view.min then s.view.onChange(s.view.min)
      if eng.input.keyEnd then
        eng.input.keyEnd = false
        if s.view.value != s.view.max then s.view.onChange(s.view.max)

  private def emitSliderValue(eng: Engine, s: SliderNode): Unit =
    val range = s.view.max - s.view.min
    if range <= 0 || s.bounds.w <= 0 then return
    val rel0 = (eng.input.mouseX - s.bounds.x).toDouble / s.bounds.w.toDouble
    val rel  = if rel0 < 0.0 then 0.0 else if rel0 > 1.0 then 1.0 else rel0
    val v    = s.view.min + math.round(rel * range).toInt
    if v != s.view.value then s.view.onChange(v)

  private def dispatchInput(eng: Engine, n: InputNode): Unit =
    val inside = n.bounds.contains(eng.input.mouseX, eng.input.mouseY)
    n.hover = inside && !n.focused

    if eng.input.mousePressed then
      if inside then
        if eng.focused ne n then
          focusWidget(eng, n)
          eng.resetCaret()
      else if n.focused then
        n.focused = false
        if eng.focused == n then eng.focused = null

    if n.focused then editInput(eng, n)

  private def editInput(eng: Engine, n: InputNode): Unit =
    var changed = false
    val v       = n.view
    var value   = v.value

    var i = 0
    while i < eng.input.typedLen do
      val ch = eng.input.typedChars(i)
      value  = stringInsert(value, n.cursor, ch.toChar.toString)
      n.cursor = n.cursor + 1
      changed = true
      i = i + 1
    if eng.input.keyBackspace && n.cursor > 0 then
      value  = stringRemove(value, n.cursor - 1)
      n.cursor = n.cursor - 1
      changed = true
    if eng.input.keyDelete && n.cursor < value.length then
      value = stringRemove(value, n.cursor)
      changed = true
    if eng.input.keyLeft && n.cursor > 0 then
      n.cursor = n.cursor - 1
      changed = true
    if eng.input.keyRight && n.cursor < value.length then
      n.cursor = n.cursor + 1
      changed = true
    if eng.input.keyHome then
      n.cursor = 0
      changed = true
    if eng.input.keyEnd then
      n.cursor = value.length
      changed = true

    if changed then
      eng.resetCaret()
      if value != v.value then v.onChange(value)

  // Move focus to `n`, clearing whatever was focused before.
  private def focusWidget(eng: Engine, n: Node): Unit =
    val prev = eng.focused
    if prev ne n then
      if prev != null then setFocus(prev, false)
      setFocus(n, true)
      eng.focused = n

  private def setFocus(n: Node, on: Boolean): Unit = n match
    case x: InputNode    => x.focused = on
    case x: CheckboxNode => x.focused = on
    case x: RadioNode    => x.focused = on
    case x: SliderNode   => x.focused = on
    case _               => ()

  private def stringInsert(s: String, pos: Int, ch: String): String =
    s.substring(0, pos) + ch + s.substring(pos)

  private def stringRemove(s: String, pos: Int): String =
    s.substring(0, pos) + s.substring(pos + 1)


  // ----- render -----------------------------------------------------------

  def render(eng: Engine, n: Node): Unit = n match
    case t: TextNode     => renderText(eng, t)
    case b: ButtonNode   => renderButton(eng, b)
    case i: InputNode    => renderInput(eng, i)
    case c: CheckboxNode => renderCheckbox(eng, c)
    case r: RadioNode    => renderRadio(eng, r)
    case s: SliderNode   => renderSlider(eng, s)
    case i: ImageNode    => eng.drawList += DrawImage(i.bounds, i.view.source)
    case _: SpacerNode   => ()
    case s: StackNode =>
      var i = 0
      while i < s.children.length do
        render(eng, s.children(i))
        i = i + 1
    case c: ComponentNode =>
      val ch = c.child
      if ch != null then render(eng, ch)
    case cp: ContextProviderNode =>
      val ch = cp.child
      if ch != null then render(eng, ch)
    case eb: ErrorBoundaryNode =>
      val ch = eb.child
      if ch != null then render(eng, ch)
    case sn: ScrollNode =>
      eng.drawList += PushClip(sn.bounds)
      val ch = sn.child
      if ch != null then render(eng, ch)
      eng.drawList += PopClip
    case _: PortalNode => ()   // rendered later by renderOverlays
    case ap: AbsolutePositionNode =>
      val ch = ap.child
      if ch != null then render(eng, ch)
    case c: CenterNode =>
      val ch = c.child
      if ch != null then render(eng, ch)
    case b: BackdropNode =>
      eng.drawList += FillRect(b.bounds, b.view.color)
      val ch = b.child
      if ch != null then render(eng, ch)

  private def renderText(eng: Engine, t: TextNode): Unit =
    val ty = t.bounds.y + (t.bounds.h + eng.theme.fontSize) / 2 - 2
    eng.drawList += DrawText(t.bounds.x, ty, t.view.content, eng.theme.fg)

  private def renderButton(eng: Engine, b: ButtonNode): Unit =
    val bg =
      if !b.view.enabled then eng.theme.btnDisabledBg
      else if b.pressed   then eng.theme.btnPressedBg
      else if b.hover     then eng.theme.btnHoverBg
      else                     eng.theme.btnBg
    val fg = if b.view.enabled then eng.theme.fg else eng.theme.muted
    val r  = b.bounds
    val ra = eng.theme.btnRadius
    eng.drawList += FillRoundRect(r, ra, bg)
    eng.drawList += StrokeRoundRect(r, ra, eng.theme.border)
    val tx = r.x + (r.w - eng.theme.charWidth * b.view.label.length) / 2
    val ty = r.y + (r.h + eng.theme.fontSize) / 2 - 2
    eng.drawList += DrawText(tx, ty, b.view.label, fg)

  private def renderInput(eng: Engine, n: InputNode): Unit =
    val r  = n.bounds
    val ra = eng.theme.inputRadius

    if n.focused then
      eng.drawList += DrawShadow(r, ra, Shadow.focusRing(eng.theme.focusBorder))

    val bg =
      if n.focused then eng.theme.inputFocusBg
      else                  eng.theme.inputBg
    val border =
      if n.focused    then eng.theme.focusBorder
      else if n.hover then eng.theme.inputHoverBorder
      else                 eng.theme.inputBorder

    eng.drawList += FillRoundRect(r, ra, bg)
    eng.drawList += StrokeRoundRect(r, ra, border)

    val tx     = r.x + eng.theme.inputPaddingX
    val ty     = r.y + (r.h + eng.theme.fontSize) / 2 - 2
    val innerW = r.w - eng.theme.inputPaddingX * 2
    val value  = n.view.value

    // Keep the caret in view, and don't scroll past the end of the value
    // (otherwise deleting text leaves the visible window stranded out in
    // empty space to the right of the actual content). The 2-px slack lets
    // the caret sit just past the last glyph without touching the border.
    val caretPx   = n.cursor * eng.theme.charWidth
    val textPx    = value.length * eng.theme.charWidth
    val maxScroll = if textPx + 2 > innerW then textPx + 2 - innerW else 0
    if caretPx - n.scrollX > innerW - 2 then n.scrollX = caretPx - (innerW - 2)
    if caretPx - n.scrollX < 0           then n.scrollX = caretPx
    if n.scrollX > maxScroll then n.scrollX = maxScroll
    if n.scrollX < 0         then n.scrollX = 0

    // Clip the inner region so long values don't bleed past the rounded box.
    val clipRect = Rect(r.x + 1, r.y + 1, r.w - 2, r.h - 2)
    eng.drawList += PushClip(clipRect)

    if value.length == 0 && !n.focused && n.view.placeholder.length > 0 then
      eng.drawList += DrawText(tx - n.scrollX, ty, n.view.placeholder, eng.theme.muted)
    else
      eng.drawList += DrawText(tx - n.scrollX, ty, value, eng.theme.fg)

    val blinkMs = 530
    if n.focused && ((eng.nowMs - eng.caretEpochMs) / blinkMs) % 2 == 0 then
      val cx = tx + caretPx - n.scrollX
      eng.drawList += FillRect(Rect(cx, r.y + 4, 1, r.h - 8), eng.theme.fg)

    eng.drawList += PopClip

  private def renderCheckbox(eng: Engine, c: CheckboxNode): Unit =
    val box  = eng.theme.lineHeight
    val bx   = c.bounds.x
    val by   = c.bounds.y + (c.bounds.h - box) / 2
    val rect = Rect(bx, by, box, box)
    val ra   = eng.theme.checkRadius

    if c.focused then
      eng.drawList += DrawShadow(rect, ra, Shadow.focusRing(eng.theme.focusBorder))

    val bg = if c.view.checked then eng.theme.accent else eng.theme.inputBg
    val border =
      if c.focused    then eng.theme.focusBorder
      else if c.hover then eng.theme.inputHoverBorder
      else                 eng.theme.inputBorder

    eng.drawList += FillRoundRect(rect, ra, bg)
    eng.drawList += StrokeRoundRect(rect, ra, border)
    if c.view.checked then
      val cx = bx + box / 2
      val cy = by + box / 2
      eng.drawList += FillRect(Rect(cx - 4, cy - 1, 8, 2), eng.theme.accentText)

    val tx = bx + box + 6
    val ty = c.bounds.y + (c.bounds.h + eng.theme.fontSize) / 2 - 2
    val fg = if c.view.enabled then eng.theme.fg else eng.theme.muted
    eng.drawList += DrawText(tx, ty, c.view.label, fg)

  private def renderRadio(eng: Engine, r: RadioNode): Unit =
    val box  = eng.theme.lineHeight
    val bx   = r.bounds.x
    val by   = r.bounds.y + (r.bounds.h - box) / 2
    val rect = Rect(bx, by, box, box)
    val ra   = box / 2     // full radius → circle

    if r.focused then
      eng.drawList += DrawShadow(rect, ra, Shadow.focusRing(eng.theme.focusBorder))

    val bg = if r.view.selected then eng.theme.accent else eng.theme.inputBg
    val border =
      if r.focused    then eng.theme.focusBorder
      else if r.hover then eng.theme.inputHoverBorder
      else                 eng.theme.inputBorder

    eng.drawList += FillRoundRect(rect, ra, bg)
    eng.drawList += StrokeRoundRect(rect, ra, border)
    if r.view.selected then
      // Inner dot (concentric, half size).
      val inner = box / 3
      val ix = bx + (box - inner) / 2
      val iy = by + (box - inner) / 2
      eng.drawList += FillRoundRect(Rect(ix, iy, inner, inner), inner / 2, eng.theme.accentText)

    val tx = bx + box + 6
    val ty = r.bounds.y + (r.bounds.h + eng.theme.fontSize) / 2 - 2
    val fg = if r.view.enabled then eng.theme.fg else eng.theme.muted
    eng.drawList += DrawText(tx, ty, r.view.label, fg)

  private def renderSlider(eng: Engine, s: SliderNode): Unit =
    val trackH = 4
    val tx     = s.bounds.x
    val ty     = s.bounds.y + (s.bounds.h - trackH) / 2
    val track  = Rect(tx, ty, s.bounds.w, trackH)
    val ra     = trackH / 2

    if s.focused then
      eng.drawList += DrawShadow(track, ra, Shadow.focusRing(eng.theme.focusBorder))

    eng.drawList += FillRoundRect(track, ra, eng.theme.inputBg)
    eng.drawList += StrokeRoundRect(track, ra, eng.theme.inputBorder)

    val range = s.view.max - s.view.min
    val rel   =
      if range <= 0 then 0.0
      else (s.view.value - s.view.min).toDouble / range.toDouble
    val thumbW = 12
    val thumbH = eng.theme.lineHeight
    val px     = tx + ((s.bounds.w - thumbW).toDouble * rel).round.toInt
    val py     = s.bounds.y + (s.bounds.h - thumbH) / 2
    val thumb  = Rect(px, py, thumbW, thumbH)

    // Filled track from start to thumb center.
    if rel > 0.0 then
      val fillW = px - tx + thumbW / 2
      eng.drawList += FillRoundRect(Rect(tx, ty, fillW, trackH), ra, eng.theme.accent)

    val thumbBg =
      if !s.view.enabled then eng.theme.btnDisabledBg
      else if s.dragging then eng.theme.btnPressedBg
      else if s.hover    then eng.theme.btnHoverBg
      else                    eng.theme.btnBg
    eng.drawList += FillRoundRect(thumb, eng.theme.btnRadius, thumbBg)
    eng.drawList += StrokeRoundRect(thumb, eng.theme.btnRadius, eng.theme.border)
