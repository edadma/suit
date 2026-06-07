package io.github.edadma.suit

import scala.collection.mutable
import io.github.edadma.libcairo.{Context, FontFace, Pattern, patternCreateLinear, patternCreateRadial}

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
  private def roundedPath(rect: Rect, radius: BorderRadius): Unit =
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
    cr.newSubPath()
    cr.arc(x + w - tr, y + tr, tr, -halfPi, 0.0)            // top-right
    cr.arc(x + w - brad, y + hh - brad, brad, 0.0, halfPi)  // bottom-right
    cr.arc(x + bl, y + hh - bl, bl, halfPi, math.Pi)        // bottom-left
    cr.arc(x + tl, y + tl, tl, math.Pi, 3 * halfPi)         // top-left
    cr.closePath()

  def fillRoundedRect(rect: Rect, radius: BorderRadius, paint: Paint): Unit =
    roundedPath(rect, radius)
    val p = source(paint, rect)
    cr.fill()
    disposeSource(p)

  def strokeRoundedRect(rect: Rect, radius: BorderRadius, paint: Paint, width: Double): Unit =
    val h     = width / 2
    val inner = Rect(rect.x + h, rect.y + h, rect.width - width, rect.height - width)
    roundedPath(inner, radius)
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

  // Fake a soft shadow by feathering: draw concentric rounded rectangles from the outer
  // edge inward, each slightly smaller and more opaque, so their overlap builds up a graded
  // edge. Cairo has no blur primitive (true Gaussian shadows are a fidelity pass), and this
  // single seam call keeps the fake out of the render tree, so a blur-based backend can
  // replace it without any RenderObject changing.
  def drawShadow(rect: Rect, radius: BorderRadius, shadow: Shadow): Unit =
    val steps = math.max(1, shadow.blur.round.toInt)
    val baseA = shadow.color.a / 255.0
    val dx    = shadow.offset.x
    val dy    = shadow.offset.y
    var i     = steps
    while i >= 1 do
      val t    = i.toDouble / steps                 // 1 at the soft outer edge, → 0 at the core
      val grow = shadow.spread + shadow.blur * t
      val a    = math.min(baseA, baseA * (1.0 - t) * 2.0 / steps)
      val ring = Rect(rect.x + dx - grow, rect.y + dy - grow, rect.width + 2 * grow, rect.height + 2 * grow)
      val rad  = BorderRadius(
        radius.topLeft + grow,
        radius.topRight + grow,
        radius.bottomRight + grow,
        radius.bottomLeft + grow,
      )
      roundedPath(ring, rad)
      cr.setSourceRGBA(shadow.color.r / 255.0, shadow.color.g / 255.0, shadow.color.b / 255.0, a)
      cr.fill()
      i -= 1

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
