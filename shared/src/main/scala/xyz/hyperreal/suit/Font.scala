package xyz.hyperreal.suit

abstract class Font(typeface: String, size: Double, style: TextStyle) {

  def textDimensions(text: String): (Double, Double)

}

abstract class TextPosition
case object RIGHT extends TextPosition
case object LEFT extends TextPosition
case object BELOW extends TextPosition
case object ABOVE extends TextPosition
case object BASELINE extends TextPosition

abstract class TextStyle(val name: String)

object TextStyle {

  case object ITALIC extends TextStyle("italic")

  case object BOLD extends TextStyle("bold")

  case object BOLD_ITALIC extends TextStyle("bold italic")

  case object PLAIN extends TextStyle("plain")

}
