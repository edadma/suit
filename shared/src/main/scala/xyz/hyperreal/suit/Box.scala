package xyz.hyperreal.suit

class Box extends Container {

  override protected val limit: Boolean = true

  override def layout(): Unit = {
    super.layout()
    contents.head.x = padding
    contents.head.y = padding
    width += contents.head.width
    height += contents.head.height
  }

}
