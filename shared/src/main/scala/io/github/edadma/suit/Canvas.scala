package io.github.edadma.suit

// The paint seam every RenderObject draws through — the boundary between "what to
// draw" (the render tree) and "how to draw it" (a backend). `SdlCanvas` drives SDL3
// for real rendering; `RecordingCanvas` captures the same calls for headless tests.
// Keeping painting behind this interface is what will let the layout engine be
// verified off-device, the way vdom's reconciler is tested headlessly on the JVM.
//
// It is push-based and imperative: a paint pass walks the render tree top-down and
// issues calls in z-order (back to front). Coordinates are absolute (already offset
// by each object's position) — the canvas itself holds no transform state yet;
// clipping and transforms arrive with scrolling and compositing.
trait Canvas:
  def fillRect(rect: Rect, color: Color): Unit
  def strokeRect(rect: Rect, color: Color, width: Double): Unit
  def fillCircle(center: Offset, radius: Double, color: Color): Unit
  def line(a: Offset, b: Offset, width: Double, color: Color): Unit

  /** Draw a single line of `text` in `style` with its top-left at `origin`. The SDL
    * backend rasterises glyphs through sdl3_ttf; the recording backend captures the
    * call. The text's size on screen matches what [[TextMeasurer]] reported for the
    * same string and style, so paint lands exactly where layout placed it. */
  def drawText(origin: Offset, text: String, style: TextStyle): Unit
