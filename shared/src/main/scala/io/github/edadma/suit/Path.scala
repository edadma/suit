package io.github.edadma.suit

import scala.collection.mutable

/** A segment of a [[Path]] — the move/line/arc/close vocabulary every 2D imaging model (Cairo,
  * HTML canvas, PostScript/PDF) shares. A path is a value built from these; a [[Canvas]] strokes
  * or fills it. Cubic Béziers are intentionally omitted for now (the Cairo backend would need an
  * absolute curve-to binding); add a `CurveTo` case when a curved shape calls for one.
  */
enum PathSeg:
  /** Begin a new subpath at `(x, y)` without drawing. */
  case MoveTo(x: Double, y: Double)
  /** A straight line from the current point to `(x, y)`. */
  case LineTo(x: Double, y: Double)
  /** A circular arc centred at `(cx, cy)` of radius `r`, from `start` to `end` radians. `negative`
    * sweeps clockwise (decreasing angle) rather than counter-clockwise. */
  case Arc(cx: Double, cy: Double, r: Double, start: Double, end: Double, negative: Boolean)
  /** Close the current subpath with a straight line back to its start. */
  case Close

/** A vector path: an ordered list of [[PathSeg]]s built with a [[PathBuilder]] and handed to a
  * [[Canvas]] to stroke or fill. It is a plain value, so it is trivially asserted on a
  * `RecordingCanvas`; the Cairo backend replays it into the engine's own path calls, which means a
  * stroked outline gets real line joins instead of the gaps/overlaps you get stroking each edge as
  * a separate segment.
  */
final case class Path(segments: Vector[PathSeg]):
  def isEmpty: Boolean = segments.isEmpty

  /** The axis-aligned bounding box of the path's geometry (arc extents included). Empty for an
    * empty path. The backend uses it to resolve gradient geometry; for a solid colour it is
    * irrelevant. */
  def bounds: Rect =
    if segments.isEmpty then Rect(0, 0, 0, 0)
    else
      var minX = Double.MaxValue
      var minY = Double.MaxValue
      var maxX = Double.MinValue
      var maxY = Double.MinValue
      def pt(x: Double, y: Double): Unit =
        if x < minX then minX = x
        if x > maxX then maxX = x
        if y < minY then minY = y
        if y > maxY then maxY = y
      segments.foreach {
        case PathSeg.MoveTo(x, y)            => pt(x, y)
        case PathSeg.LineTo(x, y)            => pt(x, y)
        case PathSeg.Arc(cx, cy, r, _, _, _) => pt(cx - r, cy - r); pt(cx + r, cy + r)
        case PathSeg.Close                   => ()
      }
      if minX > maxX then Rect(0, 0, 0, 0) else Rect(minX, minY, maxX - minX, maxY - minY)

object Path:
  val empty: Path = Path(Vector.empty)

  def builder(): PathBuilder = new PathBuilder

  /** A polyline through `points`, optionally closed back to the first — the common case for a
    * vector outline like a ship, an asteroid, or a saucer. */
  def polyline(points: Seq[Offset], closed: Boolean = true): Path =
    if points.isEmpty then empty
    else
      val bld = builder()
      bld.moveTo(points.head.x, points.head.y)
      var i = 1
      while i < points.length do
        bld.lineTo(points(i).x, points(i).y)
        i += 1
      if closed then bld.close()
      bld.build()

/** A mutable builder for a [[Path]]; each call returns `this` so segments chain. */
final class PathBuilder:
  private val segs = mutable.ArrayBuffer.empty[PathSeg]
  def moveTo(x: Double, y: Double): PathBuilder = { segs += PathSeg.MoveTo(x, y); this }
  def lineTo(x: Double, y: Double): PathBuilder = { segs += PathSeg.LineTo(x, y); this }
  def arc(cx: Double, cy: Double, r: Double, start: Double, end: Double, negative: Boolean = false): PathBuilder =
    { segs += PathSeg.Arc(cx, cy, r, start, end, negative); this }
  def close(): PathBuilder = { segs += PathSeg.Close; this }
  def build(): Path = Path(segs.toVector)

/** How consecutive stroked segments meet at a vertex. */
enum LineJoin:
  case Miter, Round, Bevel

/** How the open ends of a stroked path are finished. */
enum LineCap:
  case Butt, Round, Square
