package xyz.hyperreal.suit

class Box extends Container {

  override protected val limit: Boolean = true

  override def layout(): Unit = {
    super.layout()
    contents.head.x = 0
    contents.head.y = 0
    contents.head.parent = this
    width = contents.head.width
    height = contents.head.height
  }

}
