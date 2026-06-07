package io.github.edadma.suit

import scala.collection.mutable
import scala.scalanative.unsafe.*
import io.github.edadma.libcairo.{Context, FontFace, Format, Pattern, imageSurfaceCreate, patternCreateLinear, patternCreateRadial}

// suit's production paint target: a Canvas backed by a Cairo drawing context. Cairo is a
// real 2D vector engine, so every primitive is anti-aliased by its coverage rasteriser —
// no supersampling, no hand-built triangle meshes. SDL's job shrinks to creating the
// window, reading input, and blitting the finished Cairo surface to the screen (see
// [[Suit]]); all drawing happens here.
//
// The context draws into an in-memory ARGB32 image surface that the runtime uploads to a
// streaming texture each frame. Coordinates arrive already in absolute (window) space —
// suit's paint pass offsets every object before calling — so the canvas applies no
// transform of its own. Cairo channels are 0–1 doubles; suit's are 0–255 ints.
//
// Text uses a `FontFace` loaded from a specific font file through FreeType (see [[Suit]]),
// not Cairo's "toy" `selectFontFace` API, so the typeface is exactly the one chosen rather
// than whatever the platform resolves a family name to.
final class CairoCanvas(cr: Context, fontFace: FontFace) extends Canvas:

  private def rgba(c: Color): Unit =
    cr.setSourceRGBA(c.r / 255.0, c.g / 255.0, c.b / 255.0, c.a / 255.0)

  // Install `paint` as Cairo's source over the bounds `rect`. A solid colour is set
  // directly; a gradient becomes a Cairo pattern whose geometry is resolved against the
  // rectangle (box-relative alignments → absolute points). The pattern carries one
  // reference from creation that this canvas owns, so it is destroyed after the fill/stroke
  // consumes it — Cairo keeps its own reference while it is the source.
  private def source(paint: Paint, rect: Rect): Pattern | Null =
    paint match
      case Solid(c) =>
        rgba(c)
        null
      case LinearGradient(stops, begin, end) =>
        val (x0, y0) = pointIn(begin, rect)
        val (x1, y1) = pointIn(end, rect)
        val p        = patternCreateLinear(x0, y0, x1, y1)
        addStops(p, stops)
        cr.setSource(p)
        p
      case RadialGradient(stops, center, radius) =>
        val (cx, cy) = pointIn(center, rect)
        val r        = radius * math.max(rect.width, rect.height)
        val p        = patternCreateRadial(cx, cy, 0.0, cx, cy, r)
        addStops(p, stops)
        cr.setSource(p)
        p

  private def addStops(p: Pattern, stops: Seq[ColorStop]): Unit =
    var i = 0
    while i < stops.length do
      val s = stops(i)
      p.addColorStopRGBA(s.offset, s.color.r / 255.0, s.color.g / 255.0, s.color.b / 255.0, s.color.a / 255.0)
      i += 1

  private def pointIn(a: Alignment, rect: Rect): (Double, Double) =
    (rect.x + rect.width * (a.x + 1) / 2, rect.y + rect.height * (a.y + 1) / 2)

  private def disposeSource(p: Pattern | Null): Unit =
    p match
      case pat: Pattern => pat.destroy()
      case null         => ()

  def fillRect(rect: Rect, paint: Paint): Unit =
    cr.rectangle(rect.x, rect.y, rect.width, rect.height)
    val p = source(paint, rect)
    cr.fill()
    disposeSource(p)

  // Cairo strokes centred on the path; insetting the rectangle by half the line width keeps
  // the whole border inside the object's bounds, matching how a CSS-style border reads.
  def strokeRect(rect: Rect, paint: Paint, width: Double): Unit =
    val h     = width / 2
    val inner = Rect(rect.x + h, rect.y + h, rect.width - width, rect.height - width)
    cr.rectangle(inner.x, inner.y, inner.width, inner.height)
    cr.setLineWidth(width)
    val p = source(paint, rect)
    cr.stroke()
    disposeSource(p)

  // Trace a rounded-rectangle path with four quarter-circle arcs joined by the straight
  // sides. Each corner radius is clamped to half the smaller dimension so a large radius
  // becomes a pill rather than overlapping into a malformed path.
  private def roundedPath(ctx: Context, rect: Rect, radius: BorderRadius): Unit =
    val maxR = math.min(rect.width, rect.height) / 2
    def c(r: Double): Double = math.max(0.0, math.min(r, maxR))
    val tl = c(radius.topLeft)
    val tr = c(radius.topRight)
    val brad = c(radius.bottomRight)
    val bl = c(radius.bottomLeft)
    val x  = rect.x
    val y  = rect.y
    val w  = rect.width
    val hh = rect.height
    val halfPi = math.Pi / 2
    ctx.newSubPath()
    ctx.arc(x + w - tr, y + tr, tr, -halfPi, 0.0)            // top-right
    ctx.arc(x + w - brad, y + hh - brad, brad, 0.0, halfPi)  // bottom-right
    ctx.arc(x + bl, y + hh - bl, bl, halfPi, math.Pi)        // bottom-left
    ctx.arc(x + tl, y + tl, tl, math.Pi, 3 * halfPi)         // top-left
    ctx.closePath()

  def fillRoundedRect(rect: Rect, radius: BorderRadius, paint: Paint): Unit =
    roundedPath(cr, rect, radius)
    val p = source(paint, rect)
    cr.fill()
    disposeSource(p)

  def strokeRoundedRect(rect: Rect, radius: BorderRadius, paint: Paint, width: Double): Unit =
    val h     = width / 2
    val inner = Rect(rect.x + h, rect.y + h, rect.width - width, rect.height - width)
    roundedPath(cr, inner, radius)
    cr.setLineWidth(width)
    val p = source(paint, rect)
    cr.stroke()
    disposeSource(p)

  def fillCircle(center: Offset, radius: Double, paint: Paint): Unit =
    cr.arc(center.x, center.y, radius, 0.0, 2 * math.Pi)
    val p = source(paint, Rect(center.x - radius, center.y - radius, radius * 2, radius * 2))
    cr.fill()
    disposeSource(p)

  def line(a: Offset, b: Offset, width: Double, paint: Paint): Unit =
    cr.moveTo(a.x, a.y)
    cr.lineTo(b.x, b.y)
    cr.setLineWidth(width)
    val p = source(paint, Rect(a.x, a.y, b.x - a.x, b.y - a.y))
    cr.stroke()
    disposeSource(p)

  // librsvg renders straight into this same Cairo context, so an SVG composites with the active
  // clip and opacity group like any other drawing — no pixel round-trip. Only a Cairo-backed
  // image carries a real librsvg handle; any other SvgImage (a headless test stub) is a no-op.
  def drawSvg(image: SvgImage, rect: Rect): Unit =
    image match
      case s: CairoSvg => s.handle.renderDocument(cr, rect.x, rect.y, rect.width, rect.height)
      case _           => ()

  // Cast a real soft shadow: draw the (spread) rounded shape filled with the shadow colour into
  // an offscreen ARGB32 surface, blur that surface, and composite it back at the shadow's offset.
  // Cairo has no blur primitive, so the softening is a separable box blur (three passes ≈ a
  // Gaussian) run over the surface's pixel buffer by [[BoxBlur]] — that is the one part that is
  // unit-tested off-device; everything around it is Cairo plumbing. The shape is drawn into a
  // transparent margin wide enough for the blur to feather into, so the edge fades to nothing
  // instead of smearing against the surface's sides. Keeping this behind the single Canvas seam
  // is what let the fake-feather version be swapped out with no RenderObject change.
  def drawShadow(rect: Rect, radius: BorderRadius, shadow: Shadow): Unit =
    val passes = 3
    val r      = math.max(1, math.round(shadow.blur / passes.toDouble).toInt)
    val margin = r * passes + 1
    val spread = shadow.spread

    // The shape grows by `spread` on every side; the surface adds the blur margin around that.
    val shapeW = rect.width + 2 * spread
    val shapeH = rect.height + 2 * spread
    val sw     = math.ceil(shapeW).toInt + 2 * margin
    val sh     = math.ceil(shapeH).toInt + 2 * margin
    if sw <= 0 || sh <= 0 then return

    val surface = imageSurfaceCreate(Format.ARGB32, sw, sh)
    val scr     = surface.create
    val shape   = Rect(margin.toDouble, margin.toDouble, shapeW, shapeH)
    val grown = BorderRadius(
      radius.topLeft + spread,
      radius.topRight + spread,
      radius.bottomRight + spread,
      radius.bottomLeft + spread,
    )
    if grown.isZero then scr.rectangle(shape.x, shape.y, shape.width, shape.height)
    else roundedPath(scr, shape, grown)
    scr.setSourceRGBA(shadow.color.r / 255.0, shadow.color.g / 255.0, shadow.color.b / 255.0, shadow.color.a / 255.0)
    scr.fill()

    // Blur the buffer directly, then mark it dirty so the composite below samples the blurred
    // pixels and not Cairo's pre-blur snapshot.
    surface.flush()
    BoxBlur.blur(new PtrByteSurface(surface.getData, sw, sh, surface.getStride), r, passes)
    surface.markDirty()

    // Place the surface so the shape sits under `rect`, offset by the shadow's displacement; the
    // margin and spread that padded the surface are subtracted back out.
    val destX = rect.x + shadow.offset.x - spread - margin
    val destY = rect.y + shadow.offset.y - spread - margin
    cr.setSourceSurface(surface, destX, destY)
    cr.paint()

    scr.destroy()
    surface.destroy()

  // `origin` is the text's top-left; Cairo draws from the baseline, so drop down by the
  // font's ascent. Measurement (see [[CairoTextMeasurer]]) uses the same family and size,
  // so paint lands exactly where layout reserved it.
  def drawText(origin: Offset, text: String, style: TextStyle): Unit =
    if text.nonEmpty then
      cr.setFontFace(fontFace)
      cr.setFontSize(style.size)
      rgba(style.color)
      val fe = cr.fontExtents
      cr.moveTo(origin.x, origin.y + fe.ascent)
      cr.showText(text)

  // Subtree opacity via Cairo groups: redirect drawing into a temporary group surface, then
  // composite the whole group back at `alpha`. `save`/`restore` brackets the source/state
  // the group push and pop touch. A stack carries the alpha to the matching pop so nested
  // opaque boxes each fade against their own group.
  private val opacityStack = mutable.Stack.empty[Double]

  def pushOpacity(alpha: Double): Unit =
    cr.save()
    cr.pushGroup()
    opacityStack.push(alpha)

  def popOpacity(): Unit =
    val alpha = if opacityStack.nonEmpty then opacityStack.pop() else 1.0
    cr.popGroupToSource()
    cr.paintWithAlpha(alpha)
    cr.restore()

  // Clipping rides on Cairo's own save/restore stack: `save` snapshots the clip region,
  // the (rounded) path plus `clip` intersects it, and the matching `popClip` `restore`s
  // the snapshot. Because Cairo intersects rather than replaces, nested clips compound,
  // which is exactly the overflow-hidden semantics a scroll view inside a card needs.
  def pushClip(rect: Rect, radius: BorderRadius): Unit =
    cr.save()
    if radius.isZero then cr.rectangle(rect.x, rect.y, rect.width, rect.height)
    else roundedPath(cr, rect, radius)
    cr.clip()
    cr.newPath() // `clip` keeps the path current; clear it so later drawing starts clean

  def popClip(): Unit = cr.restore()

// A [[ByteSurface]] view over a Cairo image surface's native pixel buffer, so [[BoxBlur]] can
// convolve a shadow surface in place. `data` is the pointer from `getData` (valid after a flush
// and until the surface is drawn to again); `stride` is its row pitch in bytes.
private final class PtrByteSurface(data: Ptr[Byte], val width: Int, val height: Int, val stride: Int)
    extends ByteSurface:
  def get(offset: Int): Int          = data(offset) & 0xff
  def set(offset: Int, value: Int): Unit = data(offset) = value.toByte
