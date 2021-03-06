package xyz.hyperreal.suit

import java.awt.Color
import java.awt.RenderingHints
import scala.swing.{Graphics2D, Panel}
import scala.swing.Swing._
import scala.swing.event.{KeyTyped, MouseClicked, MouseExited, MouseMoved, MousePressed, MouseReleased}

class WindowPanel(win: Window) extends Panel {

  val gc = new JVMGraphicsContext
  val EDGE = 0

  win.layout()

  preferredSize = (win.width.toInt + 2 * EDGE, win.height.toInt + 2 * EDGE)
  background = Color.BLACK

  focusable = true
  requestFocus()

  listenTo(mouse.clicks, mouse.moves, keys)

  reactions += {
    case MouseExited(_, _, _)         => win.mouse publish MouseExit
    case MouseMoved(_, p, _)          => win.mouse publish MouseMove(p.getX, p.getY)
    case MouseClicked(_, p, _, _, _)  => win.mouse publish MouseClick(p.getX, p.getY)
    case MousePressed(_, p, _, _, _)  => win.mouse publish MouseDown(p.getX, p.getY)
    case MouseReleased(_, p, _, _, _) => win.mouse publish MouseUp(p.getX, p.getY)
    case KeyTyped(_, c, _, _)         => if (win.focussed) win.keyboard publish Keystroke(c)
  }

  override protected def paintComponent(g: Graphics2D): Unit = {
    super.paintComponent(g)

    g.setRenderingHints(
      new RenderingHints(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON))
    g.setRenderingHints(new RenderingHints(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON))
    g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)

    gc.graphics2D = g
    win.x = EDGE
    win.y = EDGE

    def windowPaint(): Unit = win.paintComponent(new Graphics(EDGE, EDGE, gc))

    win.repaintWindow = () => {
      windowPaint()
      repaint()
    }
    windowPaint()
  }

}
