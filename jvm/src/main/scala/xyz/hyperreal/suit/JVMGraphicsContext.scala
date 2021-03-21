package xyz.hyperreal.suit

import java.awt.geom.{Line2D, Rectangle2D, RoundRectangle2D}
import java.awt.{BasicStroke, Color}
import scala.swing.Graphics2D

class JVMGraphicsContext extends GraphicsContext {

  var graphics2D: Graphics2D = _

  def setLineWidth(n: Double): Unit =
    graphics2D.setStroke(new BasicStroke(n.toFloat, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_ROUND))

  def setLineType(t: LineType): Unit = ???

  def setColor(c: Int): Unit = graphics2D.setColor(new Color(c))

  def setFont(f: Font): Unit = ???

  def drawLine(x1: Double, y1: Double, x2: Double, y2: Double): Unit =
    graphics2D.draw(new Line2D.Double(x1, y1, x2, y2))

  def drawRectangle(x: Double, y: Double, w: Double, h: Double): Unit =
    graphics2D.draw(new Rectangle2D.Double(x, y, w, h))
//    graphics2D.draw(new RoundRectangle2D.Double(x, y, w, h, 5, 5))

  def fillRectangle(x: Double, y: Double, w: Double, h: Double): Unit =
    graphics2D.fill(new Rectangle2D.Double(x, y, w, h))

  def drawGlyphString(gs: JVMGlyphString, x: Double, y: Double, pos: TextPosition): Unit = {
    val (xp, yp) =
      pos match {
        case TextPosition.ABOVE => (x - gs.vb.getCenterX, y)
        case TextPosition.BELOW => (x - gs.vb.getCenterX, y + gs.vb.getHeight)
        case TextPosition.RIGHT => (x - gs.vb.getX, y - gs.vb.getCenterY)
        case TextPosition.LEFT  => (x - gs.vb.getWidth - gs.vb.getX, y - gs.vb.getCenterY)
      }

    graphics2D.drawGlyphVector(gs.gv, xp.toFloat, yp.toFloat)
  }

}
