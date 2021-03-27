package xyz.hyperreal.suit

class VerticalLayout(space: Double = 10) extends Container {

  override def layout(): Unit = {
    super.layout()

    var wmax = 0D
    var cy = 0D

    for (c <- contents) {
      c.x = padding + border.left
      c.y = cy + padding + border.top
      c.parent = this
      wmax = c.width max wmax
      height += c.height
      cy += c.height

      if (c != contents.last) {
        height += space
        cy += space
      }
    }

    width += wmax
  }

}
