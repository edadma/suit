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

  // --- wheel chaining -------------------------------------------------------
  //
  // A wheel the inner view cannot use must reach the outer one. Without this a cursor
  // resting on a short list (or on any view already at its end) kills the page scroll
  // underneath it, which reads to a user as the whole window having frozen.

  /** An outer vertical scroll with room to move, wrapping an inner vertical scroll of
    * `innerContent` in a 200x200 viewport. Returns both. */
  private def nested(innerContent: Size): (RenderScroll, RenderScroll) =
    val outer = new RenderScroll(Axis.Vertical)
    val col   = new RenderBox
    col.width = Some(200)
    col.height = Some(2000) // outer has plenty of overflow
    val inner = new RenderScroll(Axis.Vertical)
    val box   = new RenderBox
    box.width = Some(innerContent.width)
    box.height = Some(innerContent.height)
    inner.insertChild(box, null)
    col.insertChild(inner, null)
    outer.insertChild(col, null)
    outer.layout(Constraints.tight(Size(200, 400)))
    inner.layout(Constraints.tight(Size(200, 200)))
    (outer, inner)

  test("an inner view with nothing to scroll passes the wheel to the view outside it"):
    val (outer, inner) = nested(Size(200, 100)) // content shorter than the inner viewport
    assert(inner.maxScroll == 0)
    new PointerRouter(outer).wheel(Offset(100, 100), 0, -3)
    assert(inner.scrollOffset == 0)
    assert(outer.scrollOffset == 3 * RenderScroll.WheelStep) // the outer view moved instead

  test("an inner view that can scroll claims the wheel and the outer view stays put"):
    val (outer, inner) = nested(Size(200, 1000))
    assert(inner.maxScroll == 800)
    new PointerRouter(outer).wheel(Offset(100, 100), 0, -3)
    assert(inner.scrollOffset == 3 * RenderScroll.WheelStep)
    assert(outer.scrollOffset == 0)

  test("a wheel at the inner view's end chains to the outer view"):
    val (outer, inner) = nested(Size(200, 1000))
    inner.scrollBy(10000) // pin the inner view to its bottom
    assert(inner.scrollOffset == 800)
    new PointerRouter(outer).wheel(Offset(100, 100), 0, -3) // still scrolling down
    assert(inner.scrollOffset == 800)                       // nothing left to give
    assert(outer.scrollOffset == 3 * RenderScroll.WheelStep)

  test("a vertical wheel over a horizontal-only view chains to the vertical view outside"):
    val outer = new RenderScroll(Axis.Vertical)
    val col   = new RenderBox
    col.width = Some(200)
    col.height = Some(2000)
    val inner = new RenderScroll(Axis.Horizontal)
    val box   = new RenderBox
    box.width = Some(1000)
    box.height = Some(200)
    inner.insertChild(box, null)
    col.insertChild(inner, null)
    outer.insertChild(col, null)
    outer.layout(Constraints.tight(Size(200, 400)))
    inner.layout(Constraints.tight(Size(200, 200)))
    new PointerRouter(outer).wheel(Offset(100, 100), 0, -3) // a purely vertical wheel
    assert(inner.scrollOffset == 0)
    assert(outer.scrollOffset == 3 * RenderScroll.WheelStep)

  test("a consumed event stops at the handler that claimed it"):
    val e = ScrollEvent(Offset.zero, 0, -3)
    assert(!e.consumed)
    e.consume()
    assert(e.consumed)

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

  test("a press on the visible scrollbar band hits the scroll view, not the content beneath"):
    val s   = new RenderScroll(Axis.Vertical)
    val box = new RenderBox
    box.width = Some(200.0)
    box.height = Some(1000.0)
    s.insertChild(box, null)
    s.scrollbar = true
    s.scrollbarThickness = 8.0
    s.layout(Constraints.tight(Size(200, 400)))
    // A point in the right-edge band (x >= 200-8) claims the scroll itself; the content beneath
    // would otherwise receive the press meant for the bar.
    assert(s.hitTest(Offset(197, 100), Offset.zero) eq s)
    // A point in the content area still hits the child.
    assert(s.hitTest(Offset(100, 100), Offset.zero) eq box)

  test("the scrollbar band claims nothing when the content fits (no bar shown)"):
    val s   = new RenderScroll(Axis.Vertical)
    val box = new RenderBox
    box.width = Some(200.0)
    box.height = Some(200.0) // fits the 400-tall viewport — nothing to scroll
    s.insertChild(box, null)
    s.scrollbar = true
    s.scrollbarThickness = 8.0
    s.layout(Constraints.tight(Size(200, 400)))
    assert(s.hitTest(Offset(197, 100), Offset.zero) eq box)

  // --- biaxial (both-ways) scrolling ----------------------------------------

  /** A both-ways scroll view: content larger than the viewport on each axis, scrolling on both. */
  private def biScroller(viewport: Size, content: Size): RenderScroll =
    val s   = new RenderScroll(Axis.Vertical)
    s.biaxial = true
    val box = new RenderBox
    box.width = Some(content.width)
    box.height = Some(content.height)
    box.background = Solid(Color(0, 255, 0))
    s.insertChild(box, null)
    s.layout(Constraints.tight(viewport))
    s

  test("a biaxial view keeps its content's natural size on both axes and overflows each"):
    val s = biScroller(Size(400, 400), Size(1000, 800))
    // the content is not squeezed to the viewport on either axis; both overflow independently
    assert(s.maxScrollX == 600) // 1000 - 400
    assert(s.maxScrollY == 400) // 800 - 400

  test("the wheel scrolls a biaxial view on both axes at once"):
    val s = biScroller(Size(400, 400), Size(1000, 1000))
    new PointerRouter(s).wheel(Offset(10, 10), -2, -3) // x and y deltas in one notch event
    assert(s.offsetX == 2 * RenderScroll.WheelStep)
    assert(s.offsetY == 3 * RenderScroll.WheelStep)

  test("a biaxial view clips and offsets its content on both axes"):
    val s = biScroller(Size(400, 400), Size(1000, 1000))
    s.scrollByX(50)
    s.scrollByY(30)
    val canvas = new RecordingCanvas
    s.paint(canvas, Offset.zero)
    assert(canvas.commands.toList.head == Command.PushClip(Rect(0, 0, 400, 400), BorderRadius.zero))
    assert(canvas.commands.toList.contains(Command.FillRect(Rect(-50, -30, 1000, 1000), Solid(Color(0, 255, 0)))))

  test("a biaxial view with the bar on paints a thumb for each overflowing axis"):
    val s = biScroller(Size(400, 400), Size(1000, 1000))
    s.scrollbar = true
    s.scrollbarThumb = Color(255, 255, 255)
    s.scrollbarTrack = Color(0, 0, 0)
    val canvas = new RecordingCanvas
    s.paint(canvas, Offset.zero)
    val bars = canvas.commands.toList.collect { case c: Command.FillRoundedRect => c }
    // two axes overflow → a track + thumb on each → four rounded-rect fills
    assert(bars.length == 4)
    // one bar pinned to the right edge (vertical), one to the bottom edge (horizontal)
    assert(bars.exists(b => b.rect.x == 400 - 8))
    assert(bars.exists(b => b.rect.y == 400 - 8))

  test("an axis that fits shows no bar even when the other overflows"):
    val s = biScroller(Size(400, 400), Size(1000, 300)) // wide content, but it fits vertically
    s.scrollbar = true
    s.scrollbarThumb = Color(255, 255, 255)
    val canvas = new RecordingCanvas
    s.paint(canvas, Offset.zero)
    val bars = canvas.commands.toList.collect { case c: Command.FillRoundedRect => c }
    assert(bars.length == 1)          // only the horizontal bar
    assert(bars.head.rect.y == 400 - 8) // along the bottom edge
    assert(s.maxScrollY == 0)

  // --- host wiring ---------------------------------------------------------

  test("the host config wires the biaxial flag"):
    val h = new SuitHostConfig
    val s = h.createElement("scroll", null).asInstanceOf[RenderScroll]
    h.setProperty(s, "biaxial", true)
    assert(s.biaxial)

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
