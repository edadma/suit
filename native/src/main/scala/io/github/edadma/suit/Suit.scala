package io.github.edadma.suit

import scala.collection.mutable
import io.github.edadma.vdom.{Host, Scheduler}
import io.github.edadma.sdl3.{Color => SdlColor, Cursor => SdlCursor, *}
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
  def run(title: String, width: Int, height: Int, fontPath: String | Null = null, maximized: Boolean = false)(
      app: VNode,
  ): Unit =
    setMainReady()
    // This thread owns the tree, the hooks, and every seam installed below, for the window's
    // lifetime. Claiming it lets a worker thread tell (via `UiThread.isCurrent`) that it must
    // hand work over rather than touch state itself.
    UiThread.claim()
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
    // HIGH_PIXEL_DENSITY makes the window report a true pixel size on a HiDPI/Retina display, so the
    // backbuffer is allocated at the display's real resolution and the frame rasterises sharp rather
    // than being drawn at logical (1×) size and stretched up to the screen by the compositor.
    // `maximized` opens the window filling the desktop work area (the OS maximized state); the requested
    // size is then the restored size. The frame loop reflows to whatever size the window reports.
    // SDL_WINDOW_MAXIMIZED as a literal: this build compiles against the published sdl3, which does not
    // yet export the flag (source sdl3 adds `WINDOW_MAXIMIZED`) — switch to it on the next sdl3 bump.
    val maximizedFlag = if maximized then 0x00000080L else 0L
    val window = createWindow(title, initW, initH, WINDOW_RESIZABLE | WINDOW_HIGH_PIXEL_DENSITY | maximizedFlag)
    if window.isNull then
      System.err.println(s"suit: failed to create window: ${error}")
      quit()
      return
    // Place a clamped window at the top-left of the usable area, so its title bar clears the menu
    // bar and the content runs down into the visible desktop rather than under the dock.
    place.foreach((x, y) => window.setPosition(x, y))

    // Let the application retitle the window at runtime (e.g. the open document's name).
    WindowControl.titleSetter = t => window.setTitle(t)

    // Present native file panels parented to this window, so a dialog is a non-blocking sheet on
    // macOS rather than a modal that freezes the frame loop. SDL delivers the panel's callback on
    // this (UI) thread through the event pump, so it forwards straight to the app's callback.
    FileDialog.impl = (request, callback) =>
      val sdlFilters = request.filters.map(f => FileFilter(f.name, f.pattern))
      val onResult: DialogResult => Unit = r =>
        callback(r match
          case DialogResult.Chosen(paths) => FileDialog.Result.Chosen(paths)
          case DialogResult.Cancelled     => FileDialog.Result.Cancelled
          case DialogResult.Failed(msg)   => FileDialog.Result.Failed(msg))
      request.kind match
        case FileDialog.Kind.OpenFile =>
          showOpenFileDialog(window, sdlFilters, request.defaultLocation, request.allowMany,
            request.title, request.accept, request.cancel)(onResult)
        case FileDialog.Kind.SaveFile =>
          showSaveFileDialog(window, sdlFilters, request.defaultLocation,
            request.title, request.accept, request.cancel)(onResult)
        case FileDialog.Kind.OpenFolder =>
          showOpenFolderDialog(window, request.defaultLocation, request.allowMany,
            request.title, request.accept, request.cancel)(onResult)

    val renderer = window.createRenderer()
    if renderer.isNull then
      System.err.println(s"suit: failed to create renderer: ${error}")
      window.destroy()
      quit()
      return
    renderer.setVSync(true)

    // Publish the renderer so application code can create its own GPU-side layers (a
    // `VideoTexture` for a decoded frame). It is the same kind of seam as DevicePixelRatio and
    // Clipboard: the app needs something the runtime owns, and has no other way to reach it.
    VideoTexture.renderer = renderer.ptr

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

    // The monospaced family (JetBrains Mono), always the embedded face — a `fontPath` overrides
    // only the proportional default. The two together are the FontSet the text seams resolve a
    // run's face from by its family and weight.
    val monoFonts = Fonts.open(ftLib, () => ftLib.newMemoryFace(MonoFont.suit_mono_font_data(), MonoFont.suit_mono_font_size().toLong, 0)) match
      case Right(f)  => f
      case Left(msg) => System.err.println(s"suit: $msg"); return
    val fontSet = new FontSet(fonts, monoFonts)

    // Cairo serves both halves of text: the measurer the layout pass consults and the canvas
    // that rasterises glyphs, both selecting the face for a run's family and weight from the same
    // FontSet, so a string measures and paints identically.
    val measurer = new CairoTextMeasurer(fontSet)
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
      // The UI layer composites over whatever is beneath it rather than replacing it, which is
      // what lets a video texture show through the hole a `video` widget punches. A texture
      // defaults to BLENDMODE_NONE, and with the UI opaque everywhere except its holes, blending
      // costs nothing where there is no video.
      tex.setBlendMode(BLENDMODE_BLEND)
      new Backbuffer(surf, ctx, tex, new CairoCanvas(ctx, fontSet, dev.scaleX, dev.scaleY), dev)

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

    // Compose the window and show it. Video is the one thing suit does not rasterise: a `video`
    // widget leaves a transparent hole where its frame belongs, so the layers go down in z-order —
    // video textures first, then the UI over them — rather than the single blit a Cairo-only
    // window needs.
    //
    // This runs every iteration, not only on a dirty frame, and that is the point: a decoder
    // swapping a texture's contents changes nothing in the tree, so nothing is marked dirty and
    // Cairo is never re-run. Playback rides the present the loop was doing anyway.
    //
    // The video rectangles arrive in logical coordinates (everything the tree computes is
    // logical); the render target is in device pixels, like the backbuffer texture that fills it.
    // Hence the scale.
    def presentFrame(): Unit =
      val layers = root.videoLayers
      if layers.nonEmpty then
        renderer.clear(SdlColor(clearColor.r, clearColor.g, clearColor.b))
        val sx = bb.device.scaleX
        val sy = bb.device.scaleY
        layers.foreach { (layer, src, dst) =>
          layer match
            case v: VideoTexture =>
              renderer.copy(
                v.texture,
                (src.x, src.y, src.width, src.height),
                (dst.x * sx, dst.y * sy, dst.width * sx, dst.height * sy),
              )
            // A layer from another backend (or a test stub) has no texture to blit; the widget's
            // background stays, so it reads as a black frame rather than a hole onto nothing.
            case _ => ()
        }
      renderer.copy(bb.texture)
      renderer.present()

    // Re-read the window's logical and pixel sizes and, when either moved, point the tree at the new
    // logical size and rebuild the backbuffer to the new pixel size, forcing a full repaint (the
    // fresh surface has no prior pixels). Returns whether anything changed. Shared by the event-loop
    // resize case and the live-resize watch below, so both keep the tree and backbuffer in step.
    def syncBackbuffer(): Boolean =
      val (logicalW, logicalH) = window.size
      val (pixelW, pixelH)     = window.sizeInPixels
      val changed =
        logicalW.toDouble != root.windowSize.width || logicalH.toDouble != root.windowSize.height ||
          pixelW != bb.device.width || pixelH != bb.device.height
      if changed then
        root.windowSize = Size(logicalW.toDouble, logicalH.toDouble)
        bb.cr.destroy()
        bb.surface.destroy()
        bb.texture.destroy()
        bb = makeBackbuffer()
        root.needsRepaint = true
        root.dirty        = true
      changed

    val appRoot = createRoot(root)
    appRoot.render(OverlayContext.provide(OverlayEnv(overlay, focusManager), app))
    root.insertChild(overlay, null)
    drainScheduler() // commit any effects the initial mount queued
    repaint()        // lay out and paint the first frame before the loop

    // Redraw *during* a live window resize. macOS (and some window managers) run a modal loop while
    // the user drags a window edge, and the main frame loop below is blocked for its whole duration
    // — so without this the OS just stretches the last frame until the drag ends. SDL still pumps
    // events through an event watch during that modal loop, so re-lay-out and present from here on
    // every resize/expose, live. The guard stops a present that itself pumped an event (unlikely,
    // but cheap to rule out) from re-entering.
    var inResizeWatch = false
    addEventWatch { e =>
      e.kind match
        case (WINDOW_RESIZED | WINDOW_PIXEL_SIZE_CHANGED) if !inResizeWatch =>
          inResizeWatch = true
          try
            syncBackbuffer()
            if root.dirty then
              root.dirty = false
              repaint()
            presentFrame()
          finally inResizeWatch = false
        case _ => ()
    }

    // The wheel event does not carry the cursor position in the bound accessors, so the
    // last position seen from a motion event is used to route the scroll.
    var lastMouse = Offset.zero

    // The modifiers currently held, mirrored from the keyboard events' own mod mask. A wheel
    // event carries no modifier field, but a wheel handler may give a modified wheel a second
    // job (the universal scroll-versus-zoom split), so the state is kept current here: SDL
    // updates the mask before posting a key event in both directions, so the last mask seen —
    // including from a modifier key's own press or release — is the live state.
    var heldMods = 0

    // The pointer shape. Which suit `Cursor` a widget asks for is pure tree logic (the router
    // resolves it); mapping that to a platform cursor is the one part that needs SDL. The system
    // cursors are built on first use and cached — they are process-wide, so one is set as the
    // active shape whenever the resolved shape changes. `hasMouse` gates it so the arrow is not
    // moved before the pointer has even entered the window.
    val systemCursors                = mutable.Map.empty[Cursor, SdlCursor]
    var currentCursor: Cursor        = Cursor.Default
    var hasMouse                     = false
    def cursorShapeId(c: Cursor): Int = c match
      case Cursor.Default    => SYSTEM_CURSOR_DEFAULT
      case Cursor.Pointer    => SYSTEM_CURSOR_POINTER
      case Cursor.Text       => SYSTEM_CURSOR_TEXT
      case Cursor.Crosshair  => SYSTEM_CURSOR_CROSSHAIR
      case Cursor.Move       => SYSTEM_CURSOR_MOVE
      case Cursor.NotAllowed => SYSTEM_CURSOR_NOT_ALLOWED
      case Cursor.Progress   => SYSTEM_CURSOR_PROGRESS
      case Cursor.Wait       => SYSTEM_CURSOR_WAIT
      case Cursor.ResizeEW   => SYSTEM_CURSOR_EW_RESIZE
      case Cursor.ResizeNS   => SYSTEM_CURSOR_NS_RESIZE
      case Cursor.ResizeNESW => SYSTEM_CURSOR_NESW_RESIZE
      case Cursor.ResizeNWSE => SYSTEM_CURSOR_NWSE_RESIZE
    def applyCursor(c: Cursor): Unit =
      val sc = systemCursors.getOrElseUpdate(c, createSystemCursor(cursorShapeId(c)))
      if !sc.isNull then sc.set(): Unit

    var running = true
    while running do
      var event = pollEvent()
      while event.isDefined do
        val e = event.get
        e.kind match
          // A close request is routed through WindowControl, so the app can intervene (e.g. confirm
          // discarding unsaved changes) before the loop actually stops.
          case QUIT              => WindowControl.requestClose(() => running = false)
          case MOUSE_BUTTON_DOWN => router.down(Offset(e.mouseX, e.mouseY), e.mouseButton)
          case MOUSE_BUTTON_UP   => router.up(Offset(e.mouseX, e.mouseY), e.mouseButton)
          case MOUSE_MOTION =>
            lastMouse = Offset(e.mouseX, e.mouseY)
            hasMouse  = true
            router.move(lastMouse)
          case MOUSE_WHEEL =>
            router.wheel(
              lastMouse, e.wheelX, e.wheelY,
              shift = (heldMods & KMOD_SHIFT) != 0,
              ctrl  = (heldMods & KMOD_CTRL) != 0,
              meta  = (heldMods & KMOD_GUI) != 0,
              alt   = (heldMods & KMOD_ALT) != 0,
            )
          case KEY_DOWN =>
            val mod   = e.keyMod
            heldMods  = mod
            val shift = (mod & KMOD_SHIFT) != 0
            val ctrl  = (mod & KMOD_CTRL) != 0
            val meta  = (mod & KMOD_GUI) != 0
            val alt   = (mod & KMOD_ALT) != 0
            // Tab is the global focus-traversal key — it moves focus through the focusable
            // objects rather than reaching the focused widget, so it is intercepted here
            // (Shift+Tab walks backward). Every other key routes to whatever holds focus.
            if e.keyScancode == Key.Tab then focusManager.focusNext(root, backward = shift)
            // Escape closes a trapping modal from anywhere inside it; with no trap active it
            // is an ordinary key routed to whatever holds focus.
            else if e.keyScancode == Key.Escape && focusManager.escape() then ()
            else keyRouter.down(e.keyScancode, e.keyRepeat, shift, ctrl, meta, alt)
          case KEY_UP =>
            heldMods = e.keyMod
            keyRouter.up(e.keyScancode)
          case TEXT_INPUT => textRouter.input(e.text)
          // The window changed size (a user drag, or the OS fitting it to the display). The live
          // resize is already handled by the event watch above, which fires even while this loop is
          // blocked in a modal drag; this keeps the non-drag paths (a programmatic resize, a
          // display move) in step. Both RESIZED and PIXEL_SIZE_CHANGED can fire for one resize; the
          // size guard inside makes the second a no-op.
          case WINDOW_RESIZED | WINDOW_PIXEL_SIZE_CHANGED => syncBackbuffer()
          case _                                          => ()
        event = pollEvent()

      // Run work handed over by background threads (a decoded frame, a finished load) before
      // the scheduler drains, so a posted thunk's state writes commit in this same frame rather
      // than waiting for the next one. This is the only point at which another thread's work
      // enters the UI; everything below here is single-threaded again.
      UiThread.drain()

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

      // Match the pointer shape to whatever is under it now — recomputed each frame (after the
      // tree has settled), so a cursor tracks a layout change beneath a still pointer, not only a
      // move. Only a change touches SDL.
      if hasMouse then
        val wantCursor = router.cursorAt(lastMouse)
        if wantCursor != currentCursor then
          applyCursor(wantCursor)
          currentCursor = wantCursor

      // Re-draw and re-upload only when the tree changed; blit and present every frame so an
      // expose or resize always shows a full frame.
      if root.dirty then
        root.dirty = false
        repaint()
      presentFrame()

    // The window is closing. Unmount the application tree first, so every mount effect's cleanup
    // runs — closing files, stopping worker threads, releasing native handles — while the renderer,
    // the window and SDL are still alive. Without this an application's background thread (a decoder,
    // a scanner) keeps running as the runtime tears SDL down beneath it and crashes on a freed device;
    // effect cleanups are the toolkit's contract for orderly teardown and must fire on shutdown, not
    // only on a mid-run unmount. The runtime's own resources are torn down after.
    appRoot.unmount()

    measurer.close()
    systemCursors.values.foreach(_.destroy()) // only the shapes we created; never the default
    bb.cr.destroy()
    bb.surface.destroy()
    bb.texture.destroy()
    renderer.destroy()
    window.destroy()
    fontSet.close()
    ftLib.doneFreeType
    quit()
