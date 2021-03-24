package xyz.hyperreal.suit

abstract class Event

//case class MouseMove(x: Double, y: Double) extends Event
case class MouseClick(x: Double, y: Double) extends Event
case class MouseDown(x: Double, y: Double) extends Event
case class MouseUp(x: Double, y: Double) extends Event

case class Keystroke(c: Char) extends Event
