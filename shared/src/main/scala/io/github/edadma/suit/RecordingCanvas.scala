package io.github.edadma.suit

import scala.collection.mutable

// A Canvas that records every call instead of drawing — the headless paint target.
// It is the analogue of suit-old's draw-command list: a test renders a tree against
// a RecordingCanvas and asserts on `commands` to verify exactly what would be
// painted, with no window in the loop. Pure Scala, so it (and the layout engine and
// styling model it exercises) is testable off-device.
final class RecordingCanvas extends Canvas:
  import RecordingCanvas.Command

  val commands: mutable.ArrayBuffer[Command] = mutable.ArrayBuffer.empty

  def fillRect(rect: Rect, paint: Paint): Unit =
    commands += Command.FillRect(rect, paint)

  def clearRect(rect: Rect): Unit =
    commands += Command.ClearRect(rect)

  def strokeRect(rect: Rect, paint: Paint, width: Double): Unit =
    commands += Command.StrokeRect(rect, paint, width)

  def fillRoundedRect(rect: Rect, radius: BorderRadius, paint: Paint): Unit =
    commands += Command.FillRoundedRect(rect, radius, paint)

  def strokeRoundedRect(rect: Rect, radius: BorderRadius, paint: Paint, width: Double): Unit =
    commands += Command.StrokeRoundedRect(rect, radius, paint, width)

  def fillCircle(center: Offset, radius: Double, paint: Paint): Unit =
    commands += Command.FillCircle(center, radius, paint)

  def line(a: Offset, b: Offset, width: Double, paint: Paint): Unit =
    commands += Command.Line(a, b, width, paint)

  def strokePath(path: Path, paint: Paint, width: Double, join: LineJoin, cap: LineCap): Unit =
    commands += Command.StrokePath(path, paint, width, join, cap)

  def fillPath(path: Path, paint: Paint): Unit =
    commands += Command.FillPath(path, paint)

  def drawSvg(image: SvgImage, rect: Rect): Unit =
    commands += Command.DrawSvg(image, rect)

  def drawImage(image: RasterImage, rect: Rect): Unit =
    commands += Command.DrawImage(image, rect)

  def drawShadow(rect: Rect, radius: BorderRadius, shadow: Shadow): Unit =
    commands += Command.DrawShadow(rect, radius, shadow)

  def drawText(origin: Offset, text: String, style: TextStyle): Unit =
    commands += Command.DrawText(origin, text, style)

  def pushOpacity(alpha: Double): Unit =
    commands += Command.PushOpacity(alpha)

  def popOpacity(): Unit =
    commands += Command.PopOpacity

  def pushClip(rect: Rect, radius: BorderRadius): Unit =
    commands += Command.PushClip(rect, radius)

  def popClip(): Unit =
    commands += Command.PopClip

  def pushTranslate(dx: Double, dy: Double): Unit =
    commands += Command.PushTranslate(dx, dy)

  def popTranslate(): Unit =
    commands += Command.PopTranslate

object RecordingCanvas:
  /** One recorded paint call. Defined on the companion rather than nested in the
    * instance so tests can name `RecordingCanvas.Command.FillRect(...)` to assert
    * against the captured list. */
  enum Command:
    case FillRect(rect: Rect, paint: Paint)
    case ClearRect(rect: Rect)
    case StrokeRect(rect: Rect, paint: Paint, width: Double)
    case FillRoundedRect(rect: Rect, radius: BorderRadius, paint: Paint)
    case StrokeRoundedRect(rect: Rect, radius: BorderRadius, paint: Paint, width: Double)
    case FillCircle(center: Offset, radius: Double, paint: Paint)
    case Line(a: Offset, b: Offset, width: Double, paint: Paint)
    case StrokePath(path: Path, paint: Paint, width: Double, join: LineJoin, cap: LineCap)
    case FillPath(path: Path, paint: Paint)
    case DrawSvg(image: SvgImage, rect: Rect)
    case DrawImage(image: RasterImage, rect: Rect)
    case DrawShadow(rect: Rect, radius: BorderRadius, shadow: Shadow)
    case DrawText(origin: Offset, text: String, style: TextStyle)
    case PushOpacity(alpha: Double)
    case PopOpacity
    case PushClip(rect: Rect, radius: BorderRadius)
    case PopClip
    case PushTranslate(dx: Double, dy: Double)
    case PopTranslate
