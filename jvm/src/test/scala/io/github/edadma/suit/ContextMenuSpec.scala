package io.github.edadma.suit

import org.scalatest.funsuite.AnyFunSuite
import io.github.edadma.vdom.{Host, Scheduler}
import io.github.edadma.suit.dsl.*
import io.github.edadma.suit.widgets.*

// Headless tests for the right-click context menu. Unlike Menu/Select it opens at the cursor
// point (via the popover's `point` anchor) rather than beside a trigger, and only a right-press
// (button 3) opens it. The settle lays out each iteration so the card measures and positions.
class ContextMenuSpec extends AnyFunSuite:

  private case class Mounted(
      root:    RenderRoot,
      overlay: RenderOverlay,
      focus:   FocusManager,
      pointer: PointerRouter,
      clock:   FrameClock,
      clockMs: Array[Double],
  ):
    def settle(): Unit =
      var i = 0
      while i < 24 do
        Scheduler.flushSync()
        clockMs(0) += 10_000.0
        clock.pump()
        Scheduler.flushSync()
        root.layout(Constraints.tight(root.windowSize))
        i += 1

  private def mount(app: VNode, size: Size = Size(300, 300)): Mounted =
    Host.config = new SuitHostConfig
    val clockMs = new Array[Double](1)
    val clock   = new FrameClock(() => clockMs(0))
    clock.install()
    val root    = new RenderRoot(size)
    val overlay = new RenderOverlay
    val focus   = new FocusManager
    createRoot(root).render(OverlayContext.provide(OverlayEnv(overlay, focus), app))
    root.insertChild(overlay, null)
    Scheduler.flushSync()
    root.layout(Constraints.tight(size))
    Mounted(root, overlay, focus, new PointerRouter(root, focus), clock, clockMs)

  private def allObjects(o: RenderObject): List[RenderObject] = o :: o.children.toList.flatMap(allObjects)

  private def clickableBoxWith(root: RenderObject, label: String): RenderBox =
    val t = allObjects(root).collect { case t: RenderText if t.text == label => t }.head
    var n: RenderObject | Null = t
    while n != null && !(n.isInstanceOf[RenderBox] && n.asInstanceOf[RenderBox].handlers.contains("click")) do
      n = n.asInstanceOf[RenderObject].parent
    n.asInstanceOf[RenderBox]

  private def ctxApp(onCut: () => Unit): VNode =
    view {
      col()(
        contextMenu(width = 120)(
          box(width = 80, height = 40)(text("target")),
        ) { close =>
          Seq(
            MenuItem("Cut", () => { onCut(); close() }),
            MenuItem("Copy", () => close()),
          )
        },
      )
    }.apply()

  test("a left-click does not open the menu"):
    val m = mount(ctxApp(() => ()))
    m.settle()
    m.pointer.down(Offset(40, 20), 1)
    m.pointer.up(Offset(40, 20), 1)
    m.settle()
    assert(m.overlay.children.isEmpty)

  test("a right-click opens a menu at the cursor point"):
    val m = mount(ctxApp(() => ()))
    m.settle()
    m.pointer.down(Offset(40, 20), 3)
    m.settle()
    assert(m.overlay.children.nonEmpty)
    val placed = m.overlay.children.head.children.head.children.head // catcher -> positioned -> card
    assert(placed.offset.x == 40.0) // at the cursor x
    assert(placed.offset.y == 20.0) // at the cursor y (default Below placement, zero-size anchor)

  test("selecting an item runs its action and closes the menu"):
    var cut = 0
    val m   = mount(ctxApp(() => cut += 1))
    m.settle()
    m.pointer.down(Offset(40, 20), 3)
    m.settle()
    val item = clickableBoxWith(m.overlay, "Cut")
    item.handlers("click").apply(PointerEvent(Offset.zero, Offset.zero, item.size))
    m.settle()
    assert(cut == 1)
    assert(m.overlay.children.isEmpty)

  test("Escape closes an open context menu"):
    val m = mount(ctxApp(() => ()))
    m.settle()
    m.pointer.down(Offset(40, 20), 3)
    m.settle()
    assert(m.overlay.children.nonEmpty)
    assert(m.focus.escape())
    m.settle()
    assert(m.overlay.children.isEmpty)

  test("the menu animates out at the click point, not snapped to the top-left corner"):
    val m = mount(ctxApp(() => ()))
    m.settle()
    m.pointer.down(Offset(40, 20), 3)
    m.settle()
    // Begin closing but do not advance the clock, so the exit animation has not finished and the
    // menu is still mounted, fading out — it must hold its open position, not jump to (0,0).
    assert(m.focus.escape())
    var i = 0
    while i < 3 do
      Scheduler.flushSync()
      m.root.layout(Constraints.tight(m.root.windowSize))
      i += 1
    assert(m.overlay.children.nonEmpty) // still mounted through the exit fade
    val placed = m.overlay.children.head.children.head.children.head
    assert(placed.offset.x == 40.0)
    assert(placed.offset.y == 20.0)
