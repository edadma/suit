package xyz.hyperreal.suit

import java.awt.{Font => JFont}

class JVMFont(val font: JFont) extends Font {

  import JVM._

  val (ascent, descent, height) = {
    val gv = font.createGlyphVector(FRC, "[")
    val vb = gv.getVisualBounds

    (-vb.getY, vb.getHeight + vb.getY, vb.getHeight)
  }

  def getGlyphString(s: String): GlyphString = new JVMGlyphString(font.createGlyphVector(FRC, s), this)

}
