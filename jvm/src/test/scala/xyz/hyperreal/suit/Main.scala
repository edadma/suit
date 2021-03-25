package xyz.hyperreal.suit

import scala.swing.{Frame, MainFrame, SimpleSwingApplication}

object Main extends SimpleSwingApplication {
  val w: Window =
    new Window {
      contents += new Button("OK")(println("mouse click")) {
        reactions += {
          case MouseMove(x, y) => println(s"moved $x, $y")
        }
      }
    }

  def top: Frame =
    new MainFrame {
      contents = new WindowPanel(w)
      pack()
    }
}
