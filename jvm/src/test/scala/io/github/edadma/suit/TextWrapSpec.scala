package io.github.edadma.suit

import org.scalatest.funsuite.AnyFunSuite

// Headless tests for multi-line text: word wrapping, line capping, ellipsis, alignment,
// and explicit newlines. As with TextSpec these run on the JVM behind a deterministic
// `TextMeasurer`, so the line-breaking algorithm is verified with no font and no device.
class TextWrapSpec extends AnyFunSuite:

  /** Monospace metrics: every glyph (the ellipsis included) is 10px wide, the line is as
    * tall as the point size. Picked so wrap widths come out as round numbers. */
  private val fake: TextMeasurer = (text, style) => Size(text.length * 10.0, style.size)

  private def withFake(body: => Unit): Unit =
    val saved = TextMeasurer.installed
    TextMeasurer.installed = fake
    try body
    finally TextMeasurer.installed = saved

  private def mk(s: String): RenderText =
    val t = new RenderText(s)
    t.explicitSize = Some(16) // line height 16
    t

  /** The text of each painted line, in order. */
  private def paintedLines(t: RenderText): List[String] =
    val c = new RecordingCanvas
    t.paint(c, Offset.zero)
    c.commands.toList.collect { case RecordingCanvas.Command.DrawText(_, s, _) => s }

  test("default text stays single-line and clamps to the constraint"):
    withFake:
      val t = mk("aaa bbb ccc") // 11 glyphs → 110px
      t.layout(Constraints(0, 70, 0, 1000))
      assert(t.size == Size(70, 16))         // one line, width clamped
      assert(paintedLines(t) == List("aaa bbb ccc"))

  test("wraps greedily to the available width"):
    withFake:
      val t = mk("aaa bbb ccc")
      t.maxLines = 0                          // unlimited
      t.layout(Constraints(0, 70, 0, 1000))
      assert(paintedLines(t) == List("aaa bbb", "ccc"))
      assert(t.size == Size(70, 32))          // two lines, widest is "aaa bbb" = 70

  test("maxLines caps the line count"):
    withFake:
      val t = mk("aa bb cc dd ee ff")         // wraps to 3 lines at width 50
      t.maxLines = 2
      t.layout(Constraints(0, 50, 0, 1000))
      assert(paintedLines(t) == List("aa bb", "cc dd"))
      assert(t.size == Size(50, 32))

  test("ellipsis marks a capped tail on the last visible line"):
    withFake:
      val t = mk("aa bb cc dd ee ff")
      t.maxLines = 2
      t.overflow = TextOverflow.Ellipsis
      t.layout(Constraints(0, 50, 0, 1000))
      assert(paintedLines(t) == List("aa bb", "cc d…")) // "cc dd…" is 60px > 50, trim one

  test("ellipsis truncates a single over-wide line"):
    withFake:
      val t = mk("aaaaaaaa")                   // 80px
      t.overflow = TextOverflow.Ellipsis       // maxLines stays 1
      t.layout(Constraints(0, 50, 0, 1000))
      assert(paintedLines(t) == List("aaaa…"))  // 5 glyphs = 50px
      assert(t.size.width == 50)

  test("a word wider than the line is hard-broken"):
    withFake:
      val t = mk("aaaaaaaa")                   // 80px, no spaces
      t.maxLines = 0
      t.layout(Constraints(0, 30, 0, 1000))
      assert(paintedLines(t) == List("aaa", "aaa", "aa"))
      assert(t.size == Size(30, 48))

  test("alignment offsets each line within the block"):
    withFake:
      val t = mk("aaaa bb")                    // wraps to "aaaa" / "bb" at width 40
      t.maxLines = 0
      t.align = TextAlign.Right
      t.layout(Constraints(0, 40, 0, 1000))
      val c = new RecordingCanvas
      t.paint(c, Offset(5, 7))
      val origins = c.commands.toList.collect { case RecordingCanvas.Command.DrawText(o, s, _) => (s, o) }
      assert(origins == List(
        ("aaaa", Offset(5, 7)),   // 40px line fills the 40px block, dx 0
        ("bb", Offset(25, 23)),   // 20px line, dx = 40 - 20 = 20; y = 7 + 16
      ))

  test("explicit newlines break even with soft wrap off"):
    withFake:
      val t = mk("ab\ncd")
      t.maxLines = 0
      t.softWrap = false
      t.layout(Constraints(0, 1000, 0, 1000))
      assert(paintedLines(t) == List("ab", "cd"))
      assert(t.size == Size(20, 32))

  test("the wrap helper honours paragraph breaks and word wrapping together"):
    withFake:
      val out = RenderText.wrap("aaa bbb\nccc", TextStyle(size = 16), 70.0, fake)
      assert(out == Vector("aaa bbb", "ccc"))

  // --- host wiring -----------------------------------------------------------

  test("the host config maps the multi-line text props"):
    val h = new SuitHostConfig
    val node = h.createElement("text", null)
    val t = node.asInstanceOf[RenderText]
    h.setProperty(node, "align", TextAlign.Center)
    h.setProperty(node, "maxLines", 3)
    h.setProperty(node, "overflow", TextOverflow.Ellipsis)
    h.setProperty(node, "softWrap", false)
    assert(t.align == TextAlign.Center)
    assert(t.maxLines == 3)
    assert(t.overflow == TextOverflow.Ellipsis)
    assert(!t.softWrap)
    // removal restores the single-line defaults
    h.setProperty(node, "align", null)
    h.setProperty(node, "maxLines", null)
    h.setProperty(node, "overflow", null)
    h.setProperty(node, "softWrap", null)
    assert(t.align == TextAlign.Left)
    assert(t.maxLines == 1)
    assert(t.overflow == TextOverflow.Clip)
    assert(t.softWrap)
