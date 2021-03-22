package xyz.hyperreal.suit

import scala.collection.mutable.ArrayBuffer

abstract class Component extends Publisher {

  private[suit] var parent: Component = _
  private[suit] var x: Double = _
  private[suit] var y: Double = _
  private[suit] var minwidth: Double = 0
  private[suit] var minheight: Double = 0
  private[suit] var width: Double = 0
  private[suit] var height: Double = 0

  var backgroundColor: Int = Color.DARK_GRAY
  var foregroundColor: Int = Color.LIGHT_GRAY
  var font: Font = Font.default

  protected def screen(x: Double, y: Double): (Double, Double) = {
    val (x1, y1) = parent.screen(this.x, this.y)

    (x1 + x, y1 + y)
  }

  private[suit] def size(w: Double, h: Double): Unit = {
    width = w
    height = h
  }

  private[suit] def paint(g: Graphics): Unit = {
    g.setColor(backgroundColor)
    g.fillRectangle(0, 0, width, height)
    g.setColor(foregroundColor)
    g.setFont(font)
  }

}

class Nonreactive extends Component {

  private def na = sys.error("non-reactive component")

  def publish(e: Event): Unit = na

  def deafTo(ps: Publisher*): Unit = na

  def listenTo(ps: Publisher*): Unit = na

  def subscribe(r: Reactor): Unit = na

  def unsubscribe(r: Reactor): Unit = na

  val reactions: Reactions = new Reactions {

    def +=(r: Reaction): Reactions = na

    def -=(r: Reaction): Reactions = na

    def isDefinedAt(x: Event): Boolean = na

    def apply(v1: Event): Unit = na

  }

}

class Reactive extends Component {

  val listening = new ArrayBuffer[Reactor]

  def publish(e: Event): Unit = {
    for (r <- listening)
      r.reactions(e)
  }

  def deafTo(ps: Publisher*): Unit =
    for (p <- ps)
      p.unsubscribe(this)

  def listenTo(ps: Publisher*): Unit =
    for (p <- ps)
      p.subscribe(this)

  def subscribe(r: Reactor): Unit = listening += r

  def unsubscribe(r: Reactor): Unit = listening -= r

  val reactions: Reactions = new Reactions {

    private val handlers = new ArrayBuffer[Reaction]

    def isDefinedAt(e: Event): Boolean = handlers.exists(_.isDefinedAt(e))

    def apply(e: Event): Unit =
      handlers.find(_.isDefinedAt(e)) match {
        case Some(r) => r(e)
        case None    => sys.error(s"can't react to event: $e")
      }

    def +=(r: Reaction): Reactions = {
      handlers += r
      this
    }

    def -=(r: Reaction): Reactions = {
      handlers -= r
      this
    }

  }

}
