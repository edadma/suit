package xyz.hyperreal.suit

trait Font {

  val em: Double
  val ascent: Double
  val descent: Double
  val height: Double

  def getGlyphString(s: String): GlyphString

}

object Font {

  def apply(typeface: String, size: Double, style: TextStyle): Font =
    PlatformDependent.getFont(typeface: String, size: Double, style: TextStyle)

  def default: Font = apply("Nimbus Sans L", 18, TextStyle.PLAIN)

}

abstract class TextPosition

object TextPosition {

  case object RIGHT extends TextPosition
  case object LEFT extends TextPosition
  case object BELOW extends TextPosition
  case object BELOW_RIGHT extends TextPosition
  case object ABOVE extends TextPosition
  case object BASELINE extends TextPosition

}

abstract class TextStyle(val name: String)

object TextStyle {

  case object ITALIC extends TextStyle("italic")
  case object BOLD extends TextStyle("bold")
  case object BOLD_ITALIC extends TextStyle("bold italic")
  case object PLAIN extends TextStyle("plain")

}
