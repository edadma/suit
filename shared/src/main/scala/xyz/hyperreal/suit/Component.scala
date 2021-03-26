package xyz.hyperreal.suit

abstract class Component extends Reactor {

  var parent: Component = _
  var x: Double = _
  var y: Double = _
  var width: Double = 0
  var height: Double = 0

  var backgroundColor: Int = Color.DARK_GRAY
  var foregroundColor: Int = Color.LIGHT_GRAY
  var font: Font = Font.default

  val mouse: Publisher = new Publisher
  val keyboard: Publisher = new Publisher

  def screen: (Double, Double) = {
    val (x1, y1) = parent.screen

    (x1 + x, y1 + y)
  }

  def contains(px: Double, py: Double): Boolean = x <= px && px < x + width && y <= py && py < y + height

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

  object timer {
    def start(action: => Unit, ms: Int): Unit = {}

    def stop(): Unit = {}
  }

}
