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

  // --- host wiring ---------------------------------------------------------

  test("the host config maps the scroll tag and its axis"):
    val h    = new SuitHostConfig
    val node = h.createElement("scroll", null)
    assert(node.isInstanceOf[RenderScroll])
    h.setProperty(node, "axis", Axis.Horizontal)
    assert(node.asInstanceOf[RenderScroll].axis == Axis.Horizontal)
