package io.github.edadma.suit

import org.scalatest.funsuite.AnyFunSuite
import io.github.edadma.vdom.{Host, Scheduler}
import io.github.edadma.suit.dsl.*
import io.github.edadma.suit.widgets.*

// Headless tests for the modal Dialog — the overlay path end to end, minus SDL. A Dialog is
// mounted for real through `SuitHostConfig` into a root that also carries an overlay layer and
// an `OverlayEnv`, the same wiring `Suit.run` sets up. The tests then assert that the dialog
// portals its scrim into the overlay, that a scrim click dismisses while a card click does
// not, and that opening traps focus and Escape closes — all off-device. No text measurer is
// installed, so the card sizes from its padding alone (a small centred box).
class DialogSpec extends AnyFunSuite:

  private case class Mounted(
      root:    RenderRoot,
      overlay: RenderOverlay,
      focus:   FocusManager,
      pointer: PointerRouter,
      clock:   FrameClock,
      clockMs: Array[Double],
  ):
    /** Commit queued state and run every animation to completion, then re-lay-out — so
      * assertions see the settled tree. Mirrors WidgetSpec's settle. */
    def settle(): Unit =
      Scheduler.flushSync()
      var guard = 0
      while clock.active && guard < 100 do
        clockMs(0) += 10_000.0
        clock.pump()
        Scheduler.flushSync()
        guard += 1
      root.layout(Constraints.tight(root.windowSize))

  /** Mount `app` into a root wired with an overlay layer and the matching `OverlayEnv`, the
    * headless equivalent of `Suit.run`'s overlay setup. */
  private def mount(app: VNode, size: Size = Size(200, 200)): Mounted =
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

  private def allObjects(o: RenderObject): List[RenderObject] =
    o :: o.children.toList.flatMap(allObjects)

  test("a dialog caps its content to its width"):
    // A child far wider than the dialog's width must be clamped to it, so body content stays
    // within the modal instead of sprawling — the bound that lets text wrap.
    val m = mount(
      Dialog(open = true, onClose = () => (), width = 300)(sizedBox(width = 999, height = 20)()),
      Size(800, 600),
    )
    m.settle()
    val constraineds = allObjects(m.overlay).collect { case c: RenderConstrained => c }
    assert(constraineds.nonEmpty)
    assert(constraineds.forall(_.size.width <= 300)) // the 999-wide child was capped to the dialog width

  test("a closed dialog portals nothing into the overlay"):
    val m = mount(Dialog(open = false, onClose = () => ())(text("body")))
    m.settle()
    assert(m.overlay.children.isEmpty)

  test("an open dialog portals a full-window scrim into the overlay"):
    val m = mount(Dialog(open = true, onClose = () => ())(text("body")))
    m.settle()
    assert(m.overlay.children.nonEmpty)
    assert(m.overlay.children.head.size == Size(200, 200)) // the scrim fills the window

  test("clicking the scrim dismisses the dialog"):
    var closed = 0
    val m      = mount(Dialog(open = true, onClose = () => closed += 1)(text("body")))
    m.settle()
    m.pointer.down(Offset(2, 2), 1) // a corner — outside the centred card, on the scrim
    m.pointer.up(Offset(2, 2), 1)
    assert(closed == 1)

  test("a non-mask-closable dialog ignores a scrim click"):
    var closed = 0
    val m      = mount(Dialog(open = true, onClose = () => closed += 1, maskClosable = false)(text("body")))
    m.settle()
    m.pointer.down(Offset(2, 2), 1)
    m.pointer.up(Offset(2, 2), 1)
    assert(closed == 0)

  test("clicking the card does not dismiss the dialog"):
    var closed = 0
    val m      = mount(Dialog(open = true, onClose = () => closed += 1)(text("body")))
    m.settle()
    m.pointer.down(Offset(100, 100), 1) // window centre — on the card
    m.pointer.up(Offset(100, 100), 1)
    assert(closed == 0)

  test("opening traps focus to the overlay and moves focus inside it"):
    val m = mount(Dialog(open = true, onClose = () => ())(text("body")))
    m.settle()
    assert(m.focus.trapRoot eq m.overlay)
    val f = m.focus.focused
    assert(f != null)
    assert(m.focus.focusables(m.overlay).contains(f.asInstanceOf[RenderObject]))

  test("Escape closes a trapping dialog from anywhere inside it"):
    var closed = 0
    val m      = mount(Dialog(open = true, onClose = () => closed += 1)(text("body")))
    m.settle()
    assert(m.focus.escape())
    assert(closed == 1)

  test("closing releases the trap and unmounts the dialog after its exit"):
    var setOpen: Boolean => Unit = _ => ()
    val App = view {
      val (open, set, _) = useState(true)
      setOpen = set
      Dialog(open, () => set(false))(text("body"))
    }
    val m = mount(App())
    m.settle()
    assert(m.overlay.children.nonEmpty)
    assert(m.focus.trapRoot eq m.overlay)
    setOpen(false)
    m.settle() // play the exit animation to completion, then unmount
    assert(m.focus.trapRoot == null)
    assert(m.overlay.children.isEmpty)
