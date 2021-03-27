package xyz.hyperreal.suit

import scala.swing.{Frame, MainFrame, SimpleSwingApplication}

object Main extends SimpleSwingApplication {
  val w: Window =
    new Window {
      contents +=
//        new VerticalLayout {
//          contents +=
        new HorizontalLayout {
          contents +=
            new Button("A Button")(println("a mouse click")) {
              listenTo(mouse)

              reactions += {
                case MouseDown(x, y) => println(s"a mouse down: $x, $y")
                case MouseEnter      => println("a enter")
                case MouseExit       => println("a exit")
              }
            }
//              contents +=
//                new Button("BB Button")(println("b mouse click")) {
//                  listenTo(mouse)
//
//                  reactions += {
//                    case MouseDown(x, y) => println(s"b mouse down: $x, $y")
//                    case MouseEnter      => println("b enter")
//                    case MouseExit       => println("b exit")
//                  }
//                }
//            }
//          contents +=
//            new HorizontalLayout {
//              contents +=
//                new Button("CCC Button")(println("c mouse click")) {
//                  listenTo(mouse)
//
//                  reactions += {
//                    case MouseDown(x, y) => println(s"c mouse down: $x, $y")
//                    case MouseEnter      => println("c enter")
//                    case MouseExit       => println("c exit")
//                  }
//                }
//              contents +=
//                new Button("DDDD Button")(println("d mouse click")) {
//                  listenTo(mouse)
//
//                  reactions += {
//                    case MouseDown(x, y) => println(s"d mouse down: $x, $y")
//                    case MouseEnter      => println("d enter")
//                    case MouseExit       => println("d exit")
//                  }
//                }
//            }
        }
    }

  def top: Frame =
    new MainFrame {
      contents = new WindowPanel(w)
      pack()
    }
}
