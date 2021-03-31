package xyz.hyperreal.suit

import xyz.hyperreal.hsl.HSL

import math.exp

object Button {

  def apply(text: String)(action: => Unit): Button = new Button(text) { def click(): Unit = action }

}

abstract class Button(s: String) extends Label(s) {

  override val name: String = "Button"

  protected val mouseEnterBorderColor: Int = Color.GREEN
  protected val mouseEnterBackgroundColor: Int = Color.GRAY

  val solidBorder = new SolidRoundBorder(1, foregroundColor)

  def click(): Unit

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
    case MouseClick(_, _) => click()
  }

}
