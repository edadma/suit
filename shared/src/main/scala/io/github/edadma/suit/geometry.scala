package io.github.edadma.suit

// Pure geometry and colour values — no SDL types, no platform dependency. Spatial
// quantities are `Double`: SDL3 renders in floats and the constraint-layout engine
// wants sub-pixel precision. Colour channels are 0–255 `Int`s.

/** A 2-D vector — a position relative to some origin, or a displacement. */
final case class Offset(x: Double, y: Double):
  def +(o: Offset): Offset = Offset(x + o.x, y + o.y)
  def -(o: Offset): Offset = Offset(x - o.x, y - o.y)

object Offset:
  val zero: Offset = Offset(0, 0)

/** A width × height extent. */
final case class Size(width: Double, height: Double)

object Size:
  val zero: Size = Size(0, 0)

/** An axis-aligned rectangle, given as a top-left origin and an extent. */
final case class Rect(x: Double, y: Double, width: Double, height: Double):
  def right: Double  = x + width
  def bottom: Double = y + height

  /** True if `(px, py)` lies inside, treating the right/bottom edges as exclusive so
    * adjacent rectangles never both claim a boundary pixel. */
  def contains(px: Double, py: Double): Boolean =
    px >= x && px < right && py >= y && py < bottom

object Rect:
  def at(origin: Offset, size: Size): Rect = Rect(origin.x, origin.y, size.width, size.height)

/** Per-edge insets — the vocabulary padding and margins are expressed in. */
final case class EdgeInsets(top: Double, right: Double, bottom: Double, left: Double):
  def horizontal: Double = left + right
  def vertical: Double   = top + bottom

object EdgeInsets:
  val zero: EdgeInsets           = EdgeInsets(0, 0, 0, 0)
  def all(v: Double): EdgeInsets = EdgeInsets(v, v, v, v)
  def symmetric(horizontal: Double, vertical: Double): EdgeInsets =
    EdgeInsets(vertical, horizontal, vertical, horizontal)

/** A point inside a box, given in fractional coordinates independent of the box's
  * size: `(-1, -1)` is the top-left corner, `(0, 0)` the centre, `(1, 1)` the
  * bottom-right. This is SwiftUI's and Flutter's alignment model — the same value
  * positions a child in a container of any size, which is exactly what alignment,
  * centring, and z-stacks need. */
final case class Alignment(x: Double, y: Double):

  /** The offset that places a `child` of the given size inside `container` so the
    * child sits at this alignment. With `Alignment.center` the child is centred; with
    * `topLeft` it lands at the origin; with `bottomRight` its far corner meets the
    * container's. */
  def inscribe(child: Size, container: Size): Offset =
    Offset(
      (container.width - child.width) * (x + 1) / 2,
      (container.height - child.height) * (y + 1) / 2,
    )

object Alignment:
  val topLeft: Alignment      = Alignment(-1, -1)
  val topCenter: Alignment    = Alignment(0, -1)
  val topRight: Alignment     = Alignment(1, -1)
  val centerLeft: Alignment   = Alignment(-1, 0)
  val center: Alignment       = Alignment(0, 0)
  val centerRight: Alignment  = Alignment(1, 0)
  val bottomLeft: Alignment   = Alignment(-1, 1)
  val bottomCenter: Alignment = Alignment(0, 1)
  val bottomRight: Alignment  = Alignment(1, 1)

/** An RGBA colour, each channel 0–255. */
final case class Color(r: Int, g: Int, b: Int, a: Int = 255):
  def withAlpha(newA: Int): Color = copy(a = newA)

  /** Lowercase 8-digit hex `rrggbbaa` — a convenient text form for a colour (the
    * inverse of [[Color.parse]]). The DSL hands colours to the render tree as typed
    * values, so this is a utility, not a transport format. */
  def toHex: String = f"$r%02x$g%02x$b%02x$a%02x"

object Color:
  val black:       Color = Color(0, 0, 0)
  val white:       Color = Color(255, 255, 255)
  val transparent: Color = Color(0, 0, 0, 0)

  /** From a packed `0xRRGGBB` literal, fully opaque. */
  def rgb(hex: Int): Color = Color((hex >> 16) & 0xff, (hex >> 8) & 0xff, hex & 0xff)

  /** Parse `rrggbb` or `rrggbbaa`, with an optional leading `#`. The inverse of
    * [[Color.toHex]]. */
  def parse(s: String): Color =
    val h                 = if s.startsWith("#") then s.substring(1) else s
    def byte(i: Int): Int = Integer.parseInt(h.substring(i, i + 2), 16)
    if h.length >= 8 then Color(byte(0), byte(2), byte(4), byte(6))
    else Color(byte(0), byte(2), byte(4))
