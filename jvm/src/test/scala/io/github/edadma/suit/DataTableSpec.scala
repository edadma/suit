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

  /** The rows the grid actually built, in tree (top-to-bottom) order. Both the body rows and the
    * header cells/resize handles now carry a `click` handler (the header sorts, the handle's is a
    * no-op that stops a resize press from sorting), so the body rows are picked out as the widest
    * clickable boxes — they span the full content width, the header cells are per-column. */
  private def rowBoxes(root: RenderObject): List[RenderBox] =
    val clickable = allObjects(root).collect { case b: RenderBox if b.handlers.contains("click") => b }
    val maxW      = clickable.flatMap(_.width).maxOption.getOrElse(0.0)
    clickable.filter(_.width.contains(maxW))

  /** The clickable header cell whose label is `name` (the nearest clickable box above that text). */
  private def headerCell(root: RenderObject, name: String): RenderBox =
    val t = allObjects(root).collect { case t: RenderText if t.text == name => t }.head
    var n: RenderObject | Null = t
    while n != null && !(n.isInstanceOf[RenderBox] && n.asInstanceOf[RenderBox].handlers.contains("click")) do
      n = n.asInstanceOf[RenderObject].parent
    n.asInstanceOf[RenderBox]

  /** The resize handles (the 8px-wide grab boxes), in column order. */
  private def resizeHandles(root: RenderObject): List[RenderBox] =
    allObjects(root).collect {
      case b: RenderBox if b.width.contains(8.0) && b.handlers.contains("mousedown") => b
    }

  private def rowTexts(b: RenderBox): List[String] = allObjects(b).collect { case t: RenderText => t.text }

  private def click(b: RenderBox): Unit =
    b.handlers("click").apply(PointerEvent(Offset.zero, Offset.zero, b.size))

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

  test("cell text is vertically centred within its row band, not top-aligned"):
    val m     = mount(Vector("id", "name", "value"), sampleRows(3), _ => ())
    val texts = allObjects(m.root).collect { case t: RenderText => t }
    val idText = texts.find(_.text == "id").get
    // The enclosing row band is the nearest ancestor box of the row height (28); the cell boxes
    // carry only a width, so they are skipped.
    def bandOf(o: RenderObject): RenderBox =
      var n: RenderObject | Null = o
      while n != null && !(n.isInstanceOf[RenderBox] && n.asInstanceOf[RenderBox].height.contains(28.0)) do
        n = n.asInstanceOf[RenderObject].parent
      n.asInstanceOf[RenderBox]
    val band    = bandOf(idText)
    val textMid = idText.absoluteOffset.y + idText.size.height / 2
    val bandMid = band.absoluteOffset.y + band.size.height / 2
    assert(math.abs(textMid - bandMid) < 1.0) // centred, not sitting at the band's top

  test("clicking a column header sorts the rows ascending, then descending"):
    // Rows out of order on the id column; clicking its header should sort them.
    val rows = Vector(Vector("3", "c"), Vector("1", "a"), Vector("2", "b"))
    val m    = mount(Vector("id", "name"), rows, _ => ())
    assert(rowTexts(rowBoxes(m.root).head).contains("3")) // unsorted: first row is id 3
    click(headerCell(m.root, "id"))
    m.settle()
    assert(rowTexts(rowBoxes(m.root).head).contains("1")) // ascending: id 1 first
    click(headerCell(m.root, "id"))
    m.settle()
    assert(rowTexts(rowBoxes(m.root).head).contains("3")) // descending: id 3 first

  test("an unsorted table keeps the rows in their given order"):
    val rows = Vector(Vector("3", "c"), Vector("1", "a"), Vector("2", "b"))
    val m    = mount(Vector("id", "name"), rows, _ => ())
    assert(rowTexts(rowBoxes(m.root).head).contains("3"))

  test("selection and onSelect use the original row index regardless of sort"):
    var sel  = -1
    val rows = Vector(Vector("3", "c"), Vector("1", "a"), Vector("2", "b"))
    val m    = mount(Vector("id", "name"), rows, i => sel = i)
    click(headerCell(m.root, "id")) // sort ascending: displayed row 0 is original index 1 (id "1")
    m.settle()
    click(rowBoxes(m.root).head)
    assert(sel == 1) // the original index, not the displayed position 0

  test("dragging a column's resize handle widens that column"):
    val m      = mount(Vector("id", "name", "value"), sampleRows(5), _ => ())
    val before = headerCell(m.root, "id").width.get
    val h      = resizeHandles(m.root).head // column 0's handle
    h.handlers("mousedown").apply(PointerEvent(Offset(100, 0), Offset.zero, h.size, 1))
    h.handlers("mousemove").apply(PointerEvent(Offset(140, 0), Offset.zero, h.size, 1))
    m.settle()
    val after = headerCell(m.root, "id").width.get
    assert(after > before)
    assert(math.abs(after - (before + 40)) < 0.001)
