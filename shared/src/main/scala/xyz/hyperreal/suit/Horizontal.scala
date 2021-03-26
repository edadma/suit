package xyz.hyperreal.suit

class Horizontal(interspace: Double = 5) extends Container {

  override def layout(): Unit = {
    super.layout()

    width = 0
    height = 0

    for (c <- contents) {
      c.x = width
      c.y = 0
      c.parent = this
      width += c.width
      height = c.height max height

      if (c != contents.last)
        width += interspace
    }
  }

}
