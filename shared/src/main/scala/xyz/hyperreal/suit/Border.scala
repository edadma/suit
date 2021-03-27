package xyz.hyperreal.suit

abstract class Border {

  def top: Double

  def right: Double

  def left: Double

  def bottom: Double

  def paint(g: Graphics, c: Component): Unit = {
    g.setColor(c.backgroundColor)
    g.fillRectangle(0, 0, c.width, c.height)
  }

  override def toString: String = s"border[$top, $right, $bottom, $left]"

}
