package io.github.edadma.suit

// Module-level constants. In sysl these become `val` declarations at module scope.
// Applications can swap themes by writing into a Theme instance held by the Engine.
final class Theme:

  var bg: Color           = Color(22, 22, 32)
  var fg: Color           = Color(220, 220, 240)
  var muted: Color        = Color(140, 140, 160)
  var accent: Color       = Color(60, 130, 255)
  var accentText: Color   = Color(255, 255, 255)

  var btnBg: Color        = Color(40, 40, 55)
  var btnHoverBg: Color   = Color(55, 55, 75)
  var btnPressedBg: Color = Color(30, 30, 45)
  var btnDisabledBg: Color = Color(30, 30, 38)

  var inputBg: Color        = Color(15, 15, 22)
  var inputFocusBg: Color   = Color(20, 22, 32)   // subtle lift when focused
  var inputBorder: Color    = Color(60, 60, 80)
  var inputHoverBorder: Color = Color(95, 95, 130)
  var focusBorder: Color    = Color(60, 130, 255)
  var focusRing: Color      = Color(40, 95, 200)  // outer halo around a focused field

  var border: Color       = Color(60, 60, 80)

  var fontSize: Int       = 13
  var charWidth: Int      = 8       // monospaced — width of one glyph in px
  var lineHeight: Int     = 18

  var padding: Int        = 8
  var spacing: Int        = 6
  var btnPaddingX: Int    = 12
  var btnPaddingY: Int    = 6
  var inputPaddingX: Int  = 6
  var inputPaddingY: Int  = 4

  // Corner radii (px). Used by FillRoundRect / StrokeRoundRect.
  var btnRadius: Int      = 4
  var inputRadius: Int    = 4
  var checkRadius: Int    = 3
