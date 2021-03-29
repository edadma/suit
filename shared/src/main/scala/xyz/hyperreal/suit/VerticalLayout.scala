package xyz.hyperreal.suit

object VerticalLayout {

  def apply(cs: Component*): VerticalLayout = new VerticalLayout() { contents ++= cs }

  def apply(space: Double, cs: Component*): VerticalLayout = new VerticalLayout(space) { contents ++= cs }

}

class VerticalLayout(space: Double = 10) extends Container {

  val name: String = "VerticalLayout"

  override def layout(): Unit = {
    super.layout()

    var wmax = 0D
    var cy = 0D

    for (c <- contents) {
      c.x = padding + border.left
      c.y = cy + padding + border.top
      c.container = this
      height += c.height
      cy += c.height
      wmax = c.width max wmax

      if (c != contents.last) {
        height += space
        cy += space
      }
    }

    width += wmax
  }

}
