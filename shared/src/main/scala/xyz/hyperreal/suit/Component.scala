package xyz.hyperreal.suit

abstract class Component extends Publisher {

  private[suit] var parent: Component = _
  private[suit] var x: Double = _
  private[suit] var y: Double = _
  private[suit] var width: Double = 0
  private[suit] var height: Double = 0

  var backgroundColor: Int = Color.DARK_GRAY
  var foregroundColor: Int = Color.LIGHT_GRAY
  var font: Font = Font.default

  val mouse: Publisher = new Publisher {}

  private[suit] def screen(x: Double, y: Double): (Double, Double) = {
    val (x1, y1) = parent.screen(this.x, this.y)

    (x1 + x, y1 + y)
  }

  private[suit] def contains(px: Double, py: Double): Boolean = x <= px && px < x + width && y <= py && py < y + height

  def size(w: Double, h: Double): Unit = {
    width = w
    height = h
  }

  def layout(): Unit

  def paint(g: Graphics): Unit = {
    g.setColor(backgroundColor)
    g.fillRectangle(0, 0, width, height)
    g.setColor(foregroundColor)
    g.setFont(font)
  }

}

abstract class Nonreactive extends Component {

  private def na = sys.error("non-reactive component")

  override def publish(e: Event): Unit = na

  override def deafTo(ps: Publisher*): Unit = na

  override def listenTo(ps: Publisher*): Unit = na

  override def subscribe(r: Reactor): Unit = na

  override def unsubscribe(r: Reactor): Unit = na

  override val reactions: Reactions = new Reactions {

    def +=(r: Reaction): Reactions = na

    def -=(r: Reaction): Reactions = na

    def isDefinedAt(x: Event): Boolean = na

    def apply(v1: Event): Unit = na

  }

}
