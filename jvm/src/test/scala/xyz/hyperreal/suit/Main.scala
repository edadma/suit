package xyz.hyperreal.suit

import scala.swing.{Frame, MainFrame, SimpleSwingApplication}

object Main extends SimpleSwingApplication {
  val w: Window =
    Window(
      VerticalLayout(HorizontalLayout(Button("asdf")(println("start")), Button("asdf 1")(println("stop"))),
                     HorizontalLayout(Label("asdf 12"), new Input("asdf 123", 10))))

  def top: Frame =
    new MainFrame {
      contents = new WindowPanel(w)
      pack()
    }
}
