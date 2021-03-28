package xyz.hyperreal.suit

import scala.collection.mutable.ArrayBuffer

abstract class Container extends Component {

  val name: String

  protected val limit = false

  class Contents extends ArrayBuffer[Component] {
    override def addOne(c: Component): Contents.this.type = {
      require(!limit || isEmpty, "only one component can be added")
      c.parent = Container.this
      super.addOne(c)
    }
  }

  val contents = new Contents
  var within: Option[Component] = None

  listenTo(mouse)

  reactions += {
    case MouseExit if within.isDefined =>
      within.get.mouse publish MouseExit
      within = None
    case MouseMove(mx, my) =>
      contents.find(c => c.contains(mx - c.x, my - c.y)) match {
        case None if within.isDefined =>
//          println(s"exit $mx, $my - $name$screen")
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
//              println(s"enter $mx, $my - $name$screen")
              c.mouse publish MouseEnter
          }

          c.mouse publish MouseMove(mx - c.x, my - c.y)
      }
    case e: MouseButtonEvent =>
      contents.find(c => c.contains(e.x - c.x, e.y - c.y)) match {
        case None    =>
        case Some(c) => c.mouse publish e(e.x - c.x, e.y - c.y)
      }
  }

  override def layout(): Unit = {
    super.layout()

    for (c <- contents)
      c.layout()
  }

  override def paintComponent(g: Graphics): Unit = {
    border.paint(g, this)
//    paint(g.graphics(border.left + padding, border.top + padding))

    for (c <- contents)
      c.paintComponent(g.graphics(c.x, c.y))
  }

//  override def paint(g: Graphics): Unit = {
//    super.paint(g)
//
//    for (c <- contents) {
//      val (sx, sy) = c.screen
//
//      c.paintComponent(new Graphics(sx, sy, g.gc))
//    }
//  }

}
