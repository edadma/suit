package io.github.edadma.suit

import org.scalatest.funsuite.AnyFunSuite
import io.github.edadma.vdom.{Host, Scheduler}
import io.github.edadma.suit.dsl.*
import io.github.edadma.suit.widgets.*

// Headless tests for the widget library. Widgets are vdom components, so these mount one
// for real through `SuitHostConfig` into a fresh render root, flush the scheduler
// synchronously, lay the tree out, then drive the input routers and assert on the
// resulting render tree — the same off-device path the runtime takes, minus SDL. No
// text measurer is installed, so labels lay out as zero-size; the tests assert on
// structure, colour, and callbacks rather than text extent.
class WidgetSpec extends AnyFunSuite:

  /** Mount `app` into a root of `size`, returning the root and routers wired to one
    * focus manager — the headless equivalent of `Suit.run`'s setup. A FrameClock over a
    * hand-advanced time is installed so the widgets' animations can be run to completion
    * deterministically; `settle()` drives them there. */
  private def mount(app: VNode, size: Size = Size(200, 100)): Mounted =
    Host.config = new SuitHostConfig
    val clockMs = new Array[Double](1)
    val clock   = new FrameClock(() => clockMs(0))
    clock.install()
    val root = new RenderRoot(size)
    createRoot(root).render(app)
    Scheduler.flushSync()
    root.layout(Constraints.tight(size))
    val focus = new FocusManager
    Mounted(root, new PointerRouter(root, focus), new KeyRouter(focus), focus, clock, clockMs)

  private case class Mounted(
      root:    RenderRoot,
      pointer: PointerRouter,
      keys:    KeyRouter,
      focus:   FocusManager,
      clock:   FrameClock,
      clockMs: Array[Double],
  ):
    /** Commit any state the handlers queued, run every in-flight animation straight to its
      * target by jumping the clock past any duration and pumping until nothing is pending,
      * then re-position the tree — so assertions see the settled result. */
    def settle(): Unit =
      Scheduler.flushSync()
      var guard = 0
      while clock.active && guard < 100 do
        clockMs(0) += 10_000.0
        clock.pump()
        Scheduler.flushSync()
        guard += 1
      root.layout(Constraints.tight(root.windowSize))

  private def allObjects(o: RenderObject): List[RenderObject] =
    o :: o.children.toList.flatMap(allObjects)

  private def focusableBox(root: RenderObject): RenderBox =
    allObjects(root).collectFirst { case b: RenderBox if b.focusable => b }.get

  private def boxes(root: RenderObject): List[RenderBox] =
    allObjects(root).collect { case b: RenderBox => b }

  private def hasBox(root: RenderObject)(p: RenderBox => Boolean): Boolean =
    boxes(root).exists(p)

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
    assert(focusableBox(m.root).background == Solid(Theme.default.primary))
    m.pointer.down(Offset(50, 50), 1)
    m.settle()
    assert(focusableBox(m.root).background == Solid(Theme.default.primaryActive))
    m.pointer.up(Offset(50, 50), 1)
    m.settle()
    assert(focusableBox(m.root).background == Solid(Theme.default.primary))

  test("a focused button activates on Space"):
    var clicks = 0
    val m      = mount(Button("OK", () => clicks += 1))
    m.focus.focus(focusableBox(m.root))
    m.keys.down(Key.Space, false)
    m.settle()
    assert(clicks == 1)

  test("a ThemeProvider restyles a widget from the provided theme"):
    val custom = Theme.default.copy(primary = Color(200, 50, 100), radius = 20)
    val m      = mount(ThemeProvider(custom)(Button("OK", () => ())))
    val btn    = focusableBox(m.root)
    assert(btn.background == Solid(Color(200, 50, 100)))
    assert(btn.borderRadius == BorderRadius.all(20))

  test("the built-in themes carry the right colour scheme"):
    assert(Theme.default == Theme.dark)
    assert(Theme.dark.isDark)
    assert(!Theme.light.isDark)
    assert(Theme.violetDark.isDark)
    assert(!Theme.violetLight.isDark)
    assert(Theme.builtIns == List(Theme.dark, Theme.light, Theme.violetDark, Theme.violetLight))

  test("a light theme has a bright surface over a darker body; a dark theme inverts the ink"):
    def lum(c: Color): Int = c.r + c.g + c.b
    assert(lum(Theme.light.surface) > lum(Theme.light.surfaceText)) // light: surface brighter than its ink
    assert(lum(Theme.dark.surface) < lum(Theme.dark.surfaceText))   // dark: surface darker than its ink
    // the body sits beneath the elevated surface in either scheme
    assert(lum(Theme.dark.background) < lum(Theme.dark.surface))
    assert(lum(Theme.light.background) < lum(Theme.light.surface))

  test("a button paints the primary of whichever built-in theme is provided"):
    val lightM = mount(ThemeProvider(Theme.light)(Button("OK", () => ())), Size(120, 60))
    lightM.settle()
    assert(focusableBox(lightM.root).background == Solid(Theme.light.primary))
    val violetM = mount(ThemeProvider(Theme.violetDark)(Button("OK", () => ())), Size(120, 60))
    violetM.settle()
    assert(focusableBox(violetM.root).background == Solid(Theme.violetDark.primary))

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
        case b: RenderBox => b.background == Solid(Theme.default.accent) && b.width.contains(12.0)
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

  // --- Card ----------------------------------------------------------------

  test("a card paints the theme surface"):
    val m = mount(Card(text("hi")))
    assert(hasBox(m.root)(_.background == Solid(Theme.default.surface)))

  // --- Divider -------------------------------------------------------------

  test("a horizontal divider is a hairline in the border colour"):
    val m = mount(col(crossAxisAlignment = CrossAxisAlignment.Stretch)(Divider(false)), Size(200, 50))
    assert(hasBox(m.root)(b => b.background == Solid(Theme.default.border) && b.height.contains(1.0)))

  test("a vertical divider is a hairline along the cross axis"):
    val m = mount(row(crossAxisAlignment = CrossAxisAlignment.Stretch)(Divider(true)), Size(50, 200))
    assert(hasBox(m.root)(b => b.background == Solid(Theme.default.border) && b.width.contains(1.0)))

  // --- Badge ---------------------------------------------------------------

  test("a badge paints the accent as a pill"):
    val m = mount(Badge("3"), Size(40, 20))
    assert(hasBox(m.root)(_.background == Solid(Theme.default.accent)))

  // --- ProgressBar ---------------------------------------------------------

  test("a progress bar fills the value's fraction of the track"):
    val m = mount(ProgressBar(0.5), Size(200, 8))
    m.settle()
    assert(hasBox(m.root)(b => b.background == Solid(Theme.default.accent) && b.flex == 500))

  test("a full progress bar has no empty remainder"):
    val m = mount(ProgressBar(1.0), Size(200, 8))
    m.settle()
    assert(hasBox(m.root)(b => b.background == Solid(Theme.default.accent) && b.flex == 1000))
    assert(!hasBox(m.root)(b => b.background == null && b.flex > 0))

  // --- Switch --------------------------------------------------------------

  test("a switch reports the flipped value on click"):
    var on = false
    val m  = mount(Switch(false, b => on = b), Size(44, 24))
    m.pointer.down(Offset(22, 12), 1)
    m.pointer.up(Offset(22, 12), 1)
    m.settle()
    assert(on)

  test("a switch tints its track to the accent when on"):
    val onM  = mount(Switch(true, _ => ()), Size(44, 24))
    val offM = mount(Switch(false, _ => ()), Size(44, 24))
    onM.settle()
    offM.settle()
    assert(focusableBox(onM.root).background == Solid(Theme.default.accent))
    assert(focusableBox(offM.root).background == Solid(Theme.default.track))

  // --- RadioGroup ----------------------------------------------------------

  private val radioOptions = Seq("a" -> "A", "b" -> "B")

  test("a radio group reports the clicked option's value"):
    var sel = "a"
    val m   = mount(RadioGroup(radioOptions, "a", v => sel = v), Size(200, 100))
    m.pointer.down(Offset(9, 35), 1) // the second row's circle (row 0 is 0..18, gap 8, row 1 at 26..44)
    m.pointer.up(Offset(9, 35), 1)
    m.settle()
    assert(sel == "b")

  test("only the selected radio option shows a dot"):
    val m = mount(RadioGroup(radioOptions, "a", _ => ()), Size(200, 100))
    m.settle()
    val dots = boxes(m.root).count(b => b.background == Solid(Theme.default.accent) && b.width.contains(10.0))
    assert(dots == 1)

  // --- Alert ---------------------------------------------------------------

  test("an alert borders in its status colour"):
    val m = mount(Alert(AlertKind.Success, "saved"), Size(200, 50))
    assert(hasBox(m.root)(_.border == Solid(Theme.default.success)))

  // --- Tabs ----------------------------------------------------------------

  test("a tab bar reports the clicked tab's value"):
    var tab = "one"
    val m   = mount(Tabs(Seq("one" -> "One", "two" -> "Two"), "one", v => tab = v), Size(200, 40))
    m.pointer.down(Offset(33, 8), 1) // second tab (tab 0 is 24 wide, gap 4, tab 1 at x≥28)
    m.pointer.up(Offset(33, 8), 1)
    m.settle()
    assert(tab == "two")

  test("a tab fills with the surface colour as it animates — no dark flash"):
    // A controlled wrapper so clicking a tab re-renders with the new selection and the
    // selection transition actually runs. Mid-transition a tab's fill must be the surface
    // colour at partial alpha, never a darkened colour — the bug was fading from `transparent`
    // (which is black), so the fill passed through dark on its way in/out.
    val app = view {
      val (sel, setSel, _) = useState("one")
      Tabs(Seq("one" -> "One", "two" -> "Two"), sel, setSel)
    }
    val m = mount(app(), Size(200, 40))
    m.pointer.down(Offset(33, 8), 1)
    m.pointer.up(Offset(33, 8), 1)
    Scheduler.flushSync()                  // commit the selection; both tabs start animating
    m.clockMs(0) += 75.0                    // halfway through the 150ms transition
    m.clock.pump()
    Scheduler.flushSync()
    m.root.layout(Constraints.tight(m.root.windowSize))

    val surface = Theme.default.surface
    val midFills = boxes(m.root).flatMap { b =>
      b.background match
        case Solid(c) if c.a > 0 && c.a < 255 => Some(c) // a tab caught mid-fade
        case _                                => None
    }
    assert(midFills.nonEmpty) // we actually caught a transition in progress
    assert(midFills.forall(c => c.r == surface.r && c.g == surface.g && c.b == surface.b))

  // --- MenuItem ------------------------------------------------------------

  test("a menu item reports its selection on click"):
    var picked = ""
    val m      = mount(MenuItem("One", () => picked = "one"))
    m.pointer.down(Offset(2, 2), 1)
    m.pointer.up(Offset(2, 2), 1)
    m.settle()
    assert(picked == "one")

  test("a focused menu item activates from the keyboard"):
    var picked = ""
    val m      = mount(MenuItem("Two", () => picked = "two"))
    m.focus.focus(focusableBox(m.root))
    m.keys.down(Key.Enter, false)
    m.settle()
    assert(picked == "two")

  test("a menu item highlights on hover"):
    val m    = mount(MenuItem("One", () => ()))
    val item = focusableBox(m.root)
    val rest = item.background
    m.pointer.move(Offset(2, 2))
    m.settle()
    assert(item.background != rest)
