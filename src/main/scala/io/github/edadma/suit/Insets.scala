package io.github.edadma.suit

// CSS-style padding/margin descriptor with separate sides. Maps to:
//   struct Insets
//       top: int
//       right: int
//       bottom: int
//       left: int
final case class Insets(top: Int, right: Int, bottom: Int, left: Int):

  def horizontal: Int = left + right

  def vertical: Int = top + bottom

object Insets:

  val Zero: Insets = Insets(0, 0, 0, 0)

  def all(v: Int): Insets = Insets(v, v, v, v)

  def sym(h: Int, v: Int): Insets = Insets(v, h, v, h)
