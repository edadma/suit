package io.github.edadma.suit

// A rectangle in screen-space pixels. Maps to:
//   struct Rect
//       x: int
//       y: int
//       w: int
//       h: int
final case class Rect(x: Int, y: Int, w: Int, h: Int):

  def right: Int = x + w

  def bottom: Int = y + h

  def contains(px: Int, py: Int): Boolean =
    px >= x && px < x + w && py >= y && py < y + h
