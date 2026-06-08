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
