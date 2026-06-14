package io.github.edadma.suit

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.BeforeAndAfterEach
import io.github.edadma.vdom.{Host, Scheduler}
import io.github.edadma.suit.dsl.*
import io.github.edadma.suit.widgets.*

// Headless tests for the application menu bar — the top-level row plus its dropdowns, driven the
// way the runtime drives them (pointer down/up and moves through the router) into a root carrying
// an overlay layer and an `OverlayEnv`, the same wiring `Suit.run` sets up. A monospace fake
// measurer gives every label a known width so a click lands on the intended label. The bar opens a
// menu on a click, slides it across the row on a hover while one is open, and closes on an outside
// click, on Escape, or on choosing an item — each checked by what the overlay layer holds.
class MenuBarSpec extends AnyFunSuite with BeforeAndAfterEach:

  private val mono: TextMeasurer = (s, _) => Size(s.length * 10.0, 16.0)

  override def beforeEach(): Unit = TextMeasurer.installed = mono
  override def afterEach(): Unit  = TextMeasurer.installed = TextMeasurer.zero

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
      while i < 12 do
        Scheduler.flushSync()
        clockMs(0) += 10_000.0
        clock.pump()
        Scheduler.flushSync()
        root.layout(Constraints.tight(root.windowSize))
        i += 1

  private def mount(app: VNode, size: Size = Size(400, 300)): Mounted =
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

  // A File/Edit bar; every chosen item records its name into `picked` and closes the menu.
  private def barApp(picked: collection.mutable.ListBuffer[String]): VNode =
    view {
      menuBar(
        menu("File")(close =>
          Seq(
            MenuItem("New", () => { picked += "New"; close() }),
            MenuItem("Open", () => { picked += "Open"; close() }),
          ),
        ),
        menu("Edit")(close =>
          Seq(
            MenuItem("Undo", () => { picked += "Undo"; close() }),
            MenuItem("Redo", () => { picked += "Redo"; close() }),
          ),
        ),
      )
    }.apply()

  private def allObjects(o: RenderObject): List[RenderObject] = o :: o.children.toList.flatMap(allObjects)

  // The clickable box whose direct child is the given label text — a top-level label in the bar, or
  // a `MenuItem` row in an open dropdown.
  private def boxWithText(root: RenderObject, label: String): RenderObject =
    allObjects(root).collectFirst {
      case b: RenderBox if b.children.exists {
            case t: RenderText => t.text == label
            case _             => false
          } =>
        b
    }.getOrElse(throw new NoSuchElementException(s"no box with text '$label'"))

  private def center(o: RenderObject): Offset =
    val off = o.absoluteOffset
    Offset(off.x + o.size.width / 2, off.y + o.size.height / 2)

  private def click(m: Mounted, label: String): Unit =
    val c = center(boxWithText(m.root, label))
    m.pointer.down(c, 1); m.pointer.up(c, 1); m.settle()

  private def hover(m: Mounted, label: String): Unit =
    m.pointer.move(center(boxWithText(m.root, label))); m.settle()

  // The label of every MenuItem currently portaled into the overlay (an open dropdown's rows).
  private def overlayItems(m: Mounted): Set[String] =
    allObjects(m.overlay).collect { case t: RenderText => t.text }.toSet

  test("with nothing open, the bar portals no menu into the overlay"):
    val m = mount(barApp(collection.mutable.ListBuffer.empty))
    m.settle()
    assert(m.overlay.children.isEmpty)

  test("clicking a top-level label opens that menu's dropdown"):
    val m = mount(barApp(collection.mutable.ListBuffer.empty))
    m.settle()
    click(m, "File")
    assert(overlayItems(m).contains("New"))
    assert(overlayItems(m).contains("Open"))
    assert(!overlayItems(m).contains("Undo"))

  test("choosing an item runs its action and closes the menu"):
    val picked = collection.mutable.ListBuffer.empty[String]
    val m      = mount(barApp(picked))
    m.settle()
    click(m, "File")
    click(m, "Open")
    assert(picked.toList == List("Open"))
    assert(m.overlay.children.isEmpty)

  test("with a menu open, hovering another label slides the open menu to it"):
    val m = mount(barApp(collection.mutable.ListBuffer.empty))
    m.settle()
    click(m, "File")
    assert(overlayItems(m).contains("New"))
    hover(m, "Edit")
    assert(overlayItems(m).contains("Undo"))
    assert(!overlayItems(m).contains("New"))

  test("clicking outside the open menu closes it"):
    val m = mount(barApp(collection.mutable.ListBuffer.empty))
    m.settle()
    click(m, "File")
    assert(m.overlay.children.nonEmpty)
    m.pointer.down(Offset(380, 280), 1) // bottom-right, away from the bar and the card
    m.pointer.up(Offset(380, 280), 1)
    m.settle()
    assert(m.overlay.children.isEmpty)

  test("Escape closes the open menu"):
    val m = mount(barApp(collection.mutable.ListBuffer.empty))
    m.settle()
    click(m, "File")
    assert(m.overlay.children.nonEmpty)
    assert(m.focus.escape())
    m.settle()
    assert(m.overlay.children.isEmpty)
