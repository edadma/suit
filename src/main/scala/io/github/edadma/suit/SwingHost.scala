package io.github.edadma.suit

import java.awt.{Color as AwtColor, Dimension, Font, Graphics, Graphics2D, RenderingHints}
import java.awt.event.{
  ComponentAdapter,
  ComponentEvent,
  KeyAdapter,
  KeyEvent,
  MouseAdapter,
  MouseEvent,
  MouseMotionAdapter,
  MouseWheelEvent,
  MouseWheelListener,
}
import javax.swing.{JFrame, JPanel, SwingUtilities, Timer, WindowConstants}

// ----------------------------------------------------------------------------
// SwingHost is the platform layer: it owns the JFrame, drains the engine's
// draw list through a SwingRenderer onto a JPanel, and translates AWT events
// into InputState mutations. Painting itself lives in SwingRenderer; this
// file is only window/event glue. A future SkiaHost replaces both files
// (window via GLFW, painting via SkiaRenderer) without touching the engine.
// ----------------------------------------------------------------------------
final class SwingHost(title: String, width: Int, height: Int, val engine: Engine):

  private val panel = new SuitPanel(engine)
  private val frame = new JFrame(title)

  frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE)
  panel.setPreferredSize(new Dimension(width, height))
  frame.add(panel)
  frame.pack()
  frame.setLocationRelativeTo(null)

  // Wire the engine's text-measurement up to AWT FontMetrics so layout sees
  // real glyph widths instead of the hard-coded monospaced fallback. Done
  // here (not in the engine) because FontMetrics requires a Component.
  engine.textMeasure = (s, sz) =>
    val font = new Font(Font.MONOSPACED, Font.PLAIN, sz)
    panel.getFontMetrics(font).stringWidth(s)

  def show(): Unit =
    SwingUtilities.invokeLater(() => {
      frame.setVisible(true)
      panel.requestFocusInWindow()
    })


private final class SuitPanel(engine: Engine) extends JPanel:

  setFocusable(true)
  setBackground(AwtColor.BLACK)

  // Tab is consumed by Swing for focus traversal by default — disable so we
  // see VK_TAB in our key listener.
  setFocusTraversalKeysEnabled(false)

  // 60 Hz timer that runs only while the engine has an animation in flight.
  // It checks needsFrame on each tick; once everything settles, it stops
  // itself and the host goes back to event-driven (zero idle CPU).
  private val animTimer: Timer = new Timer(16, _ => {
    if engine.needsFrame then repaint()
    else animTimer.stop()
  })

  // Mark input arrived and request a single repaint. If after rendering an
  // animation is pending, the timer fires further frames until it isn't.
  private def fireEvent(): Unit =
    engine.input.hadEvents = true
    repaint()

  // ----- input wiring -----
  addMouseMotionListener(new MouseMotionAdapter:
    override def mouseMoved(e: MouseEvent): Unit =
      engine.input.mouseX = e.getX
      engine.input.mouseY = e.getY
      fireEvent()
    override def mouseDragged(e: MouseEvent): Unit =
      engine.input.mouseX = e.getX
      engine.input.mouseY = e.getY
      fireEvent()
  )

  addMouseListener(new MouseAdapter:
    override def mousePressed(e: MouseEvent): Unit =
      e.getButton match
        case MouseEvent.BUTTON1 =>
          engine.input.mouseDown    = true
          engine.input.mousePressed = true
          fireEvent()
        case MouseEvent.BUTTON3 =>
          engine.input.mouseRightPressed = true
          fireEvent()
        case _ => ()
    override def mouseReleased(e: MouseEvent): Unit =
      e.getButton match
        case MouseEvent.BUTTON1 =>
          engine.input.mouseDown     = false
          engine.input.mouseReleased = true
          fireEvent()
        case MouseEvent.BUTTON3 =>
          engine.input.mouseRightReleased = true
          fireEvent()
        case _ => ()
  )

  addKeyListener(new KeyAdapter:
    override def keyPressed(e: KeyEvent): Unit =
      e.getKeyCode match
        case KeyEvent.VK_BACK_SPACE => engine.input.keyBackspace = true
        case KeyEvent.VK_DELETE     => engine.input.keyDelete    = true
        case KeyEvent.VK_LEFT       => engine.input.keyLeft      = true
        case KeyEvent.VK_RIGHT      => engine.input.keyRight     = true
        case KeyEvent.VK_HOME       => engine.input.keyHome      = true
        case KeyEvent.VK_END        => engine.input.keyEnd       = true
        case KeyEvent.VK_ENTER      => engine.input.keyEnter     = true
        case KeyEvent.VK_TAB        => engine.input.keyTab       = true
        case KeyEvent.VK_SPACE      => engine.input.keySpace     = true
        case KeyEvent.VK_SHIFT      => engine.input.keyShiftDown = true
        case _                      => ()
      fireEvent()
    override def keyReleased(e: KeyEvent): Unit =
      e.getKeyCode match
        case KeyEvent.VK_SHIFT => engine.input.keyShiftDown = false
        case _                 => ()
      fireEvent()
    override def keyTyped(e: KeyEvent): Unit =
      val ch = e.getKeyChar
      if ch >= 0x20 && ch != 0x7f then
        engine.input.pushChar(ch.toInt)
        fireEvent()
  )

  addComponentListener(new ComponentAdapter:
    override def componentResized(e: ComponentEvent): Unit =
      engine.markDirty()
      repaint()
  )

  addMouseWheelListener(new MouseWheelListener:
    override def mouseWheelMoved(e: MouseWheelEvent): Unit =
      val pixelsPerTick = 30f
      engine.input.wheelDeltaY = engine.input.wheelDeltaY +
        (e.getPreciseWheelRotation.toFloat * pixelsPerTick)
      fireEvent()
  )

  // ----- paint -----
  override def paintComponent(g: Graphics): Unit =
    super.paintComponent(g)
    val g2 = g.asInstanceOf[Graphics2D]
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON)
    g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
    g2.setFont(new Font(Font.MONOSPACED, Font.PLAIN, engine.theme.fontSize))

    // Re-emit the draw list only if something changed; otherwise repaint the
    // last frame's commands (Swing may call us for occlusion damage even when
    // nothing of ours changed).
    if engine.needsFrame then
      engine.runFrame(getWidth, getHeight)

    val renderer = new SwingRenderer(g2)
    var i = 0
    while i < engine.drawList.length do
      Renderer.paint(renderer, engine.drawList(i))
      i = i + 1

    // If the frame left an animation pending, kick the lazy timer. It stops
    // itself once needsFrame goes back to false.
    if engine.animating && !animTimer.isRunning then animTimer.start()
