package io.github.edadma.suit

import org.scalatest.funsuite.AnyFunSuite
import io.github.edadma.vdom.{Host, Scheduler}
import io.github.edadma.suit.dsl.*
import io.github.edadma.suit.widgets.*

// Headless tests for the dropdown Select — the anchored-overlay path again, but with the open
// state owned inside the widget (a click on the field opens it) rather than by the caller. The
// settle lays the tree out on every iteration so the dropdown can measure its own card and
// converge on a position, the same as MenuSpec.
class SelectSpec extends AnyFunSuite:

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

  /** The nearest ancestor box of `label`'s text that carries a click handler — a [[MenuItem]] box
    * (or the trigger). Used to invoke a row's selection without depending on the card's geometry. */
  private def clickableBoxWith(root: RenderObject, label: String): RenderBox =
    val t = allObjects(root).collect { case t: RenderText if t.text == label => t }.head
    var n: RenderObject | Null = t
    while n != null && !(n.isInstanceOf[RenderBox] && n.asInstanceOf[RenderBox].handlers.contains("click")) do
      n = n.asInstanceOf[RenderObject].parent
    n.asInstanceOf[RenderBox]

  private def selectApp(options: Seq[(String, String)], selected: String, onChange: String => Unit): VNode =
    view {
      col()(Select(options, selected, onChange, width = 120))
    }.apply()

  private val fruit = Seq("a" -> "Apple", "b" -> "Banana", "c" -> "Cherry")

  test("a select starts closed — nothing in the overlay"):
    val m = mount(selectApp(fruit, "a", _ => ()))
    m.settle()
    assert(m.overlay.children.isEmpty)

  test("the placeholder shows when the value matches no option"):
    val m = mount(selectApp(fruit, "", _ => ()))
    m.settle()
    val texts = allObjects(m.root).collect { case t: RenderText => t.text }
    assert(texts.contains("Select…"))

  test("clicking the trigger opens a dropdown below it"):
    val m = mount(selectApp(fruit, "a", _ => ()))
    m.settle()
    m.pointer.down(Offset(10, 10), 1)
    m.pointer.up(Offset(10, 10), 1)
    m.settle()
    assert(m.overlay.children.nonEmpty)
    val placed = m.overlay.children.head.children.head.children.head // catcher -> positioned -> card
    assert(placed.offset.x == 0.0) // aligned to the trigger's left edge
    assert(placed.offset.y > 0.0)  // below the trigger

  test("selecting an option reports its value through onChange and closes"):
    var chosen = ""
    val m      = mount(selectApp(fruit, "", v => chosen = v))
    m.settle()
    m.pointer.down(Offset(10, 10), 1)
    m.pointer.up(Offset(10, 10), 1)
    m.settle()
    val banana = clickableBoxWith(m.overlay, "Banana")
    banana.handlers("click").apply(PointerEvent(Offset.zero, Offset.zero, banana.size))
    m.settle()
    assert(chosen == "b")
    assert(m.overlay.children.isEmpty)

  test("Escape closes an open select"):
    val m = mount(selectApp(fruit, "a", _ => ()))
    m.settle()
    m.pointer.down(Offset(10, 10), 1)
    m.pointer.up(Offset(10, 10), 1)
    m.settle()
    assert(m.overlay.children.nonEmpty)
    assert(m.focus.escape())
    m.settle()
    assert(m.overlay.children.isEmpty)
