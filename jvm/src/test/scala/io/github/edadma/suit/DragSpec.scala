package io.github.edadma.suit

import org.scalatest.funsuite.AnyFunSuite
import scala.collection.mutable

// Headless tests for drag-and-drop routing. suit synthesises DnD from the raw pointer stream, so the
// whole gesture — arm on a press over a draggable, start once past the threshold, `dragover` the target
// under the cursor, `drop` on release — is a pure function of the tree, the handler maps, and the drag
// payload, and is verified on the JVM the same way the rest of input routing is.
class DragSpec extends AnyFunSuite:

  private def fixed(w: Double, h: Double): RenderBox =
    val b = new RenderBox
    b.width = Some(w)
    b.height = Some(h)
    b

  /** A 200×100 row: a draggable source spanning x 0–50 carrying the payload "clip-1", and a drop target
    * spanning x 50–100. Each logs the drag events it receives (the target reporting the payload, and the
    * drop its local x), so a gesture reads out as a sequence. */
  private def tree(log: mutable.ArrayBuffer[String]): (RenderFlex, RenderBox, RenderBox) =
    val row = new RenderFlex(Axis.Horizontal)
    val src = fixed(50, 100); src.dragPayload = "clip-1"
    val tgt = fixed(50, 100)
    row.insertChild(src, null)
    row.insertChild(tgt, null)
    src.handlers("dragstart") = _ => log += "dragstart"
    src.handlers("dragend")   = _ => log += "dragend"
    src.handlers("click")     = _ => log += "click"
    tgt.handlers("dragover")  = e => log += s"dragover:${e.asInstanceOf[DragEvent].payload}"
    tgt.handlers("dragleave") = _ => log += "dragleave"
    tgt.handlers("drop")      = e =>
      val d = e.asInstanceOf[DragEvent]
      log += s"drop:${d.payload}@${d.localX.toInt}"
    row.layout(Constraints.tight(Size(200, 100)))
    (row, src, tgt)

  test("a press, a move past the threshold, and a release over a target is a drag-and-drop"):
    val log         = mutable.ArrayBuffer.empty[String]
    val (row, _, _) = tree(log)
    val router      = new PointerRouter(row)
    router.down(Offset(10, 10), 1) // press on the source — arms a drag
    router.move(Offset(70, 10))    // past the threshold and over the target — starts, then dragover
    router.up(Offset(70, 10), 1)   // drop on the target, at local x 20 (70 − 50)
    assert(log.toList == List("dragstart", "dragover:clip-1", "drop:clip-1@20", "dragend"))

  test("a plain click on a draggable still clicks, not drags"):
    val log         = mutable.ArrayBuffer.empty[String]
    val (row, _, _) = tree(log)
    val router      = new PointerRouter(row)
    router.down(Offset(10, 10), 1)
    router.up(Offset(10, 10), 1)
    assert(log.toList == List("click"))

  test("a press with a sub-threshold wiggle is still a click, not a drag"):
    val log         = mutable.ArrayBuffer.empty[String]
    val (row, _, _) = tree(log)
    val router      = new PointerRouter(row)
    router.down(Offset(10, 10), 1)
    router.move(Offset(13, 11)) // moved ~3px, under the 5px threshold
    router.up(Offset(13, 11), 1)
    assert(log.toList == List("click"))

  test("moving off a target during a drag fires dragleave, and a release off any target does not drop"):
    val log         = mutable.ArrayBuffer.empty[String]
    val (row, _, _) = tree(log)
    val router      = new PointerRouter(row)
    router.down(Offset(10, 10), 1)
    router.move(Offset(70, 10)) // over the target — dragstart + dragover
    router.move(Offset(30, 10)) // back over the (non-target) source — leaves the target
    router.up(Offset(30, 10), 1) // released over nothing droppable — no drop, but the drag ends
    assert(log.toList == List("dragstart", "dragover:clip-1", "dragleave", "dragend"))

  test("pressing a non-draggable object and moving is not a drag"):
    // Press the target (no payload) and move away: no drag is armed, so nothing drag-related fires and
    // no drop happens. (The source's click needs press and release to resolve to it, which they don't.)
    val log         = mutable.ArrayBuffer.empty[String]
    val (row, _, _) = tree(log)
    val router      = new PointerRouter(row)
    router.down(Offset(70, 10), 1)
    router.move(Offset(10, 10))
    router.up(Offset(10, 10), 1)
    assert(log.isEmpty)

  test("the payload is opaque — any value rides through to the drop"):
    val log = mutable.ArrayBuffer.empty[String]
    val src = fixed(50, 100); src.dragPayload = 42
    val tgt = fixed(50, 100)
    val row = new RenderFlex(Axis.Horizontal)
    row.insertChild(src, null); row.insertChild(tgt, null)
    tgt.handlers("drop") = e => log += s"drop:${e.asInstanceOf[DragEvent].payload}"
    row.layout(Constraints.tight(Size(200, 100)))
    val router = new PointerRouter(row)
    router.down(Offset(10, 10), 1)
    router.move(Offset(70, 10))
    router.up(Offset(70, 10), 1)
    assert(log.toList == List("drop:42"))
