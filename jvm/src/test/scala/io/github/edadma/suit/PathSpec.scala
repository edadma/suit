package io.github.edadma.suit

import org.scalatest.funsuite.AnyFunSuite
import RecordingCanvas.Command

// Headless tests for the vector path model. Path is a plain value and the strokePath/fillPath
// calls are captured by RecordingCanvas, so the whole API is verified on the JVM with no window —
// the same way an app would test its own canvas drawing.
class PathSpec extends AnyFunSuite:

  test("polyline builds move-then-lines and closes by default"):
    val p = Path.polyline(Seq(Offset(0, 0), Offset(10, 0), Offset(10, 10)))
    assert(
      p.segments == Vector(
        PathSeg.MoveTo(0, 0),
        PathSeg.LineTo(10, 0),
        PathSeg.LineTo(10, 10),
        PathSeg.Close,
      ),
    )

  test("an open polyline omits the closing segment"):
    val p = Path.polyline(Seq(Offset(0, 0), Offset(10, 0)), closed = false)
    assert(p.segments == Vector(PathSeg.MoveTo(0, 0), PathSeg.LineTo(10, 0)))

  test("an empty point list yields the empty path"):
    assert(Path.polyline(Nil).isEmpty)

  test("the builder chains and arcs carry their direction"):
    val p = Path.builder().moveTo(5, 5).lineTo(15, 5).arc(10, 10, 4, 0.0, math.Pi, negative = true).close().build()
    assert(
      p.segments == Vector(
        PathSeg.MoveTo(5, 5),
        PathSeg.LineTo(15, 5),
        PathSeg.Arc(10, 10, 4, 0.0, math.Pi, true),
        PathSeg.Close,
      ),
    )

  test("bounds spans the line points and the arc's extent"):
    val p = Path.builder().moveTo(0, 0).lineTo(10, 2).arc(20, 5, 5, 0.0, math.Pi, false).build()
    // line points reach x∈[0,10] y∈[0,2]; the arc's circle spans x∈[15,25] y∈[0,10]
    assert(p.bounds == Rect(0, 0, 25, 10))

  test("the empty path has a zero bounding box"):
    assert(Path.empty.bounds == Rect(0, 0, 0, 0))

  test("strokePath and fillPath are recorded with their path and paint"):
    val rec   = new RecordingCanvas
    val path  = Path.polyline(Seq(Offset(0, 0), Offset(4, 0), Offset(2, 4)))
    val paint = Solid(Color(200, 210, 220))
    rec.strokePath(path, paint, 2.0, LineJoin.Round, LineCap.Butt)
    rec.fillPath(path, paint)
    assert(
      rec.commands.toList == List(
        Command.StrokePath(path, paint, 2.0, LineJoin.Round, LineCap.Butt),
        Command.FillPath(path, paint),
      ),
    )
