package io.github.edadma.suit

// Partial-frame compositing. When only a repaint boundary's content changed (an animating
// canvas, say), the runtime does not re-rasterise the whole window — it repaints just that
// boundary's region and leaves the rest of the previous frame's pixels in place. The actual
// "leave the rest in place" is the persistent drawing surface: the runtime never clears it
// wholesale on a partial frame, so untouched pixels survive. This object holds the in-place
// repaint of one boundary, written against the Canvas seam so it is the same on the native
// Cairo backend and the JVM RecordingCanvas the tests assert against.
object Compositor:

  /** Repaint a single repaint boundary in place. Clip to its bounds, clear them to the
    * window background, then paint its subtree — nothing outside the bounds is touched. This
    * is what lets an animating canvas redraw at frame rate without re-rasterising the static
    * UI around it. The boundary's content is assumed to cover its bounds opaquely; the
    * background clear is the floor under a surface that does not fill every pixel. */
  def repaintBoundary(canvas: Canvas, boundary: RenderObject, background: Color): Unit =
    val origin = boundary.absoluteOffset
    val rect   = Rect.at(origin, boundary.size)
    canvas.pushClip(rect, BorderRadius.zero)
    canvas.fillRect(rect, Solid(background))
    boundary.paint(canvas, origin)
    canvas.popClip()
