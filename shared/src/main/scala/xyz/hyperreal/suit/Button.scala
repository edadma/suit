package xyz.hyperreal.suit

class Button(text: String)(action: => Unit) extends Component {

  private val gs = font.getGlyphString(text)

  protected val padding: Double = 10
  protected val thickness: Double = 1
  protected val hoverBackgroundColor: Int = Color.BLUE
  protected val mouseDownBackgroundColor: Int = Color.GREEN

  listenTo(mouse)

  reactions += {
    case MouseClick(_, _) => action
  }

  override def paint(g: Graphics): Unit = {
    println(text, x, y, width, height)
    super.paint(g)
    g.drawGlyphString(gs, padding, padding)
    g.drawRectangle(0, 0, width - thickness, height - thickness)
  }

  def layout(): Unit = {
    width = gs.width + 2 * padding
    height = gs.height + 2 * padding
  }

}
