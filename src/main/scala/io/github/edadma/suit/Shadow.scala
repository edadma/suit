package io.github.edadma.suit

// Drop-shadow / outer-glow descriptor. The shadow is laid down before its
// owner's fill, so spread > 0 produces a halo around the widget. blur is
// approximated by the Swing renderer with several semi-transparent rounded
// strokes; in the sysl port DrawEngine has native blur. Maps to:
//
//   struct Shadow
//       color: Color
//       blur: int
//       spread: int
//       offsetX: int
//       offsetY: int
final case class Shadow(
    color:   Color,
    blur:    Int,
    spread:  Int,
    offsetX: Int,
    offsetY: Int,
)

object Shadow:

  val None: Shadow = Shadow(Color.Transparent, 0, 0, 0, 0)

  // A focus ring: zero offset, small spread, mostly-transparent accent.
  def focusRing(accent: Color): Shadow =
    Shadow(accent.withAlpha(110), blur = 4, spread = 3, offsetX = 0, offsetY = 0)
