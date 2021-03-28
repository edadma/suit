package xyz.hyperreal.suit

abstract class Event

abstract class MouseEvent extends Event
abstract class MouseButtonEvent(cons: (Double, Double) => MouseButtonEvent) extends MouseEvent {
  val x: Double
  val y: Double
  def apply(x: Double, y: Double): MouseButtonEvent = cons(x, y)
}
case class MouseClick(x: Double, y: Double) extends MouseButtonEvent(MouseClick)
case class MouseDown(x: Double, y: Double) extends MouseButtonEvent(MouseDown)
case class MouseUp(x: Double, y: Double) extends MouseButtonEvent(MouseUp)
case class MouseMove(x: Double, y: Double) extends MouseEvent
case object MouseEnter extends MouseEvent
case object MouseExit extends MouseEvent

case class Keystroke(c: Char) extends Event

case object Tick extends Event
