package xyz.hyperreal.suit

class Button(text: String)(action: => Unit) extends Component {

  private val gs = font.getGlyphString(text)

  protected val hoverBackgroundColor: Int = Color.BLUE
  protected val mouseDownBackgroundColor: Int = Color.GREEN

  border = new SolidBorder(1, foregroundColor)

  listenTo(mouse)

  reactions += {
    case MouseClick(_, _) => action
  }

  override def paint(g: Graphics): Unit = {
    super.paint(g)
    g.drawGlyphString(gs, 0, 0, TextPosition.BELOW_RIGHT)
//    g.drawGlyphString(gs, padding, padding, TextPosition.BELOW_RIGHT)
//    g.drawRectangle(0, 0, width - thickness, height - thickness)
  }

  override def layout(): Unit = {
    super.layout()
    width += gs.width
    height += gs.height
  }

}
