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
    def field: RenderBox          = focusableBox(m.root)
    def focus(): Unit             = { m.focus.focus(field); m.settle() }
    def typeText(s: String): Unit = { m.text.input(s); m.settle() }
    def key(scancode: Int, shift: Boolean = false, ctrl: Boolean = false): Unit =
      m.keys.down(scancode, repeat = false, shift = shift, ctrl = ctrl); m.settle()
    def click(x: Double): Unit = { m.pointer.down(Offset(x, 12), 1); m.pointer.up(Offset(x, 12), 1); m.settle() }
    def drag(x0: Double, x1: Double): Unit =
      m.pointer.down(Offset(x0, 12), 1); m.pointer.move(Offset(x1, 12)); m.pointer.up(Offset(x1, 12), 1); m.settle()

  private case class Mounted(root: RenderRoot, pointer: PointerRouter, keys: KeyRouter, text: TextRouter, focus: FocusManager):
    def settle(): Unit =
      Scheduler.flushSync()
      root.layout(Constraints.tight(root.windowSize))

  /** Mount a controlled TextField (echoing edits through `useState`) filling a 240-wide
    * field, focus it, and return a harness. The Stretch column gives the field a tight width
    * so it fills the row and click coordinates map across the whole field. */
  private def mount(initial: String = ""): Harness =
    Host.config = new SuitHostConfig
    var live  = initial
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
    val m     = Mounted(root, new PointerRouter(root, focus), new KeyRouter(focus), new TextRouter(focus), focus)
    val h     = new Harness(() => live, m)
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
