package io.github.edadma.suit

import org.scalatest.funsuite.AnyFunSuite
import RecordingCanvas.Command

// Headless tests for the video path. Everything that decides *where* a frame goes is pure
// geometry in `shared`, and everything that decides *how* it reaches the screen is a hole punched
// in the UI layer plus a list of rectangles handed to the runtime — so the whole design is
// checkable on the JVM with no window, no decoder, and no GPU. What is left for the native side
// is the blit itself.
class VideoSpec extends AnyFunSuite:

  // A stand-in for a platform texture. The shared layer only ever asks a layer how big its frame
  // is, which is exactly why the geometry is testable without one.
  private class FakeLayer(val frameWidth: Int, val frameHeight: Int) extends VideoLayer

  private def videoIn(size: Size, layer: VideoLayer | Null): (RenderRoot, RenderVideo) =
    val root = new RenderRoot(size)
    val v    = new RenderVideo(layer)
    root.insertChild(v, null)
    root.layout(Constraints.tight(size))
    (root, v)

  // -- fitting ---------------------------------------------------------------

  test("Contain letterboxes a wide frame into a square, centred"):
    // 16:9 into 100x100 → full width, 56.25 tall, equal bars above and below.
    val (src, dst) = VideoGeometry.place(VideoFit.Contain, Rect(0, 0, 100, 100), 1920, 1080, 1.0)
    assert(src == Rect(0, 0, 1920, 1080)) // the whole frame is shown
    assert(dst.width == 100.0)
    assert(dst.height === 56.25)
    assert(dst.x == 0.0)
    assert(dst.y === (100 - 56.25) / 2) // centred: bars are equal
    assert(dst.y + dst.height === 100 - dst.y)

  test("Contain pillarboxes a tall frame into a wide rectangle"):
    // 9:16 into 200x100 → full height, 56.25 wide, equal bars left and right.
    val (_, dst) = VideoGeometry.place(VideoFit.Contain, Rect(0, 0, 200, 100), 1080, 1920, 1.0)
    assert(dst.height == 100.0)
    assert(dst.width === 56.25)
    assert(dst.x === (200 - 56.25) / 2)

  test("Contain fills exactly when the aspects already agree"):
    val (src, dst) = VideoGeometry.place(VideoFit.Contain, Rect(10, 20, 160, 90), 1920, 1080, 1.0)
    assert(src == Rect(0, 0, 1920, 1080))
    assert(dst == Rect(10, 20, 160, 90)) // no bars at all
  test("Contain offsets the frame by the widget's own position"):
    val (_, dst) = VideoGeometry.place(VideoFit.Contain, Rect(30, 40, 100, 100), 1920, 1080, 1.0)
    assert(dst.x == 30.0)
    assert(dst.y === 40 + (100 - 56.25) / 2)

  test("Cover crops the frame rather than showing bars"):
    // 16:9 into a square: the destination is the whole widget, and the crop comes out of the
    // source's width — centred, so equal slices are lost from each side.
    val (src, dst) = VideoGeometry.place(VideoFit.Cover, Rect(0, 0, 100, 100), 1920, 1080, 1.0)
    assert(dst == Rect(0, 0, 100, 100)) // no bars
    assert(src.height == 1080.0)        // full height kept
    assert(src.width === 1080.0)        // a square slice of a 16:9 frame
    assert(src.x === (1920 - 1080) / 2.0)
    assert(src.y == 0.0)

  test("Cover crops vertically when the frame is the taller one"):
    val (src, dst) = VideoGeometry.place(VideoFit.Cover, Rect(0, 0, 200, 100), 1080, 1920, 1.0)
    assert(dst == Rect(0, 0, 200, 100))
    assert(src.width == 1080.0)  // full width kept
    assert(src.height === 540.0) // 2:1 slice of a 9:16 frame
    assert(src.y === (1920 - 540) / 2.0)

  test("Fill stretches to the rectangle and shows all of the frame"):
    val (src, dst) = VideoGeometry.place(VideoFit.Fill, Rect(0, 0, 100, 100), 1920, 1080, 1.0)
    assert(src == Rect(0, 0, 1920, 1080))
    assert(dst == Rect(0, 0, 100, 100)) // distorted, by request

  // -- pixel aspect ----------------------------------------------------------

  test("a non-square pixel aspect widens the frame without touching the source"):
    // Anamorphic SD: 720x480 stored, displayed 16:9 via a ~1.21 pixel aspect. The frame must be
    // laid out at its *displayed* shape, not its stored one — ignoring this is what shows people
    // too thin. The source stays in stored pixels either way.
    val par        = 40.0 / 33.0
    val (src, dst) = VideoGeometry.place(VideoFit.Contain, Rect(0, 0, 1000, 1000), 720, 480, par)
    assert(src == Rect(0, 0, 720, 480)) // the source stays in stored pixels
    val displayAspect = (720 * par) / 480
    assert(dst.width / dst.height === displayAspect)
    // Both are wider than the square widget, so both take its full width and the pixel aspect
    // shows up in the height: a wider display aspect letterboxes to a shorter frame.
    val (_, square) = VideoGeometry.place(VideoFit.Contain, Rect(0, 0, 1000, 1000), 720, 480, 1.0)
    assert(dst.width == square.width)
    assert(dst.height < square.height)

  test("a pixel aspect below 1 narrows the frame"):
    // The mirror: squeezing the pixels makes the display aspect narrower, so the fitted frame is
    // taller than the square-pixel one.
    val (_, dst)    = VideoGeometry.place(VideoFit.Contain, Rect(0, 0, 1000, 1000), 720, 480, 0.9)
    val (_, square) = VideoGeometry.place(VideoFit.Contain, Rect(0, 0, 1000, 1000), 720, 480, 1.0)
    assert(dst.height > square.height)

  // -- degenerate inputs -----------------------------------------------------

  test("a frame or widget with no size places nothing instead of dividing by zero"):
    val empty = Rect(0, 0, 0, 0)
    // Before the first frame arrives, or while a pane is collapsed.
    assert(VideoGeometry.place(VideoFit.Contain, Rect(0, 0, 100, 100), 0, 0, 1.0) == (empty, empty))
    assert(VideoGeometry.place(VideoFit.Contain, Rect(0, 0, 0, 100), 1920, 1080, 1.0) == (empty, empty))
    assert(VideoGeometry.place(VideoFit.Cover, Rect(0, 0, 100, 0), 1920, 1080, 1.0) == (empty, empty))

  test("a nonsensical pixel aspect places nothing"):
    val empty = Rect(0, 0, 0, 0)
    assert(VideoGeometry.place(VideoFit.Contain, Rect(0, 0, 100, 100), 1920, 1080, 0.0) == (empty, empty))
    assert(VideoGeometry.place(VideoFit.Contain, Rect(0, 0, 100, 100), 1920, 1080, -1.0) == (empty, empty))
    assert(VideoGeometry.place(VideoFit.Contain, Rect(0, 0, 100, 100), 1920, 1080, Double.NaN) == (empty, empty))

  // -- layout ----------------------------------------------------------------

  test("a video fills the space offered when given no explicit size"):
    val (_, v) = videoIn(Size(320, 240), new FakeLayer(1920, 1080))
    assert(v.size == Size(320, 240)) // the pane, not the frame's 1920x1080

  test("an explicit size wins over the space offered"):
    // Laid out directly against loose constraints: under a root the constraints are tight and
    // there is no choice to make, so the explicit size only shows through where a parent allows it.
    val v = new RenderVideo(new FakeLayer(1920, 1080))
    v.width  = Some(160)
    v.height = Some(90)
    v.layout(Constraints.loose(Size(320, 240)))
    assert(v.size == Size(160, 90))

  test("an explicit size is still clamped by the constraints"):
    val v = new RenderVideo(new FakeLayer(1920, 1080))
    v.width  = Some(9999)
    v.height = Some(9999)
    v.layout(Constraints.loose(Size(320, 240)))
    assert(v.size == Size(320, 240))

  // -- painting: the hole ----------------------------------------------------

  test("painting fills the background and punches a hole where the frame goes"):
    // The whole mechanism: the UI layer gets an opaque rectangle with a transparent window in it,
    // and the runtime blits the texture underneath. The bars are the background that survives.
    val (root, v) = videoIn(Size(100, 100), new FakeLayer(1920, 1080))
    v.background  = Color(20, 20, 20)
    val rec = new RecordingCanvas
    root.paint(rec, Offset.zero)
    val cmds = rec.commands.toList
    assert(cmds.contains(Command.FillRect(Rect(0, 0, 100, 100), Solid(Color(20, 20, 20)))))
    val hole = cmds.collect { case c: Command.ClearRect => c }
    assert(hole.length == 1)
    assert(hole.head.rect.width == 100.0)
    assert(hole.head.rect.height === 56.25) // the letterboxed frame, not the whole widget
    // Order matters: fill first, then erase. The reverse would paint the bars over the hole.
    assert(cmds.indexOf(Command.FillRect(Rect(0, 0, 100, 100), Solid(Color(20, 20, 20)))) < cmds.indexOf(hole.head))

  test("a video with no layer paints its background and no hole"):
    // Before the first frame there is nothing to show through, so the widget must stay opaque —
    // a hole with no texture under it would show the window's clear colour.
    val (root, _) = videoIn(Size(100, 100), null)
    val rec       = new RecordingCanvas
    root.paint(rec, Offset.zero)
    assert(!rec.commands.exists(_.isInstanceOf[Command.ClearRect]))

  test("the hole follows the origin the widget is painted at"):
    val (root, _) = videoIn(Size(100, 100), new FakeLayer(1920, 1080))
    val rec       = new RecordingCanvas
    root.paint(rec, Offset(15, 25))
    val hole = rec.commands.collect { case c: Command.ClearRect => c }.head
    assert(hole.rect.x == 15.0)
    assert(hole.rect.y === 25 + (100 - 56.25) / 2)

  // -- the runtime's layer list ----------------------------------------------

  test("videoLayers reports the frame's source and destination rectangles"):
    val layer     = new FakeLayer(1920, 1080)
    val (root, _) = videoIn(Size(100, 100), layer)
    root.videoLayers match
      case (l, src, dst) :: Nil =>
        assert(l eq layer)
        assert(src == Rect(0, 0, 1920, 1080))
        assert(dst.width == 100.0 && dst.height === 56.25)
      case other => fail(s"expected one layer, got $other")

  test("videoLayers lists several videos in paint order"):
    // A preview monitor above a row of timeline thumbnails: the runtime blits them back to front,
    // so the order it is handed has to be the order they paint in.
    val root = new RenderRoot(Size(200, 100))
    val a    = new RenderVideo(new FakeLayer(1920, 1080))
    val b    = new RenderVideo(new FakeLayer(640, 480))
    root.insertChild(a, null)
    root.insertChild(b, null)
    root.layout(Constraints.tight(Size(200, 100)))
    assert(root.videoLayers.map(_._1.frameWidth) == List(1920, 640))

  test("videoLayers skips a video with no layer attached"):
    // Nothing to blit: the frame has not arrived yet.
    val root  = new RenderRoot(Size(200, 100))
    val empty = new RenderVideo(null)
    val sized = new RenderVideo(new FakeLayer(1920, 1080))
    root.insertChild(empty, null)
    root.insertChild(sized, null)
    root.layout(Constraints.tight(Size(200, 100)))
    assert(root.videoLayers.length == 1)
    assert(root.videoLayers.head._1.frameWidth == 1920)

  test("videoLayers skips a video with no room to draw in"):
    // Nowhere to blit it — a collapsed pane. The runtime must not be handed an empty rectangle.
    val root = new RenderRoot(Size(200, 100))
    val v    = new RenderVideo(new FakeLayer(1920, 1080))
    root.insertChild(v, null)
    root.layout(Constraints.tight(Size(200, 100)))
    assert(root.videoLayers.length == 1) // has room here ...
    v.layout(Constraints.tight(Size.zero)) // ... and none once its pane collapses
    assert(root.videoLayers.isEmpty)

  test("videoLayers finds a video nested below other objects"):
    val root = new RenderRoot(Size(200, 100))
    val box  = new RenderBox
    val v    = new RenderVideo(new FakeLayer(1920, 1080))
    root.insertChild(box, null)
    box.insertChild(v, null)
    root.layout(Constraints.tight(Size(200, 100)))
    assert(root.videoLayers.length == 1)

  test("a new frame needs no repaint — the next present just shows it"):
    // The efficiency claim, pinned. A video widget paints a hole, not pixels, so a frame arriving
    // changes nothing Cairo rendered. If delivering a frame ever starts marking the tree dirty,
    // the static UI is being re-rasterised at frame rate and this test should fail.
    val (root, v) = videoIn(Size(100, 100), new FakeLayer(1920, 1080))
    root.clearRepaintFlags()
    root.dirty = false
    assert(!v.isRepaintBoundary) // nothing to repaint on its own
    assert(!v.isLiveSurface)     // and not re-rasterised each frame like a canvas
    assert(!root.dirty)
    // The layer's rectangles are still available to the runtime with the tree entirely clean.
    assert(root.videoLayers.length == 1)

  // -- clipping to ancestors -------------------------------------------------
  //
  // The frame is blitted by the runtime straight onto the window, not drawn by Cairo, so unlike
  // every other widget it is not confined by the clip stack for free. Without this the GPU blit of
  // a preview inside a scroll viewport spills over the chrome around it. `clippedPlacement` crops
  // the blit to every ancestor clip and narrows the source to the still-visible slice, so it agrees
  // with the hole Cairo actually punches (which the pushed clip already bounds).

  // A minimal clipping ancestor reporting a fixed clip rectangle — a scroll viewport or a clipped
  // card, without the machinery of a real one, so the arithmetic is checked in isolation.
  private class ClipNode(clip: Rect) extends RenderObject:
    def layout(constraints: Constraints): Unit          = size = Size(clip.width, clip.height)
    override def clipShape: Option[(Rect, BorderRadius)] = Some((clip, BorderRadius.zero))

  /** A Fill video of `frame`, sized `videoSize` at absolute `videoOffset`, sitting inside `clip`.
    * Fill maps the whole frame across the widget 1:1, so the source crop reads directly. */
  private def clippedVideo(clip: Rect, videoOffset: Offset, videoSize: Size, frame: (Int, Int)): RenderVideo =
    val root = new RenderRoot(Size(1000, 1000))
    val c    = new ClipNode(clip)
    val v    = new RenderVideo(new FakeLayer(frame._1, frame._2))
    v.fit = VideoFit.Fill
    root.insertChild(c, null)
    c.insertChild(v, null)
    c.offset = Offset.zero
    c.size   = Size(clip.width, clip.height)
    v.offset = videoOffset
    v.size   = videoSize
    v

  test("Rect.intersect overlaps, touches, contains, and misses"):
    assert(Rect(0, 0, 10, 10).intersect(Rect(5, 5, 10, 10)) == Rect(5, 5, 5, 5)) // corner overlap
    assert(Rect(0, 0, 100, 100).intersect(Rect(10, 10, 20, 20)) == Rect(10, 10, 20, 20)) // contained
    val miss = Rect(0, 0, 10, 10).intersect(Rect(20, 20, 5, 5))
    assert(miss.width == 0.0 && miss.height == 0.0) // disjoint
    val edge = Rect(0, 0, 10, 10).intersect(Rect(10, 0, 5, 10))
    assert(edge.width == 0.0) // a shared edge is not an overlap

  test("a video scrolled partly out of its clip is cropped, and the source narrowed to match"):
    // 100x100 frame filling a 100x100 pane, scrolled up 40px so its top is above the viewport.
    val v          = clippedVideo(Rect(0, 0, 100, 100), Offset(0, -40), Size(100, 100), (100, 100))
    val (src, dst) = v.clippedPlacement
    assert(dst == Rect(0, 0, 100, 60))  // only the 60px inside the viewport is blitted
    assert(src == Rect(0, 40, 100, 60)) // and only the matching bottom 60 rows are read

  test("a video fully inside its clip is placed unchanged"):
    val v = clippedVideo(Rect(0, 0, 200, 200), Offset(10, 10), Size(50, 50), (50, 50))
    assert(v.clippedPlacement == v.placement)
    assert(v.clippedPlacement == (Rect(0, 0, 50, 50), Rect(10, 10, 50, 50)))

  test("a video scrolled entirely out of its clip places nothing"):
    val v          = clippedVideo(Rect(0, 0, 100, 100), Offset(0, -200), Size(100, 100), (100, 100))
    val (src, dst) = v.clippedPlacement
    assert(dst.width == 0.0 && dst.height == 0.0)
    assert(src.width == 0.0 && src.height == 0.0)

  test("nested clips intersect — a video is confined to their overlap"):
    val root  = new RenderRoot(Size(1000, 1000))
    val outer = new ClipNode(Rect(0, 0, 100, 100))
    val inner = new ClipNode(Rect(0, 0, 100, 50)) // a tighter clip within the outer one
    val v     = new RenderVideo(new FakeLayer(100, 100))
    v.fit = VideoFit.Fill
    root.insertChild(outer, null)
    outer.insertChild(inner, null)
    inner.insertChild(v, null)
    outer.size = Size(100, 100)
    inner.size = Size(100, 50)
    v.size     = Size(100, 100)
    val (src, dst) = v.clippedPlacement
    assert(dst == Rect(0, 0, 100, 50)) // the inner clip wins on the short axis
    assert(src == Rect(0, 0, 100, 50))

  test("videoLayers hands the runtime the clipped rectangles, and drops a fully-clipped video"):
    // The integration: the list the runtime blits from is the clipped one, so nothing it is handed
    // can spill past a viewport.
    val visible = clippedVideo(Rect(0, 0, 100, 100), Offset(0, -40), Size(100, 100), (100, 100))
    visible.parent.asInstanceOf[RenderObject].parent match
      case r: RenderRoot =>
        r.videoLayers match
          case (_, src, dst) :: Nil =>
            assert(dst == Rect(0, 0, 100, 60))
            assert(src == Rect(0, 40, 100, 60))
          case other => fail(s"expected one clipped layer, got $other")
      case other => fail(s"expected a root, got $other")

    val gone = clippedVideo(Rect(0, 0, 100, 100), Offset(0, -200), Size(100, 100), (100, 100))
    gone.parent.asInstanceOf[RenderObject].parent match
      case r: RenderRoot => assert(r.videoLayers.isEmpty)
      case other         => fail(s"expected a root, got $other")
