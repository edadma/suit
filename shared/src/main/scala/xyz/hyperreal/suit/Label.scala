package xyz.hyperreal.suit

object Label {

  def apply(s: String) = new Label(s)

}

class Label(s: String) extends Component {

  val name: String = "Label"

  protected var gs: GlyphString = _
  private var text0: String = _

  text = s

  def text_=(t: String): Unit = {
    text0 = t
    gs = font.getGlyphString(t)
    repaint()
  }

  def text: String = text0

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
