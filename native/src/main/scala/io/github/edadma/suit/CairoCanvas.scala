package io.github.edadma.suit

import io.github.edadma.libcairo.{Context, FontFace}

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

  private def source(c: Color): Unit =
    cr.setSourceRGBA(c.r / 255.0, c.g / 255.0, c.b / 255.0, c.a / 255.0)

  def fillRect(rect: Rect, color: Color): Unit =
    cr.rectangle(rect.x, rect.y, rect.width, rect.height)
    source(color)
    cr.fill()

  // Cairo strokes centred on the path; insetting the rectangle by half the line width keeps
  // the whole border inside the object's bounds, matching how a CSS-style border reads.
  def strokeRect(rect: Rect, color: Color, width: Double): Unit =
    val h = width / 2
    cr.rectangle(rect.x + h, rect.y + h, rect.width - width, rect.height - width)
    cr.setLineWidth(width)
    source(color)
    cr.stroke()

  def fillCircle(center: Offset, radius: Double, color: Color): Unit =
    cr.arc(center.x, center.y, radius, 0.0, 2 * math.Pi)
    source(color)
    cr.fill()

  def line(a: Offset, b: Offset, width: Double, color: Color): Unit =
    cr.moveTo(a.x, a.y)
    cr.lineTo(b.x, b.y)
    cr.setLineWidth(width)
    source(color)
    cr.stroke()

  // `origin` is the text's top-left; Cairo draws from the baseline, so drop down by the
  // font's ascent. Measurement (see [[CairoTextMeasurer]]) uses the same family and size,
  // so paint lands exactly where layout reserved it.
  def drawText(origin: Offset, text: String, style: TextStyle): Unit =
    if text.nonEmpty then
      cr.setFontFace(fontFace)
      cr.setFontSize(style.size)
      source(style.color)
      val fe = cr.fontExtents
      cr.moveTo(origin.x, origin.y + fe.ascent)
      cr.showText(text)
