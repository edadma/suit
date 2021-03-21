package xyz.hyperreal.suit

abstract class Component extends Publisher {

  private[suit] var parent: Component = _
  private[suit] var x: Double = _
  private[suit] var y: Double = _
  private[suit] var width: Double = 0
  private[suit] var height: Double = 0

  var backgroundColor: Int = Color.DARK_GRAY
  var foregroundColor: Int = Color.LIGHT_GRAY
  var font: Font

  protected def screen(x: Double, y: Double): (Double, Double) = {
    val (x1, y1) = parent.screen(this.x, this.y)

    (x1 + x, y1 + y)
  }

  protected def paint(g: Graphics): Unit = {
    g.fillColor = backgroundColor
    g.fillRectangle(0, 0, width, height)
    g.fillColor = foregroundColor
  }

}

class Nonreactive extends Component {

  private def na = sys.error("non-reactive component")

  def publish(e: Event): Unit = na

  def deafTo(ps: Publisher*): Unit = na

  def listenTo(ps: Publisher*): Unit = na

  val reactions: Reactions = new Reactions {

    def +=(r: Reaction): Reactions = na

    def -=(r: Reaction): Reactions = na

    def isDefinedAt(x: Event): Boolean = na

    def apply(v1: Event): Unit = na

  }

  var font: Font = _

}
