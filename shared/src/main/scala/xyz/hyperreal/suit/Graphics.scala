package xyz.hyperreal.suit

class Graphics(gx: Double, gy: Double, val gc: GraphicsContext) {

  def graphics(ox: Double, oy: Double) = new Graphics(gx + ox, gy + oy, gc)

  def setLineWidth(n: Double): Unit = gc.setLineWidth(n)

  def setLineType(t: LineType): Unit = gc.setLineType(t)

  def setColor(c: Int): Unit = gc.setColor(c)

  def setFont(f: Font): Unit = gc.setFont(f)

  def drawLine(x1: Double, y1: Double, x2: Double, y2: Double): Unit = gc.drawLine(gx + x1, gy + y1, gx + x2, gy + y2)

  def drawRectangle(x: Double, y: Double, w: Double, h: Double): Unit =
    gc.drawRectangle(gx + x, gy + y, w, h)

  def drawRoundRectangle(x: Double, y: Double, w: Double, h: Double, arcw: Double, arch: Double): Unit =
    gc.drawRoundRectangle(gx + x, gy + y, w, h, arcw, arch)

  def drawRectangleThin(x: Double, y: Double, w: Double, h: Double): Unit =
    gc.drawRectangle((gx + x).floor + 0.5, (gy + y).floor + 0.5, w.floor, h.floor)

  def drawRoundRectangleThin(x: Double, y: Double, w: Double, h: Double, arcw: Double, arch: Double): Unit =
    gc.drawRoundRectangle((gx + x).floor + 0.5, (gy + y).floor + 0.5, w.floor, h.floor, arcw, arch)

  def fillRectangle(x: Double, y: Double, w: Double, h: Double): Unit = gc.fillRectangle(gx + x, gy + y, w, h)

  def fillRoundRectangle(x: Double, y: Double, w: Double, h: Double, arcw: Double, arch: Double): Unit =
    gc.fillRoundRectangle(gx + x, gy + y, w, h, arcw, arch)

//  def drawArc(x: Double, y: Double, r: Double, start: Double, end: Double): Unit
//
//  def fillArc(x: Double, y: Double, r: Double, start: Double, end: Double): Unit
//
//  def drawCircle(x: Double, y: Double, r: Double): Unit
//
//  def fillCircle(x: Double, y: Double, r: Double): Unit

  def drawGlyphString(gs: GlyphString, x: Double, y: Double, pos: TextPosition): Unit = {
    gc.drawGlyphString(gs, gx + x, gy + y, pos)
//    gc.drawRectangle(gx + x, gy + y, gs.width, gs.font.height)
  }

  override def toString: String = s"graphics[$gx, $gy]"

}

object Graphics {}

trait LineType
case object SolidLineType extends LineType
case object DashedLineType extends LineType
