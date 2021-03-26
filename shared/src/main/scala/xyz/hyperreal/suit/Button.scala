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
    case MouseDown(x, y)  => println(s"mouse down: $x, $y")
    case MouseEnter       => println("enter")
    case MouseExit        => println("exit")
  }

  override def paint(g: Graphics): Unit = {
    super.paint(g)
    g.drawGlyphString(gs, x + padding, y + padding)
    g.drawRectangle(x, y, width - thickness, height - thickness)
  }

  def layout(): Unit = {
    width = gs.width + 2 * padding
    height = gs.height + 2 * padding
  }

}
