package io.github.edadma.suit

import io.github.edadma.libcairo.{Context, Format, Surface, imageSurfaceCreate}

// The measurement half of text support, backed by Cairo. Layout runs the constraint pass
// off to the side of painting, so the measurer keeps its own tiny scratch surface and
// context rather than borrowing the frame's — text extents are resolution-independent, so a
// 1×1 surface measures the same as the real one. It selects the face for the run's weight
// from the same `Fonts` cache the canvas paints with, so a string measures exactly as wide
// as it will draw.
final class CairoTextMeasurer(fonts: Fonts) extends TextMeasurer:
  private val surface: Surface = imageSurfaceCreate(Format.ARGB32, 1, 1)
  private val cr: Context      = surface.create

  def measure(text: String, style: TextStyle): Size =
    cr.setFontFace(fonts.faceFor(style.weight))
    cr.setFontSize(style.size)
    val fe = cr.fontExtents
    // An empty string still reports the line height, so a blank text node reserves a line
    // instead of collapsing. Width is the pen advance, the right measure for laying out a
    // run (not the ink width, which excludes side bearings).
    if text.isEmpty then Size(0.0, fe.height)
    else Size(cr.textExtents(text).xAdvance, fe.height)

  /** Release the scratch surface. Call at shutdown. */
  def close(): Unit =
    cr.destroy()
    surface.destroy()
