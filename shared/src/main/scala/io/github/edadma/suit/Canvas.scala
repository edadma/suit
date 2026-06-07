package io.github.edadma.suit

// The paint seam every RenderObject draws through — the boundary between "what to
// draw" (the render tree) and "how to draw it" (a backend). `CairoCanvas` drives Cairo
// for real rendering; `RecordingCanvas` captures the same calls for headless tests.
// Keeping painting behind this interface is what lets the layout engine and the styling
// model be verified off-device, the way vdom's reconciler is tested headlessly on the JVM.
//
// It is push-based and imperative: a paint pass walks the render tree top-down and
// issues calls in z-order (back to front). Coordinates are absolute (already offset
// by each object's position) — the canvas itself holds no transform state yet;
// clipping and transforms arrive with scrolling and compositing.
//
// Fills and strokes take a [[Paint]] (a solid colour or a gradient); a `Color` converts
// to a `Solid` implicitly, so a plain colour reads naturally at every call site.
trait Canvas:
  def fillRect(rect: Rect, paint: Paint): Unit
  def strokeRect(rect: Rect, paint: Paint, width: Double): Unit

  /** Fill a rounded rectangle. `radius` gives the four corner radii (the backend clamps
    * each to half the smaller side). A zero radius is just a rectangle, but callers use
    * [[fillRect]] for that — this path exists for the rounded case. */
  def fillRoundedRect(rect: Rect, radius: BorderRadius, paint: Paint): Unit

  /** Stroke the outline of a rounded rectangle, centred on the path and `width` wide. */
  def strokeRoundedRect(rect: Rect, radius: BorderRadius, paint: Paint, width: Double): Unit

  def fillCircle(center: Offset, radius: Double, paint: Paint): Unit
  def line(a: Offset, b: Offset, width: Double, paint: Paint): Unit

  /** Draw the SVG `image` scaled to fill `rect`. The Cairo backend renders it as vectors
    * straight into its context — crisp at any size, and honouring the clip and opacity group
    * currently in force — while the recording backend captures the call. An image the backend
    * doesn't recognise (e.g. a test stub on the Cairo backend) draws nothing. */
  def drawSvg(image: SvgImage, rect: Rect): Unit

  /** Cast a drop shadow for the (rounded) rectangle `rect`. The backend renders the look
    * described by [[Shadow]]; keeping it a single seam call (rather than the render tree
    * composing many fills) lets a future blur-based backend replace the fake feather
    * without touching any RenderObject. */
  def drawShadow(rect: Rect, radius: BorderRadius, shadow: Shadow): Unit

  /** Draw a single line of `text` in `style` with its top-left at `origin`. The Cairo
    * backend rasterises glyphs through the loaded font face; the recording backend
    * captures the call. The text's size on screen matches what [[TextMeasurer]] reported
    * for the same string and style, so paint lands exactly where layout placed it. */
  def drawText(origin: Offset, text: String, style: TextStyle): Unit

  /** Begin a group whose drawing is composited at `alpha` (0–1) when the matching
    * [[popOpacity]] runs. A box with `opacity < 1` brackets itself and its children this
    * way, so the whole subtree fades as one — translucency applies to the composite, not
    * to each overlapping shape independently. Calls nest. */
  def pushOpacity(alpha: Double): Unit

  /** Close the most recent [[pushOpacity]] group, compositing it at the alpha given. */
  def popOpacity(): Unit

  /** Restrict subsequent drawing to the (rounded) rectangle `rect` until the matching
    * [[popClip]] runs — anything painted outside is discarded. A scroll view brackets its
    * content this way so the part scrolled past its edges does not bleed over its
    * neighbours; a rounded clip also gives an overflow-hidden card crisp corners. The
    * clip intersects whatever clip is already in force, so nested clips compound, and a
    * [[popClip]] restores exactly the region that was in effect before its push. Calls
    * nest with [[pushOpacity]]/[[popOpacity]] as long as each push is balanced. */
  def pushClip(rect: Rect, radius: BorderRadius): Unit

  /** Close the most recent [[pushClip]], restoring the previous clip region. */
  def popClip(): Unit
