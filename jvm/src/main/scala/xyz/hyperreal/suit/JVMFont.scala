package xyz.hyperreal.suit

import java.awt.{Font => JFont}

class JVMFont(val font: JFont) extends Font {

  import JVM._

  def getGlyphString(s: String): GlyphString = new JVMGlyphString(font.createGlyphVector(FRC, s))

}
