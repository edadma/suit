package io.github.edadma.suit

import org.scalatest.funsuite.AnyFunSuite
import io.github.edadma.vdom.{Host, Scheduler}
import io.github.edadma.suit.dsl.*
import RecordingCanvas.Command

// Headless tests for the canvas widget — the direct drawing surface. The Canvas seam (including
// the new translate bracket) is captured by RecordingCanvas, and RenderCanvas's sizing and paint
// bracketing are pure, so the surface, its local coordinate space, and the frame-driven repaint
// are all verified on the JVM with no window. This mirrors how the app would test its own draw
// routine: against a RecordingCanvas.
class CanvasSpec extends AnyFunSuite:

  test("a canvas fills the space it is given when given no size of its own"):
    val c = new RenderCanvas
    c.layout(Constraints.tight(Size(320, 200)))
    assert(c.size == Size(320, 200))

  test("explicit width and height override the fill, clamped to the constraints"):
    val c = new RenderCanvas
    c.width = Some(150)
    c.height = Some(100)
    c.layout(Constraints.loose(Size(500, 500)))
    assert(c.size == Size(150, 100))
    c.width = Some(900) // wider than the constraint allows
    c.layout(Constraints.loose(Size(300, 300)))
    assert(c.size == Size(300, 100))

  test("an unbounded axis with no explicit size collapses to nothing"):
    val c = new RenderCanvas
    c.layout(Constraints(0, Double.PositiveInfinity, 0, Double.PositiveInfinity))
    assert(c.size == Size.zero)

  test("paint clips to the bounds, translates to the top-left, then runs the painter"):
    val c = new RenderCanvas
    c.painter = (canvas, size) =>
      // The app draws in local coordinates: this rect sits at the canvas's own origin.
      canvas.fillRect(Rect(0, 0, size.width, size.height), Solid(Color(10, 20, 30)))
    c.layout(Constraints.tight(Size(120, 80)))
    val rec = new RecordingCanvas
    c.paint(rec, Offset(40, 25))
    assert(rec.commands.toList == List(
      Command.PushClip(Rect(40, 25, 120, 80), BorderRadius.zero),
      Command.PushTranslate(40, 25),
      Command.FillRect(Rect(0, 0, 120, 80), Solid(Color(10, 20, 30))),
      Command.PopTranslate,
      Command.PopClip,
    ))

  test("a canvas with the default (no-op) painter paints only the empty bracket"):
    val c = new RenderCanvas
    c.layout(Constraints.tight(Size(10, 10)))
    val rec = new RecordingCanvas
    c.paint(rec, Offset.zero)
    assert(rec.commands.toList == List(
      Command.PushClip(Rect(0, 0, 10, 10), BorderRadius.zero),
      Command.PushTranslate(0, 0),
      Command.PopTranslate,
      Command.PopClip,
    ))

  // --- host wiring ---------------------------------------------------------

  test("the host config maps the canvas tag and its draw and size props"):
    val h    = new SuitHostConfig
    val node = h.createElement("canvas", null)
    assert(node.isInstanceOf[RenderCanvas])
    var ran = false
    val draw: (Canvas, Size) => Unit = (_, _) => ran = true
    h.setProperty(node, "draw", draw)
    h.setProperty(node, "width", 64.0)
    h.setProperty(node, "height", 48.0)
    val c = node.asInstanceOf[RenderCanvas]
    assert(c.width.contains(64.0) && c.height.contains(48.0))
    c.painter(new RecordingCanvas, Size.zero)
    assert(ran)
    // dropping the draw prop restores the no-op painter (paints nothing)
    h.setProperty(node, "draw", null)
    val rec = new RecordingCanvas
    c.painter(rec, Size(5, 5))
    assert(rec.commands.isEmpty)

  // --- the frame-driven repaint --------------------------------------------

  test("useFrame ticks once per pump and requests a repaint each frame"):
    Host.config = new SuitHostConfig
    var t     = 0.0
    val clock = new FrameClock(() => t)
    clock.install()
    val root = new RenderRoot(Size(50, 50))
    Repaint.request = () => root.dirty = true // the runtime installs the equivalent

    // The canvas draws from a ref the frame callback advances; the closure captures the ref
    // (stable identity) and never changes, so only the repaint request brings the new value
    // on screen — the very case prop-diffing would miss.
    var drawn = -1
    val probe = view {
      val frame = useRef(0)
      useFrame(_ => frame.current += 1)
      canvas(width = 10, height = 10)((_, _) => drawn = frame.current)
    }
    createRoot(root).render(probe())
    Scheduler.flushSync() // commit the mount and run the effect that arms the frame loop
    root.dirty = false    // clear the startup-dirty flag so each frame's dirtying is observable

    clock.pump()
    Scheduler.flushSync()
    assert(root.dirty)
    root.layout(Constraints.tight(root.windowSize)) // a repaint lays out then paints
    root.paint(new RecordingCanvas, Offset.zero)
    assert(drawn == 1) // the painter ran and saw the advanced value

    root.dirty = false
    t = 16
    clock.pump()
    Scheduler.flushSync()
    assert(root.dirty)
    root.paint(new RecordingCanvas, Offset.zero)
    assert(drawn == 2)

  test("useFrame stops ticking once its component unmounts"):
    Host.config = new SuitHostConfig
    var t     = 0.0
    val clock = new FrameClock(() => t)
    clock.install()
    Repaint.request = () => ()

    var ticks = 0
    var setShown: Boolean => Unit = _ => ()
    val animator = view {
      useFrame(_ => ticks += 1)
      VEmpty
    }
    val probe = view {
      val (shown, set, _) = useState(true)
      setShown = set
      if shown then animator() else VEmpty
    }
    val root = new RenderRoot(Size(10, 10))
    createRoot(root).render(probe())
    Scheduler.flushSync()

    clock.pump(); Scheduler.flushSync()
    assert(ticks == 1)

    setShown(false) // unmount the animator
    Scheduler.flushSync()
    clock.pump(); Scheduler.flushSync()
    assert(ticks == 1)    // the cancelled loop no longer fires
    assert(!clock.active) // and nothing remains scheduled
