package io.github.edadma.suit

import scala.collection.mutable
import io.github.edadma.vdom.{Host, Scheduler}
import io.github.edadma.sdl3.{Color => SdlColor, *}
import io.github.edadma.libcairo.{Context, Format, Surface, imageSurfaceCreate}
import io.github.edadma.freetype.*

// The runtime. It owns the SDL window and the Cairo drawing surface, installs the vdom
// host and scheduler seams, mounts the application, and runs the frame loop.
//
// The division of labour is the one every serious 2D toolkit uses: SDL is the
// cross-platform plumbing — window, input, the present loop — and Cairo is the graphics
// engine. Each frame is drawn by Cairo into an in-memory ARGB32 image surface (with real
// anti-aliasing, since Cairo is a coverage rasteriser), then that surface is uploaded to a
// streaming texture and blitted to the window. SDL never draws a shape; Cairo never talks
// to the OS.
//
// The frame loop bridges vdom's React-style batched updates and a game-style render loop.
// vdom never paints on its own; it requests work through two injectable scheduler seams (a
// microtask queue for re-renders, a macrotask queue for passive effects). A state write
// flows: handler -> useState setter -> Scheduler enqueue -> loop drains it -> reconciler
// re-renders -> render tree mutated -> markDirty bubbles to the root -> the next iteration
// repaints. Painting happens only when the tree was marked dirty, so an idle UI costs
// nothing but event polling.
object Suit:

  /** Open a window of the given size and run `app` in it until the window is closed. Blocks
    * on the frame loop for the lifetime of the window. Text is rendered in the bundled Inter
    * font by default; pass `fontPath` to load a specific TrueType/OpenType (or collection)
    * file through FreeType instead. */
  def run(title: String, width: Int, height: Int, fontPath: String | Null = null)(app: VNode): Unit =
    setMainReady()
    if !init(INIT_VIDEO) then
      System.err.println(s"suit: SDL_Init failed: ${error}")
      return

    // Open the window no larger than the display can actually show. The requested size is
    // clamped to the primary display's usable bounds — the desktop minus the menu bar and dock —
    // with a little headroom for the title bar, so the whole window lands on-screen even on a
    // laptop panel smaller than the requested size (otherwise the bottom and right edges, and any
    // content there, spill off where the user can't reach them). The window is resizable; the
    // frame loop reflows the tree to whatever size the window settles at, here or on a later drag.
    val (initW, initH, place) =
      displayUsableBounds(getPrimaryDisplay) match
        case Some((ux, uy, uw, uh)) =>
          (math.min(width, uw), math.max(1, math.min(height, uh - 40)), Some((ux, uy)))
        case None => (width, height, None)

    // Window and renderer creation can fail — e.g. when there is no usable display (a process not
    // attached to the desktop GUI session). Detect it and exit with the SDL error, rather than
    // falling through into the event loop and spinning forever with no window.
    val window = createWindow(title, initW, initH, WINDOW_RESIZABLE)
    if window.isNull then
      System.err.println(s"suit: failed to create window: ${error}")
      quit()
      return
    // Place a clamped window at the top-left of the usable area, so its title bar clears the menu
    // bar and the content runs down into the visible desktop rather than under the dock.
    place.foreach((x, y) => window.setPosition(x, y))

    val renderer = window.createRenderer()
    if renderer.isNull then
      System.err.println(s"suit: failed to create renderer: ${error}")
      window.destroy()
      quit()
      return
    renderer.setVSync(true)

    // Load the font through FreeType. With no `fontPath`, the variable Inter font embedded in
    // the binary is loaded straight from memory; otherwise the given file is read. Inter is a
    // variable font, so a face is created per weight on demand and wrapped as a Cairo font face
    // (see [[Fonts]]); the faces are held for the app's lifetime (nothing here is created per
    // frame) and the FreeType handles are released at shutdown, after the Cairo contexts that
    // use them.
    val ftLib = initFreeType match
      case Right(lib) => lib
      case Left(err)  => System.err.println(s"suit: FreeType init failed ($err)"); return
    val openFace: () => Either[Int, io.github.edadma.freetype.Face] = fontPath match
      case null      => () => ftLib.newMemoryFace(InterFont.suit_inter_font_data(), InterFont.suit_inter_font_size().toLong, 0)
      case p: String => () => ftLib.newFace(p, 0)
    val fonts = Fonts.open(ftLib, openFace) match
      case Right(f)  => f
      case Left(msg) => System.err.println(s"suit: $msg"); return

    // Cairo serves both halves of text: the measurer the layout pass consults and the canvas
    // that rasterises glyphs, both selecting the face for a run's weight from the same cache,
    // so a string measures and paints identically.
    val measurer = new CairoTextMeasurer(fonts)
    TextMeasurer.installed = measurer

    // Back the text widgets' copy/paste with the system clipboard (the headless default is
    // in-memory). SDL hands back UTF-8 text; the wrapper copies and frees it.
    Clipboard.installed = new Clipboard:
      def get(): String           = getClipboardText
      def set(text: String): Unit = setClipboardText(text)

    // The backbuffer: the Cairo image surface the frame is drawn into, its scaled context, the SDL
    // streaming texture the surface uploads to, and the canvas the tree paints through. ARGB32's
    // little-endian byte layout matches SDL's ARGB8888, so the upload is a straight copy with no
    // conversion. Everything here is sized to the window's *pixel* dimensions and the context is
    // scaled by the pixel-to-logical ratio, so the tree — laid out and hit-tested in logical units
    // throughout — rasterises at the display's true resolution (a 1× display leaves the scale at 1
    // and the surface equal to the window). The pieces are grouped because they are rebuilt as a
    // unit whenever the window's pixel size changes (see the resize handler in the loop).
    final class Backbuffer(
        val surface: Surface,
        val cr:      Context,
        val texture: Texture,
        val canvas:  CairoCanvas,
        val device:  DeviceSurface,
    )

    def makeBackbuffer(): Backbuffer =
      val (logicalW, logicalH) = window.size
      val (pixelW, pixelH)     = window.sizeInPixels
      val dev                  = DeviceSurface.from(logicalW, logicalH, pixelW, pixelH)
      // Publish the display density so application code that allocates its own pixel buffer (a
      // `surface(...)` widget's backing surface) can size it to the real resolution and stay sharp.
      DevicePixelRatio.scaleX = dev.scaleX
      DevicePixelRatio.scaleY = dev.scaleY
      val surf = imageSurfaceCreate(Format.ARGB32, dev.width, dev.height)
      val ctx  = surf.create
      // The base transform scales logical coordinates up to pixels; it is set once on the fresh
      // context and never reset, so it underlies every frame drawn through this backbuffer.
      ctx.scale(dev.scaleX, dev.scaleY)
      val tex = renderer.createTexture(PIXELFORMAT_ARGB8888, TEXTUREACCESS_STREAMING, dev.width, dev.height)
      new Backbuffer(surf, ctx, tex, new CairoCanvas(ctx, fonts, dev.scaleX, dev.scaleY), dev)

    var bb = makeBackbuffer()

    // The tree is laid out at the window's logical size, which the resize handler keeps current.
    val (rootW, rootH) = window.size
    val root           = new RenderRoot(Size(rootW.toDouble, rootH.toDouble))

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

    // Motion seam. The animation hooks (useTransition, usePresence) drive themselves by
    // asking for frames and timers; the frame loop is their clock. Each iteration pumps the
    // due frames and timers, which enqueue re-renders that mark the tree dirty, so an
    // animation repaints every frame while it runs and the loop goes quiet once it settles.
    // A monotonic millisecond clock feeds both the easing and the timer deadlines.
    val clock = new FrameClock(() => System.nanoTime() / 1.0e6)
    clock.install()

    // The repaint seam: an imperative animation (a canvas driven by `useFrame`) changes nothing
    // in the tree, so it asks for the next frame to be drawn through here. The seam cannot say
    // which canvas advanced, so it marks every live surface for repaint and requests a frame;
    // the static UI, not a live surface, stays cached and is not re-rasterised.
    Repaint.request = () =>
      root.invalidateLiveSurfaces()
      root.dirty = true

    // Pointer events route by hit-test; key events route to whatever the focus manager
    // currently holds (a press updates focus through the same pointer router).
    val focusManager = new FocusManager
    val router       = new PointerRouter(root, focusManager)
    val keyRouter    = new KeyRouter(focusManager)
    val textRouter   = new TextRouter(focusManager)
    val clearColor   = Color(24, 24, 28)

    // Whether the platform's text-input session is currently open. It is reconciled each
    // iteration against the focused object: a text field (an object that `acceptsText`) opens
    // the session while focused so its `TEXT_INPUT` events flow, and anything else closes it.
    var textInputOn = false

    // The overlay layer sits as the root's last child, so it paints above the application and
    // is hit before it. The app is wrapped in a provider that hands this layer and the focus
    // manager down through context, so dialogs (and later menus and tooltips) can portal their
    // content into it and trap focus without reaching for the runtime globally. It is allocated
    // here so the partial-frame compositor below can re-paint it over an animating boundary it
    // sits above; it is mounted into the tree just before the first frame.
    val overlay = new RenderOverlay

    // Lay out and draw the current frame into the Cairo surface, then upload it. Called on
    // startup and whenever the tree is marked dirty. The surface is persistent — it is never
    // cleared wholesale on a partial frame — which is what makes boundary compositing work:
    // untouched pixels from the previous frame simply remain.
    //
    // When the root boundary is dirty (a change in the static UI), the whole scene is
    // re-rasterised: an opaque background fill replaces the previous frame, then the tree
    // paints on top. When only nested boundaries are dirty (an animating canvas), just those
    // regions are repainted in place and the rest of the window's pixels are left as they
    // were — so the static UI is not re-rasterised at frame rate. Layout always runs (it is
    // cheap next to rasterisation and keeps every boundary's absolute position current); a
    // needs-layout vs needs-paint split is a later optimisation.
    def repaint(): Unit =
      root.layout(Constraints.tight(root.windowSize))
      if root.needsRepaint then
        bb.cr.setSourceRGBA(clearColor.r / 255.0, clearColor.g / 255.0, clearColor.b / 255.0, 1.0)
        bb.cr.paint()
        root.paint(bb.canvas, Offset.zero)
        root.clearRepaintFlags()
      else Compositor.partialFrame(bb.canvas, root.dirtyBoundaries, overlay, clearColor)
      bb.surface.flush()
      bb.texture.update(bb.surface.getData, bb.surface.getStride)

    createRoot(root).render(OverlayContext.provide(OverlayEnv(overlay, focusManager), app))
    root.insertChild(overlay, null)
    drainScheduler() // commit any effects the initial mount queued
    repaint()        // lay out and paint the first frame before the loop

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
          case KEY_DOWN =>
            val mod   = e.keyMod
            val shift = (mod & KMOD_SHIFT) != 0
            val ctrl  = (mod & KMOD_CTRL) != 0
            val meta  = (mod & KMOD_GUI) != 0
            // Tab is the global focus-traversal key — it moves focus through the focusable
            // objects rather than reaching the focused widget, so it is intercepted here
            // (Shift+Tab walks backward). Every other key routes to whatever holds focus.
            if e.keyScancode == Key.Tab then focusManager.focusNext(root, backward = shift)
            // Escape closes a trapping modal from anywhere inside it; with no trap active it
            // is an ordinary key routed to whatever holds focus.
            else if e.keyScancode == Key.Escape && focusManager.escape() then ()
            else keyRouter.down(e.keyScancode, e.keyRepeat, shift, ctrl, meta)
          case KEY_UP    => keyRouter.up(e.keyScancode)
          case TEXT_INPUT => textRouter.input(e.text)
          // The window changed size (a user drag, or the OS fitting it to the display). Re-read
          // the logical and pixel sizes, and when either moved, point the tree at the new logical
          // size and rebuild the backbuffer to the new pixel size — then force a full repaint, the
          // fresh surface having no prior pixels. The layout reflows on the next frame, so the
          // panes (and anything riding their edges, like a button or a scrollbar) follow the
          // window. Both RESIZED and PIXEL_SIZE_CHANGED can fire for one resize; the guard makes
          // the second a no-op.
          case WINDOW_RESIZED | WINDOW_PIXEL_SIZE_CHANGED =>
            val (logicalW, logicalH) = window.size
            val (pixelW, pixelH)     = window.sizeInPixels
            val resized =
              logicalW.toDouble != root.windowSize.width || logicalH.toDouble != root.windowSize.height ||
                pixelW != bb.device.width || pixelH != bb.device.height
            if resized then
              root.windowSize = Size(logicalW.toDouble, logicalH.toDouble)
              bb.cr.destroy()
              bb.surface.destroy()
              bb.texture.destroy()
              bb = makeBackbuffer()
              root.needsRepaint = true
              root.dirty        = true
          case _          => ()
        event = pollEvent()

      // Run whatever the handlers produced (state updates, effects), advance any animation
      // by one frame, then commit whatever that produced, before painting.
      drainScheduler()
      clock.pump()
      drainScheduler()

      // Open or close the platform text-input session to match the focused object, so a text
      // field receives typed characters while focused and the IME is dismissed otherwise.
      val wantText = focusManager.focused match
        case r: RenderObject => r.acceptsText
        case null            => false
      if wantText != textInputOn then
        if wantText then window.startTextInput() else window.stopTextInput()
        textInputOn = wantText

      // Re-draw and re-upload only when the tree changed; blit and present every frame so an
      // expose or resize always shows a full frame.
      if root.dirty then
        root.dirty = false
        repaint()
      renderer.copy(bb.texture)
      renderer.present()

    measurer.close()
    bb.cr.destroy()
    bb.surface.destroy()
    bb.texture.destroy()
    renderer.destroy()
    window.destroy()
    fonts.close()
    ftLib.doneFreeType
    quit()
