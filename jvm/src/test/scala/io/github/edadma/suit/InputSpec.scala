package io.github.edadma.suit

import org.scalatest.funsuite.AnyFunSuite
import scala.collection.mutable

// Headless tests for pointer routing. The router needs only `hitTest` and the handler
// maps, both pure, so the press→release→click and hover enter/leave behaviour is
// verified on the JVM against a laid-out tree with no device.
class InputSpec extends AnyFunSuite:

  private def fixed(w: Double, h: Double): RenderBox =
    val b = new RenderBox
    b.width = Some(w)
    b.height = Some(h)
    b

  /** Build a row of two 50×100 boxes laid out in a 200×100 space: box A spans x 0–50,
    * box B spans x 50–100, and x ≥ 100 hits the row itself. Returns the row and the two
    * boxes, each fitted with handlers that append `"<name>:<label>"` to `log`. */
  private def tree(log: mutable.ArrayBuffer[String]): (RenderFlex, RenderBox, RenderBox) =
    val row = new RenderFlex(Axis.Horizontal)
    val a   = fixed(50, 100)
    val b   = fixed(50, 100)
    row.insertChild(a, null)
    row.insertChild(b, null)
    def wire(o: RenderObject, label: String): Unit =
      for ev <- Seq("mousedown", "mouseup", "click", "mousemove", "mouseenter", "mouseleave") do
        o.handlers(ev) = _ => log += s"$ev:$label"
    wire(a, "a")
    wire(b, "b")
    row.layout(Constraints.tight(Size(200, 100)))
    (row, a, b)

  test("a press then release on the same object is a click"):
    val log        = mutable.ArrayBuffer.empty[String]
    val (row, _, _) = tree(log)
    val router     = new PointerRouter(row)
    router.down(Offset(10, 10), 1)
    router.up(Offset(10, 10), 1)
    assert(log.toList == List("mousedown:a", "mouseup:a", "click:a"))

  test("a press and release on different objects is not a click"):
    val log        = mutable.ArrayBuffer.empty[String]
    val (row, _, _) = tree(log)
    val router     = new PointerRouter(row)
    router.down(Offset(10, 10), 1)  // press on a
    router.up(Offset(60, 10), 1)    // release on b
    assert(log.toList == List("mousedown:a", "mouseup:b"))

  test("the delivered event carries position and button"):
    val seen   = mutable.ArrayBuffer.empty[PointerEvent]
    val a      = fixed(50, 100)
    a.handlers("mousedown") = e => seen += e.asInstanceOf[PointerEvent]
    val row = new RenderFlex(Axis.Horizontal)
    row.insertChild(a, null)
    row.layout(Constraints.tight(Size(200, 100)))
    new PointerRouter(row).down(Offset(12, 34), 3)
    assert(seen.toList == List(PointerEvent(Offset(12, 34), 3)))

  test("moving across a boundary fires leave then enter then move"):
    val log        = mutable.ArrayBuffer.empty[String]
    val (row, _, _) = tree(log)
    val router     = new PointerRouter(row)
    router.move(Offset(10, 10)) // onto a
    router.move(Offset(60, 10)) // onto b
    assert(log.toList == List(
      "mouseenter:a", "mousemove:a",
      "mouseleave:a", "mouseenter:b", "mousemove:b",
    ))

  test("moving within one object does not re-fire enter/leave"):
    val log        = mutable.ArrayBuffer.empty[String]
    val (row, _, _) = tree(log)
    val router     = new PointerRouter(row)
    router.move(Offset(10, 10))
    router.move(Offset(20, 10)) // still on a
    assert(log.toList == List("mouseenter:a", "mousemove:a", "mousemove:a"))
