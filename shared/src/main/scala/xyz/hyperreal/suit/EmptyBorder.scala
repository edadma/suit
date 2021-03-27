package xyz.hyperreal.suit

object EmptyBorder extends Border {
  val left: Double = 0
  val top: Double = 0
  val right: Double = 0
  val bottom: Double = 0

  override def paint(g: Graphics, c: Component): Unit = super.paint(g, c)
}
