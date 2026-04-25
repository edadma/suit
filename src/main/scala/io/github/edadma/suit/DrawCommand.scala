package io.github.edadma.suit

// A retained-mode toolkit still emits a flat list of draw commands per frame;
// the platform layer is the only thing that knows how to actually paint pixels.
// The sealed-trait + final-case-class shape maps to a sysl tagged-union enum:
//
//   enum DrawCommand
//       FillRect(rect: Rect, color: Color)
//       StrokeRect(rect: Rect, color: Color)
//       FillRoundRect(rect: Rect, radius: int, color: Color)
//       StrokeRoundRect(rect: Rect, radius: int, color: Color)
//       DrawShadow(rect: Rect, radius: int, shadow: Shadow)
//       DrawText(x: int, y: int, text: string, color: Color)
//       PushClip(rect: Rect)
//       PopClip
sealed trait DrawCommand

final case class FillRect(rect: Rect, color: Color)              extends DrawCommand
final case class StrokeRect(rect: Rect, color: Color)            extends DrawCommand
final case class FillRoundRect(rect: Rect, radius: Int, color: Color)   extends DrawCommand
final case class StrokeRoundRect(rect: Rect, radius: Int, color: Color) extends DrawCommand
final case class DrawShadow(rect: Rect, radius: Int, shadow: Shadow)    extends DrawCommand
final case class DrawText(x: Int, y: Int, text: String, color: Color)   extends DrawCommand
final case class PushClip(rect: Rect)                            extends DrawCommand
case object PopClip                                              extends DrawCommand
