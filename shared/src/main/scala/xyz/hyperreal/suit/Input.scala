package xyz.hyperreal.suit

class Input(placeholder: String, chars: Int) extends Label("") {

  override val name: String = "Input"

  protected val mouseEnterBorderColor: Int = Color.GREEN
  protected val mouseEnterBackgroundColor: Int = Color.GRAY

  private val solidBorder = new SolidBorder(1, foregroundColor)
  private val pgs = font.getGlyphString(placeholder)

  padding = 5
  border = solidBorder

  listenTo(mouse)
//  listenTo(timer)
//
//  val granularity = 20
//
//  var t = 0.0
//  var hsl: HSL = null

  reactions += {
    case MouseEnter =>
      solidBorder.color = mouseEnterBorderColor
      repaint()

//      t = 0
//      hsl = HSL(.26, 1, 0)
//      timer.start("enter", granularity)
//    case Tick("enter") =>
//      t += granularity / 1000.0
//
//      if (t >= .3)
//        timer.stop("enter")
//
//      val (r, g, b) = hsl.toRGB
//
//      solidBorder.color = r << 16 | g << 8 | b
//      hsl = hsl.luminosity(1 / (1 + exp(-(50 * t - 5))) * .5)
//      repaint()
    case MouseExit =>
      solidBorder.color = foregroundColor
      repaint()

//      t = 0
//      hsl = HSL(.26, 1, 0.5)
//      timer.start("exit", granularity)
//    case Tick("exit") =>
//      t += granularity / 1000.0
//
//      if (t >= .3)
//        timer.stop("exit")
//
//      val (r, g, b) = hsl.toRGB
//
//      solidBorder.color = r << 16 | g << 8 | b
//      hsl = hsl.luminosity(1 / (1 + exp(-(50 * (.3 - t) - 5))) * .5)
//      repaint()
    case MouseClick(_, _) =>
  }

  override def paint(g: Graphics): Unit =
    if (text.isEmpty) {
      g.setColor(Color.GRAY)
      g.drawGlyphString(pgs, 0, 0, TextPosition.BELOW_RIGHT)
    } else super.paint(g)

  override def layout(): Unit = {
    super.layout()
    width = 2 * padding + border.left + border.right + chars * font.em
  }

}
