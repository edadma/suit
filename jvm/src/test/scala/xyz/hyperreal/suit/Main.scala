package xyz.hyperreal.suit

import scala.swing.{Frame, MainFrame, SimpleSwingApplication}

object Main extends SimpleSwingApplication {
  val w =
    new Window {
      contents += new Text("Some Text asdfasdfasdfsadfasdf")
    }

  def top: Frame =
    new MainFrame {
      contents = new WindowPanel(w)
      pack()
    }
}
