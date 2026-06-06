package io.github.edadma.suit

import scala.collection.mutable

// A Canvas that records every call instead of drawing — the headless paint target.
// It is the analogue of suit-old's draw-command list: a test renders a tree against
// a RecordingCanvas and asserts on `commands` to verify exactly what would be
// painted, with no SDL window in the loop. Pure Scala, so it (and the layout engine
// it exercises) is testable off-device.
final class RecordingCanvas extends Canvas:

  enum Command:
    case FillRect(rect: Rect, color: Color)
    case StrokeRect(rect: Rect, color: Color, width: Double)
    case FillCircle(center: Offset, radius: Double, color: Color)
    case Line(a: Offset, b: Offset, width: Double, color: Color)

  val commands: mutable.ArrayBuffer[Command] = mutable.ArrayBuffer.empty

  def fillRect(rect: Rect, color: Color): Unit =
    commands += Command.FillRect(rect, color)

  def strokeRect(rect: Rect, color: Color, width: Double): Unit =
    commands += Command.StrokeRect(rect, color, width)

  def fillCircle(center: Offset, radius: Double, color: Color): Unit =
    commands += Command.FillCircle(center, radius, color)

  def line(a: Offset, b: Offset, width: Double, color: Color): Unit =
    commands += Command.Line(a, b, width, color)
