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
      t.style = TextStyle(size = 16, color = Color(0, 0, 0))
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
      val style = TextStyle(size = 18, color = Color(10, 20, 30))
      t.style = style
      t.layout(Constraints.loose(Size(200, 200)))
      val canvas = new RecordingCanvas
      t.paint(canvas, Offset(5, 7))
      assert(canvas.commands.toList == List(
        RecordingCanvas.Command.DrawText(Offset(5, 7), "hi", style),
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
    assert(t.style.size == 24.0)
    assert(t.style.color == Color(1, 2, 3))

  test("createText makes a default-styled text node and setText updates it"):
    val h = new SuitHostConfig
    val node = h.createText("bare")
    val t = node.asInstanceOf[RenderText]
    assert(t.text == "bare")
    assert(t.style == TextStyle.default)
    h.setText(node, "changed")
    assert(t.text == "changed")
