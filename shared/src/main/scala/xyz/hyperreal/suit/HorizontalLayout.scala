package xyz.hyperreal.suit

class HorizontalLayout(interspace: Double = 10) extends Container {

  override def layout(): Unit = {
    super.layout()

    for (c <- contents) {
      c.x = width + padding + border.left
      c.y = padding + border.top
      c.parent = this
      width += c.width
      height = c.height max height

      if (c != contents.last)
        width += interspace
    }
  }

}
