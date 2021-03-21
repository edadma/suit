package xyz.hyperreal.suit

import scala.swing.Graphics2D

class JVMGraphicsContext extends GraphicsContext {

  var g: Graphics2D = _

  def setLineWidth(n: Double): Unit = ???

  def setLineType(t: LineType): Unit = ???

  def setDrawColor(c: Int): Unit = ???

  def setFont(f: Font): Unit = ???

  def setFillColor(c: Int): Unit = ???

  def drawLine(x1: Double, y1: Double, x2: Double, y2: Double): Unit = ???

  def drawRectangle(x: Double, y: Double, w: Double, h: Double): Unit = ???

  def fillRectangle(x: Double, y: Double, w: Double, h: Double): Unit = ???

  def drawText(s: String, x: Double, y: Double): Unit = ???

}
