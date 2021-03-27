package xyz.hyperreal.suit

abstract class Border {

  def top: Double

  def right: Double

  def left: Double

  def bottom: Double

  def paint(g: Graphics, c: Component): Unit

  override def toString: String = s"border[$top, $right, $bottom, $left]"

}
