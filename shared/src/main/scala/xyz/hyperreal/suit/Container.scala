package xyz.hyperreal.suit

import scala.collection.mutable.ArrayBuffer

abstract class Container extends Component {

  protected val limit = false

  class Contents extends ArrayBuffer[Component] {
    override def addOne(elem: Component): Contents.this.type = {
      require(!limit || isEmpty, "only one component can be added")
      super.addOne(elem)
    }
  }

  val contents = new Contents

  listenTo(mouse)

  reactions += {
    case MouseDown(x, y) =>
      contents.find(_.contains(x, y)) match {
        case None    =>
        case Some(c) => c.mouse publish MouseDown(x - c.x, y - c.y)
      }
  }

  def layout(): Unit = {
    for (c <- contents)
      c.layout()
  }

  override def paint(g: Graphics): Unit = {
    super.paint(g)

    for (c <- contents)
      c.paint(new Graphics(c.x, c.y, g.gc))
  }

}

class Composite extends Container {

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

class Single(c: Component) extends Composite {

  contents += c

}
