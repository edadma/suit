package io.github.edadma.suit

import org.scalatest.funsuite.AnyFunSuite

// Headless tests for the raster-image render object and its paint seam. stb_image (the real
// decoder) and Cairo are native-only, but RenderImage, the DSL, and the Canvas.drawImage seam
// live in `shared`, so a lightweight RasterImage stub drives the sizing and the recorded paint
// call off-device — the same way SvgSpec exercises the SVG path.
class RasterSpec extends AnyFunSuite:

  private final class FakeImage(val width: Int, val height: Int) extends RasterImage

  test("a RenderImage sizes to the image's pixel size when given none of its own"):
    val s = new RenderImage(new FakeImage(120, 80))
    s.layout(Constraints.loose(Size(500, 500)))
    assert(s.size == Size(120, 80))

  test("explicit width and height override the pixel size, clamped to the constraints"):
    val s = new RenderImage(new FakeImage(120, 80))
    s.width = Some(200)
    s.height = Some(150)
    s.layout(Constraints.loose(Size(500, 500)))
    assert(s.size == Size(200, 150))
    s.width = Some(900) // wider than the constraint allows
    s.layout(Constraints.loose(Size(300, 300)))
    assert(s.size == Size(300, 150))

  test("a RenderImage with no image collapses to nothing"):
    val s = new RenderImage(null)
    s.layout(Constraints.loose(Size(200, 200)))
    assert(s.size == Size.zero)

  test("a RenderImage paints one DrawImage of its image over its laid-out rectangle"):
    val img = new FakeImage(120, 80)
    val s   = new RenderImage(img)
    s.layout(Constraints.tight(Size(120, 80)))
    val c = new RecordingCanvas
    s.paint(c, Offset(5, 7))
    assert(c.commands.toList == List(RecordingCanvas.Command.DrawImage(img, Rect(5, 7, 120, 80))))

  test("a RenderImage with no image paints nothing"):
    val s = new RenderImage(null)
    s.layout(Constraints.tight(Size(10, 10)))
    val c = new RecordingCanvas
    s.paint(c, Offset.zero)
    assert(c.commands.isEmpty)

  test("the host config maps the image tag and its image and size props"):
    val h = new SuitHostConfig
    val n = h.createElement("image", null)
    assert(n.isInstanceOf[RenderImage])
    val img = new FakeImage(64, 64)
    h.setProperty(n, "image", img)
    h.setProperty(n, "width", 32.0)
    h.setProperty(n, "height", 32.0)
    val s = n.asInstanceOf[RenderImage]
    assert(s.image == img)
    assert(s.width.contains(32.0) && s.height.contains(32.0))
    // dropping the size props restores the unset (pixel-sized) state
    h.setProperty(n, "width", null)
    assert(s.width.isEmpty)
