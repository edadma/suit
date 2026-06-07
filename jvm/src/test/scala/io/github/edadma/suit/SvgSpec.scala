package io.github.edadma.suit

import org.scalatest.funsuite.AnyFunSuite

// Headless tests for the SVG render object and its paint seam. librsvg (the real renderer) is
// native-only, but RenderSvg, the DSL, and the Canvas.drawSvg seam live in `shared`, so a
// lightweight SvgImage stub drives the sizing and the recorded paint call off-device — the same
// way the rest of the render tree is tested against a RecordingCanvas.
class SvgSpec extends AnyFunSuite:

  private final class FakeSvg(val sz: Option[Size]) extends SvgImage:
    def intrinsicSize: Option[Size] = sz

  test("a RenderSvg sizes to the document's intrinsic size when given none of its own"):
    val s = new RenderSvg(new FakeSvg(Some(Size(40, 24))))
    s.layout(Constraints.loose(Size(200, 200)))
    assert(s.size == Size(40, 24))

  test("explicit width and height override the intrinsic size, clamped to the constraints"):
    val s = new RenderSvg(new FakeSvg(Some(Size(40, 24))))
    s.width = Some(100)
    s.height = Some(50)
    s.layout(Constraints.loose(Size(200, 200)))
    assert(s.size == Size(100, 50))
    s.width = Some(500) // wider than the constraint allows
    s.layout(Constraints.loose(Size(200, 200)))
    assert(s.size == Size(200, 50))

  test("a RenderSvg with no intrinsic size and no explicit size collapses to nothing"):
    val s = new RenderSvg(new FakeSvg(None))
    s.layout(Constraints.loose(Size(200, 200)))
    assert(s.size == Size.zero)

  test("a RenderSvg paints one DrawSvg of its image over its laid-out rectangle"):
    val img = new FakeSvg(Some(Size(40, 24)))
    val s   = new RenderSvg(img)
    s.layout(Constraints.tight(Size(40, 24)))
    val c = new RecordingCanvas
    s.paint(c, Offset(5, 7))
    assert(c.commands.toList == List(RecordingCanvas.Command.DrawSvg(img, Rect(5, 7, 40, 24))))

  test("a RenderSvg with no image paints nothing"):
    val s = new RenderSvg(null)
    s.layout(Constraints.tight(Size(10, 10)))
    val c = new RecordingCanvas
    s.paint(c, Offset.zero)
    assert(c.commands.isEmpty)

  test("the host config maps the svg tag and its image and size props"):
    val h = new SuitHostConfig
    val n = h.createElement("svg", null)
    assert(n.isInstanceOf[RenderSvg])
    val img = new FakeSvg(None)
    h.setProperty(n, "image", img)
    h.setProperty(n, "width", 64.0)
    h.setProperty(n, "height", 64.0)
    val s = n.asInstanceOf[RenderSvg]
    assert(s.image == img)
    assert(s.width.contains(64.0) && s.height.contains(64.0))
    // dropping the size props restores the unset (intrinsic-sized) state
    h.setProperty(n, "width", null)
    assert(s.width.isEmpty)
