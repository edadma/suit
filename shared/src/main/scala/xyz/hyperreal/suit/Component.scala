package xyz.hyperreal.suit

abstract class Component extends Reactor {

  val name: String

  var container: Container = _
  var x: Double = _
  var y: Double = _
  var width: Double = 0
  var height: Double = 0
  var padding: Double = 0
  var border: Border = EmptyBorder

  var backgroundColor: Int = Color.DARK_GRAY
  var foregroundColor: Int = Color.LIGHT_GRAY
  var font: Font = Font.default

  val focusable: Boolean = false
  var focussed: Boolean = false

  val mouse: Publisher = new Publisher
  val keyboard: Publisher = new Publisher
  val timer: TimerPublisher = new TimerPublisher

//  def screen: (Double, Double) = {
//    val (px, py) = parent.screen
//
//    (px + x, py + y)
//  }

  def focus(f: Boolean): Unit =
    if (focusable) {
      focussed = f
      container.changeFocus(f)
    }

  def repaint(): Unit = if (container ne null) container.repaint()

  def contains(px: Double, py: Double): Boolean = 0 <= px && px < width && 0 <= py && py < height

  def size(w: Double, h: Double): Unit = {
    width = w
    height = h
  }

  def layout(): Unit = {
    width = 2 * padding + border.left + border.right
    height = 2 * padding + border.top + border.bottom
  }

  def paintComponent(g: Graphics): Unit = {
    border.paint(g, this)
    paint(g.graphics(border.left + padding, border.top + padding))
  }

  def paint(g: Graphics): Unit = {
    g.setColor(foregroundColor)
    g.setFont(font)
  }

  override def toString: String = s"$name [$width, $height]"

}
