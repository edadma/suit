package xyz.hyperreal.suit

import scala.swing.{Frame, MainFrame, SimpleSwingApplication}

object Main extends SimpleSwingApplication {
  val w: Window =
    Window(HorizontalLayout(Button("[Start")(println("start")), Button("Stop")(println("stop"))))

  def top: Frame =
    new MainFrame {
      contents = new WindowPanel(w)
      pack()
    }
}
