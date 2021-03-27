package xyz.hyperreal.suit

import scala.collection.mutable.ArrayBuffer

abstract class Container extends Component {

  protected val limit = false

  class Contents extends ArrayBuffer[Component] {
    override def addOne(elem: Component): Contents.this.type = {
      require(!limit || isEmpty, "only one component can be added")
      elem.parent = Container.this
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
                within = Some(c)
                cw.mouse publish MouseExit
                c.mouse publish MouseEnter
              }
            case None =>
              within = Some(c)
              c.mouse publish MouseEnter
          }

          c.mouse publish MouseMove(x - c.x, y - c.y)
      }
    case e: MouseButtonEvent =>
      contents.find(_.contains(e.x, e.y)) match {
        case None    =>
        case Some(c) => c.mouse publish e(e.x - c.x, e.y - c.y)
      }
  }

  override def layout(): Unit = {
    super.layout()

    for (c <- contents)
      c.layout()
  }

  override def paint(g: Graphics): Unit = {
    super.paint(g)

    for (c <- contents) {
      val (sx, sy) = c.screen

      c.paintComponent(new Graphics(sx, sy, g.gc))
    }
  }

}
