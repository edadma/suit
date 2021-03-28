package xyz.hyperreal.suit

class HorizontalLayout(space: Double = 10) extends Container {

  val name: String = "HorizontalLayout"

  override def layout(): Unit = {
    super.layout()

    var hmax = 0D
    var cx = 0D

    for (c <- contents) {
      c.x = cx + padding + border.left
      c.y = padding + border.top
      c.container = this
      width += c.width
      cx += c.width
      hmax = c.height max hmax

      if (c != contents.last) {
        width += space
        cx += space
      }
    }

    height += hmax
  }

}
