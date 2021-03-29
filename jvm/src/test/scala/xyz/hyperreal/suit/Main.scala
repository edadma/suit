package xyz.hyperreal.suit

import scala.swing.{Frame, MainFrame, SimpleSwingApplication}

object Main extends SimpleSwingApplication {
  val w: Window =
    new Window {
      contents += new Button("Button")(println("click"))
    }

  def top: Frame =
    new MainFrame {
      contents = new WindowPanel(w)
      pack()
    }
}
