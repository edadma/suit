package io.github.edadma.suit.sysl

import io.github.edadma.trisc.TProgram

import java.awt.event.{KeyAdapter, KeyEvent, MouseAdapter, MouseEvent, MouseMotionAdapter, WindowAdapter, WindowEvent}
import java.awt.{BasicStroke, Color as AwtColor, Dimension, Font, Graphics, Graphics2D, RenderingHints}
import java.util.concurrent.LinkedBlockingQueue
import javax.swing.{JFrame, JPanel, SwingUtilities, WindowConstants}

import scala.collection.mutable.ArrayBuffer

// SyslEngineHost — Swing dev shell for the all-sysl engine. Wires the
// host externs (host_poll_event, host_event_*, host_now_ms, host_*_frame,
// host_fill_rect/draw_text/draw_image) to a JFrame + JPanel so a sysl
// run_loop drives a real window.
//
// Threading model:
//   * Sysl run_loop runs on a worker thread (started in show()). It
//     drains events via host_poll_event and emits draws via the
//     host_fill_rect / host_draw_text / host_draw_image stubs.
//   * AWT events come in on the EDT and push HostEvents onto a
//     thread-safe queue; the worker thread polls.
//   * Draw commands are appended to a `pending` buffer on the worker
//     thread. host_present_frame swaps `pending` to `current` under a
//     lock and asks Swing to repaint; the EDT's paintComponent replays
//     `current` to Graphics2D.
//
// The engine itself doesn't know any of this — it only sees the externs
// it always uses. Same engine, different host: in the OS port, these
// externs hit the framebuffer + input drivers instead.
final class SyslEngineHost(title: String, screenW: Int, screenH: Int, host: SyslHost):

  // Event tags must match suit/engine.sysl's EVENT_* constants.
  private val EVENT_QUIT        = 1L
  private val EVENT_MOUSE_PRESS = 2L
  private val EVENT_MOUSE_REL   = 3L
  private val EVENT_MOUSE_MOVE  = 4L
  private val EVENT_KEY_DOWN    = 5L

  private case class HostEvent(tag: Long, x: Long, y: Long, key: Long, text: String)

  private val eventQueue                  = new LinkedBlockingQueue[HostEvent]()
  @volatile private var lastEvent: HostEvent = HostEvent(0L, 0L, 0L, 0L, "")

  private sealed trait DrawCmd
  private case class FillRect(x: Int, y: Int, w: Int, h: Int, color: AwtColor)                  extends DrawCmd
  private case class StrokeRect(x: Int, y: Int, w: Int, h: Int, color: AwtColor)                extends DrawCmd
  private case class FillRoundRect(x: Int, y: Int, w: Int, h: Int, r: Int, color: AwtColor)     extends DrawCmd
  private case class StrokeRoundRect(x: Int, y: Int, w: Int, h: Int, r: Int, color: AwtColor)   extends DrawCmd
  private case class DrawText(x: Int, y: Int, text: String, color: AwtColor)                    extends DrawCmd
  private case class DrawImage(x: Int, y: Int, w: Int, h: Int, source: String)                  extends DrawCmd

  private var pending = ArrayBuffer.empty[DrawCmd]
  private var current = ArrayBuffer.empty[DrawCmd]
  private val bufferLock = new Object

  private val started = System.currentTimeMillis()

  private val canvas: JPanel = new JPanel:
    setPreferredSize(new Dimension(screenW, screenH))
    setBackground(new AwtColor(22, 22, 32))
    setFocusable(true)

    override def paintComponent(g: Graphics): Unit =
      super.paintComponent(g)
      val g2 = g.asInstanceOf[Graphics2D]
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
      g2.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14))
      val cmds = bufferLock.synchronized(current.toArray)
      val savedStroke = g2.getStroke
      g2.setStroke(new BasicStroke(1f))
      cmds.foreach {
        case FillRect(x, y, w, h, c) =>
          g2.setColor(c)
          g2.fillRect(x, y, w, h)
        case StrokeRect(x, y, w, h, c) =>
          g2.setColor(c)
          g2.drawRect(x, y, w - 1, h - 1)
        case FillRoundRect(x, y, w, h, r, c) =>
          g2.setColor(c)
          g2.fillRoundRect(x, y, w, h, r * 2, r * 2)
        case StrokeRoundRect(x, y, w, h, r, c) =>
          g2.setColor(c)
          g2.drawRoundRect(x, y, w - 1, h - 1, r * 2, r * 2)
        case DrawText(x, y, t, c) =>
          g2.setColor(c)
          g2.drawString(t, x, y)
        case DrawImage(x, y, w, h, src) =>
          // Stub renderer — a future host wires real image loading via
          // ImageIO or similar. For the demo we just sketch the box
          // and label it with the source name.
          g2.setColor(new AwtColor(60, 60, 80))
          g2.fillRect(x, y, w, h)
          g2.setColor(new AwtColor(180, 180, 200))
          g2.drawString(s"[$src]", x + 4, y + 16)
      }
      g2.setStroke(savedStroke)

  canvas.addMouseListener(new MouseAdapter:
    override def mousePressed(e: MouseEvent): Unit =
      eventQueue.put(HostEvent(EVENT_MOUSE_PRESS, e.getX.toLong, e.getY.toLong, 0L, ""))
      canvas.requestFocusInWindow()

    override def mouseReleased(e: MouseEvent): Unit =
      eventQueue.put(HostEvent(EVENT_MOUSE_REL, e.getX.toLong, e.getY.toLong, 0L, ""))
  )
  canvas.addMouseMotionListener(new MouseMotionAdapter:
    override def mouseMoved(e: MouseEvent): Unit =
      eventQueue.put(HostEvent(EVENT_MOUSE_MOVE, e.getX.toLong, e.getY.toLong, 0L, ""))

    override def mouseDragged(e: MouseEvent): Unit =
      eventQueue.put(HostEvent(EVENT_MOUSE_MOVE, e.getX.toLong, e.getY.toLong, 0L, ""))
  )
  canvas.addKeyListener(new KeyAdapter:
    override def keyPressed(e: KeyEvent): Unit =
      // Special keys arrive through keyPressed (keyTyped doesn't fire
      // for them reliably). Currently we only forward Backspace; arrows
      // and Tab land here when the focus model grows to need them.
      if e.getKeyCode == KeyEvent.VK_BACK_SPACE then
        eventQueue.put(HostEvent(EVENT_KEY_DOWN, 0L, 0L, 8L, ""))

    override def keyTyped(e: KeyEvent): Unit =
      val ch = e.getKeyChar
      if ch >= 32 && ch != 127 then
        eventQueue.put(HostEvent(EVENT_KEY_DOWN, 0L, 0L, ch.toLong, ch.toString))
  )

  private val frame: JFrame = new JFrame(title):
    setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE)
    add(canvas)
    pack()
    addWindowListener(new WindowAdapter:
      override def windowClosing(e: WindowEvent): Unit =
        eventQueue.put(HostEvent(EVENT_QUIT, 0L, 0L, 0L, ""))
    )

  // -- extern wiring ---------------------------------------------------------

  host.register("host_poll_event", { _ =>
    val ev = eventQueue.poll()
    if ev == null then SyslHost.long(0L)
    else
      lastEvent = ev
      SyslHost.long(ev.tag)
  })
  host.register("host_event_x",    { _ => SyslHost.long(lastEvent.x) })
  host.register("host_event_y",    { _ => SyslHost.long(lastEvent.y) })
  host.register("host_event_key",  { _ => SyslHost.long(lastEvent.key) })
  host.register("host_event_text", { _ => SyslHost.string(lastEvent.text) })
  host.register("host_now_ms",     { _ => SyslHost.long(System.currentTimeMillis() - started) })
  host.register("host_present_frame", { _ =>
    bufferLock.synchronized {
      val tmp = current
      current = pending
      pending = tmp
      pending.clear()
    }
    SwingUtilities.invokeLater(() => canvas.repaint())
    SyslHost.unit
  })
  host.register("host_sleep_until_next_frame", { _ =>
    Thread.sleep(16L)
    SyslHost.unit
  })
  host.register("host_fill_rect", {
    case List(x, y, w, h, r, g, b, a) =>
      pending += FillRect(
        SyslHost.asLong(x).toInt, SyslHost.asLong(y).toInt,
        SyslHost.asLong(w).toInt, SyslHost.asLong(h).toInt,
        new AwtColor(
          SyslHost.asLong(r).toInt, SyslHost.asLong(g).toInt,
          SyslHost.asLong(b).toInt, SyslHost.asLong(a).toInt,
        ),
      )
      SyslHost.unit
    case other => sys.error(s"host_fill_rect: $other")
  })
  host.register("host_stroke_rect", {
    case List(x, y, w, h, r, g, b, a) =>
      pending += StrokeRect(
        SyslHost.asLong(x).toInt, SyslHost.asLong(y).toInt,
        SyslHost.asLong(w).toInt, SyslHost.asLong(h).toInt,
        new AwtColor(
          SyslHost.asLong(r).toInt, SyslHost.asLong(g).toInt,
          SyslHost.asLong(b).toInt, SyslHost.asLong(a).toInt,
        ),
      )
      SyslHost.unit
    case other => sys.error(s"host_stroke_rect: $other")
  })
  host.register("host_fill_round_rect", {
    case List(x, y, w, h, rad, r, g, b, a) =>
      pending += FillRoundRect(
        SyslHost.asLong(x).toInt, SyslHost.asLong(y).toInt,
        SyslHost.asLong(w).toInt, SyslHost.asLong(h).toInt,
        SyslHost.asLong(rad).toInt,
        new AwtColor(
          SyslHost.asLong(r).toInt, SyslHost.asLong(g).toInt,
          SyslHost.asLong(b).toInt, SyslHost.asLong(a).toInt,
        ),
      )
      SyslHost.unit
    case other => sys.error(s"host_fill_round_rect: $other")
  })
  host.register("host_stroke_round_rect", {
    case List(x, y, w, h, rad, r, g, b, a) =>
      pending += StrokeRoundRect(
        SyslHost.asLong(x).toInt, SyslHost.asLong(y).toInt,
        SyslHost.asLong(w).toInt, SyslHost.asLong(h).toInt,
        SyslHost.asLong(rad).toInt,
        new AwtColor(
          SyslHost.asLong(r).toInt, SyslHost.asLong(g).toInt,
          SyslHost.asLong(b).toInt, SyslHost.asLong(a).toInt,
        ),
      )
      SyslHost.unit
    case other => sys.error(s"host_stroke_round_rect: $other")
  })
  host.register("host_draw_text", {
    case List(x, y, text, r, g, b, a) =>
      pending += DrawText(
        SyslHost.asLong(x).toInt, SyslHost.asLong(y).toInt,
        SyslHost.asString(text),
        new AwtColor(
          SyslHost.asLong(r).toInt, SyslHost.asLong(g).toInt,
          SyslHost.asLong(b).toInt, SyslHost.asLong(a).toInt,
        ),
      )
      SyslHost.unit
    case other => sys.error(s"host_draw_text: $other")
  })
  host.register("host_draw_image", {
    case List(x, y, w, h, src) =>
      pending += DrawImage(
        SyslHost.asLong(x).toInt, SyslHost.asLong(y).toInt,
        SyslHost.asLong(w).toInt, SyslHost.asLong(h).toInt,
        SyslHost.asString(src),
      )
      SyslHost.unit
    case other => sys.error(s"host_draw_image: $other")
  })
  // host_record gets a default no-op stub. Tests register their own
  // capturing handler before this constructor runs (the install order
  // in registerBuiltins is "last write wins"), but in interactive
  // mode we want the records swallowed silently.
  host.register("host_record", { _ => SyslHost.unit })

  // -- public API ------------------------------------------------------------

  def show(program: TProgram): Unit =
    SwingUtilities.invokeLater { () =>
      frame.setLocationRelativeTo(null)
      frame.setVisible(true)
      canvas.requestFocusInWindow()
    }
    val worker = new Thread(new Runnable { def run(): Unit = { host.run(program); () } }, "sysl-run-loop")
    worker.setDaemon(true)
    worker.start()
