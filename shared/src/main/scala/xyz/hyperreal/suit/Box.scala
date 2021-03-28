package xyz.hyperreal.suit

class Box extends Container {

  val name = "Box"

  override protected val limit: Boolean = true

  override def layout(): Unit = {
    super.layout()
    contents.head.x = padding + border.left
    contents.head.y = padding + border.top
    width += contents.head.width
    height += contents.head.height
  }

}
