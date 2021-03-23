package xyz.hyperreal.suit

class Graphics(cx: Double, cy: Double, val gc: GraphicsContext) {

  private var font: Font = null

  def setLineWidth(n: Double): Unit = gc.setLineWidth(n)

  def setLineType(t: LineType): Unit = gc.setLineType(t)

  def setColor(c: Int): Unit = gc.setColor(c)

  def setFont(f: Font): Unit = {
    font = f
    gc.setFont(f)
  }

  def drawLine(x1: Double, y1: Double, x2: Double, y2: Double): Unit = gc.drawLine(cx + x1, cy + y1, cx + x2, cy + y2)

  def drawRectangle(x: Double, y: Double, w: Double, h: Double): Unit = gc.drawRectangle(cx + x, cy + y, w, h)

  def fillRectangle(x: Double, y: Double, w: Double, h: Double): Unit = gc.fillRectangle(cx + x, cy + y, w, h)

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

  def drawText(s: String, x: Double, y: Double): Unit = drawGlyphString(font.getGlyphString(s), x, y)

  def drawGlyphString(gs: GlyphString, x: Double, y: Double): Unit =
    gc.drawGlyphString(gs, cx + x, cy + y, TextPosition.BELOW_RIGHT)

}

object Graphics {}

trait LineType
case object SolidLineType extends LineType
case object DashedLineType extends LineType
