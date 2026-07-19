package io.github.edadma.suit

import org.scalatest.funsuite.AnyFunSuite
import scala.collection.mutable

// Headless tests for input routing. The routers need only `hitTest`, the parent chain,
// and the handler maps — all pure — so press→release→click, pointer capture during a
// drag, hover enter/leave, event bubbling, focus, and keyboard dispatch are all verified
// on the JVM against a laid-out tree with no device.
class InputSpec extends AnyFunSuite:

  private def fixed(w: Double, h: Double): RenderBox =
    val b = new RenderBox
    b.width = Some(w)
    b.height = Some(h)
    b

  /** Build a row of two 50×100 boxes laid out in a 200×100 space: box A spans x 0–50,
    * box B spans x 50–100, and x ≥ 100 hits the row itself. Each box is fitted with
    * handlers that append `"<event>:<label>"` to `log`. */
  private def tree(log: mutable.ArrayBuffer[String]): (RenderFlex, RenderBox, RenderBox) =
    val row = new RenderFlex(Axis.Horizontal)
    val a   = fixed(50, 100)
    val b   = fixed(50, 100)
    row.insertChild(a, null)
    row.insertChild(b, null)
    def wire(o: RenderObject, label: String): Unit =
      for ev <- Seq("mousedown", "mouseup", "click", "mousemove", "mouseenter", "mouseleave") do
        o.handlers(ev) = _ => log += s"$ev:$label"
    wire(a, "a")
    wire(b, "b")
    row.layout(Constraints.tight(Size(200, 100)))
    (row, a, b)

  test("a press then release on the same object is a click"):
    val log         = mutable.ArrayBuffer.empty[String]
    val (row, _, _) = tree(log)
    val router      = new PointerRouter(row)
    router.down(Offset(10, 10), 1)
    router.up(Offset(10, 10), 1)
    assert(log.toList == List("mousedown:a", "mouseup:a", "click:a"))

  test("a captured pointer delivers release and click to the press target"):
    // Press on A, release over B: with pointer capture the up goes to A (the captor),
    // and there is no click because press and release resolve to different objects.
    val log         = mutable.ArrayBuffer.empty[String]
    val (row, _, _) = tree(log)
    val router      = new PointerRouter(row)
    router.down(Offset(10, 10), 1)
    router.up(Offset(60, 10), 1)
    assert(log.toList == List("mousedown:a", "mouseup:a"))

  test("a drag delivers moves to the captor even after the cursor leaves it"):
    val log         = mutable.ArrayBuffer.empty[String]
    val (row, _, _) = tree(log)
    val router      = new PointerRouter(row)
    router.down(Offset(10, 10), 1) // press on a
    router.move(Offset(60, 10))    // cursor now over b, but a captured the pointer
    router.move(Offset(120, 10))   // cursor over the row, still captured by a
    router.up(Offset(60, 10), 1)
    assert(log.toList == List("mousedown:a", "mousemove:a", "mousemove:a", "mouseup:a"))

  test("the delivered event carries absolute and local position, size, and button"):
    val seen = mutable.ArrayBuffer.empty[PointerEvent]
    val a    = fixed(50, 100)
    a.handlers("mousedown") = e => seen += e.asInstanceOf[PointerEvent]
    val row = new RenderFlex(Axis.Horizontal)
    row.insertChild(a, null)
    row.layout(Constraints.tight(Size(200, 100)))
    new PointerRouter(row).down(Offset(12, 34), 3)
    assert(seen.toList == List(PointerEvent(Offset(12, 34), Offset(12, 34), Size(50, 100), 3)))

  test("moving across a boundary fires leave then enter then move"):
    val log         = mutable.ArrayBuffer.empty[String]
    val (row, _, _) = tree(log)
    val router      = new PointerRouter(row)
    router.move(Offset(10, 10)) // onto a
    router.move(Offset(60, 10)) // onto b
    assert(log.toList == List(
      "mouseenter:a", "mousemove:a",
      "mouseleave:a", "mouseenter:b", "mousemove:b",
    ))

  test("moving within one object does not re-fire enter/leave"):
    val log         = mutable.ArrayBuffer.empty[String]
    val (row, _, _) = tree(log)
    val router      = new PointerRouter(row)
    router.move(Offset(10, 10))
    router.move(Offset(20, 10)) // still on a
    assert(log.toList == List("mouseenter:a", "mousemove:a", "mousemove:a"))

  // --- bubbling ------------------------------------------------------------

  test("an event bubbles to the nearest ancestor with a handler"):
    // Outer 100×100 box holds the click handler; an inner decoration with none is what
    // the cursor actually hits. The click must still reach the outer box, reported in
    // the outer box's coordinate space.
    val log   = mutable.ArrayBuffer.empty[PointerEvent]
    val outer = fixed(100, 100)
    val inner = fixed(80, 80)
    outer.padding = EdgeInsets.all(10)
    outer.insertChild(inner, null)
    outer.handlers("click") = e => log += e.asInstanceOf[PointerEvent]
    outer.layout(Constraints.tight(Size(100, 100)))
    val router = new PointerRouter(outer)
    router.down(Offset(40, 40), 1)
    router.up(Offset(40, 40), 1)
    assert(log.length == 1)
    assert(log.head.size == Size(100, 100)) // resolved against the outer box, not the inner hit

  // --- focus + keyboard ----------------------------------------------------

  test("a press focuses the nearest focusable ancestor and blurs on empty space"):
    val log   = mutable.ArrayBuffer.empty[String]
    val outer = fixed(100, 100)
    val inner = fixed(80, 80)
    outer.padding = EdgeInsets.all(10)
    outer.focusable = true
    outer.handlers("focus") = _ => log += "focus"
    outer.handlers("blur") = _ => log += "blur"
    outer.insertChild(inner, null)
    outer.layout(Constraints.tight(Size(100, 100)))
    val focus  = new FocusManager
    val router = new PointerRouter(outer, focus)
    router.down(Offset(40, 40), 1) // press on inner → focus bubbles to outer
    router.up(Offset(40, 40), 1)
    assert(focus.isFocused(outer))
    focus.blur()
    assert(log.toList == List("focus", "blur"))

  test("the key router dispatches to the focused object only"):
    val log = mutable.ArrayBuffer.empty[String]
    val a   = fixed(50, 50)
    val b   = fixed(50, 50)
    a.handlers("keydown") = e => log += s"a:${e.asInstanceOf[KeyEvent].scancode}"
    b.handlers("keydown") = e => log += s"b:${e.asInstanceOf[KeyEvent].scancode}"
    val focus     = new FocusManager
    val keyRouter = new KeyRouter(focus)
    keyRouter.down(Key.Enter, false) // nobody focused: dropped
    focus.focus(b)
    keyRouter.down(Key.Space, false)
    assert(log.toList == List(s"b:${Key.Space}"))

  // --- focus traversal (Tab order) -----------------------------------------

  /** A 100×100 root holding focusable boxes a, b, c with a non-focusable box between a and
    * b — so traversal must skip the plain one and visit only a, b, c in order. */
  private def focusTree(): (RenderBox, RenderBox, RenderBox, RenderBox) =
    val root  = fixed(100, 100)
    val a     = fixed(10, 10); a.focusable = true
    val plain = fixed(10, 10)
    val b     = fixed(10, 10); b.focusable = true
    val c     = fixed(10, 10); c.focusable = true
    root.insertChild(a, null)
    root.insertChild(plain, null)
    root.insertChild(b, null)
    root.insertChild(c, null)
    (root, a, b, c)

  test("focusables lists the focusable objects in document order"):
    val (root, a, b, c) = focusTree()
    assert(new FocusManager().focusables(root) == List(a, b, c))

  test("Tab traversal advances through the focusables and wraps"):
    val (root, a, b, c) = focusTree()
    val fm              = new FocusManager
    fm.focusNext(root); assert(fm.isFocused(a)) // nothing focused → first
    fm.focusNext(root); assert(fm.isFocused(b))
    fm.focusNext(root); assert(fm.isFocused(c))
    fm.focusNext(root); assert(fm.isFocused(a)) // wraps past the end

  test("Shift+Tab traversal walks backward and wraps"):
    val (root, a, b, c) = focusTree()
    val fm              = new FocusManager
    fm.focusNext(root, backward = true); assert(fm.isFocused(c)) // nothing focused → last
    fm.focusNext(root, backward = true); assert(fm.isFocused(b))
    fm.focusNext(root, backward = true); assert(fm.isFocused(a))
    fm.focusNext(root, backward = true); assert(fm.isFocused(c)) // wraps past the start

  test("traversal fires blur on the old object and focus on the new"):
    val (root, a, b, _) = focusTree()
    val log             = mutable.ArrayBuffer.empty[String]
    a.handlers("blur") = _ => log += "blur:a"
    b.handlers("focus") = _ => log += "focus:b"
    val fm = new FocusManager
    fm.focus(a)
    fm.focusNext(root) // a → b
    assert(fm.isFocused(b))
    assert(log.toList == List("blur:a", "focus:b"))

  test("traversal with no focusables is a no-op"):
    val fm = new FocusManager
    fm.focusNext(fixed(100, 100))
    assert(fm.focused == null)

  // --- focus trap (modal) --------------------------------------------------

  test("escape runs the trap handler and reports it consumed the key"):
    val fm      = new FocusManager
    var closed  = 0
    assert(!fm.escape()) // no trap: nothing consumes Escape
    fm.trap(fixed(10, 10), () => closed += 1)
    assert(fm.escape())  // trapped: the handler runs and the key is consumed
    assert(closed == 1)
    fm.releaseTrap()
    assert(!fm.escape())

  test("a trap scopes Tab traversal to the trapped subtree"):
    // Two focusables in the main tree (a, b) and two inside a separate trapped subtree
    // (t1, t2). With the trap active, traversal must cycle only t1/t2 and never reach a/b.
    val (root, a, b, _) = focusTree()
    val trap            = fixed(50, 50)
    val t1              = fixed(10, 10); t1.focusable = true
    val t2              = fixed(10, 10); t2.focusable = true
    trap.insertChild(t1, null)
    trap.insertChild(t2, null)
    val fm = new FocusManager
    fm.trap(trap, () => ())
    fm.focusNext(root); assert(fm.isFocused(t1)) // nothing focused → first in the trap
    fm.focusNext(root); assert(fm.isFocused(t2))
    fm.focusNext(root); assert(fm.isFocused(t1)) // wraps inside the trap, never escapes to a/b
    assert(!fm.focusables(trap).contains(a))
    assert(!fm.focusables(trap).contains(b))

  test("traps stack: the innermost governs Escape and Tab, and releasing restores the outer"):
    // An outer trap (o1, o2) with an inner trap (i1) pushed over it — a menu opened from inside
    // a dialog. While the inner trap is active, Tab cycles only its focusables and Escape runs
    // only the inner handler. Releasing the inner trap uncovers the outer one again.
    def f: RenderBox = { val b = fixed(10, 10); b.focusable = true; b }
    val outer = fixed(50, 50); val o1 = f; val o2 = f
    outer.insertChild(o1, null); outer.insertChild(o2, null)
    val inner = fixed(20, 20); val i1 = f
    inner.insertChild(i1, null)
    val root = fixed(100, 100)
    root.insertChild(outer, null); root.insertChild(inner, null)

    var outerEsc = 0
    var innerEsc = 0
    val fm = new FocusManager
    fm.trap(outer, () => outerEsc += 1)
    assert(fm.trapRoot eq outer)

    fm.trap(inner, () => innerEsc += 1) // nest the inner trap over the outer
    assert(fm.trapRoot eq inner)
    fm.focusNext(root); assert(fm.isFocused(i1)) // Tab confined to the inner trap
    fm.focusNext(root); assert(fm.isFocused(i1)) // only one focusable: it stays put
    assert(fm.escape() && innerEsc == 1 && outerEsc == 0) // Escape peels only the inner

    fm.releaseTrap()                    // inner closed → outer governs again
    assert(fm.trapRoot eq outer)
    fm.focus(null)
    fm.focusNext(root); assert(fm.isFocused(o1)) // Tab now cycles the outer trap's focusables
    fm.focusNext(root); assert(fm.isFocused(o2))
    assert(fm.escape() && outerEsc == 1)

    fm.releaseTrap()
    assert(fm.trapRoot == null && !fm.escape()) // fully released: Escape no longer consumed

  // --- ignore pointer ------------------------------------------------------

  test("an ignore-pointer object is transparent to hit-testing"):
    // An outer box holds the handler; an inner overlay box marked ignore-pointer covers it.
    // A press over the inner box must pass through it and resolve to the outer box, as if the
    // overlay were not there.
    val outer   = fixed(100, 100)
    val overlay = fixed(100, 100)
    overlay.ignorePointer = true
    outer.insertChild(overlay, null)
    outer.layout(Constraints.tight(Size(100, 100)))
    assert(outer.hitTest(Offset(50, 50), Offset.zero) eq outer)

  // --- wheel ---------------------------------------------------------------

  test("the wheel bubbles a scroll event to the nearest wheel handler"):
    val seen  = mutable.ArrayBuffer.empty[ScrollEvent]
    val outer = fixed(100, 100)
    val inner = new RenderBox
    outer.insertChild(inner, null)
    outer.handlers("wheel") = e => seen += e.asInstanceOf[ScrollEvent]
    outer.layout(Constraints.tight(Size(100, 100)))
    new PointerRouter(outer).wheel(Offset(50, 50), 0, -3)
    assert(seen.toList == List(ScrollEvent(Offset(50, 50), 0, -3, Offset(50, 50), Size(100, 100))))

  test("a wheel event carries the held modifiers and the receiver's own local/size"):
    // The receiver sits inset in a parent, so its local coordinates differ from the window's —
    // a zoom handler anchoring on `localX` must get the cursor relative to itself. The modifier
    // flags ride along so one surface can split plain-wheel from modified-wheel behaviour.
    val seen  = mutable.ArrayBuffer.empty[ScrollEvent]
    val outer = fixed(100, 100)
    val inner = fixed(60, 60)
    outer.insertChild(inner, null)
    inner.handlers("wheel") = e => seen += e.asInstanceOf[ScrollEvent]
    outer.layout(Constraints.tight(Size(100, 100)))
    inner.offset = Offset(20, 10)
    new PointerRouter(outer).wheel(Offset(50, 50), 0, -3, ctrl = true, shift = true)
    val e = seen.head
    assert(e.local == Offset(30, 40) && e.size == Size(60, 60))
    assert(e.ctrl && e.shift && !e.meta && !e.alt)

  test("a chaining wheel event is re-resolved against each receiver"):
    // The inner handler leaves the event unconsumed, so it chains to the outer — and each must
    // see its own local mapping, not the other's. Consuming at the inner stops the chain.
    val locals = mutable.ArrayBuffer.empty[(String, Offset)]
    val outer  = fixed(100, 100)
    val inner  = fixed(60, 60)
    outer.insertChild(inner, null)
    inner.handlers("wheel") = e => locals += ("inner" -> e.asInstanceOf[ScrollEvent].local)
    outer.handlers("wheel") = e => locals += ("outer" -> e.asInstanceOf[ScrollEvent].local)
    outer.layout(Constraints.tight(Size(100, 100)))
    inner.offset = Offset(20, 10)
    val router = new PointerRouter(outer)
    router.wheel(Offset(50, 50), 0, -3)
    assert(locals.toList == List("inner" -> Offset(30, 40), "outer" -> Offset(50, 50)))

    locals.clear()
    inner.handlers("wheel") = e => { locals += ("inner" -> e.asInstanceOf[ScrollEvent].local); e.asInstanceOf[ScrollEvent].consume() }
    router.wheel(Offset(50, 50), 0, -3)
    assert(locals.toList == List("inner" -> Offset(30, 40)))

  // --- cursor resolution ---------------------------------------------------
  //
  // The shape shown for the pointer: the nearest object at or above the target that names a
  // Cursor, the arrow otherwise. The target is the captured object during a drag, else the hit.

  /** An outer 100×100 box holding an inner 50×50 box at the top-left, laid out. */
  private def cursorTree(): (RenderBox, RenderBox, PointerRouter) =
    val outer = fixed(100, 100)
    val inner = fixed(50, 50)
    outer.insertChild(inner, null)
    outer.layout(Constraints.tight(Size(100, 100)))
    (outer, inner, new PointerRouter(outer))

  test("with nothing set, the pointer resolves to the default arrow"):
    val (_, _, router) = cursorTree()
    assert(router.cursorAt(Offset(25, 25)) == Cursor.Default)

  test("a widget's cursor is shown while the pointer is over it"):
    val (outer, _, router) = cursorTree()
    outer.cursor = Cursor.Pointer
    assert(router.cursorAt(Offset(75, 75)) == Cursor.Pointer)

  test("the cursor is inherited from the nearest ancestor that names one"):
    val (outer, _, router) = cursorTree()
    outer.cursor = Cursor.Pointer // inner names none, so it inherits the outer's
    assert(router.cursorAt(Offset(10, 10)) == Cursor.Pointer)

  test("an inner widget overrides the ancestor's cursor, including back to the arrow"):
    val (outer, inner, router) = cursorTree()
    outer.cursor = Cursor.Pointer
    inner.cursor = Cursor.Text
    assert(router.cursorAt(Offset(10, 10)) == Cursor.Text)     // inner wins over outer
    inner.cursor = Cursor.Default                              // an explicit override, not "inherit"
    assert(router.cursorAt(Offset(10, 10)) == Cursor.Default)  // the arrow, not the inherited hand
    assert(router.cursorAt(Offset(75, 75)) == Cursor.Pointer)  // still the outer's where inner isn't

  test("during a capture the cursor sticks to the captured widget, not what is under the pointer"):
    // A splitter sets a resize cursor and captures on press; the shape must hold while the drag
    // strays off the thin gutter onto a neighbour that wants a different (or no) cursor.
    val (outer, inner, router) = cursorTree()
    inner.cursor = Cursor.ResizeEW
    router.down(Offset(10, 10), 1)                              // capture the inner box
    assert(router.cursorAt(Offset(90, 90)) == Cursor.ResizeEW)  // pointer off it, shape unchanged
    router.up(Offset(90, 90), 1)                                // release
    assert(router.cursorAt(Offset(90, 90)) == Cursor.Default)   // now resolves from the hit again

  test("an ignore-pointer child does not steal the cursor from the box behind it"):
    val (outer, inner, router) = cursorTree()
    outer.cursor          = Cursor.Pointer
    inner.cursor          = Cursor.Text
    inner.ignorePointer   = true // the pointer passes through inner to outer, so does the cursor
    assert(router.cursorAt(Offset(10, 10)) == Cursor.Pointer)
