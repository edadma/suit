package xyz.hyperreal.suit

class PlatformGraphics(lineWidth: Double, lineType: LineType, drawColor: Int, font: Font)
    extends Graphics(lineWidth, lineType, drawColor, font) {

  var fillColor: Int = drawColor

  def drawLine(x1: Double, y1: Double, x2: Double, y2: Double): Unit = ???

  def drawRectangle(x: Double, y: Double, w: Double, h: Double): Unit = ???

  def fillRectangle(x: Double, y: Double, w: Double, h: Double): Unit = ???

  def drawText(s: String, x: Double, y: Double): Unit = ???

}
