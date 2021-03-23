package xyz.hyperreal.suit

trait GraphicsContext {

  def setLineWidth(n: Double): Unit

  def setLineType(t: LineType): Unit

  def setColor(c: Int): Unit

  def setFont(f: Font): Unit

  def drawLine(x1: Double, y1: Double, x2: Double, y2: Double): Unit

  def drawRectangle(x: Double, y: Double, w: Double, h: Double): Unit

  def fillRectangle(x: Double, y: Double, w: Double, h: Double): Unit

  def drawGlyphString(gs: GlyphString, x: Double, y: Double, pos: TextPosition): Unit

}
