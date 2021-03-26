package xyz.hyperreal.suit

import java.awt.RenderingHints
import scala.swing.{Graphics2D, Panel}
import scala.swing.Swing._
import scala.swing.event.{MouseClicked, MouseExited, MouseMoved, MousePressed, MouseReleased}

class WindowPanel(win: Window) extends Panel {

  val gc = new JVMGraphicsContext

  win.layout()
  preferredSize = (win.width.toInt, win.height.toInt)

  listenTo(mouse.clicks, mouse.moves)

  reactions += {
    case MouseExited(_, _, _)         => win.mouse publish MouseExit
    case MouseMoved(_, p, _)          => win.mouse publish MouseMove(p.getX, p.getY)
    case MouseClicked(_, p, _, _, _)  => win.mouse publish MouseClick(p.getX, p.getY)
    case MousePressed(_, p, _, _, _)  => win.mouse publish MouseDown(p.getX, p.getY)
    case MouseReleased(_, p, _, _, _) => win.mouse publish MouseUp(p.getX, p.getY)
  }

  override protected def paintComponent(g: Graphics2D): Unit = {
    super.paintComponent(g)

    g.setRenderingHints(
      new RenderingHints(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON))
    g.setRenderingHints(new RenderingHints(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON))
    g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)

    gc.graphics2D = g
    win.paint(new Graphics(0, 0, gc))
  }

}
