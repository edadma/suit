package xyz.hyperreal.suit

import xyz.hyperreal.hsl.HSL

import math.exp

object Button {

  def apply(text: String)(action: => Unit) = new Button(text)(action)

}

class Button(text: String)(action: => Unit) extends Component {

  val name: String = "Button"

  private val gs = font.getGlyphString(text)

  protected val mouseEnterBackgroundColor: Int = Color.BLUE
  protected val mouseDownBackgroundColor: Int = Color.GREEN

  val solidBorder = new SolidBorder(1, Color.BLACK)

  padding = 5
  border = solidBorder

  listenTo(mouse)
  listenTo(timer)

  val granularity = 20

  var t = 0.0
  var hsl: HSL = null

  reactions += {
    case MouseEnter =>
      t = 0
      hsl = HSL(.26, 1, 0)
      timer.start("enter", granularity)
    case Tick("enter") =>
      t += granularity / 1000.0

      if (t >= .3)
        timer.stop("enter")

      val (r, g, b) = hsl.toRGB

      solidBorder.color = r << 16 | g << 8 | b
      hsl = hsl.luminosity(1 / (1 + exp(-(50 * t - 5))) * .5)
      repaint()
    case MouseExit =>
      t = 0
      hsl = HSL(.26, 1, 0.5)
      timer.start("exit", granularity)
    case Tick("exit") =>
      t += granularity / 1000.0

      if (t >= .3)
        timer.stop("exit")

      val (r, g, b) = hsl.toRGB

      solidBorder.color = r << 16 | g << 8 | b
      hsl = hsl.luminosity(1 / (1 + exp(-(50 * (.3 - t) - 5))) * .5)
      repaint()
    case MouseClick(_, _) => action
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
