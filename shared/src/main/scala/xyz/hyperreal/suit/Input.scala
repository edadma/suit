package xyz.hyperreal.suit

class Input(placeholder: String, chars: Int) extends Component {

  override val name: String = "Input"

  protected val mouseEnterBorderColor: Int = Color.GREEN
  protected val mouseEnterBackgroundColor: Int = Color.GRAY

  private val solidBorder = new SolidBorder(1, foregroundColor)
  private val pgs = font.getGlyphString(placeholder)

  val text = new StringBuilder

  override val focusable: Boolean = true

  padding = 5
  border = solidBorder

  listenTo(mouse, keyboard)

  reactions += {
    case MouseEnter =>
      solidBorder.color = mouseEnterBorderColor
      repaint()
    case MouseExit =>
      solidBorder.color = foregroundColor
      repaint()
    case MouseClick(_, _) =>
      focus(true)
      println("focussed")
    case Keystroke(c) => println(c)
  }

  override def paint(g: Graphics): Unit =
    if (text.isEmpty) {
      g.setColor(Color.GRAY)
      g.drawGlyphString(pgs, 0, 0, TextPosition.BELOW_RIGHT)
    } else super.paint(g)

  override def layout(): Unit = {
    super.layout()
    width += chars * font.em
    height += font.height
  }

}
