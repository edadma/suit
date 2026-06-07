package io.github.edadma.suit

import org.scalatest.funsuite.AnyFunSuite

// Headless tests for text layout and painting. Text rendering needs SDL, but text
// *measurement* sits behind the TextMeasurer seam, so a deterministic fake measurer
// lets the constraint pass and the paint pass run on the JVM with no font, no device.
class TextSpec extends AnyFunSuite:

  /** A measurer with predictable metrics: each glyph is 7px wide, the line is as tall
    * as the point size. Installing it makes RenderText sizes exactly computable. */
  private val fake: TextMeasurer = (text, style) => Size(text.length * 7.0, style.size)

  private def withFake(body: => Unit): Unit =
    val saved = TextMeasurer.installed
    TextMeasurer.installed = fake
    try body
    finally TextMeasurer.installed = saved

  test("a text node sizes itself from the installed measurer"):
    withFake:
      val t = new RenderText("hello") // 5 glyphs
      t.explicitSize = Some(16)
      t.layout(Constraints.loose(Size(500, 500)))
      assert(t.size == Size(35, 16)) // 5 * 7 wide, size tall

  test("a text node clamps its measured size into the constraints"):
    withFake:
      val t = new RenderText("a very long line that overflows")
      t.layout(Constraints(0, 40, 0, 100))
      assert(t.size.width == 40) // clamped to maxWidth

  test("a text node paints a single draw-text call at its origin"):
    withFake:
      val t = new RenderText("hi")
      t.explicitSize = Some(18)
      t.explicitColor = Some(Color(10, 20, 30))
      t.layout(Constraints.loose(Size(200, 200)))
      val canvas = new RecordingCanvas
      t.paint(canvas, Offset(5, 7))
      assert(canvas.commands.toList == List(
        RecordingCanvas.Command.DrawText(Offset(5, 7), "hi", TextStyle(size = 18, color = Color(10, 20, 30))),
      ))

  test("an empty text node paints nothing"):
    withFake:
      val t = new RenderText("")
      t.layout(Constraints.loose(Size(200, 200)))
      val canvas = new RecordingCanvas
      t.paint(canvas, Offset.zero)
      assert(canvas.commands.isEmpty)

  // --- host wiring -----------------------------------------------------------

  test("the host config maps the text tag and sets its content and style"):
    val h = new SuitHostConfig
    val node = h.createElement("text", null)
    assert(node.isInstanceOf[RenderText])
    val t = node.asInstanceOf[RenderText]

    h.setProperty(node, "content", "label")
    h.setProperty(node, "size", 24.0)
    h.setProperty(node, "color", Color(1, 2, 3))
    assert(t.text == "label")
    assert(t.resolvedStyle.size == 24.0)
    assert(t.resolvedStyle.color == Color(1, 2, 3))

  test("createText makes a default-styled text node and setText updates it"):
    val h = new SuitHostConfig
    val node = h.createText("bare")
    val t = node.asInstanceOf[RenderText]
    assert(t.text == "bare")
    assert(t.resolvedStyle == TextStyle.default) // no explicit, no ancestor → default
    h.setText(node, "changed")
    assert(t.text == "changed")

  // --- the text-style cascade ------------------------------------------------

  test("text inherits colour and size from the nearest ancestor that sets them"):
    val outer = new RenderBox
    outer.textAttrs = TextStyleAttrs(size = Some(20), color = Some(Color(9, 9, 9)))
    val middle = new RenderBox // sets nothing — transparent to the cascade
    val t      = new RenderText("x")
    outer.insertChild(middle, null)
    middle.insertChild(t, null)
    assert(t.resolvedStyle == TextStyle(20, Color(9, 9, 9)))

  test("a text node's own value overrides an inherited one, per property"):
    val outer = new RenderBox
    outer.textAttrs = TextStyleAttrs(size = Some(20), color = Some(Color(9, 9, 9)))
    val t = new RenderText("x")
    t.explicitColor = Some(Color(1, 2, 3)) // overrides colour; size still inherits
    outer.insertChild(t, null)
    assert(t.resolvedStyle == TextStyle(20, Color(1, 2, 3)))

  test("the nearer ancestor wins when both set the same property"):
    val outer = new RenderBox
    outer.textAttrs = TextStyleAttrs(color = Some(Color(9, 9, 9)))
    val inner = new RenderBox
    inner.textAttrs = TextStyleAttrs(color = Some(Color(1, 1, 1)))
    val t = new RenderText("x")
    outer.insertChild(inner, null)
    inner.insertChild(t, null)
    assert(t.resolvedStyle.color == Color(1, 1, 1))

  test("a box carries a text cascade set through the host config"):
    val h = new SuitHostConfig
    val b = h.createElement("box", null).asInstanceOf[RenderBox]
    h.setProperty(b, "textColor", Color(7, 8, 9))
    h.setProperty(b, "textSize", 13.0)
    assert(b.textAttrs == TextStyleAttrs(size = Some(13.0), color = Some(Color(7, 8, 9))))
