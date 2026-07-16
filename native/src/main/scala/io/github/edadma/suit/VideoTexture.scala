package io.github.edadma.suit

import scala.scalanative.unsafe.*
import io.github.edadma.sdl3.*

// The native video backend: a frame lives in an SDL texture in the decoder's own YUV layout, and
// the renderer converts it to RGB in the blit's shader. SDL appears only here and in the runtime's
// present; the render tree, the DSL, and the geometry stay platform-neutral behind the shared
// VideoLayer type (see `video.scala` for why the whole path bypasses Cairo).

/** The plane layout of the frames a [[VideoTexture]] will be fed. */
enum VideoFormat:
  /** Three planes — Y, then U, then V, each chroma plane half-size on both axes. libavcodec's
    * `AV_PIX_FMT_YUV420P`, the usual output of a software H.264/HEVC decode. Feed it with
    * [[VideoTexture.update]]. */
  case I420

  /** Two planes — Y, then U and V interleaved into one. What hardware decoders (VideoToolbox,
    * VAAPI) hand back. Feed it with [[VideoTexture.updateNV]]. */
  case NV12

/** How a frame's YUV values map to colour. Fixed when the texture is created, because that is the
  * only point SDL lets it be set — and getting it wrong is not an error, just a picture with
  * shifted colour, so it is worth matching to what the source declares. */
enum VideoColorspace:
  /** BT.709, limited (studio) range — essentially all HD video. */
  case BT709

  /** BT.601, limited (studio) range — SD video. */
  case BT601

  /** Full-range YUV — JPEG, and many cameras and screen-capture sources. */
  case JPEG

/** A video frame held in GPU memory, ready to blit — the native [[VideoLayer]].
  *
  * Feeding it a frame is the whole of the frame path: no colour conversion, no CPU blit, no
  * repaint. The texture's contents change, and the next present shows them, because a
  * [[dsl.video]] widget paints a hole rather than pixels.
  *
  * **Both creating one and updating it must happen on the UI thread** — an SDL renderer is not
  * thread-safe, and neither is the tree the widget lives in. A decoder thread hands its frame over
  * with [[UiThread.post]] and the update happens there; that is what the seam is for. */
final class VideoTexture private (
    private[suit] val texture: Texture,
    val frameWidth:            Int,
    val frameHeight:           Int,
    val format:                VideoFormat,
) extends VideoLayer:

  /** Replace the frame from three planes ([[VideoFormat.I420]]). Each plane is a pointer to its
    * first byte and a **pitch in bytes per row** — the decoder's stride, which is often wider than
    * the frame because planes are padded for alignment. The chroma planes are half-size on both
    * axes. From libavcodec, `data(0)`/`linesize(0)` is Y, `1` is U, `2` is V.
    *
    * Returns false if the upload failed or the texture was made for a different layout. */
  def update(y: Ptr[Byte], yPitch: Int, u: Ptr[Byte], uPitch: Int, v: Ptr[Byte], vPitch: Int): Boolean =
    format == VideoFormat.I420 && texture.updateYUV(y, yPitch, u, uPitch, v, vPitch)

  /** Replace the frame from two planes ([[VideoFormat.NV12]]), the chroma interleaved into one.
    * `uvPitch` counts bytes per row of that combined plane, so for 4:2:0 it spans width/2 U/V
    * pairs and is typically equal to `yPitch`. */
  def updateNV(y: Ptr[Byte], yPitch: Int, uv: Ptr[Byte], uvPitch: Int): Boolean =
    format == VideoFormat.NV12 && texture.updateNV(y, yPitch, uv, uvPitch)

  /** Release the GPU texture. The layer must not be attached to a mounted widget afterwards. */
  def destroy(): Unit = texture.destroy()

object VideoTexture:

  // The renderer to make textures on. Published by the runtime as the window comes up, the way
  // DevicePixelRatio and Clipboard are, because an application creates its own layers (it knows
  // the frame size when it opens a file) and has no other way to reach it.
  private[suit] var renderer: Ptr[Byte] | Null = null

  /** Create a texture for frames of `width` x `height`.
    *
    * `colorspace` must match what the source declares — [[VideoColorspace.BT709]] for HD, which is
    * why it is the default. Frames are uploaded with [[VideoTexture.update]] or
    * [[VideoTexture.updateNV]] according to `format`, and scaled to their widget with linear
    * filtering.
    *
    * Call it on the UI thread, once a window is open: it needs the runtime's renderer, and throws
    * if there is none (a headless test, or a call before `Suit.run`). */
  def apply(
      width:      Int,
      height:     Int,
      format:     VideoFormat     = VideoFormat.I420,
      colorspace: VideoColorspace = VideoColorspace.BT709,
  ): VideoTexture =
    val r = renderer match
      case p: Ptr[Byte] => p
      case null =>
        throw new IllegalStateException(
          "VideoTexture requires a running window: create it from inside Suit.run, on the UI thread",
        )
    require(width > 0 && height > 0, s"video frame size must be positive, got ${width}x$height")

    val fmt = format match
      case VideoFormat.I420 => PIXELFORMAT_IYUV
      case VideoFormat.NV12 => PIXELFORMAT_NV12
    val cs = colorspace match
      case VideoColorspace.BT709 => COLORSPACE_BT709_LIMITED
      case VideoColorspace.BT601 => COLORSPACE_BT601_LIMITED
      case VideoColorspace.JPEG  => COLORSPACE_JPEG

    val tex = new Renderer(r).createYUVTexture(fmt, TEXTUREACCESS_STREAMING, width, height, cs)
    if tex.isNull then throw new RuntimeException(s"suit: could not create video texture: $error")
    // A preview is almost never shown at exactly its decoded size, so the scale wants filtering
    // rather than nearest-neighbour's aliasing.
    tex.setScaleMode(SCALEMODE_LINEAR)
    new VideoTexture(tex, width, height, format)
