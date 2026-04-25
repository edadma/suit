package io.github.edadma.suit

import java.awt.{BasicStroke, Color as AwtColor, Dimension, Font, Graphics, Graphics2D, RenderingHints}
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
// SwingHost is the platform layer: it owns the JFrame, paints the Engine's
// draw list onto a JPanel, and translates AWT events into InputState mutations.
// Nothing inside `package suit` other than this file imports javax.swing or
// java.awt — when porting to sysl, this whole file is replaced by a SLIX
// DrawEngine + display-server bridge.
// ----------------------------------------------------------------------------
final class SwingHost(title: String, width: Int, height: Int, val engine: Engine):

  private val panel = new SuitPanel(engine)
  private val frame = new JFrame(title)

  frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE)
  panel.setPreferredSize(new Dimension(width, height))
  frame.add(panel)
  frame.pack()
  frame.setLocationRelativeTo(null)

  def show(): Unit =
    SwingUtilities.invokeLater(() => {
      frame.setVisible(true)
      panel.requestFocusInWindow()
    })


private final class SuitPanel(engine: Engine) extends JPanel:

  setFocusable(true)
  setBackground(AwtColor.BLACK)

  // Per-paint clip stack for nested PushClip/PopClip pairs.
  private val clipStack: scala.collection.mutable.Stack[java.awt.Shape] =
    scala.collection.mutable.Stack.empty

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

    var i = 0
    while i < engine.drawList.length do
      paintCommand(g2, engine.drawList(i))
      i = i + 1

    // If the frame left an animation pending, kick the lazy timer. It stops
    // itself once needsFrame goes back to false.
    if engine.animating && !animTimer.isRunning then animTimer.start()

  private def paintCommand(g2: Graphics2D, cmd: DrawCommand): Unit = cmd match
    case FillRect(r, c) =>
      g2.setColor(toAwt(c))
      g2.fillRect(r.x, r.y, r.w, r.h)
    case StrokeRect(r, c) =>
      g2.setColor(toAwt(c))
      g2.setStroke(new BasicStroke(1f))
      g2.drawRect(r.x, r.y, r.w - 1, r.h - 1)
    case FillRoundRect(r, radius, c) =>
      g2.setColor(toAwt(c))
      g2.fillRoundRect(r.x, r.y, r.w, r.h, radius * 2, radius * 2)
    case StrokeRoundRect(r, radius, c) =>
      g2.setColor(toAwt(c))
      g2.setStroke(new BasicStroke(1f))
      g2.drawRoundRect(r.x, r.y, r.w - 1, r.h - 1, radius * 2, radius * 2)
    case DrawShadow(r, radius, shadow) =>
      paintShadow(g2, r, radius, shadow)
    case DrawText(x, y, text, c) =>
      g2.setColor(toAwt(c))
      g2.drawString(text, x, y)
    case PushClip(r) =>
      clipStack.push(g2.getClip)
      g2.clipRect(r.x, r.y, r.w, r.h)   // intersects with current clip
    case PopClip =>
      if clipStack.nonEmpty then g2.setClip(clipStack.pop())
      else g2.setClip(null)

  // Cheap approximation of a Gaussian-blurred drop shadow: paint several
  // outward-expanding rounded strokes whose alpha falls off linearly. The
  // sysl/DrawEngine port replaces this with a real blurred sprite.
  private def paintShadow(g2: Graphics2D, r: Rect, radius: Int, sh: Shadow): Unit =
    val layers = if sh.blur < 1 then 1 else sh.blur
    val baseAlpha = sh.color.a
    var i = 0
    while i < layers do
      // i goes 0..layers-1 ; outer rings get fainter alpha.
      val falloff = 1f - (i.toFloat / layers.toFloat)
      val alpha   = (baseAlpha.toFloat * falloff * 0.6f).toInt
      val ring    = Color(sh.color.r, sh.color.g, sh.color.b, if alpha < 0 then 0 else alpha)
      val out     = sh.spread + i
      val rr      = Rect(r.x + sh.offsetX - out, r.y + sh.offsetY - out, r.w + out * 2, r.h + out * 2)
      g2.setColor(toAwt(ring))
      g2.setStroke(new BasicStroke(1f))
      g2.drawRoundRect(rr.x, rr.y, rr.w - 1, rr.h - 1, (radius + out) * 2, (radius + out) * 2)
      i = i + 1

  private def toAwt(c: Color): AwtColor = new AwtColor(c.r, c.g, c.b, c.a)
