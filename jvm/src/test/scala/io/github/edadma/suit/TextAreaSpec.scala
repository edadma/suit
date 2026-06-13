package io.github.edadma.suit

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.BeforeAndAfterEach
import io.github.edadma.vdom.{Host, Scheduler}
import io.github.edadma.suit.dsl.*
import io.github.edadma.suit.widgets.*

// Headless tests for the multi-line text area, driven the way the runtime drives it (focus,
// then text-input and key events through the routers) with a monospace fake measurer so the
// caret geometry — including which line a click lands on — is exact. The pure caret logic is
// covered in EditBufferSpec; this checks the widget wires the model to the routers correctly,
// including the across-line motion a single-line field never exercises.
class TextAreaSpec extends AnyFunSuite with BeforeAndAfterEach:

  private val mono: TextMeasurer = (s, _) => Size(s.length * 10.0, 16.0)

  override def beforeEach(): Unit = { TextMeasurer.installed = mono; Clipboard.installed = new Clipboard.InMemory }
  override def afterEach(): Unit  = { TextMeasurer.installed = TextMeasurer.zero; Clipboard.installed = new Clipboard.InMemory }

  private def allObjects(o: RenderObject): List[RenderObject] = o :: o.children.toList.flatMap(allObjects)
  private def focusableBox(root: RenderObject): RenderBox =
    allObjects(root).collectFirst { case b: RenderBox if b.focusable => b }.get

  private case class Mounted(
      root:    RenderRoot,
      pointer: PointerRouter,
      keys:    KeyRouter,
      text:    TextRouter,
      focus:   FocusManager,
  ):
    def settle(): Unit =
      Scheduler.flushSync()
      root.layout(Constraints.tight(root.windowSize))

  private class Harness(val current: () => String, m: Mounted):
    def field: RenderBox = focusableBox(m.root)
    def focus(): Unit    = { m.focus.focus(field); m.settle() }
    def typeText(s: String): Unit = { m.text.input(s); m.settle() }
    def key(scancode: Int, shift: Boolean = false, ctrl: Boolean = false): Unit =
      m.keys.down(scancode, repeat = false, shift = shift, ctrl = ctrl); m.settle()
    def click(x: Double, y: Double): Unit =
      m.pointer.down(Offset(x, y), 1); m.pointer.up(Offset(x, y), 1); m.settle()
    // Re-lay the tree out at a new window width — what a splitter drag does to a pane (the runtime
    // sets windowSize then relayouts). The editor's box resize fires, re-rendering it; a few rounds
    // let the re-wrap settle.
    def resizeWidth(w: Double): Unit =
      m.root.windowSize = Size(w, 120)
      var i = 0
      while i < 4 do
        m.root.layout(Constraints.tight(m.root.windowSize))
        Scheduler.flushSync()
        i += 1

  private def mount(initial: String = ""): Harness =
    Host.config = new SuitHostConfig
    val clockMs = new Array[Double](1)
    val clock   = new FrameClock(() => clockMs(0))
    clock.install()
    var live = initial
    val app = view {
      val (v, setV, _) = useState(initial)
      live = v
      col(crossAxisAlignment = CrossAxisAlignment.Stretch)(TextArea(v, setV))
    }
    val root = new RenderRoot(Size(240, 120))
    createRoot(root).render(app())
    Scheduler.flushSync()
    root.layout(Constraints.tight(Size(240, 120)))
    val focus = new FocusManager
    val m     = Mounted(root, new PointerRouter(root, focus), new KeyRouter(focus), new TextRouter(focus), focus)
    val h     = new Harness(() => live, m)
    h.focus()
    h

  test("typing inserts characters"):
    val h = mount()
    h.typeText("hi")
    assert(h.current() == "hi")

  test("Enter splits the line"):
    val h = mount()
    h.typeText("ab")
    h.key(Key.Enter)
    h.typeText("cd")
    assert(h.current() == "ab\ncd")

  test("Up and Down move between lines"):
    val h = mount("abc\ndef")
    h.key(Key.End)              // unfocused caret starts at 0; End → end of line 0
    h.key(Key.Down)             // → line 1, same column (end)
    h.typeText("X")             // append to "def"
    assert(h.current() == "abc\ndefX")

  test("backspace at a line start joins the lines"):
    val h = mount()
    h.typeText("ab")
    h.key(Key.Enter)
    h.typeText("cd")            // "ab\ncd", caret after d
    h.key(Key.Home)            // start of line 1
    h.key(Key.Backspace)       // join
    assert(h.current() == "abcd")

  test("Ctrl+A then a deletion clears the whole buffer"):
    val h = mount("line one\nline two")
    h.key(Key.A, ctrl = true)
    h.key(Key.Delete)
    assert(h.current() == "")

  test("Ctrl+C copies the selection, Ctrl+V pastes it — newlines preserved"):
    val h = mount("line one\nline two")
    h.key(Key.A, ctrl = true)
    h.key(Key.C, ctrl = true)
    assert(Clipboard.installed.get() == "line one\nline two")
    assert(h.current() == "line one\nline two") // copy leaves the buffer unchanged
    h.key(Key.End, ctrl = true)                 // caret to end of document
    h.key(Key.V, ctrl = true)                   // paste appends, keeping the newline
    assert(h.current() == "line one\nline twoline one\nline two")

  test("Ctrl+X cuts the selection to the clipboard"):
    val h = mount("alpha\nbeta")
    h.key(Key.A, ctrl = true)
    h.key(Key.X, ctrl = true)
    assert(Clipboard.installed.get() == "alpha\nbeta")
    assert(h.current() == "")

  test("shift+Down extends a selection across the line break, replaced by typing"):
    val h = mount("abc\ndef")
    h.key(Key.Down, shift = true) // from 0 → line 1 col 0, selecting "abc\n"
    h.typeText("X")
    assert(h.current() == "Xdef")

  test("a click places the caret on the line and column it lands on"):
    val h = mount("abc\ndef")          // padX=8, padY=6, lineH=16, glyph 10 wide
    h.click(18, 30)                    // x→col 1, y in [22,38)→line 1
    h.typeText("X")
    assert(h.current() == "abc\ndXef")

  test("the editor accepts text and is focusable so the runtime opens text input for it"):
    val h = mount()
    assert(h.field.focusable)
    assert(h.field.acceptsText)

  // --- soft wrapping ---------------------------------------------------------
  // The editor mounts 240px wide; with padX=8 the content is 224px, so at 10px/char a row holds
  // 22 characters. A single logical line longer than that wraps into several visual rows.

  private def textRows(h: Harness): List[String] =
    allObjects(h.field).collect { case t: RenderText => t.text }

  test("a long logical line soft-wraps into multiple visual rows"):
    val h = mount("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaa") // 30 a's: one word, hard-broken at 22
    h.focus()
    val rows = textRows(h)
    assert(rows.length == 2)
    assert(rows.mkString == "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaa") // segments reconstruct the line

  test("a short line stays on one visual row"):
    val h = mount("short")
    h.focus()
    assert(textRows(h) == List("short"))

  test("clicking on the wrapped second row places the caret on that row"):
    // "aaaaaaaaaa bbbbbbbbbb cccccccccc": row 0 is "aaaaaaaaaa bbbbbbbbbb " (cols 0..22), row 1 is
    // "cccccccccc" (cols 22..32). A click at the start of row 1 lands at column 22.
    val h = mount("aaaaaaaaaa bbbbbbbbbb cccccccccc")
    h.focus()
    h.click(8, 30) // x→padX (col = row start), y in [22,38)→visual row 1
    h.typeText("X")
    assert(h.current() == "aaaaaaaaaa bbbbbbbbbb Xcccccccccc")

  test("Down moves to the next visual row within a wrapped line, not to the line's end"):
    val h = mount("aaaaaaaaaa bbbbbbbbbb cccccccccc")
    h.focus()
    h.key(Key.Home) // caret to start of the first visual row (column 0)
    h.key(Key.Down) // → visual row 1 (column 22), not the logical line end (column 32)
    h.typeText("X")
    assert(h.current() == "aaaaaaaaaa bbbbbbbbbb Xcccccccccc")

  test("End goes to the end of the visual row, not the whole logical line"):
    val h = mount("aaaaaaaaaa bbbbbbbbbb cccccccccc")
    h.focus()
    h.key(Key.Home) // start of visual row 0
    h.key(Key.End)  // end of visual row 0 = column 22 (start of row 1's content)
    h.typeText("X")
    assert(h.current() == "aaaaaaaaaa bbbbbbbbbb Xcccccccccc")

  test("the editor re-wraps when its width changes, as on a splitter drag"):
    val h    = mount("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaa") // 30 a's
    h.focus()
    val wide = textRows(h).length // 240px window → 224px content → ~22 chars/row → 2 rows
    h.resizeWidth(120)            // ~104px content → ~10 chars/row → more rows
    assert(textRows(h).length > wide)
    assert(textRows(h).mkString == "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaa") // still reconstructs the line
