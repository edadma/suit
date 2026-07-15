package io.github.edadma.suit

// Video rides a different path through the runtime than everything else suit draws, and the
// reason is arithmetic.
//
// Every other widget is rasterised by Cairo into one image surface, which is uploaded to one
// texture and blitted to the window. Push video through that and each frame costs a colourspace
// conversion (a decoder emits YUV, Cairo wants BGRA), a CPU blit into the backbuffer, a CPU
// scale to the preview's size, and a re-upload of the whole window. Three full-frame passes per
// frame, at 30 or 60 of them a second.
//
// So a video layer skips Cairo entirely. Its frame lives in its own texture in the decoder's own
// YUV layout, and the renderer converts and scales it in the blit's shader — free, on the GPU.
// suit's part is to say *where* the frame goes and to get out of the way: a [[RenderVideo]]
// paints an opaque background with a transparent hole in it, the runtime blits the video texture
// underneath, and the UI layer composites on top. The hole is the entire mechanism.
//
// Consequence worth knowing: video always sits *under* the UI. That is the right constraint for
// an editor — a preview monitor and timeline thumbnails with chrome above them — and it is not a
// compositing engine. Two clips dissolving into each other is the application's pipeline doing
// the mix and handing suit one output frame.

/** How a frame is fitted into the rectangle laid out for it, when the two disagree in shape. */
enum VideoFit:
  /** Scale to fit entirely inside, preserving aspect, and centre it — the whole frame is
    * visible and the leftover is background (letterbox or pillarbox bars). What a preview
    * monitor wants: never crop what the editor is judging. */
  case Contain

  /** Scale to cover the rectangle, preserving aspect, and centre it — no bars, but the frame's
    * overhanging edges are cropped away. For a thumbnail that should fill its cell. */
  case Cover

  /** Stretch to the rectangle exactly, ignoring aspect. Distorts, and is here for the case where
    * the caller has already done the geometry. */
  case Fill

/** A platform-held video frame, ready to blit — a texture on the native runtime, holding the
  * decoder's planes in their own layout. suit's shared layer knows only how big the frame is,
  * which is all the geometry needs; the runtime recognises its own concrete implementation and
  * blits it. Obtain one from the platform (`VideoTexture` on Native) and feed it decoded frames.
  *
  * `frameWidth`/`frameHeight` are the frame's dimensions in **pixels** as decoded. Where those
  * pixels are not square (anamorphic, DV, and most SD formats), the shape on screen comes from
  * `pixelAspect` on the widget, not from these. */
trait VideoLayer:
  /** The decoded frame's width in pixels. */
  def frameWidth: Int

  /** The decoded frame's height in pixels. */
  def frameHeight: Int

/** The placement arithmetic: given a widget's rectangle and a frame, where does the frame land
  * and how much of it is shown. Pure geometry, no platform anywhere near it, so the fitting and
  * the pixel-aspect correction are settled here and verified off-device — the runtime just blits
  * the two rectangles it is handed.
  */
object VideoGeometry:

  /** Work out the source and destination rectangles for a frame drawn under `fit`.
    *
    * Returns `(src, dst)`: `src` is the region **of the frame** to read, in frame pixels, and
    * `dst` is where it lands in the window, in logical coordinates. [[VideoFit.Cover]] crops by
    * narrowing `src` rather than by overflowing `dst`, so the caller never needs a clip.
    *
    * `pixelAspect` is the frame's pixel aspect ratio — the width of one stored pixel over its
    * height, as displayed. It is 1 for square-pixel formats (everything HD and most modern
    * files) but not for anamorphic or SD ones, where ignoring it shows people visibly too thin or
    * too wide. It scales the frame's *displayed* width, leaving `src` in stored pixels.
    *
    * A degenerate input — an empty widget, a frame with no size, a nonsensical pixel aspect, as
    * can happen before the first frame arrives — yields two empty rectangles, so a caller draws
    * nothing rather than dividing by zero. */
  def place(fit: VideoFit, widget: Rect, frameWidth: Int, frameHeight: Int, pixelAspect: Double): (Rect, Rect) =
    if widget.width <= 0 || widget.height <= 0 || frameWidth <= 0 || frameHeight <= 0 ||
      pixelAspect <= 0 || !pixelAspect.isFinite
    then (Rect(0, 0, 0, 0), Rect(0, 0, 0, 0))
    else
      val fw = frameWidth.toDouble
      val fh = frameHeight.toDouble
      // The frame's aspect as *displayed*: stored pixels stretched by the pixel aspect ratio.
      val frameAspect  = (fw * pixelAspect) / fh
      val widgetAspect = widget.width / widget.height
      val full         = Rect(0, 0, fw, fh)

      fit match
        case VideoFit.Fill => (full, widget)

        case VideoFit.Contain =>
          // Match whichever axis runs out first; the other keeps the aspect and is centred,
          // leaving equal bars on the two sides that are short.
          val (w, h) =
            if frameAspect > widgetAspect then (widget.width, widget.width / frameAspect)
            else (widget.height * frameAspect, widget.height)
          val dst = Rect(
            widget.x + (widget.width - w) / 2,
            widget.y + (widget.height - h) / 2,
            w,
            h,
          )
          (full, dst)

        case VideoFit.Cover =>
          // The mirror of Contain: the axis that would overhang is cropped instead of bar'd, by
          // reading a centred sub-rectangle of the frame. The visible fraction is the ratio of
          // the two aspects, taken whichever way round is < 1.
          val (sw, sh) =
            if frameAspect > widgetAspect then (fw * (widgetAspect / frameAspect), fh)
            else (fw, fh * (frameAspect / widgetAspect))
          val src = Rect((fw - sw) / 2, (fh - sh) / 2, sw, sh)
          (src, widget)
