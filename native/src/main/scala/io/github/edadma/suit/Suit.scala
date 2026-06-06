package io.github.edadma.suit

import scala.collection.mutable
import io.github.edadma.vdom.{Host, Scheduler, VNode, createRoot}
import io.github.edadma.sdl3.{Color => SdlColor, *}

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

  /** Open a window of the given size and run `app` in it until the window is closed.
    * Blocks on the frame loop for the lifetime of the window. */
  def run(title: String, width: Int, height: Int)(app: VNode): Unit =
    setMainReady()
    if !init(INIT_VIDEO) then
      System.err.println(s"suit: SDL_Init failed: ${error}")
      return

    val window   = createWindow(title, width, height)
    val renderer = window.createRenderer()
    renderer.setVSync(true)
    val canvas = new SdlCanvas(renderer)

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

    val clearColor = SdlColor(24, 24, 28)

    var running = true
    while running do
      var event = pollEvent()
      while event.isDefined do
        val e = event.get
        e.kind match
          case QUIT => running = false
          case MOUSE_BUTTON_DOWN =>
            root.hitTest(Offset(e.mouseX, e.mouseY), Offset.zero) match
              case hit: RenderObject => hit.handlers.get("click").foreach(_.apply(e))
              case null              => ()
          case _ => ()
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

    renderer.destroy()
    window.destroy()
    quit()
