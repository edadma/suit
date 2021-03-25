package xyz.hyperreal.suit

class Text(s: String, padding: Double = 10) extends Component {

  private val gs = font.getGlyphString(s)

  override def paint(g: Graphics): Unit = {
    super.paint(g)
    g.drawGlyphString(gs, x + padding, y + padding)
  }

  def layout(): Unit = {
    width = gs.width + 2 * padding
    height = gs.height + 2 * padding
  }

}
