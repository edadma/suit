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
  var within: Option[Component] = None

  listenTo(mouse)

  reactions += {
    case MouseExit if within.isDefined =>
      within.get.mouse publish MouseExit
      within = None
    case MouseMove(x, y) =>
      contents.find(_.contains(x, y)) match {
        case None if within.isDefined =>
          within.get.mouse publish MouseExit
          within = None
        case None =>
        case Some(c) =>
          within match {
            case Some(cw) =>
              if (c != cw) {
                cw.mouse publish MouseExit
                c.mouse publish MouseEnter
                within = Some(c)
              }
            case None =>
              c.mouse publish MouseEnter
              within = Some(c)
          }

          c.mouse publish MouseMove(x - c.x, y - c.y)
      }
    case e: MouseButtonEvent =>
      contents.find(_.contains(e.x, e.y)) match {
        case None    =>
        case Some(c) => c.mouse publish e(e.x - c.x, e.y - c.y)
      }
  }

  def layout(): Unit = {
    for (c <- contents)
      c.layout()
  }

  override def paint(g: Graphics): Unit = {
    super.paint(g)

    for (c <- contents) {
      println(c.x, c.y)
      c.paint(new Graphics(c.x, c.y, g.gc))
    }
  }

}
