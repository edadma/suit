package xyz.hyperreal.suit

class Text(s: String) extends Component {

  private val gs = font.getGlyphString(s)

  override def paint(g: Graphics): Unit = {
    super.paint(g)
    g.drawGlyphString(gs, padding, padding, TextPosition.BELOW_RIGHT)
  }

  override def layout(): Unit = {
    super.layout()
    width += gs.width
    height += gs.height
  }

}
