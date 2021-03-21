package xyz.hyperreal.suit

class Graphics(gc: GraphicsContext) {

  def setLineWidth(n: Double): Unit = ???

  def setLineType(t: LineType): Unit = ???

  def setDrawColor(c: Int): Unit = ???

  def setFont(f: Font): Unit = ???

  def setFillColor(c: Int): Unit = ???

  def drawLine(x1: Double, y1: Double, x2: Double, y2: Double): Unit = ???

  def drawRectangle(x: Double, y: Double, w: Double, h: Double): Unit = ???

  def fillRectangle(x: Double, y: Double, w: Double, h: Double): Unit = ???

//  def drawRoundRectangle(x1: Double, y1: Double, x2: Double, y2: Double): Unit
//
//  def fillRoundRectangle(x1: Double, y1: Double, x2: Double, y2: Double): Unit
//
//  def drawArc(x: Double, y: Double, r: Double, start: Double, end: Double): Unit
//
//  def fillArc(x: Double, y: Double, r: Double, start: Double, end: Double): Unit
//
//  def drawCircle(x: Double, y: Double, r: Double): Unit
//
//  def fillCircle(x: Double, y: Double, r: Double): Unit

  def drawText(s: String, x: Double, y: Double): Unit = ???

}

object Graphics {}

trait LineType
case object SolidLineType extends LineType
case object DashedLineType extends LineType
