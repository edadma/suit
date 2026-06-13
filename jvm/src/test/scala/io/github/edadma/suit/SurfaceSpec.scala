package io.github.edadma.suit

import org.scalatest.funsuite.AnyFunSuite

// Headless tests for the application-owned surface widget and its repaint handle. Cairo (the real
// surface backend) is native-only, but RenderSurface, the SurfaceHandle binding, the DSL, and the
// Canvas.drawImage seam live in `shared`, so a lightweight RasterImage stub drives the sizing,
// the recorded blit, and the repaint plumbing off-device — the same way RasterSpec exercises the
// raster path.
class SurfaceSpec extends AnyFunSuite:

  private final class FakeImage(val width: Int, val height: Int) extends RasterImage

  test("a RenderSurface sizes to the surface's pixel size when given none of its own"):
    val s = new RenderSurface(new FakeImage(120, 80))
    s.layout(Constraints.loose(Size(500, 500)))
    assert(s.size == Size(120, 80))

  test("explicit width and height override the pixel size, clamped to the constraints"):
    val s = new RenderSurface(new FakeImage(120, 80))
    s.width = Some(200)
    s.height = Some(150)
    s.layout(Constraints.loose(Size(500, 500)))
    assert(s.size == Size(200, 150))
    s.width = Some(900) // wider than the constraint allows
    s.layout(Constraints.loose(Size(300, 300)))
    assert(s.size == Size(300, 150))

  test("a RenderSurface with no image collapses to nothing and paints nothing"):
    val s = new RenderSurface(null)
    s.layout(Constraints.loose(Size(200, 200)))
    assert(s.size == Size.zero)
    val c = new RecordingCanvas
    s.paint(c, Offset.zero)
    assert(c.commands.isEmpty)

  test("a RenderSurface blits one DrawImage of its surface over its laid-out rectangle"):
    val img = new FakeImage(120, 80)
    val s   = new RenderSurface(img)
    s.layout(Constraints.tight(Size(120, 80)))
    val c = new RecordingCanvas
    s.paint(c, Offset(5, 7))
    assert(c.commands.toList == List(RecordingCanvas.Command.DrawImage(img, Rect(5, 7, 120, 80))))

  test("a RenderSurface is a repaint boundary but not a live surface"):
    val s = new RenderSurface(new FakeImage(10, 10))
    assert(s.isRepaintBoundary)   // a redraw re-blits just its region
    assert(!s.isLiveSurface)      // but it does not re-rasterise every frame like a canvas

  test("a handle's repaint marks only the surface boundary and requests a frame"):
    val root = new RenderRoot(Size(200, 100))
    val box  = new RenderBox
    val surf = new RenderSurface(new FakeImage(50, 50))
    root.insertChild(box, null)
    root.insertChild(surf, null)
    val handle = new SurfaceHandle
    handle.target = surf
    root.clearRepaintFlags()
    root.dirty = false

    handle.repaint()
    assert(surf.needsRepaint) // the surface region will be re-blitted
    assert(!root.needsRepaint) // but the static scene is not re-rasterised
    assert(root.dirty)         // a frame is still requested
    assert(root.dirtyBoundaries == List(surf))

  test("a repaint before the widget mounts (or after it unmounts) is a harmless no-op"):
    val handle = new SurfaceHandle
    handle.repaint() // target is null — must not throw
    val surf = new RenderSurface(new FakeImage(10, 10))
    handle.target = surf
    handle.target = null
    handle.repaint() // detached again — still a no-op

  test("the host config maps the surface tag and its image, size, and handle props"):
    val h = new SuitHostConfig
    val n = h.createElement("surface", null)
    assert(n.isInstanceOf[RenderSurface])
    val img    = new FakeImage(64, 64)
    val handle = new SurfaceHandle
    h.setProperty(n, "image", img)
    h.setProperty(n, "width", 32.0)
    h.setProperty(n, "height", 32.0)
    h.setProperty(n, "handle", handle)
    val s = n.asInstanceOf[RenderSurface]
    assert(s.image == img)
    assert(s.width.contains(32.0) && s.height.contains(32.0))
    assert(s.handle == handle)
    assert(handle.target == s) // the handle points back at the render object, so repaint reaches it
    // dropping the size props restores the unset (pixel-sized) state
    h.setProperty(n, "width", null)
    assert(s.width.isEmpty)

  test("rebinding the handle prop detaches the previous handle"):
    val h      = new SuitHostConfig
    val n      = h.createElement("surface", null)
    val first  = new SurfaceHandle
    val second = new SurfaceHandle
    h.setProperty(n, "handle", first)
    h.setProperty(n, "handle", second)
    val s = n.asInstanceOf[RenderSurface]
    assert(s.handle == second)
    assert(second.target == s)
    assert(first.target == null) // the stale handle no longer drives the surface
