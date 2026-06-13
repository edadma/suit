package io.github.edadma.suit

import org.scalatest.funsuite.AnyFunSuite
import RecordingCanvas.Command

// Headless tests for clipping and the scrolling viewport. The clip seam is captured by
// RecordingCanvas, and RenderScroll's layout/scroll/paint logic is pure, so the whole of
// scrolling — viewport sizing, content overflow, clamping, wheel handling, and the clip
// that hides the overflow — is verified on the JVM with no device.
class ScrollSpec extends AnyFunSuite:

  /** A scroll view of `axis` whose content is a coloured box of `content` size, laid out
    * in a `viewport`-sized viewport. Returns the viewport and its content box. */
  private def scroller(viewport: Size, content: Size, axis: Axis = Axis.Vertical): (RenderScroll, RenderBox) =
    val s   = new RenderScroll(axis)
    val box = new RenderBox
    box.width = Some(content.width)
    box.height = Some(content.height)
    box.background = Solid(Color(255, 0, 0))
    s.insertChild(box, null)
    s.layout(Constraints.tight(viewport))
    (s, box)

  test("a scroll view fills the viewport it is given, not its content's extent"):
    val (s, _) = scroller(Size(200, 400), Size(200, 1000))
    assert(s.size == Size(200, 400))

  test("a vertical scroll view's max scroll is the content's overflow"):
    val (s, _) = scroller(Size(200, 400), Size(200, 1000))
    assert(s.maxScroll == 600) // 1000 content - 400 viewport

  test("content that fits leaves nothing to scroll"):
    val (s, _) = scroller(Size(200, 400), Size(200, 300))
    assert(s.maxScroll == 0)
    assert(!s.scrollBy(50)) // no room: the offset does not move
    assert(s.scrollOffset == 0)

  test("scrollBy moves the offset and clamps it to the ends"):
    val (s, _) = scroller(Size(200, 400), Size(200, 1000))
    assert(s.scrollBy(150))
    assert(s.scrollOffset == 150)
    assert(s.scrollBy(10000)) // past the end clamps to maxScroll
    assert(s.scrollOffset == 600)
    assert(s.scrollBy(-10000)) // past the start clamps to zero
    assert(s.scrollOffset == 0)

  test("a scroll view clips to the viewport and offsets its content by the scroll position"):
    val (s, _) = scroller(Size(200, 400), Size(200, 1000))
    s.scrollBy(30)
    val canvas = new RecordingCanvas
    s.paint(canvas, Offset.zero)
    assert(canvas.commands.toList == List(
      Command.PushClip(Rect(0, 0, 200, 400), BorderRadius.zero),
      Command.FillRect(Rect(0, -30, 200, 1000), Solid(Color(255, 0, 0))), // content shifted up by the scroll
      Command.PopClip,
    ))

  test("the wheel scrolls a vertical view by a notch step"):
    val (s, _) = scroller(Size(200, 400), Size(200, 1000))
    new PointerRouter(s).wheel(Offset(100, 200), 0, -3) // three notches down (SDL: negative is toward content)
    assert(s.scrollOffset == 3 * RenderScroll.WheelStep)

  test("a horizontal scroll view scrolls along x from the wheel's x delta"):
    val (s, _) = scroller(Size(400, 200), Size(1000, 200), Axis.Horizontal)
    assert(s.maxScroll == 600)
    new PointerRouter(s).wheel(Offset(200, 100), -2, 0)
    assert(s.scrollOffset == 2 * RenderScroll.WheelStep)

  // --- visible scrollbar ----------------------------------------------------

  /** A scroller with the visible bar switched on, as the `scrollArea` widget configures it. */
  private def barScroller(viewport: Size, content: Size, axis: Axis = Axis.Vertical): RenderScroll =
    val (s, _) = scroller(viewport, content, axis)
    s.scrollbar = true
    s.scrollbarThumb = Color(255, 255, 255)
    s.scrollbarThickness = 8.0
    s

  test("the thumb is shorter than the viewport in proportion to the visible fraction"):
    val s = barScroller(Size(200, 400), Size(200, 1000))
    val r = s.scrollbarThumbRect.asInstanceOf[Rect]
    // viewport/content = 400/1000 → thumb ≈ 0.4 of the 400px track = 160px.
    assert(math.abs(r.height - 160.0) < 0.001)
    assert(r.height < s.size.height)
    assert(r.x == 200 - 8) // pinned to the right edge

  test("there is no thumb when the content fits the viewport"):
    val s = barScroller(Size(200, 400), Size(200, 300))
    assert(s.scrollbarThumbRect == null)

  test("the thumb sits at the top unscrolled and moves down as the view scrolls"):
    val s = barScroller(Size(200, 400), Size(200, 1000))
    assert(s.scrollbarThumbRect.asInstanceOf[Rect].y == 0.0)
    s.scrollBy(600) // to the very end
    val r = s.scrollbarThumbRect.asInstanceOf[Rect]
    assert(math.abs(r.y - (400 - r.height)) < 0.001) // flush to the bottom of the track

  test("a bar-off scroll view exposes no thumb even when it overflows"):
    val (s, _) = scroller(Size(200, 400), Size(200, 1000))
    assert(s.scrollbarThumbRect == null)

  test("dragging the thumb scrolls the content proportionally"):
    val s = barScroller(Size(200, 400), Size(200, 1000))
    val thumb = s.scrollbarThumbRect.asInstanceOf[Rect]
    // Press inside the thumb, then move the cursor down 60px along the 240px of free track.
    val press = Offset(thumb.x + 1, thumb.y + 1)
    s.handlers("mousedown")(PointerEvent(press, Offset.zero, s.size, 1))
    s.handlers("mousemove")(PointerEvent(Offset(press.x, press.y + 60), Offset.zero, s.size, 1))
    // 60px of a (400-160)=240px track maps to 60/240 of the 600px scroll range = 150px.
    assert(math.abs(s.scrollOffset - 150.0) < 0.001)

  test("a press off the thumb does not start a drag"):
    val s = barScroller(Size(200, 400), Size(200, 1000))
    s.scrollBy(0)
    // Press in the lower part of the track, well below the (top-anchored) thumb.
    s.handlers("mousedown")(PointerEvent(Offset(196, 380), Offset.zero, s.size, 1))
    s.handlers("mousemove")(PointerEvent(Offset(196, 300), Offset.zero, s.size, 1))
    assert(s.scrollOffset == 0.0) // unmoved — only a press on the thumb drags

  test("a horizontal bar pins to the bottom edge"):
    val s = barScroller(Size(400, 200), Size(1000, 200), Axis.Horizontal)
    val r = s.scrollbarThumbRect.asInstanceOf[Rect]
    assert(r.y == 200 - 8)           // along the bottom
    assert(math.abs(r.width - 160.0) < 0.001) // 400/1000 of the 400px track

  // --- host wiring ---------------------------------------------------------

  test("the host config maps the scroll tag and its axis"):
    val h    = new SuitHostConfig
    val node = h.createElement("scroll", null)
    assert(node.isInstanceOf[RenderScroll])
    h.setProperty(node, "axis", Axis.Horizontal)
    assert(node.asInstanceOf[RenderScroll].axis == Axis.Horizontal)

  test("the host config wires the scrollbar props"):
    val h = new SuitHostConfig
    val s = h.createElement("scroll", null).asInstanceOf[RenderScroll]
    h.setProperty(s, "scrollbar", true)
    h.setProperty(s, "scrollbarThumb", Color(10, 20, 30))
    h.setProperty(s, "scrollbarThickness", 12.0)
    assert(s.scrollbar)
    assert(s.scrollbarThumb == Color(10, 20, 30))
    assert(s.scrollbarThickness == 12.0)
