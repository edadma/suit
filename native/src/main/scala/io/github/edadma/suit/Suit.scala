package io.github.edadma.suit

import scala.collection.mutable
import io.github.edadma.vdom.{Host, Scheduler}
import io.github.edadma.sdl3.{Color => SdlColor, *}
import io.github.edadma.libcairo.{Format, imageSurfaceCreate, fontFaceCreateForFTFace}
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

    val window   = createWindow(title, width, height)
    val renderer = window.createRenderer()
    renderer.setVSync(true)

    // HiDPI: the window's logical size (the coordinate space the UI lives in) and its actual
    // pixel size differ on a high-density display. The backbuffer is sized to the real pixels
    // and the Cairo context is scaled by the ratio, so the tree — laid out and hit-tested in
    // logical units throughout — rasterises at the display's true resolution. On a 1× display
    // the ratio is 1 and everything below is unchanged.
    val (pixelW, pixelH) = window.sizeInPixels
    val device           = DeviceSurface.from(width, height, pixelW, pixelH)

    // Cairo draws into this ARGB32 image surface; the runtime uploads it to the streaming
    // texture each dirty frame. ARGB32's little-endian byte layout is identical to SDL's
    // ARGB8888, so the upload is a straight copy with no conversion. The texture is sized to
    // match the surface (device pixels) and blitted to the window every iteration, so an expose
    // always shows a complete frame. The base transform scales logical coordinates up to pixels;
    // it is set once and never reset (no code clears the matrix), so it underlies every frame.
    val surface = imageSurfaceCreate(Format.ARGB32, device.width, device.height)
    val cr      = surface.create
    cr.scale(device.scaleX, device.scaleY)
    val texture = renderer.createTexture(PIXELFORMAT_ARGB8888, TEXTUREACCESS_STREAMING, device.width, device.height)

    // Load the font through FreeType and wrap it as a Cairo font face — a specific typeface,
    // not a platform-resolved family name. With no `fontPath`, the Inter font embedded in
    // the binary is loaded straight from memory; otherwise the given file is read. The face
    // is held for the app's lifetime (Cairo is reference-counted, and nothing here is created
    // per frame); the FreeType handles are released at shutdown, after the Cairo contexts
    // that use them.
    val ftLib = initFreeType match
      case Right(lib) => lib
      case Left(err)  => System.err.println(s"suit: FreeType init failed ($err)"); return
    val ftFace = (fontPath match
      case null      => ftLib.newMemoryFace(InterFont.suit_inter_font_data(), InterFont.suit_inter_font_size().toLong, 0)
      case p: String => ftLib.newFace(p, 0)
    ) match
      case Right(face) => face
      case Left(err)   => System.err.println(s"suit: cannot load font ($err)"); return
    val fontFace = fontFaceCreateForFTFace(ftFace.faceptr, 0)

    // Cairo serves both halves of text: the measurer the layout pass consults and the canvas
    // that rasterises glyphs, both using the same face, so a string measures and paints
    // identically.
    val measurer = new CairoTextMeasurer(fontFace)
    TextMeasurer.installed = measurer
    val canvas = new CairoCanvas(cr, fontFace)

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

    // Motion seam. The animation hooks (useTransition, usePresence) drive themselves by
    // asking for frames and timers; the frame loop is their clock. Each iteration pumps the
    // due frames and timers, which enqueue re-renders that mark the tree dirty, so an
    // animation repaints every frame while it runs and the loop goes quiet once it settles.
    // A monotonic millisecond clock feeds both the easing and the timer deadlines.
    val clock = new FrameClock(() => System.nanoTime() / 1.0e6)
    clock.install()

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

    // Lay out and draw the current tree into the Cairo surface, then upload it. Called on
    // startup and again whenever a layout change marks the tree dirty.
    def repaint(): Unit =
      root.layout(Constraints.tight(root.windowSize))
      // Fill the background opaque, then paint the tree on top. The opaque fill replaces
      // the previous frame, so no separate clear is needed.
      cr.setSourceRGBA(clearColor.r / 255.0, clearColor.g / 255.0, clearColor.b / 255.0, 1.0)
      cr.paint()
      root.paint(canvas, Offset.zero)
      surface.flush()
      texture.update(surface.getData, surface.getStride)

    // The overlay layer sits as the root's last child, so it paints above the application and
    // is hit before it. The app is wrapped in a provider that hands this layer and the focus
    // manager down through context, so dialogs (and later menus and tooltips) can portal their
    // content into it and trap focus without reaching for the runtime globally.
    val overlay = new RenderOverlay
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
            // Tab is the global focus-traversal key — it moves focus through the focusable
            // objects rather than reaching the focused widget, so it is intercepted here
            // (Shift+Tab walks backward). Every other key routes to whatever holds focus.
            if e.keyScancode == Key.Tab then focusManager.focusNext(root, backward = shift)
            // Escape closes a trapping modal from anywhere inside it; with no trap active it
            // is an ordinary key routed to whatever holds focus.
            else if e.keyScancode == Key.Escape && focusManager.escape() then ()
            else keyRouter.down(e.keyScancode, e.keyRepeat, shift, ctrl)
          case KEY_UP    => keyRouter.up(e.keyScancode)
          case TEXT_INPUT => textRouter.input(e.text)
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
        case _               => false
      if wantText != textInputOn then
        if wantText then window.startTextInput() else window.stopTextInput()
        textInputOn = wantText

      // Re-draw and re-upload only when the tree changed; blit and present every frame so an
      // expose or resize always shows a full frame.
      if root.dirty then
        root.dirty = false
        repaint()
      renderer.copy(texture)
      renderer.present()

    measurer.close()
    cr.destroy()
    surface.destroy()
    texture.destroy()
    renderer.destroy()
    window.destroy()
    ftFace.doneFace
    ftLib.doneFreeType
    quit()
