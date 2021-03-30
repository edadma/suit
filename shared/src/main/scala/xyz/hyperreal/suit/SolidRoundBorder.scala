package xyz.hyperreal.suit

class SolidRoundBorder(thickness: Double, var color: Int, val arc: Double = 15) extends Border {
  val left: Double = thickness
  val top: Double = thickness
  val right: Double = thickness
  val bottom: Double = thickness

  override def paint(g: Graphics, c: Component): Unit = {
    g.setColor(c.backgroundColor)
    g.fillRoundRectangle(0, 0, c.width, c.height, arc, arc)
    g.setColor(color)
    g.setLineWidth(thickness)

    if (thickness == 1.0 || thickness == 3.0)
      g.drawRoundRectangleThin(0, 0, c.width, c.height, arc, arc)
    else
      g.drawRoundRectangle(0, 0, c.width, c.height, arc, arc)
  }
}
