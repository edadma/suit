package xyz.hyperreal.suit

import java.awt.font.GlyphVector
import java.awt.geom.Rectangle2D

class JVMGlyphString private[suit] (val gv: GlyphVector, val font: Font) extends GlyphString {

  val vb: Rectangle2D = gv.getVisualBounds

  def width: Double = vb.getWidth

}
