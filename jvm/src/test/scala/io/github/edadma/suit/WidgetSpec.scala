package io.github.edadma.suit

import org.scalatest.funsuite.AnyFunSuite
import io.github.edadma.vdom.{Host, Scheduler}
import io.github.edadma.suit.widgets.*

// Headless tests for the widget library. Widgets are vdom components, so these mount one
// for real through `SuitHostConfig` into a fresh render root, flush the scheduler
// synchronously, lay the tree out, then drive the input routers and assert on the
// resulting render tree — the same off-device path the runtime takes, minus SDL. No
// text measurer is installed, so labels lay out as zero-size; the tests assert on
// structure, colour, and callbacks rather than text extent.
class WidgetSpec extends AnyFunSuite:

  /** Mount `app` into a root of `size`, returning the root and routers wired to one
    * focus manager — the headless equivalent of `Suit.run`'s setup. */
  private def mount(app: VNode, size: Size = Size(200, 100)): Mounted =
    Host.config = new SuitHostConfig
    val root = new RenderRoot(size)
    createRoot(root).render(app)
    Scheduler.flushSync()
    root.layout(Constraints.tight(size))
    val focus = new FocusManager
    Mounted(root, new PointerRouter(root, focus), new KeyRouter(focus), focus)

  private case class Mounted(root: RenderRoot, pointer: PointerRouter, keys: KeyRouter, focus: FocusManager):
    /** Commit any state the handlers queued and re-position the tree. */
    def settle(): Unit =
      Scheduler.flushSync()
      root.layout(Constraints.tight(root.windowSize))

  private def allObjects(o: RenderObject): List[RenderObject] =
    o :: o.children.toList.flatMap(allObjects)

  private def focusableBox(root: RenderObject): RenderBox =
    allObjects(root).collectFirst { case b: RenderBox if b.focusable => b }.get

  // --- Button --------------------------------------------------------------

  test("a button fires onPressed for a press-and-release on it"):
    var clicks = 0
    val m      = mount(Button("OK", () => clicks += 1))
    m.pointer.down(Offset(50, 50), 1)
    m.pointer.up(Offset(50, 50), 1)
    m.settle()
    assert(clicks == 1)

  test("a button tints while pressed and reverts on release"):
    val m = mount(Button("OK", () => ()))
    assert(focusableBox(m.root).background == Theme.primary)
    m.pointer.down(Offset(50, 50), 1)
    m.settle()
    assert(focusableBox(m.root).background == Theme.primaryActive)
    m.pointer.up(Offset(50, 50), 1)
    m.settle()
    assert(focusableBox(m.root).background == Theme.primary)

  test("a focused button activates on Space"):
    var clicks = 0
    val m      = mount(Button("OK", () => clicks += 1))
    m.focus.focus(focusableBox(m.root))
    m.keys.down(Key.Space, false)
    m.settle()
    assert(clicks == 1)

  // --- Checkbox ------------------------------------------------------------

  test("a checkbox reports the toggled value on click"):
    var state = false
    val m     = mount(Checkbox(false, b => state = b), Size(40, 40))
    m.pointer.down(Offset(10, 10), 1)
    m.pointer.up(Offset(10, 10), 1)
    m.settle()
    assert(state)

  test("a checked checkbox renders a fill mark"):
    val checked   = mount(Checkbox(true, _ => ()), Size(40, 40))
    val unchecked = mount(Checkbox(false, _ => ()), Size(40, 40))
    def hasMark(root: RenderObject): Boolean =
      allObjects(root).exists {
        case b: RenderBox => b.background == Theme.accent && b.width.contains(12.0)
        case _            => false
      }
    assert(hasMark(checked.root))
    assert(!hasMark(unchecked.root))

  // --- Slider --------------------------------------------------------------

  test("a slider maps a press position to a 0..1 fraction"):
    var v = 0.0
    val m = mount(Slider(0.0, nv => v = nv), Size(200, 24))
    m.pointer.down(Offset(100, 12), 1) // halfway across a 200-wide track
    m.settle()
    assert(math.abs(v - 0.5) < 1e-9)

  test("a slider drag past the ends clamps to 0 and 1"):
    var v = 0.5
    val m = mount(Slider(0.5, nv => v = nv), Size(200, 24))
    m.pointer.down(Offset(100, 12), 1) // begin drag (captures the pointer)
    m.pointer.move(Offset(-40, 12))    // drag left past the start
    m.settle()
    assert(v == 0.0)
    m.pointer.move(Offset(400, 12))    // drag right past the end
    m.settle()
    assert(v == 1.0)
    m.pointer.up(Offset(400, 12), 1)

  test("a focused slider steps with the arrow keys"):
    var v = 0.5
    val m = mount(Slider(0.5, nv => v = nv), Size(200, 24))
    m.focus.focus(focusableBox(m.root))
    m.keys.down(Key.Right, false)
    m.settle()
    assert(math.abs(v - 0.55) < 1e-9)
