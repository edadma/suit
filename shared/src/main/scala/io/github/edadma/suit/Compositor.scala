package io.github.edadma.suit

import scala.collection.mutable

// Partial-frame compositing. When only a repaint boundary's content changed (an animating
// canvas, say), the runtime does not re-rasterise the whole window — it repaints just that
// boundary's region and leaves the rest of the previous frame's pixels in place. The actual
// "leave the rest in place" is the persistent drawing surface: the runtime never clears it
// wholesale on a partial frame, so untouched pixels survive. This object holds the in-place
// repaint of one boundary, written against the Canvas seam so it is the same on the native
// Cairo backend and the JVM RecordingCanvas the tests assert against.
object Compositor:

  /** Repaint a single repaint boundary in place. Replay every clip its ancestors impose (a
    * scroll viewport, a clipped card) so the boundary is confined exactly as a full repaint
    * confines it, clear its bounds to the window background, then paint its subtree — nothing
    * outside that region is touched. This is what lets an animating canvas redraw at frame
    * rate without re-rasterising the static UI around it, while still scrolling "under" the
    * chrome the same way it does on a full repaint. The boundary's content is assumed to cover
    * its bounds opaquely; the background clear is the floor under a surface that does not fill
    * every pixel. */
  def repaintBoundary(canvas: Canvas, boundary: RenderObject, background: Color): Unit =
    val origin = boundary.absoluteOffset
    val rect   = Rect.at(origin, boundary.size)

    // Collect the clips imposed by the chain of ancestors, then push them all. Nested clips
    // intersect, so order does not matter; the boundary ends up confined to their overlap.
    val clips = mutable.ListBuffer.empty[(Rect, BorderRadius)]
    var n     = boundary.parent
    while n != null do
      n.clipShape.foreach(clips += _)
      n = n.parent
    for (r, radius) <- clips do canvas.pushClip(r, radius)

    canvas.fillRect(rect, Solid(background))
    boundary.paint(canvas, origin)

    for _ <- clips do canvas.popClip()

  /** Composite a whole partial frame: repaint each dirty `boundary` in place (clearing its
    * repaint flag), then — if the always-on-top `overlay` layer has content — re-paint that
    * layer over each repainted region, clipped to it.
    *
    * The re-composite is what keeps an animating boundary from bleeding through an open dialog
    * or menu. The overlay (a portaled dialog scrim, a menu) paints *above* the application, so
    * a boundary beneath it that re-rasterises only its own region would overwrite the overlay's
    * pixels there. Painting the overlay back over each repainted region restores exactly what a
    * full repaint would have left on top — at the cost of re-rasterising only the overlay's
    * intersection with the animating regions, not the whole window. With no overlay content the
    * step is skipped and this is just the per-boundary repaint. */
  def partialFrame(
      canvas:     Canvas,
      boundaries: List[RenderObject],
      overlay:    RenderObject,
      background: Color,
  ): Unit =
    for b <- boundaries do
      repaintBoundary(canvas, b, background)
      b.needsRepaint = false
    if overlay.children.nonEmpty then
      val overlayOrigin = overlay.absoluteOffset
      for b <- boundaries do
        canvas.pushClip(Rect.at(b.absoluteOffset, b.size), BorderRadius.zero)
        overlay.paint(canvas, overlayOrigin)
        canvas.popClip()
