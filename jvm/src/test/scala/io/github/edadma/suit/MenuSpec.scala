package io.github.edadma.suit

import org.scalatest.funsuite.AnyFunSuite
import io.github.edadma.vdom.{Host, Scheduler}
import io.github.edadma.suit.dsl.*
import io.github.edadma.suit.widgets.*

// Headless tests for the positioned dropdown Menu — the anchored-overlay path end to end,
// minus SDL. A Menu is mounted for real through `SuitHostConfig` into a root that also carries
// an overlay layer and an `OverlayEnv` (the same wiring `Suit.run` sets up), with a trigger box
// the menu anchors to via a ref. The settle here lays the tree out on every iteration, because
// the menu measures its own card (through a ref) to decide whether to flip above the anchor —
// that measurement reads the previous frame's layout, so the assertions need several
// layout/flush rounds to converge.
class MenuSpec extends AnyFunSuite:

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

  /** An app with a trigger box (anchored via a ref) at the top, then a Menu over it. `extra`
    * lets a test push the trigger down the window to exercise the flip-above behaviour. */
  private def menuApp(
      open:      Boolean,
      onClose:   () => Unit,
      items:     Seq[(String, () => Unit)],
      extra:     Double    = 0.0,
      placement: Placement = Placement(),
  ): VNode =
    view {
      val ref = useRef[RenderObject | Null](null)
      col()(
        box(width = 80, height = extra)(),
        box(ref = ref, width = 80, height = 20)(),
        Menu(open, onClose, ref, width = 100, placement = placement)(items.map { case (l, f) => MenuItem(l, f) }*),
      )
    }.apply()

  test("a closed menu portals nothing into the overlay"):
    val m = mount(menuApp(open = false, () => (), Seq("One" -> (() => ()))))
    m.settle()
    assert(m.overlay.children.isEmpty)

  test("an open menu portals a card positioned below its anchor"):
    val m = mount(menuApp(open = true, () => (), Seq("One" -> (() => ()), "Two" -> (() => ()))))
    m.settle()
    assert(m.overlay.children.nonEmpty)
    val placed = m.overlay.children.head.children.head.children.head // catcher -> positioned -> card
    assert(placed.offset.y == 20.0)                                  // just below the 20px trigger at y=0
    assert(placed.offset.x == 0.0)                                   // aligned to the trigger's left edge

  test("a menu near the bottom flips above its anchor"):
    // Push the trigger to the bottom of the 300px window; the card cannot fit below it, so it
    // opens upward and sits above the trigger's top.
    val m = mount(menuApp(open = true, () => (), Seq("One" -> (() => ()), "Two" -> (() => ())), extra = 270))
    m.settle()
    val placed = m.overlay.children.head.children.head.children.head
    assert(placed.offset.y < 270.0) // above the trigger (which starts at y=270)

  test("a placement gap offsets the card from its anchor"):
    val m = mount(menuApp(
      open = true, () => (), Seq("One" -> (() => ())),
      placement = Placement(gap = 8),
    ))
    m.settle()
    val placed = m.overlay.children.head.children.head.children.head
    assert(placed.offset.y == 28.0) // 20px trigger + 8px gap
    assert(placed.offset.x == 0.0)

  test("a Right placement opens to the side of the trigger"):
    val m = mount(menuApp(
      open = true, () => (), Seq("One" -> (() => ())),
      placement = Placement(side = PopoverSide.Right),
    ))
    m.settle()
    val placed = m.overlay.children.head.children.head.children.head
    assert(placed.offset.x == 80.0) // to the right of the 80px-wide trigger
    assert(placed.offset.y == 0.0)  // top-aligned with the trigger at y=0

  test("clicking outside the menu dismisses it"):
    var closed = 0
    val m      = mount(menuApp(open = true, () => closed += 1, Seq("One" -> (() => ()))))
    m.settle()
    m.pointer.down(Offset(250, 250), 1) // far from the top-left card, on the catcher
    m.pointer.up(Offset(250, 250), 1)
    assert(closed == 1)

  test("clicking on the menu does not dismiss it"):
    var closed = 0
    val m      = mount(menuApp(open = true, () => closed += 1, Seq("One" -> (() => ()))))
    m.settle()
    m.pointer.down(Offset(4, 28), 1) // inside the card, below the trigger
    m.pointer.up(Offset(4, 28), 1)
    assert(closed == 0)

  test("opening a menu traps focus to the overlay and focuses inside it"):
    val m = mount(menuApp(open = true, () => (), Seq("One" -> (() => ()), "Two" -> (() => ()))))
    m.settle()
    assert(m.focus.trapRoot eq m.overlay)
    val f = m.focus.focused
    assert(f != null)
    assert(m.focus.focusables(m.overlay).contains(f.asInstanceOf[RenderObject]))

  test("Escape closes an open menu"):
    var closed = 0
    val m      = mount(menuApp(open = true, () => closed += 1, Seq("One" -> (() => ()))))
    m.settle()
    assert(m.focus.escape())
    assert(closed == 1)
