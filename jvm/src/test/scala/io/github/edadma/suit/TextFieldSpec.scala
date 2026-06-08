package io.github.edadma.suit

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.BeforeAndAfterEach
import io.github.edadma.vdom.{Host, Scheduler}
import io.github.edadma.suit.dsl.*
import io.github.edadma.suit.widgets.*

// Headless tests for the text field. A monospace fake measurer (every glyph 10 wide, 16
// tall) makes caret and selection geometry exact, so click-to-caret and drag-selection are
// testable with no device. The field is driven the way the runtime drives it — focus, then
// text-input and key events through the routers — and wrapped in a controlled harness whose
// `useState` echoes edits back, exactly like a real application, so the asserted `current`
// value reflects the full edit round-trip.
class TextFieldSpec extends AnyFunSuite with BeforeAndAfterEach:

  private val mono: TextMeasurer = (s, _) => Size(s.length * 10.0, 16.0)

  override def beforeEach(): Unit = TextMeasurer.installed = mono
  override def afterEach(): Unit  = TextMeasurer.installed = TextMeasurer.zero

  private def allObjects(o: RenderObject): List[RenderObject] = o :: o.children.toList.flatMap(allObjects)
  private def focusableBox(root: RenderObject): RenderBox =
    allObjects(root).collectFirst { case b: RenderBox if b.focusable => b }.get

  private class Harness(val current: () => String, m: Mounted):
    def root: RenderRoot          = m.root
    def field: RenderBox          = focusableBox(m.root)
    def focus(): Unit             = { m.focus.focus(field); m.settle() }
    def blur(): Unit              = { m.focus.blur(); m.settle() }
    def clockActive: Boolean      = m.clock.active
    def typeText(s: String): Unit = { m.text.input(s); m.settle() }
    def key(scancode: Int, shift: Boolean = false, ctrl: Boolean = false): Unit =
      m.keys.down(scancode, repeat = false, shift = shift, ctrl = ctrl); m.settle()
    def click(x: Double): Unit = { m.pointer.down(Offset(x, 12), 1); m.pointer.up(Offset(x, 12), 1); m.settle() }
    def drag(x0: Double, x1: Double): Unit =
      m.pointer.down(Offset(x0, 12), 1); m.pointer.move(Offset(x1, 12)); m.pointer.up(Offset(x1, 12), 1); m.settle()
    /** Advance the motion clock by `ms` and pump it, committing whatever the blink interval
      * queued — the deterministic way to step the caret blink in a headless test. */
    def advance(ms: Double): Unit = { m.clockMs(0) += ms; m.clock.pump(); m.settle() }
    /** Is the caret (a 2-wide bar) currently in the render tree? */
    def caretShown: Boolean =
      allObjects(m.root).exists { case b: RenderBox => b.width.contains(2.0); case _ => false }

  private case class Mounted(
      root:    RenderRoot,
      pointer: PointerRouter,
      keys:    KeyRouter,
      text:    TextRouter,
      focus:   FocusManager,
      clock:   FrameClock,
      clockMs: Array[Double],
  ):
    def settle(): Unit =
      Scheduler.flushSync()
      root.layout(Constraints.tight(root.windowSize))

  /** Mount a controlled TextField (echoing edits through `useState`) filling a 240-wide
    * field, focus it, and return a harness. The Stretch column gives the field a tight width
    * so it fills the row and click coordinates map across the whole field. */
  private def mount(initial: String = ""): Harness =
    Host.config = new SuitHostConfig
    val clockMs = new Array[Double](1)
    val clock   = new FrameClock(() => clockMs(0))
    clock.install()
    var live = initial
    val app = view {
      val (v, setV, _) = useState(initial)
      live = v
      col(crossAxisAlignment = CrossAxisAlignment.Stretch)(TextField(v, setV))
    }
    val root = new RenderRoot(Size(240, 40))
    createRoot(root).render(app())
    Scheduler.flushSync()
    root.layout(Constraints.tight(Size(240, 40)))
    val focus = new FocusManager
    val m =
      Mounted(root, new PointerRouter(root, focus), new KeyRouter(focus), new TextRouter(focus), focus, clock, clockMs)
    val h = new Harness(() => live, m)
    h.focus()
    h

  // --- typing & deletion ---------------------------------------------------

  test("typing inserts characters at the caret"):
    val h = mount()
    h.typeText("h")
    h.typeText("i")
    assert(h.current() == "hi")

  test("backspace deletes the character before the caret"):
    val h = mount()
    h.typeText("abc")
    h.key(Key.Backspace)
    assert(h.current() == "ab")

  test("delete removes the character after the caret"):
    val h = mount()
    h.typeText("abc")
    h.key(Key.Home)
    h.key(Key.Delete)
    assert(h.current() == "bc")

  // --- caret movement ------------------------------------------------------

  test("the arrow keys move the caret"):
    val h = mount()
    h.typeText("ac")
    h.key(Key.Left)
    h.typeText("b")
    assert(h.current() == "abc")

  test("Home and End jump the caret to the ends"):
    val h = mount()
    h.typeText("bc")
    h.key(Key.Home)
    h.typeText("a")        // -> "abc", caret after 'a'
    h.key(Key.End)
    h.typeText("d")        // -> "abcd"
    assert(h.current() == "abcd")

  // --- selection -----------------------------------------------------------

  test("shift+arrow selects and typing replaces the selection"):
    val h = mount()
    h.typeText("abc")      // caret at end
    h.key(Key.Left, shift = true) // select "c"
    h.typeText("X")
    assert(h.current() == "abX")

  test("ctrl+A selects all and a deletion clears it"):
    val h = mount()
    h.typeText("abc")
    h.key(Key.A, ctrl = true)
    h.key(Key.Backspace)
    assert(h.current() == "")

  // --- mouse ---------------------------------------------------------------

  test("a click places the caret at the nearest character boundary"):
    val h = mount()
    h.typeText("abcd")     // monospace: boundaries at x = 8 + 10*i
    h.click(28)            // 8 padding + 20 -> boundary 2
    h.typeText("X")
    assert(h.current() == "abXcd")

  test("a drag selects a range that a deletion removes"):
    val h = mount()
    h.typeText("abcd")
    h.drag(18, 38)         // boundary 1 .. boundary 3 -> selects "bc"
    h.key(Key.Backspace)
    assert(h.current() == "ad")

  // --- focus / structure ---------------------------------------------------

  test("the field accepts text and is focusable so the runtime opens text input for it"):
    val h = mount()
    assert(h.field.focusable)
    assert(h.field.acceptsText)

  // --- caret blink ---------------------------------------------------------

  test("the caret blinks while focused"):
    val h = mount("abc")     // mount focuses it
    assert(h.caretShown)     // solid on focus
    h.advance(530)           // one full interval → caret off
    assert(!h.caretShown)
    h.advance(530)           // → back on
    assert(h.caretShown)

  test("an edit shows a solid caret immediately and restarts the blink"):
    val h = mount("abc")
    h.advance(530)           // blink to the off phase
    assert(!h.caretShown)
    h.typeText("d")          // an edit forces the caret solid
    assert(h.caretShown)
    h.advance(529)           // still within the restarted interval
    assert(h.caretShown)

  test("an unfocused field shows no caret and arms no blink timer"):
    val h = mount("abc")
    assert(h.clockActive)    // focused: the blink interval is running
    h.blur()
    assert(!h.caretShown)
    assert(!h.clockActive)   // blurring cancels the timer, leaving the clock idle

  // --- scroll to caret -----------------------------------------------------

  // The 240-wide field, less 8px padding each side, shows 224px ≈ 22 monospace glyphs.
  private def caretBox(h: Harness): RenderBox =
    allObjects(h.root).collectFirst { case b: RenderBox if b.width.contains(2.0) => b }.get

  test("the caret stays within the field when the text overflows it"):
    val h = mount()
    h.typeText("abcdefghijklmnopqrstuvwxyz0123456789") // 36 glyphs ≈ 360px, well past 224
    val field = h.field
    val cx    = caretBox(h).absoluteOffset.x
    assert(cx >= field.absoluteOffset.x)
    assert(cx <= field.absoluteOffset.x + field.size.width)

  test("a short value does not scroll — the caret sits at the measured offset"):
    val h = mount()
    h.typeText("abc")                       // fits with room to spare → no scroll
    val field = h.field
    val cx    = caretBox(h).absoluteOffset.x
    // caret after "abc" = 8 padding + 30 from the field's left edge, no scroll applied
    assert(math.abs(cx - (field.absoluteOffset.x + 8 + 30)) < 1e-6)

  test("moving Home after overflow scrolls the start back into view"):
    val h = mount()
    h.typeText("abcdefghijklmnopqrstuvwxyz0123456789")
    h.key(Key.Home)                         // caret to 0 → text anchors left again
    val field = h.field
    val cx    = caretBox(h).absoluteOffset.x
    assert(math.abs(cx - (field.absoluteOffset.x + 8)) < 1e-6) // caret at the left padding
