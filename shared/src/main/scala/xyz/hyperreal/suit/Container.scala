package xyz.hyperreal.suit

import scala.collection.mutable.ArrayBuffer

abstract class Container extends Component {

  protected val limit = false

  private class Contents extends ArrayBuffer[Component] {
    override def addOne(elem: Component): Contents = {
      require(!limit || isEmpty, "only one component can be added")
      super.addOne(elem)
    }
  }

  protected val contents = new Contents

  private[suit] def layout(): Unit

}

class Composite extends Container {

  override protected val limit: Boolean = true

  private[suit] def layout(): Unit = {
    contents.head.x = 0
    contents.head.y = 0
    contents.head.parent = this
  }

}

class Single(c: Component) extends Composite {

  contents += c

}
