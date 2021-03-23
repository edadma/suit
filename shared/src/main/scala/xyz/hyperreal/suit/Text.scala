package xyz.hyperreal.suit

class Text(s: String, padding: Double = 10) extends Nonreactive {

  private val gs = font.getGlyphString(s)

  width = gs.width + 2 * padding
  height = gs.height + 2 * padding

  override def paint(g: Graphics): Unit = {
    super.paint(g)

    g.drawGlyphString(gs, x + padding, y + padding)
  }

  def layout(): Unit = {}

}
