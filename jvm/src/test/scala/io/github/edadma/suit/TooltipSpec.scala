package io.github.edadma.suit

import org.scalatest.funsuite.AnyFunSuite
import io.github.edadma.vdom.{Host, Scheduler}
import io.github.edadma.suit.dsl.*
import io.github.edadma.suit.widgets.*

// Headless tests for the Tooltip — the passive, click-through overlay. The tooltip wraps its
// trigger (carrying the hover tracking and the anchor itself), shows after a hover delay, and
// floats in the overlay without intercepting the pointer. As with the menu, the settle lays the
// tree out each iteration so the tooltip can measure its card, and it advances the clock so the
// debounced hover delay elapses.
class TooltipSpec extends AnyFunSuite:

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

  // The trigger sits in a column so its wrapper wraps the small trigger box (a bare wrapper
  // would fill the window), giving the tooltip a sensible anchor rectangle at the origin.
  private def tooltipApp: VNode =
    view {
      col()(
        Tooltip("hint")(box(width = 60, height = 24)()),
      )
    }.apply()

  test("a tooltip is hidden until its trigger is hovered"):
    val m = mount(tooltipApp)
    m.settle()
    assert(m.overlay.children.isEmpty)

  test("hovering the trigger shows the tooltip after its delay"):
    val m = mount(tooltipApp)
    m.pointer.move(Offset(10, 10)) // onto the trigger
    m.settle()
    assert(m.overlay.children.nonEmpty)

  test("a shown tooltip is click-through"):
    val m = mount(tooltipApp)
    m.pointer.move(Offset(10, 10))
    m.settle()
    val layer = m.overlay.children.head
    assert(layer.ignorePointer) // the whole tooltip layer ignores the pointer
    // A hit-test over the tooltip passes through it rather than landing on the card.
    val placed = layer.children.head.children.head // layer -> positioned -> card
    val p      = placed.absoluteOffset
    assert(m.overlay.hitTest(Offset(p.x + 1, p.y + 1), m.overlay.absoluteOffset) == null)

  test("leaving the trigger hides the tooltip again"):
    val m = mount(tooltipApp)
    m.pointer.move(Offset(10, 10)) // hover on
    m.settle()
    assert(m.overlay.children.nonEmpty)
    m.pointer.move(Offset(250, 250)) // away from the trigger
    m.settle()
    assert(m.overlay.children.isEmpty)
