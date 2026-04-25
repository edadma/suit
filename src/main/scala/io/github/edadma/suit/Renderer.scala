package io.github.edadma.suit

// The platform-painter abstraction. Engine builds a flat list of immutable
// DrawCommands; the host then drains that list through whatever Renderer
// fits its backend — `SwingRenderer` for AWT/Swing, a future `SkiaRenderer`
// for Skia, or anything else.
//
// Splitting this out from the host makes porting mechanical: a new backend
// only needs to implement these eight methods. Window management, event
// delivery, and the paint loop stay in the host file.
//
// Sysl shape: an interface (or struct + function-pointer table) per backend.
trait Renderer:

  def fillRect(rect: Rect, color: Color): Unit
  def strokeRect(rect: Rect, color: Color): Unit
  def fillRoundRect(rect: Rect, radius: Int, color: Color): Unit
  def strokeRoundRect(rect: Rect, radius: Int, color: Color): Unit
  def drawShadow(rect: Rect, radius: Int, shadow: Shadow): Unit
  def drawText(x: Int, y: Int, text: String, color: Color): Unit
  def drawImage(rect: Rect, source: String): Unit
  def pushClip(rect: Rect): Unit
  def popClip(): Unit


object Renderer:

  // Generic dispatcher so hosts don't have to write their own match. Adding
  // a new DrawCommand variant requires extending Renderer + this match —
  // both are checked at compile time.
  def paint(r: Renderer, cmd: DrawCommand): Unit = cmd match
    case FillRect(rect, color)               => r.fillRect(rect, color)
    case StrokeRect(rect, color)             => r.strokeRect(rect, color)
    case FillRoundRect(rect, radius, color)  => r.fillRoundRect(rect, radius, color)
    case StrokeRoundRect(rect, radius, color)=> r.strokeRoundRect(rect, radius, color)
    case DrawShadow(rect, radius, shadow)    => r.drawShadow(rect, radius, shadow)
    case DrawText(x, y, text, color)         => r.drawText(x, y, text, color)
    case DrawImage(rect, source)             => r.drawImage(rect, source)
    case PushClip(rect)                      => r.pushClip(rect)
    case PopClip                             => r.popClip()
