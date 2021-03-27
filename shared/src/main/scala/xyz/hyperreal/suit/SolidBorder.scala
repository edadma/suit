package xyz.hyperreal.suit

class SolidBorder(thickness: Double, color: Int) extends Border {
  val left: Double = thickness
  val top: Double = thickness
  val right: Double = thickness
  val bottom: Double = thickness

  def paint(g: Graphics, c: Component): Unit = {
    g.setColor(color)
    g.drawRectangle(0, 0, c.width, c.height)
  }
}
