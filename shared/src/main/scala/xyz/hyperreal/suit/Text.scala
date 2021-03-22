package xyz.hyperreal.suit

class Text(s: String, padding: Double = 5) extends Nonreactive {

  private val gs = font.getGlyphString(s)

  minwidth = gs.width //+ 2 * padding
  minheight = gs.height //+ 2 * padding

  override private[suit] def paint(g: Graphics): Unit = {
    super.paint(g)

    g.drawGlyphString(gs, x, y)
  }

}
