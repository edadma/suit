package io.github.edadma.suit

import org.scalatest.funsuite.AnyFunSuite

// Headless tests for the constraint-layout engine. The render tree is pure Scala with
// no SDL dependency, so these run on the JVM with `sbt suitJVM/test` — no window, no
// device. Layout is checked by building RenderObjects directly, calling `layout` with
// a known Constraints, and asserting the resulting sizes and child offsets; painting
// is checked against a RecordingCanvas. This is the whole reason the engine was kept
// FFI-free.
class LayoutSpec extends AnyFunSuite:

  /** A leaf of a fixed intrinsic size — an inflexible child for the flex tests. */
  private def fixed(w: Double, h: Double): RenderBox =
    val b = new RenderBox
    b.width = Some(w)
    b.height = Some(h)
    b

  private def child[T <: RenderObject](parent: RenderObject, c: T): T =
    parent.insertChild(c, null)
    c

  // --- Constraints -----------------------------------------------------------

  test("constrain clamps a size into the allowed range"):
    val c = Constraints(10, 100, 10, 100)
    assert(c.constrain(Size(5, 200)) == Size(10, 100))
    assert(c.constrain(Size(50, 50)) == Size(50, 50))

  test("loosen drops the minimums but keeps the maximums"):
    assert(Constraints(20, 80, 20, 80).loosen == Constraints(0, 80, 0, 80))

  test("deflate shrinks the maxima by the insets and never goes negative"):
    val c = Constraints(0, 100, 0, 50).deflate(EdgeInsets.all(10))
    assert(c == Constraints(0, 80, 0, 30))
    val tiny = Constraints(0, 5, 0, 5).deflate(EdgeInsets.all(10))
    assert(tiny == Constraints(0, 0, 0, 0))

  test("tighten pins an axis to a value clamped inside the range"):
    val c = Constraints(0, 100, 0, 100)
    assert(c.tighten(width = Some(40)) == Constraints(40, 40, 0, 100))
    assert(c.tighten(height = Some(500)) == Constraints(0, 100, 100, 100)) // clamped to max

  // --- RenderBox -------------------------------------------------------------

  test("a box with no size fills a tight constraint"):
    val b = new RenderBox
    b.layout(Constraints.tight(Size(200, 120)))
    assert(b.size == Size(200, 120))

  test("a box with no size and no child shrinks under a loose constraint"):
    val b = new RenderBox
    b.layout(Constraints.loose(Size(200, 120)))
    assert(b.size == Size(0, 0))

  test("an explicit width/height is honoured and clamped to the parent"):
    val b = fixed(80, 40)
    b.layout(Constraints.loose(Size(200, 200)))
    assert(b.size == Size(80, 40))

  test("a box fires its resize handler only when its laid-out size changes"):
    val b    = new RenderBox
    var seen = List.empty[Size]
    b.handlers("resize") = s => seen = seen :+ s.asInstanceOf[Size]
    b.layout(Constraints.tight(Size(100, 50)))
    b.layout(Constraints.tight(Size(100, 50))) // unchanged — no second fire
    b.layout(Constraints.tight(Size(60, 50)))  // narrower — fires again
    assert(seen == List(Size(100, 50), Size(60, 50)))

  test("a box wraps its child plus padding under a loose constraint"):
    val b = new RenderBox
    b.padding = EdgeInsets.all(10)
    val inner = child(b, fixed(50, 30))
    b.layout(Constraints.loose(Size(500, 500)))
    assert(b.size == Size(70, 50))           // child + 10 on every side
    assert(inner.offset == Offset(10, 10))   // placed inside the padding

  // --- RenderConstrained (sizedBox) -----------------------------------------

  test("a sized box forces its child to the given size"):
    val s = new RenderConstrained
    s.width = Some(120)
    s.height = Some(60)
    val inner = child(s, new RenderBox) // a fill box with no intrinsic size
    s.layout(Constraints.loose(Size(500, 500)))
    assert(s.size == Size(120, 60))
    assert(inner.size == Size(120, 60))      // tight constraints reached the child

  test("a max-width box caps a wide child but leaves a narrow one alone"):
    // A child wider than the cap is clamped to it; a child narrower than the cap keeps its size.
    val wide = new RenderConstrained
    wide.maxWidth = Some(200)
    val w = child(wide, fixed(500, 40))
    wide.layout(Constraints.loose(Size(1000, 1000)))
    assert(w.size == Size(200, 40))  // capped to the max
    assert(wide.size == Size(200, 40))

    val narrow = new RenderConstrained
    narrow.maxWidth = Some(200)
    val n = child(narrow, fixed(80, 40))
    narrow.layout(Constraints.loose(Size(1000, 1000)))
    assert(n.size == Size(80, 40))   // under the cap → unchanged
    assert(narrow.size == Size(80, 40))

  // --- RenderPadding ---------------------------------------------------------

  test("padding insets its child and grows by the insets"):
    val p = new RenderPadding
    p.padding = EdgeInsets(top = 4, right = 8, bottom = 12, left = 16)
    val inner = child(p, fixed(100, 50))
    p.layout(Constraints.loose(Size(500, 500)))
    assert(inner.offset == Offset(16, 4))
    assert(p.size == Size(100 + 8 + 16, 50 + 4 + 12))

  // --- RenderStack / align / center -----------------------------------------

  test("a stack fills a bounded constraint and positions children by alignment"):
    val s = new RenderStack(Alignment.center)
    val a = child(s, fixed(40, 40))
    s.layout(Constraints.tight(Size(200, 100)))
    assert(s.size == Size(200, 100))
    assert(a.offset == Offset((200 - 40) / 2, (100 - 40) / 2))

  test("stack alignment reaches every corner"):
    def place(al: Alignment): Offset =
      val s = new RenderStack(al)
      val a = child(s, fixed(20, 20))
      s.layout(Constraints.tight(Size(100, 100)))
      a.offset
    assert(place(Alignment.topLeft) == Offset(0, 0))
    assert(place(Alignment.bottomRight) == Offset(80, 80))
    assert(place(Alignment.topRight) == Offset(80, 0))

  // --- RenderFlex ------------------------------------------------------------

  test("a row lays inflexible children end to end with spacing"):
    val r = new RenderFlex(Axis.Horizontal)
    r.spacing = 8
    val a = child(r, fixed(50, 20))
    val b = child(r, fixed(50, 20))
    r.layout(Constraints.tight(Size(300, 100)))
    assert(a.offset == Offset(0, 0))
    assert(b.offset == Offset(58, 0))        // 50 + 8 spacing
    assert(r.size == Size(300, 100))

  test("a spacer eats the leftover main-axis space"):
    val r = new RenderFlex(Axis.Horizontal)
    val a  = child(r, fixed(50, 20))
    val sp = child(r, new RenderBox); sp.flex = 1 // a flexible gap
    val b  = child(r, fixed(50, 20))
    r.layout(Constraints.tight(Size(300, 100)))
    assert(sp.size.width == 200)             // 300 - 50 - 50
    assert(b.offset == Offset(250, 0))       // shoved to the right edge

  test("two flexible children split the free space by their flex weights"):
    val r = new RenderFlex(Axis.Horizontal)
    val a = child(r, new RenderBox); a.flex = 1
    val b = child(r, new RenderBox); b.flex = 3
    r.layout(Constraints.tight(Size(400, 100)))
    assert(a.size.width == 100)              // 1/4 of 400
    assert(b.size.width == 300)              // 3/4 of 400
    assert(b.offset == Offset(100, 0))

  test("mainAxisAlignment SpaceBetween pushes children to the ends"):
    val r = new RenderFlex(Axis.Horizontal)
    r.mainAxisAlignment = MainAxisAlignment.SpaceBetween
    val a = child(r, fixed(40, 20))
    val b = child(r, fixed(40, 20))
    r.layout(Constraints.tight(Size(200, 100)))
    assert(a.offset == Offset(0, 0))
    assert(b.offset == Offset(160, 0))       // right-aligned: 200 - 40

  test("crossAxisAlignment Center centres each child on the cross axis"):
    val r = new RenderFlex(Axis.Horizontal)
    r.crossAxisAlignment = CrossAxisAlignment.Center
    val a = child(r, fixed(40, 20))
    r.layout(Constraints.tight(Size(200, 100)))
    assert(a.offset == Offset(0, (100 - 20) / 2))

  test("crossAxisAlignment Stretch forces children to the cross extent"):
    val r = new RenderFlex(Axis.Horizontal)
    r.crossAxisAlignment = CrossAxisAlignment.Stretch
    val a = child(r, fixed(40, 20))
    r.layout(Constraints.tight(Size(200, 100)))
    assert(a.size.height == 100)             // stretched over the explicit 20

  test("a column with MainAxisSize.Min wraps its children"):
    val c = new RenderFlex(Axis.Vertical)
    c.mainAxisSize = MainAxisSize.Min
    c.spacing = 4
    val a = child(c, fixed(30, 50))
    val b = child(c, fixed(30, 50))
    c.layout(Constraints(0, 200, 0, 400)) // loose so Min can shrink
    assert(c.size.height == 50 + 4 + 50)
    assert(b.offset == Offset(0, 54))

  // --- paint + hit-test ------------------------------------------------------

  test("a box paints its background then its border"):
    val b = new RenderBox
    b.background = Color(255, 0, 0)
    b.border = Color(0, 0, 0)
    b.borderWidth = 2
    b.layout(Constraints.tight(Size(10, 10)))
    val canvas = new RecordingCanvas
    b.paint(canvas, Offset.zero)
    assert(canvas.commands.toList == List(
      RecordingCanvas.Command.FillRect(Rect(0, 0, 10, 10), Solid(Color(255, 0, 0))),
      RecordingCanvas.Command.StrokeRect(Rect(0, 0, 10, 10), Solid(Color(0, 0, 0)), 2),
    ))

  test("hit-testing a row returns the child under the point"):
    val r = new RenderFlex(Axis.Horizontal)
    val a = child(r, fixed(50, 100))
    val b = child(r, fixed(50, 100))
    r.layout(Constraints.tight(Size(300, 100)))
    assert(r.hitTest(Offset(60, 10), Offset.zero) eq b)
    assert(r.hitTest(Offset(10, 10), Offset.zero) eq a)

  // --- host wiring -----------------------------------------------------------

  test("the host config maps tags to render objects"):
    val h = new SuitHostConfig
    assert(h.createElement("row", null).isInstanceOf[RenderFlex])
    assert(h.createElement("row", null).asInstanceOf[RenderFlex].axis == Axis.Horizontal)
    assert(h.createElement("col", null).asInstanceOf[RenderFlex].axis == Axis.Vertical)
    assert(h.createElement("padding", null).isInstanceOf[RenderPadding])
    assert(h.createElement("sizedBox", null).isInstanceOf[RenderConstrained])
    assert(h.createElement("stack", null).isInstanceOf[RenderStack])
    assert(h.createElement("box", null).isInstanceOf[RenderBox])

  test("the host config sets typed properties and resets them on removal"):
    val h = new SuitHostConfig
    val b = h.createElement("box", null)
    val box = b.asInstanceOf[RenderBox]

    h.setProperty(b, "bg", Color(255, 0, 0))
    assert(box.background == Solid(Color(255, 0, 0)))
    h.setProperty(b, "width", 100.0)
    assert(box.width == Some(100.0))
    h.setProperty(b, "padding", EdgeInsets.all(4))
    assert(box.padding == EdgeInsets.all(4))
    h.setProperty(b, "flex", 3)
    assert(box.flex == 3)

    h.setProperty(b, "bg", null)
    assert(box.background == null)
    h.setProperty(b, "width", null)
    assert(box.width == None)

  test("the host config maps max-width/height onto a constrained box"):
    val h = new SuitHostConfig
    val c = h.createElement("sizedBox", null)
    val cb = c.asInstanceOf[RenderConstrained]
    h.setProperty(c, "maxWidth", 420.0)
    assert(cb.maxWidth == Some(420.0))
    h.setProperty(c, "maxHeight", 300.0)
    assert(cb.maxHeight == Some(300.0))
    h.setProperty(c, "maxWidth", null)
    assert(cb.maxWidth == None)

  test("the host config sets flex-layout enums"):
    val h = new SuitHostConfig
    val r = h.createElement("row", null)
    h.setProperty(r, "mainAxisAlignment", MainAxisAlignment.SpaceEvenly)
    h.setProperty(r, "crossAxisAlignment", CrossAxisAlignment.Stretch)
    h.setProperty(r, "spacing", 6.0)
    val flex = r.asInstanceOf[RenderFlex]
    assert(flex.mainAxisAlignment == MainAxisAlignment.SpaceEvenly)
    assert(flex.crossAxisAlignment == CrossAxisAlignment.Stretch)
    assert(flex.spacing == 6.0)
