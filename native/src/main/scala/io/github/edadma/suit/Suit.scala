package io.github.edadma.suit

import scala.collection.mutable
import io.github.edadma.vdom.{Host, Scheduler, VNode, createRoot}
import io.github.edadma.sdl3.{Color => SdlColor, *}
import io.github.edadma.sdl3_ttf.{ttfInit, ttfQuit}

// The runtime. It owns the SDL window and renderer, installs the vdom host and
// scheduler seams, mounts the application, and runs the frame loop.
//
// The frame loop is the bridge between vdom's React-style batched updates and a
// game-style render loop. vdom never paints on its own; it requests work through two
// injectable scheduler seams (a microtask queue for re-renders, a macrotask queue
// for passive effects). Here those seams enqueue thunks that the loop drains once per
// iteration. A state write therefore flows: handler → `useState` setter →
// `Scheduler.enqueueUpdate` → microtask enqueued → loop drains it → reconciler
// re-renders → render tree mutated → `markDirty` bubbles to the root → the next
// iteration repaints. Painting happens only when the tree was marked dirty, so an
// idle UI costs nothing but event polling.
object Suit:

  /** A system font used when the caller does not supply one. Present on macOS, where
    * suit is developed; pass an explicit `fontPath` on other platforms. */
  val defaultFont = "/System/Library/Fonts/Helvetica.ttc"

  /** Open a window of the given size and run `app` in it until the window is closed.
    * Blocks on the frame loop for the lifetime of the window. `fontPath` is the TrueType
    * (or collection) file text is rendered with. */
  def run(title: String, width: Int, height: Int, fontPath: String = defaultFont)(app: VNode): Unit =
    setMainReady()
    if !init(INIT_VIDEO) then
      System.err.println(s"suit: SDL_Init failed: ${error}")
      return
    if !ttfInit() then
      System.err.println(s"suit: TTF_Init failed: ${error}")
      return

    val window   = createWindow(title, width, height)
    val renderer = window.createRenderer()
    renderer.setVSync(true)

    // Text needs fonts both to measure (at layout) and to paint. One book serves both:
    // the measurer the layout pass consults and the canvas that rasterises glyphs.
    val fonts = new FontBook(fontPath)
    TextMeasurer.installed = new SdlTextMeasurer(fonts)
    val canvas = new SdlCanvas(renderer, fonts)

    val root = new RenderRoot(Size(width.toDouble, height.toDouble))

    // Install the host binding and the scheduler seams before the first render. vdom
    // enqueues render and effect thunks onto these queues; the loop drains both.
    Host.config = new SuitHostConfig
    val microtasks = mutable.Queue.empty[() => Unit]
    val macrotasks = mutable.Queue.empty[() => Unit]
    Scheduler.scheduleMicrotask = fn => microtasks.enqueue(fn)
    Scheduler.scheduleMacrotask = fn => macrotasks.enqueue(fn)

    def drainScheduler(): Unit =
      while microtasks.nonEmpty do microtasks.dequeue().apply()
      while macrotasks.nonEmpty do macrotasks.dequeue().apply()

    createRoot(root).render(app)
    drainScheduler() // commit any effects the initial mount queued

    // Lay out once before entering the loop so the very first pointer event hit-tests
    // against a positioned tree rather than zero-size objects.
    root.layout(Constraints.tight(root.windowSize))
    root.dirty = true

    // Pointer events route by hit-test; key events route to whatever the focus manager
    // currently holds (a press updates focus through the same pointer router).
    val focusManager = new FocusManager
    val router       = new PointerRouter(root, focusManager)
    val keyRouter    = new KeyRouter(focusManager)
    val clearColor   = SdlColor(24, 24, 28)

    // The wheel event does not carry the cursor position in the bound accessors, so the
    // last position seen from a motion event is used to route the scroll.
    var lastMouse = Offset.zero

    var running = true
    while running do
      var event = pollEvent()
      while event.isDefined do
        val e = event.get
        e.kind match
          case QUIT              => running = false
          case MOUSE_BUTTON_DOWN => router.down(Offset(e.mouseX, e.mouseY), e.mouseButton)
          case MOUSE_BUTTON_UP   => router.up(Offset(e.mouseX, e.mouseY), e.mouseButton)
          case MOUSE_MOTION =>
            lastMouse = Offset(e.mouseX, e.mouseY)
            router.move(lastMouse)
          case MOUSE_WHEEL => router.wheel(lastMouse, e.wheelX, e.wheelY)
          case KEY_DOWN    => keyRouter.down(e.keyScancode, e.keyRepeat)
          case KEY_UP      => keyRouter.up(e.keyScancode)
          case _           => ()
        event = pollEvent()

      // Run whatever the handlers produced (state updates, effects) before painting.
      drainScheduler()

      if root.dirty then
        root.dirty = false
        root.layout(Constraints.tight(root.windowSize))
        renderer.clear(clearColor)
        root.paint(canvas, Offset.zero)
        renderer.present()
      else
        delay(8) // idle: yield instead of busy-spinning when nothing changed

    fonts.close()
    renderer.destroy()
    window.destroy()
    ttfQuit()
    quit()
