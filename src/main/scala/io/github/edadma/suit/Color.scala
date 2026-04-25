package io.github.edadma.suit

// 8-bit-per-channel RGBA color. Alpha defaults to 255 (opaque). Maps to:
//   struct Color
//       r: byte
//       g: byte
//       b: byte
//       a: byte
final case class Color(r: Int, g: Int, b: Int, a: Int = 255):

  def withAlpha(newA: Int): Color = Color(r, g, b, newA)

object Color:

  val Black:       Color = Color(0, 0, 0, 255)
  val White:       Color = Color(255, 255, 255, 255)
  val Transparent: Color = Color(0, 0, 0, 0)

  def rgb(r: Int, g: Int, b: Int): Color = Color(r, g, b, 255)

  // Linear interpolation between two colors. t = 0 returns a, t = 1 returns b.
  // Used for animated focus transitions. Maps to a sysl `Color.lerp` method.
  def lerp(a: Color, b: Color, t: Float): Color =
    val tt = if t < 0f then 0f else if t > 1f then 1f else t
    Color(
      lerpInt(a.r, b.r, tt),
      lerpInt(a.g, b.g, tt),
      lerpInt(a.b, b.b, tt),
      lerpInt(a.a, b.a, tt),
    )

  private def lerpInt(x: Int, y: Int, t: Float): Int =
    val v = x + ((y - x).toFloat * t).toInt
    if v < 0 then 0 else if v > 255 then 255 else v
