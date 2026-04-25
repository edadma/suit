package io.github.edadma.suit

import scala.collection.mutable

// Reconciles a new View tree into an existing Node tree. The reconciler's
// job is to keep the Node tree minimal and stateful: same View kind at the
// same position reuses the existing Node (preserving hover/focus/cursor and
// any hook state); kind mismatches mount a fresh Node and unmount the old
// one (running its hook cleanups).
//
// Keys: when a Stack child is wrapped in `Keyed(k, view)`, the reconciler
// matches old/new children by key first, falling back to position-only
// matching for unkeyed siblings. This is what lets a list survive reorders
// and inserts without losing per-item state.
//
// Fragments: a `Fragment(children)` view splices its children into the
// parent's children list at the same position. The reconciler expands them
// before the keyed/positional pass.
//
// Contexts: `ContextProvider(ctx, value, child)` pushes a value onto the
// engine's per-context stack while reconciling its subtree. The framework
// then pops it on the way back out so sibling subtrees see only their own
// providers.
//
// Sysl: nested matches on (Node-tag, View-tag).
object Reconciler:

  // Top-level entry: reconcile a single View against an optional current
  // Node. `current == null` means "mount from scratch". Returns the Node
  // that now corresponds to the supplied View.
  def reconcile(current: Node | Null, next: View, eng: Engine): Node =
    next match
      case Keyed(k, inner) =>
        val n = reconcile(current, inner, eng)
        n.key = k
        n
      case WithRef(ref, inner) =>
        val n = reconcile(current, inner, eng)
        ref.current = n
        n
      case other =>
        reconcileBase(current, other, eng)

  private def reconcileBase(current: Node | Null, next: View, eng: Engine): Node =
    (current, next) match

      case (n: TextNode, v: Text) =>
        if n.view.content != v.content then eng.markDirty()
        n.view = v
        n

      case (n: ButtonNode, v: Button) =>
        if n.view.label != v.label || n.view.enabled != v.enabled then eng.markDirty()
        n.view = v
        n

      case (n: InputNode, v: Input) =>
        if n.view.value != v.value then
          eng.markDirty()
          if n.cursor > v.value.length then n.cursor = v.value.length
        n.view = v
        n

      case (n: CheckboxNode, v: Checkbox) =>
        if n.view.checked != v.checked || n.view.label != v.label
          || n.view.enabled != v.enabled then eng.markDirty()
        n.view = v
        n

      case (n: SpacerNode, v: Spacer) =>
        if n.view.flex != v.flex then eng.markDirty()
        n.view = v
        n

      case (n: SizedNode, v: Sized) =>
        if n.view.width != v.width || n.view.height != v.height then eng.markDirty()
        n.view = v
        n.child = reconcile(n.child, v.child, eng)
        n

      case (n: ImageNode, v: Image) =>
        if n.view.source != v.source || n.view.width != v.width
          || n.view.height != v.height then eng.markDirty()
        n.view = v
        n

      case (n: BoxNode, v: Box) =>
        if n.view.color != v.color || n.view.padding != v.padding
          || n.view.radius != v.radius || n.view.border != v.border then eng.markDirty()
        n.view = v
        n.child = reconcile(n.child, v.child, eng)
        n

      case (n: SliderNode, v: Slider) =>
        if n.view.value != v.value || n.view.min != v.min || n.view.max != v.max
          || n.view.width != v.width || n.view.enabled != v.enabled then eng.markDirty()
        n.view = v
        n

      case (n: RadioNode, v: Radio) =>
        if n.view.selected != v.selected || n.view.label != v.label
          || n.view.enabled != v.enabled then eng.markDirty()
        n.view = v
        n

      case (n: StackNode, v: Stack) =>
        if n.view.axis != v.axis then
          unmount(n, eng)
          mount(v, eng)
        else
          if n.view.padding != v.padding || n.view.gap != v.gap then eng.markDirty()
          n.view = v
          reconcileChildren(n, v.children, eng)
          n

      case (n: ComponentNode, v: Component) if n.widget eq v.widget =>
        if n.widget.shouldRender then
          n.child = reconcile(n.child, v.widget.render(), eng)
        else
          // Memo bailout: walk existing children to surface any dirty fibers
          // deeper down (e.g. a child component whose own state changed).
          val ch = n.child
          if ch != null then visitForUpdates(ch, eng)
        n

      case (n: ComponentNode, v: Component) if n.widget.widgetId() == v.widget.widgetId() =>
        n.widget.updateProps(v.widget)
        if n.widget.shouldRender then
          n.child = reconcile(n.child, n.widget.render(), eng)
        else
          val ch = n.child
          if ch != null then visitForUpdates(ch, eng)
        n

      case (n: ContextProviderNode, v: ContextProvider) if n.ctx eq v.ctx =>
        // Same context — push, recurse, pop. The previous child is reused
        // when its kind matches the new one.
        if n.value != v.value then
          eng.markDirty()
          val ch = n.child
          if ch != null then invalidateContextSubscribers(ch, v.ctx, eng)
        n.value = v.value
        eng.pushContext(v.ctx.asInstanceOf[Context[Any]], v.value)
        n.child = reconcile(n.child, v.child, eng)
        eng.popContext(v.ctx.asInstanceOf[Context[Any]])
        n

      case (n: ErrorBoundaryNode, v: ErrorBoundary) =>
        n.view = v
        try
          n.child = reconcile(n.child, v.child, eng)
        catch
          case t: Throwable =>
            val ch = n.child
            if ch != null then unmount(ch, eng)
            n.child = mount(v.fallback(t), eng)
            eng.markDirty()
        n

      case (n: ScrollNode, v: Scroll) =>
        if n.view.height != v.height then eng.markDirty()
        n.view = v
        n.child = reconcile(n.child, v.child, eng)
        n

      case (n: PortalNode, v: Portal) =>
        n.overlay = reconcile(n.overlay, v.child, eng)
        n

      case (n: AbsolutePositionNode, v: AbsolutePosition) =>
        if n.view.x != v.x || n.view.y != v.y then eng.markDirty()
        n.view = v
        n.child = reconcile(n.child, v.child, eng)
        n

      case (n: CenterNode, v: Center) =>
        n.view = v
        n.child = reconcile(n.child, v.child, eng)
        n

      case (n: BackdropNode, v: Backdrop) =>
        if n.view.color != v.color then eng.markDirty()
        n.view = v
        n.child = reconcile(n.child, v.child, eng)
        n

      case (_, v) =>
        // Kind mismatch (or first mount). Unmount the old node so its hook
        // cleanups fire, then build fresh.
        if current != null then unmount(current, eng)
        eng.markDirty()
        mount(v, eng)


  // Build a fresh Node subtree from a View. Handles Fragments and Keyeds at
  // the leaves transparently (Keyed unwraps; Fragment becomes a SpacerNode
  // when it appears as a sole child — Fragments only really make sense as
  // *splice points* inside a Stack's children, which `expandFragments`
  // handles before this is called).
  def mount(v: View, eng: Engine): Node = v match
    case Keyed(k, inner) =>
      val n = mount(inner, eng); n.key = k; n
    case WithRef(ref, inner) =>
      val n = mount(inner, eng); ref.current = n; n
    case t: Text     => new TextNode(t)
    case b: Button   => new ButtonNode(b)
    case i: Input    => new InputNode(i)
    case c: Checkbox => new CheckboxNode(c)
    case s: Spacer   => new SpacerNode(s)
    case sz: Sized   =>
      val n = new SizedNode(sz)
      n.child = mount(sz.child, eng)
      n
    case i: Image    => new ImageNode(i)
    case b: Box      =>
      val n = new BoxNode(b)
      n.child = mount(b.child, eng)
      n
    case s: Slider   => new SliderNode(s)
    case r: Radio    => new RadioNode(r)
    case Empty       => new SpacerNode(Spacer(0))
    case Fragment(_) =>
      // A bare Fragment outside a Stack collapses to nothing visible; it
      // only does work when expandFragments splices it.
      new SpacerNode(Spacer(0))
    case s: Stack    =>
      val n = new StackNode(s)
      val expanded = expandFragments(s.children)
      var i = 0
      while i < expanded.length do
        // mount() recurses into Keyed / WithRef itself, so just pass the
        // raw view through.
        n.children += mount(expanded(i), eng)
        i = i + 1
      n
    case c: Component =>
      val n = new ComponentNode(c.widget)
      c.widget.attach(eng)
      n.child = mount(c.widget.render(), eng)
      n
    case cp: ContextProvider =>
      val n = new ContextProviderNode(cp.ctx, cp.value)
      eng.pushContext(cp.ctx.asInstanceOf[Context[Any]], cp.value)
      n.child = mount(cp.child, eng)
      eng.popContext(cp.ctx.asInstanceOf[Context[Any]])
      n

    case eb: ErrorBoundary =>
      val n = new ErrorBoundaryNode(eb)
      try
        n.child = mount(eb.child, eng)
      catch
        case t: Throwable =>
          n.child = mount(eb.fallback(t), eng)
      n

    case s: Scroll =>
      val n = new ScrollNode(s)
      n.child = mount(s.child, eng)
      n

    case p: Portal =>
      val n = new PortalNode
      n.overlay = mount(p.child, eng)
      eng.portalNodes += n
      n

    case ap: AbsolutePosition =>
      val n = new AbsolutePositionNode(ap)
      n.child = mount(ap.child, eng)
      n

    case c: Center =>
      val n = new CenterNode(c)
      n.child = mount(c.child, eng)
      n

    case b: Backdrop =>
      val n = new BackdropNode(b)
      n.child = mount(b.child, eng)
      n


  // Walk a node tree being torn down and run any hook cleanups stored
  // beneath. Called when the reconciler discards a subtree (kind mismatch,
  // children removal, keyed orphan). Cleanup order is child-first so a
  // parent's cleanup can still reference state belonging to a child that
  // hasn't yet been torn down — the React semantics.
  def unmount(node: Node, eng: Engine): Unit = node match
    case c: ComponentNode =>
      val ch = c.child
      if ch != null then unmount(ch, eng)
      c.widget match
        case h: HookCarrier => h.hooks.runUnmountCleanups()
        case _              => ()
    case s: StackNode =>
      var i = 0
      while i < s.children.length do
        unmount(s.children(i), eng)
        i = i + 1
    case cp: ContextProviderNode =>
      val ch = cp.child
      if ch != null then unmount(ch, eng)
    case eb: ErrorBoundaryNode =>
      val ch = eb.child
      if ch != null then unmount(ch, eng)
    case sn: ScrollNode =>
      val ch = sn.child
      if ch != null then unmount(ch, eng)
    case p: PortalNode =>
      val ovl = p.overlay
      if ovl != null then unmount(ovl, eng)
      eng.portalNodes -= p
    case ap: AbsolutePositionNode =>
      val ch = ap.child
      if ch != null then unmount(ch, eng)
    case c: CenterNode =>
      val ch = c.child
      if ch != null then unmount(ch, eng)
    case bx: BoxNode =>
      val ch = bx.child
      if ch != null then unmount(ch, eng)
    case sz: SizedNode =>
      val ch = sz.child
      if ch != null then unmount(ch, eng)
    case b: BackdropNode =>
      val ch = b.child
      if ch != null then unmount(ch, eng)
    case _ => ()


  // Walks an existing subtree marking all components whose hooks subscribe
  // to the given context as dirty. Called when a ContextProvider's value
  // changes so that memoized consumers (which would otherwise bail out
  // because their props are unchanged) re-render with the new value.
  // Stops at any nested ContextProvider for the same context — that
  // sub-subtree is shielded by its own provider value.
  def invalidateContextSubscribers(node: Node, ctx: Context[?], eng: Engine): Unit =
    node match
      case c: ComponentNode =>
        c.widget match
          case h: HookCarrier =>
            if h.hooks.subscribedContexts.contains(ctx) then h.hooks.invalidate()
          case _ => ()
        val ch = c.child
        if ch != null then invalidateContextSubscribers(ch, ctx, eng)
      case s: StackNode =>
        var i = 0
        while i < s.children.length do
          invalidateContextSubscribers(s.children(i), ctx, eng)
          i = i + 1
      case cp: ContextProviderNode =>
        if cp.ctx ne ctx then
          val ch = cp.child
          if ch != null then invalidateContextSubscribers(ch, ctx, eng)
      case eb: ErrorBoundaryNode =>
        val ch = eb.child
        if ch != null then invalidateContextSubscribers(ch, ctx, eng)
      case sn: ScrollNode =>
        val ch = sn.child
        if ch != null then invalidateContextSubscribers(ch, ctx, eng)
      case p: PortalNode =>
        val ovl = p.overlay
        if ovl != null then invalidateContextSubscribers(ovl, ctx, eng)
      case ap: AbsolutePositionNode =>
        val ch = ap.child
        if ch != null then invalidateContextSubscribers(ch, ctx, eng)
      case c: CenterNode =>
        val ch = c.child
        if ch != null then invalidateContextSubscribers(ch, ctx, eng)
      case bx: BoxNode =>
        val ch = bx.child
        if ch != null then invalidateContextSubscribers(ch, ctx, eng)
      case sz: SizedNode =>
        val ch = sz.child
        if ch != null then invalidateContextSubscribers(ch, ctx, eng)
      case b: BackdropNode =>
        val ch = b.child
        if ch != null then invalidateContextSubscribers(ch, ctx, eng)
      case _ => ()


  // After a memoized parent bails out, this helper walks its existing child
  // tree looking for descendants that *do* need to render (e.g. a nested
  // component whose own state just changed). For each such descendant, it
  // re-renders and reconciles its result against the existing subtree.
  def visitForUpdates(node: Node, eng: Engine): Unit = node match
    case c: ComponentNode =>
      if c.widget.shouldRender then
        c.child = reconcile(c.child, c.widget.render(), eng)
      else
        val ch = c.child
        if ch != null then visitForUpdates(ch, eng)
    case s: StackNode =>
      var i = 0
      while i < s.children.length do
        visitForUpdates(s.children(i), eng)
        i = i + 1
    case cp: ContextProviderNode =>
      val ch = cp.child
      if ch != null then visitForUpdates(ch, eng)
    case eb: ErrorBoundaryNode =>
      val ch = eb.child
      if ch != null then visitForUpdates(ch, eng)
    case sn: ScrollNode =>
      val ch = sn.child
      if ch != null then visitForUpdates(ch, eng)
    case p: PortalNode =>
      val ovl = p.overlay
      if ovl != null then visitForUpdates(ovl, eng)
    case ap: AbsolutePositionNode =>
      val ch = ap.child
      if ch != null then visitForUpdates(ch, eng)
    case c: CenterNode =>
      val ch = c.child
      if ch != null then visitForUpdates(ch, eng)
    case bx: BoxNode =>
      val ch = bx.child
      if ch != null then visitForUpdates(ch, eng)
    case sz: SizedNode =>
      val ch = sz.child
      if ch != null then visitForUpdates(ch, eng)
    case b: BackdropNode =>
      val ch = b.child
      if ch != null then visitForUpdates(ch, eng)
    case _ => ()


  // Splice any Fragment views into the parent's children list. Returns a
  // flat array; Fragments inside Fragments are flattened recursively.
  private def expandFragments(views: Array[View]): Array[View] =
    val needsExpand = views.exists {
      case _: Fragment => true
      case _           => false
    }
    if !needsExpand then views
    else
      val out = mutable.ArrayBuffer.empty[View]
      var i = 0
      while i < views.length do
        views(i) match
          case f: Fragment =>
            val inner = expandFragments(f.children)
            var j = 0
            while j < inner.length do
              out += inner(j)
              j = j + 1
          case other => out += other
        i = i + 1
      out.toArray


  // Looks through transparent wrappers (Keyed, WithRef) to find a key, if any.
  private def keyOf(v: View): Any = v match
    case Keyed(k, _)   => k
    case WithRef(_, x) => keyOf(x)
    case _             => null


  // Update a stack's `children` array to mirror the next set of child Views.
  // Uses keyed matching when *any* sibling is keyed; otherwise pure
  // positional matching. Discarded children are unmount-walked so their
  // hook cleanups fire.
  private def reconcileChildren(parent: StackNode, next: Array[View], eng: Engine): Unit =
    val expanded = expandFragments(next)
    val nextLen  = expanded.length
    val anyKey   = expanded.exists {
      case _: Keyed => true
      case _        => false
    }

    if anyKey then reconcileKeyed(parent, expanded, eng)
    else reconcilePositional(parent, expanded, eng)


  private def reconcilePositional(parent: StackNode, expanded: Array[View], eng: Engine): Unit =
    val nextLen = expanded.length

    var i = 0
    while i < nextLen && i < parent.children.length do
      val before = parent.children(i)
      val after  = reconcile(before, expanded(i), eng)
      if after ne before then
        parent.children(i) = after
        eng.markDirty()
      i = i + 1

    while i < nextLen do
      parent.children += mount(expanded(i), eng)
      eng.markDirty()
      i = i + 1

    if parent.children.length > nextLen then
      var j = nextLen
      while j < parent.children.length do
        unmount(parent.children(j), eng)
        j = j + 1
      parent.children.remove(nextLen, parent.children.length - nextLen)
      eng.markDirty()


  private def reconcileKeyed(parent: StackNode, expanded: Array[View], eng: Engine): Unit =
    // Index existing children by key (or by sequential fallback for unkeyed).
    val byKey = mutable.HashMap.empty[Any, Node]
    val unkeyedQueue = mutable.Queue.empty[Node]
    var k = 0
    while k < parent.children.length do
      val c = parent.children(k)
      if c.key != null then byKey.update(c.key, c) else unkeyedQueue.enqueue(c)
      k = k + 1

    val newChildren = mutable.ArrayBuffer.empty[Node]
    val claimed     = mutable.HashSet.empty[Node]

    var i = 0
    while i < expanded.length do
      val v = expanded(i)
      val key = keyOf(v)
      val candidate: Node | Null =
        if key != null then byKey.get(key).orNull
        else if unkeyedQueue.nonEmpty then unkeyedQueue.dequeue()
        else null

      val node = reconcile(candidate, v, eng)
      if candidate != null then claimed += candidate
      if node ne candidate then eng.markDirty()
      newChildren += node
      i = i + 1

    // Unmount any previous children we didn't claim.
    var j = 0
    while j < parent.children.length do
      val old = parent.children(j)
      if !claimed.contains(old) then
        unmount(old, eng)
        eng.markDirty()
      j = j + 1

    parent.children.clear()
    parent.children ++= newChildren
