package xyz.hyperreal.suit

class Box extends Container {

  override protected val limit: Boolean = true

  override def layout(): Unit = {
    super.layout()
    contents.head.x = padding + border.left
    contents.head.y = padding + border.top
    width += contents.head.width
    height += contents.head.height
  }

  val name: String = "box"
}
