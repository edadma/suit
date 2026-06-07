package io.github.edadma.suit

import org.scalatest.funsuite.AnyFunSuite
import io.github.edadma.suit.dsl.*

// Headless tests for the styling foundation: the Paint model, box decoration (background,
// border, corner radius, shadow, opacity), and the props that carry them from the DSL to
// the render tree. Painting is checked against a RecordingCanvas — the styling system is
// pure data and the paint seam is captured, so all of it runs off-device on the JVM.
class StyleSpec extends AnyFunSuite:

  // --- Paint -----------------------------------------------------------------

  test("a Color flows into a Paint position as a Solid"):
    val p: Paint = Color(10, 20, 30)
    assert(p == Solid(Color(10, 20, 30)))

  test("Color.fade ramps only the alpha, keeping the colour's hue"):
    val c = Color(40, 48, 53)
    assert(Color.fade(c, 0.0) == c.withAlpha(0))    // fully transparent
    assert(Color.fade(c, 1.0) == c)                 // its given alpha
    val mid = Color.fade(c, 0.5)
    assert((mid.r, mid.g, mid.b) == (40, 48, 53))   // hue unchanged — no darkening
    assert(mid.a == 128)                            // alpha halved (round(255 * 0.5))

  test("BorderRadius helpers build uniform and edge radii"):
    assert(BorderRadius.all(6) == BorderRadius(6, 6, 6, 6))
    assert(BorderRadius.top(8) == BorderRadius(8, 8, 0, 0))
    assert(BorderRadius.bottom(8) == BorderRadius(0, 0, 8, 8))
    assert(BorderRadius.zero.isZero)
    assert(!BorderRadius.all(1).isZero)

  // --- RenderBox decoration painting -----------------------------------------

  test("a plain box paints a flat rect, no rounding"):
    val b = new RenderBox
    b.background = Color(1, 2, 3)
    b.layout(Constraints.tight(Size(10, 10)))
    val c = new RecordingCanvas
    b.paint(c, Offset.zero)
    assert(c.commands.toList == List(
      RecordingCanvas.Command.FillRect(Rect(0, 0, 10, 10), Solid(Color(1, 2, 3))),
    ))

  test("a rounded box paints a rounded rect and a rounded border"):
    val b = new RenderBox
    b.background = Color(1, 2, 3)
    b.border = Color(4, 5, 6)
    b.borderWidth = 2
    b.borderRadius = BorderRadius.all(5)
    b.layout(Constraints.tight(Size(20, 20)))
    val c = new RecordingCanvas
    b.paint(c, Offset.zero)
    assert(c.commands.toList == List(
      RecordingCanvas.Command.FillRoundedRect(Rect(0, 0, 20, 20), BorderRadius.all(5), Solid(Color(1, 2, 3))),
      RecordingCanvas.Command.StrokeRoundedRect(Rect(0, 0, 20, 20), BorderRadius.all(5), Solid(Color(4, 5, 6)), 2),
    ))

  test("a box with a shadow draws it before its background"):
    val b = new RenderBox
    b.background = Color(1, 2, 3)
    b.shadow = Shadow()
    b.layout(Constraints.tight(Size(10, 10)))
    val c = new RecordingCanvas
    b.paint(c, Offset.zero)
    assert(c.commands.toList == List(
      RecordingCanvas.Command.DrawShadow(Rect(0, 0, 10, 10), BorderRadius.zero, Shadow()),
      RecordingCanvas.Command.FillRect(Rect(0, 0, 10, 10), Solid(Color(1, 2, 3))),
    ))

  test("a clipping box brackets its children in a clip of its rounded rect"):
    val b = new RenderBox
    b.background = Color(1, 2, 3)
    b.borderRadius = BorderRadius.all(4)
    b.clipContent = true
    val child = new RenderBox
    child.background = Color(9, 9, 9)
    child.width = Some(10)
    child.height = Some(10)
    b.insertChild(child, null)
    b.layout(Constraints.tight(Size(10, 10)))
    val c = new RecordingCanvas
    b.paint(c, Offset.zero)
    assert(c.commands.toList == List(
      RecordingCanvas.Command.FillRoundedRect(Rect(0, 0, 10, 10), BorderRadius.all(4), Solid(Color(1, 2, 3))),
      RecordingCanvas.Command.PushClip(Rect(0, 0, 10, 10), BorderRadius.all(4)),
      RecordingCanvas.Command.FillRect(Rect(0, 0, 10, 10), Solid(Color(9, 9, 9))),
      RecordingCanvas.Command.PopClip,
    ))

  test("a translucent box brackets its painting in an opacity group"):
    val b = new RenderBox
    b.background = Color(1, 2, 3)
    b.opacity = 0.5
    b.layout(Constraints.tight(Size(10, 10)))
    val c = new RecordingCanvas
    b.paint(c, Offset.zero)
    assert(c.commands.toList == List(
      RecordingCanvas.Command.PushOpacity(0.5),
      RecordingCanvas.Command.FillRect(Rect(0, 0, 10, 10), Solid(Color(1, 2, 3))),
      RecordingCanvas.Command.PopOpacity,
    ))

  test("a gradient background is carried through to the fill"):
    val grad = LinearGradient(Seq(ColorStop(0, Color(0, 0, 0)), ColorStop(1, Color(255, 255, 255))))
    val b    = new RenderBox
    b.background = grad
    b.borderRadius = BorderRadius.all(4)
    b.layout(Constraints.tight(Size(10, 10)))
    val c = new RecordingCanvas
    b.paint(c, Offset.zero)
    assert(c.commands.toList == List(
      RecordingCanvas.Command.FillRoundedRect(Rect(0, 0, 10, 10), BorderRadius.all(4), grad),
    ))

  // --- host config: typed style props ----------------------------------------

  test("the host config sets the decoration props"):
    val h   = new SuitHostConfig
    val n   = h.createElement("box", null)
    val box = n.asInstanceOf[RenderBox]

    val grad = RadialGradient(Seq(ColorStop(0, Color(1, 1, 1))))
    h.setProperty(n, "bg", grad)
    assert(box.background == grad)
    h.setProperty(n, "borderRadius", BorderRadius.all(6))
    assert(box.borderRadius == BorderRadius.all(6))
    h.setProperty(n, "borderRadius", 9.0) // a bare number means a uniform radius
    assert(box.borderRadius == BorderRadius.all(9))
    h.setProperty(n, "shadow", Shadow())
    assert(box.shadow == Shadow())
    h.setProperty(n, "opacity", 0.25)
    assert(box.opacity == 0.25)

    h.setProperty(n, "shadow", null) // removal restores the unset state
    assert(box.shadow == null)

  // --- DSL props -------------------------------------------------------------

  private def props(v: VNode): Map[String, Prop] = v.asInstanceOf[VElement].props

  test("box() emits a uniform radius from `radius` and per-corner from `corners`"):
    assert(props(box(radius = 5)()).get("borderRadius").contains(PropValue(BorderRadius.all(5))))
    val corners = BorderRadius.top(8)
    assert(props(box(corners = corners)()).get("borderRadius").contains(PropValue(corners)))
    assert(!props(box()()).contains("borderRadius")) // default omits it

  test("box() emits shadow, opacity, and a gradient background"):
    val grad = LinearGradient(Seq(ColorStop(0, Color.black), ColorStop(1, Color.white)))
    val v    = box(bg = grad, shadow = Shadow(), opacity = 0.8)()
    assert(props(v).get("bg").contains(PropValue(grad)))
    assert(props(v).get("shadow").contains(PropValue(Shadow())))
    assert(props(v).get("opacity").contains(PropValue(0.8)))
    assert(!props(box()()).contains("opacity")) // opacity 1.0 is the default, omitted
