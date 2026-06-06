package io.github.edadma.suit

import io.github.edadma.sdl3.{Color => SdlColor, *}

// suit's production paint target: a Canvas backed by an SDL3 renderer. Each call
// translates suit geometry and colour into SDL3 draw calls. Filled shapes that SDL3
// has no primitive for (circles, thick lines) go through the binding's RenderGeometry
// helpers; text goes through sdl3_ttf via the shared FontBook. The colour clash with
// suit's own `Color` is resolved by importing SDL3's as `SdlColor`.
final class SdlCanvas(renderer: Renderer, fonts: FontBook) extends Canvas:

  private def col(c: Color): SdlColor = SdlColor(c.r, c.g, c.b, c.a)

  def fillRect(rect: Rect, color: Color): Unit =
    renderer.setDrawColor(col(color))
    renderer.fillRect(rect.x, rect.y, rect.width, rect.height)

  def strokeRect(rect: Rect, color: Color, width: Double): Unit =
    renderer.setDrawColor(col(color))
    renderer.drawRect(rect.x, rect.y, rect.width, rect.height)

  def fillCircle(center: Offset, radius: Double, color: Color): Unit =
    renderer.fillCircle(center.x, center.y, radius, col(color))

  def line(a: Offset, b: Offset, width: Double, color: Color): Unit =
    renderer.thickLine(a.x, a.y, b.x, b.y, width, col(color))

  // Rasterise the string to a texture and blit it at the origin. The texture is built
  // per paint and freed immediately; painting only happens on a dirty frame, so an idle
  // UI does no glyph work. A glyph/texture cache is a later optimisation.
  def drawText(origin: Offset, text: String, style: TextStyle): Unit =
    if text.nonEmpty then
      val tex = fonts.at(style.size).texture(renderer, text, col(style.color))
      renderer.copy(tex, origin.x, origin.y)
      tex.destroy()
