package xyz.hyperreal.suit

import java.awt.{BasicStroke, Color, Font, RenderingHints}
import java.awt.font.{FontRenderContext, GlyphVector}
import java.awt.geom.{Ellipse2D, Path2D, Rectangle2D}
import scala.swing.{Graphics2D, Panel}
import scala.swing.Swing._

class WindowPanel(win: Window) extends Panel {

  private val STYLE_MAP =
    Map[TextStyle, Int](
      TextStyle.PLAIN -> Font.PLAIN,
      TextStyle.ITALIC -> Font.ITALIC
    )

  val gc = new JVMGraphicsContext

  override protected def paintComponent(g: Graphics2D): Unit = {
    super.paintComponent(g)

    g.setRenderingHints(
      new RenderingHints(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON))
    g.setRenderingHints(new RenderingHints(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON))
    g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)

    gc.graphics2D = g
  }

}
