package io.github.edadma.suit

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.BeforeAndAfterEach
import io.github.edadma.vdom.{Host, Scheduler}
import io.github.edadma.suit.dsl.*
import io.github.edadma.suit.widgets.*

// Headless tests for the data grid. A monospace fake measurer makes column auto-sizing
// deterministic. The key property is virtualization: a thousand-row table builds only the
// rows under the viewport, not all thousand. Row selection wiring is checked by invoking a
// built row's click handler (geometry-independent, so it does not depend on the one-frame
// measurement lag of the scrolling viewport).
class DataTableSpec extends AnyFunSuite with BeforeAndAfterEach:

  private val mono: TextMeasurer = (s, _) => Size(s.length * 10.0, 16.0)

  override def beforeEach(): Unit = TextMeasurer.installed = mono
  override def afterEach(): Unit  = TextMeasurer.installed = TextMeasurer.zero

  private def allObjects(o: RenderObject): List[RenderObject] = o :: o.children.toList.flatMap(allObjects)

  /** The rows the grid actually built: boxes carrying a `click` handler (the header carries
    * none), in tree (top-to-bottom) order. */
  private def rowBoxes(root: RenderObject): List[RenderBox] =
    allObjects(root).collect { case b: RenderBox if b.handlers.contains("click") => b }

  private case class Mounted(root: RenderRoot):
    def settle(): Unit =
      Scheduler.flushSync()
      root.layout(Constraints.tight(root.windowSize))

  private def mount(columns: Seq[String], rows: IndexedSeq[IndexedSeq[String]], onSelect: Int => Unit): Mounted =
    Host.config = new SuitHostConfig
    val clock = new FrameClock(() => 0.0)
    clock.install()
    val app = view {
      // A bounded height is required: the grid windows against it. 300px viewport.
      col(crossAxisAlignment = CrossAxisAlignment.Stretch, mainAxisSize = MainAxisSize.Max)(
        box(flex = 1)(dataTable(columns, rows, onSelect = onSelect, rowHeight = 28.0)),
      )
    }
    val root = new RenderRoot(Size(400, 300))
    createRoot(root).render(app())
    Scheduler.flushSync()
    root.layout(Constraints.tight(Size(400, 300)))
    val m = Mounted(root)
    // A couple of settles so the viewport ref is read and the window recomputed against the
    // real (laid-out) height rather than the first-frame fallback.
    m.settle()
    m.settle()
    m

  private def sampleRows(n: Int): IndexedSeq[IndexedSeq[String]] =
    (0 until n).map(i => Vector(i.toString, s"name$i", s"v$i"))

  test("a large table builds only the rows under the viewport, not all of them"):
    var sel = -1
    val m   = mount(Vector("id", "name", "value"), sampleRows(1000), i => sel = i)
    val built = rowBoxes(m.root)
    assert(built.nonEmpty)
    assert(built.size < 60) // a 300px viewport of 28px rows is ~11 + overscan, nowhere near 1000

  test("clicking a built row reports its index through onSelect"):
    var sel = -1
    val m   = mount(Vector("id", "name", "value"), sampleRows(1000), i => sel = i)
    val first = rowBoxes(m.root).head
    first.handlers("click").apply(PointerEvent(Offset.zero, Offset.zero, first.size))
    assert(sel == 0) // unscrolled, the first built row is row 0

  test("every column header is present in the tree"):
    val m = mount(Vector("id", "name", "value"), sampleRows(20), _ => ())
    val texts = allObjects(m.root).collect { case t: RenderText => t.text }
    assert(Set("id", "name", "value").subsetOf(texts.toSet))

  test("a small table builds all its rows and they each select"):
    var sel = -1
    val m   = mount(Vector("id", "name", "value"), sampleRows(3), i => sel = i)
    val built = rowBoxes(m.root)
    assert(built.size == 3)
