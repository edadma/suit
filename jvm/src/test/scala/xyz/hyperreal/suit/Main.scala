package xyz.hyperreal.suit

import scala.swing.{Frame, MainFrame, SimpleSwingApplication}

object Main extends SimpleSwingApplication {
  val w: Window =
    new Window {
      contents +=
        new Horizontal() {
          contents +=
            new Button("A Button")(println("a mouse click")) {
              listenTo(mouse)

              reactions += {
                case MouseDown(x, y) => println(s"a mouse down: $x, $y")
                case MouseEnter      => println("a enter")
                case MouseExit       => println("a exit")
              }
            }
          contents +=
            new Button("B Button")(println("b mouse click")) {
              listenTo(mouse)

              reactions += {
                case MouseDown(x, y) => println(s"b mouse down: $x, $y")
                case MouseEnter      => println("b enter")
                case MouseExit       => println("b exit")
              }
            }
        }
    }

  def top: Frame =
    new MainFrame {
      contents = new WindowPanel(w)
      pack()
    }
}
