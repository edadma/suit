package xyz.hyperreal.suit

abstract class Event

abstract class MouseEvent extends Event
abstract class MousePositionalEvent(cons: (Double, Double) => MousePositionalEvent) extends MouseEvent {
  val x: Double
  val y: Double
  def apply(x: Double, y: Double): MousePositionalEvent = cons(x, y)
}
case class MouseMove(x: Double, y: Double) extends MousePositionalEvent(MouseMove)
case class MouseClick(x: Double, y: Double) extends MousePositionalEvent(MouseClick)
case class MouseDown(x: Double, y: Double) extends MousePositionalEvent(MouseDown)
case class MouseUp(x: Double, y: Double) extends MousePositionalEvent(MouseUp)

case class Keystroke(c: Char) extends Event
