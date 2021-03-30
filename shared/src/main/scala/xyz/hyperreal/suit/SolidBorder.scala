package xyz.hyperreal.suit

class SolidBorder(thickness: Double, var color: Int) extends Border {
  val left: Double = thickness
  val top: Double = thickness
  val right: Double = thickness
  val bottom: Double = thickness

  override def paint(g: Graphics, c: Component): Unit = {
    super.paint(g, c)
    g.setColor(color)
    g.setLineWidth(thickness)

    if (thickness == 1.0)
      g.drawRectangleThin(0, 0, c.width, c.height)
    else
      g.drawRectangle(0, 0, c.width, c.height)
  }
}
