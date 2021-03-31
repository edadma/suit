package xyz.hyperreal.suit

object Label {

  def apply(s: String) = new Label(s)

}

class Label(s: String) extends Component {

  val name: String = "Label"

  private var gs: GlyphString = _
  private var text: String = _

  setText(s)

  def setText(s: String): Unit = {
    text = s
    gs = font.getGlyphString(s)
    repaint()
  }

  override def paint(g: Graphics): Unit = {
    super.paint(g)
    g.drawGlyphString(gs, 0, 0, TextPosition.BELOW_RIGHT)
  }

  override def layout(): Unit = {
    super.layout()
    width += gs.width
    height += gs.font.height
  }

}
