package io.github.edadma.suit

import java.awt.{BasicStroke, Color as AwtColor, Graphics2D, Shape}

// AWT/Swing implementation of Renderer — wraps a Graphics2D handed in by
// JPanel.paintComponent each frame. Self-contained: nothing else in the
// engine touches AWT.
final class SwingRenderer(g2: Graphics2D) extends Renderer:

  private val clipStack: scala.collection.mutable.Stack[Shape] =
    scala.collection.mutable.Stack.empty

  def fillRect(rect: Rect, color: Color): Unit =
    g2.setColor(toAwt(color))
    g2.fillRect(rect.x, rect.y, rect.w, rect.h)

  def strokeRect(rect: Rect, color: Color): Unit =
    g2.setColor(toAwt(color))
    g2.setStroke(new BasicStroke(1f))
    g2.drawRect(rect.x, rect.y, rect.w - 1, rect.h - 1)

  def fillRoundRect(rect: Rect, radius: Int, color: Color): Unit =
    g2.setColor(toAwt(color))
    g2.fillRoundRect(rect.x, rect.y, rect.w, rect.h, radius * 2, radius * 2)

  def strokeRoundRect(rect: Rect, radius: Int, color: Color): Unit =
    g2.setColor(toAwt(color))
    g2.setStroke(new BasicStroke(1f))
    g2.drawRoundRect(rect.x, rect.y, rect.w - 1, rect.h - 1, radius * 2, radius * 2)

  // Cheap approximation of a Gaussian-blurred drop shadow: paint several
  // outward-expanding rounded strokes whose alpha falls off linearly.
  // Skia's renderer will replace this with a real blurred sprite.
  def drawShadow(rect: Rect, radius: Int, shadow: Shadow): Unit =
    val layers    = if shadow.blur < 1 then 1 else shadow.blur
    val baseAlpha = shadow.color.a
    var i = 0
    while i < layers do
      val falloff = 1f - (i.toFloat / layers.toFloat)
      val alpha   = (baseAlpha.toFloat * falloff * 0.6f).toInt
      val ring    = Color(shadow.color.r, shadow.color.g, shadow.color.b,
                          if alpha < 0 then 0 else alpha)
      val out     = shadow.spread + i
      val rr      = Rect(rect.x + shadow.offsetX - out, rect.y + shadow.offsetY - out,
                         rect.w + out * 2, rect.h + out * 2)
      g2.setColor(toAwt(ring))
      g2.setStroke(new BasicStroke(1f))
      g2.drawRoundRect(rr.x, rr.y, rr.w - 1, rr.h - 1,
                       (radius + out) * 2, (radius + out) * 2)
      i = i + 1

  def drawText(x: Int, y: Int, text: String, color: Color): Unit =
    g2.setColor(toAwt(color))
    g2.drawString(text, x, y)

  def pushClip(rect: Rect): Unit =
    clipStack.push(g2.getClip)
    g2.clipRect(rect.x, rect.y, rect.w, rect.h)   // intersects with current clip

  def popClip(): Unit =
    if clipStack.nonEmpty then g2.setClip(clipStack.pop())
    else g2.setClip(null)

  private def toAwt(c: Color): AwtColor = new AwtColor(c.r, c.g, c.b, c.a)
