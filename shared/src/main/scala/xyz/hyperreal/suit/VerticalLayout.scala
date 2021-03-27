package xyz.hyperreal.suit

class VerticalLayout(interspace: Double = 10) extends Container {

  override def layout(): Unit = {
    super.layout()

    width = 0
    height = 0

    for (c <- contents) {
      c.x = 0
      c.y = height
      c.parent = this
      width = c.width max width
      height += c.height

      if (c != contents.last)
        height += interspace
    }
  }

}
