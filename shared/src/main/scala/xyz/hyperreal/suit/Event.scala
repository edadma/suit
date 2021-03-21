package xyz.hyperreal.suit

abstract class Event

//case class MouseMove(x: Double, y: Double) extends Event
case object MouseClick extends Event

case class Keystroke(c: Char) extends Event
