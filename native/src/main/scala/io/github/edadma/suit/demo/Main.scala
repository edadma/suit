package io.github.edadma.suit.demo

import scala.scalanative.unsafe.*
import scala.scalanative.libc.stdlib
import io.github.edadma.suit.*
import io.github.edadma.suit.dsl.*
import io.github.edadma.suit.widgets.*
import io.github.edadma.libcairo.{Format, FontSlant, Surface, imageSurfaceCreate, patternCreateLinear}
import io.github.edadma.libcairo.FontWeight as CairoFontWeight

// A showcase of the styling system, the input model, and motion. The whole UI is wrapped
// in a `ThemeProvider` carrying the active theme, so the built-in widgets (Button, Checkbox,
// Slider, Switch, RadioGroup, ProgressBar, Tabs, Badge, Divider, Alert, Card, Dialog, Menu,
// Tooltip) restyle themselves from it with no widget code touched — that is the point of
// keeping styling general rather than baked in. A switch in the header flips the whole app
// between a light and a dark built-in theme: every control, the body background, and the
// card shadows swap at once because they all read the one theme record out of context. A
// gradient header, rounded cards with drop shadows, the text-style cascade (the body sets a
// `textColor` once and the labels inherit it), a typography card showing multi-line wrapping,
// two-line ellipsis, and alignment, a font-weights card showing the same words across the
// variable font's `wght` axis, an SVG card showing a vector icon — loaded once through librsvg and
// drawn crisp at several sizes — a raster card showing a JPEG decoded with turbojpeg, and a canvas
// card drawing arbitrary 2D graphics — an orbiting comet animated with `useFrame` — through the same
// Canvas seam the widgets use, round out the picture.
//
// Motion runs throughout: the button tint fades on hover and press, the checkbox mark
// scales and fades as it ticks, the slider thumb glides toward its value, and the "Show
// details" panel fades and rises in then eases back out before unmounting (the enter/exit
// path from `usePresence` + `useTransition`), and the modal dialog floats in above a dimming
// scrim, traps focus, and fades back out on close. All of it is driven by the runtime's frame
// clock, which repaints each frame only while something is animating and goes quiet once
// it settles.
//
// It exercises the whole pipe: vdom builds the VNode tree, `SuitHostConfig` creates the
// RenderObjects, the installed measurer sizes the text, the constraint protocol lays
// everything out, the frame loop routes pointer, wheel, and keyboard events through the
// routers and the focus manager and pumps the motion clock, the widgets' `useState` writes
// flow through the scheduler seam, the tree is marked dirty, and the loop repaints. Tab (and
// Shift+Tab) walk focus through the controls; once a control is focused — by Tab or a click —
// the keyboard drives it (Space/Enter on the button and checkbox, Space on the switch and
// radio, arrows on the slider, and full text editing — typing, selection, caret movement —
// in the text field).

/** A muted secondary ink for a theme: its body text blended part-way toward the surface, so
  * it reads as dimmed in both light and dark schemes. */
private def mutedInk(theme: Theme): Color = Color.lerp(theme.surfaceText, theme.surface, 0.45)

/** A theme-aware drop shadow — heavier on dark surfaces, lighter on white ones. */
private def cardShadow(theme: Theme): Shadow =
  Shadow(color = Color(0, 0, 0, if theme.isDark then 110 else 40), offset = Offset(0, 4), blur = 12)

// The pointer shapes shown in the demo's cursor card, split across two rows.
private val cursorSwatchesTop: Seq[(String, Cursor)] =
  Seq("pointer" -> Cursor.Pointer, "text" -> Cursor.Text, "crosshair" -> Cursor.Crosshair,
    "move" -> Cursor.Move, "not-allowed" -> Cursor.NotAllowed, "wait" -> Cursor.Wait)
private val cursorSwatchesBottom: Seq[(String, Cursor)] =
  Seq("progress" -> Cursor.Progress, "resize ↔" -> Cursor.ResizeEW, "resize ↕" -> Cursor.ResizeNS,
    "resize ⤢" -> Cursor.ResizeNESW, "resize ⤡" -> Cursor.ResizeNWSE, "default" -> Cursor.Default)

/** One labelled swatch that shows `shape` while hovered. */
private def cursorSwatch(theme: Theme, muted: Color, label: String, shape: Cursor): VNode =
  box(
    bg      = theme.background,
    border  = theme.border,
    borderWidth = 1,
    radius  = 8,
    padding = EdgeInsets.symmetric(horizontal = 10, vertical = 12),
    cursor  = shape,
  )(text(label, size = 12, color = muted))

private def cursorRow(theme: Theme, muted: Color, swatches: Seq[(String, Cursor)]): Seq[VNode] =
  swatches.map((label, shape) => box(flex = 1)(cursorSwatch(theme, muted, label, shape)))

/** A vector icon loaded once through librsvg. The same document renders crisp at any size — the
  * demo draws it at several — because librsvg paints it as vectors straight into suit's Cairo
  * context, with no intermediate raster. */
private val appIcon: SvgImage = Svg.fromString(
  """<svg xmlns="http://www.w3.org/2000/svg" width="64" height="64" viewBox="0 0 64 64">
    |  <rect x="4" y="4" width="56" height="56" rx="14" fill="#4dabf7"/>
    |  <circle cx="32" cy="32" r="16" fill="#ffffff"/>
    |  <path d="M24 32 l6 6 l12 -14" stroke="#4dabf7" stroke-width="4" fill="none"
    |        stroke-linecap="round" stroke-linejoin="round"/>
    |</svg>""".stripMargin,
)

// A raster photo decoded once at startup from the JPEG embedded in the binary. turbojpeg turns
// the bytes into pixels straight inside a Cairo surface the canvas blits and scales — the path SVG
// (vectors) doesn't take. Decoding here, not per frame, keeps the loop cheap.
private val photo: RasterImage = Raster.fromPtr(DemoImage.suit_demo_jpg_data(), DemoImage.suit_demo_jpg_size().toInt)

/** A detail panel that animates on the way in and out. `usePresence` keeps it mounted
  * through its exit so the close can play, and `useTransition` fades and lifts it: the
  * opacity and a small vertical inset both glide toward the open state and back. This is
  * the enter/exit half of the motion system — the same pair of hooks a Dialog or Tooltip
  * will lean on. It reads the active theme from context, so it recolours with the rest. */
private val DetailPanel: Component[Boolean] =
  component[Boolean] { open =>
    val theme = useTheme()
    val p     = usePresence(open, exitMs = 220)
    val amt   = useTransition(if p.phase == PresencePhase.Open then 1.0 else 0.0, 220)

    if p.mounted then
      box(padding = EdgeInsets(top = 16 * (1 - amt), right = 0, bottom = 0, left = 0))(
        box(
          bg      = theme.surface,
          radius  = 12,
          opacity = amt,
          shadow  = cardShadow(theme),
          padding = EdgeInsets.all(20),
        )(
          text(
            "Now you see me — I fade and rise in, then ease back out before unmounting.",
            color = mutedInk(theme),
          ),
        ),
      )
    else VEmpty
  }

/** A live drawing surface — the Canvas widget. The application is handed the very same
  * [[Canvas]] the widgets above paint through and draws whatever it likes in a local
  * coordinate space clipped to the widget's bounds. The animation state (an orbit phase) lives
  * in a `useRef`; `useFrame` advances it each frame and requests a repaint without re-rendering,
  * and the canvas's `draw` reads it back — so the comet spins continuously while the rest of the
  * window stays put. It reads the active theme for the dot colour, recolouring with everything
  * else. */
private val OrbitCanvas: Component[Unit] =
  view {
    val theme = useTheme()
    val phase = useRef(0.0)
    useFrame(_ => phase.current += 0.03)

    canvas(height = 120) { (c, size) =>
      val w = size.width
      val h = size.height
      c.fillRect(Rect(0, 0, w, h), Color.rgb(0x0b1220))
      val cx = w / 2
      val cy = h / 2
      val r  = math.min(w, h) / 2 - 16
      val n  = 14
      var i  = 0
      while i < n do
        val a    = phase.current - i * 0.16
        val rad  = 3.0 + (n - i) * 0.55
        val fade = (n - i).toDouble / n
        c.fillCircle(Offset(cx + math.cos(a) * r, cy + math.sin(a) * r), rad, theme.primary.withAlpha((255 * fade).toInt))
        i += 1
    }
  }

// The logical size of the application-owned surface below. The backing Cairo surface is made this
// large times the display's device-pixel scale, so it blits one-to-one and stays sharp at 2×.
private val surfaceW = 400.0
private val surfaceH = 120.0

/** Everything the surface panel keeps between frames: the Cairo surface it owns, the same surface
  * presented to suit as a [[RasterImage]], the handle that asks suit to re-blit, and the device
  * scale the surface was sized at (so drawing can stay in logical units). */
private final class SurfaceState(
    val surf:   Surface,
    val image:  RasterImage,
    val handle: SurfaceHandle,
    val sx:     Double,
    val sy:     Double,
)

/** Repaint the application-owned surface with **raw Cairo** — a linear gradient and two lines of
  * text in a serif face centred with Cairo's own text extents. None of this goes through suit's
  * `Canvas`; it is the full underlying engine, the reason a `surface` exists alongside a `canvas`.
  * `gen` (bumped by the Redraw button) just varies the caption, to show that a redraw re-blits only
  * when asked. */
private def paintSurface(st: SurfaceState, theme: Theme, gen: Int): Unit =
  val cr = st.surf.create
  cr.scale(st.sx, st.sy) // draw in logical units; the scale makes it land on device pixels

  val g = patternCreateLinear(0, 0, surfaceW, surfaceH)
  g.addColorStopRGB(0, theme.primary.r / 255.0, theme.primary.g / 255.0, theme.primary.b / 255.0)
  g.addColorStopRGB(1, theme.surface.r / 255.0, theme.surface.g / 255.0, theme.surface.b / 255.0)
  cr.rectangle(0, 0, surfaceW, surfaceH)
  cr.setSource(g)
  cr.fill()
  g.destroy()

  val ink = if theme.isDark then 1.0 else 0.1
  cr.setSourceRGB(ink, ink, ink + 0.02)
  cr.selectFontFace("Georgia", FontSlant.ITALIC, CairoFontWeight.BOLD)
  cr.setFontSize(34)
  val title = "Raw Cairo"
  val te    = cr.textExtents(title)
  cr.moveTo((surfaceW - te.width) / 2 - te.xBearing, surfaceH / 2 - 4)
  cr.showText(title)

  cr.selectFontFace("Georgia", FontSlant.NORMAL, CairoFontWeight.NORMAL)
  cr.setFontSize(13)
  val sub  = s"drawn straight into an app-owned surface · redraw #$gen"
  val te2  = cr.textExtents(sub)
  cr.moveTo((surfaceW - te2.width) / 2 - te2.xBearing, surfaceH / 2 + 22)
  cr.showText(sub)

  cr.destroy()
  st.surf.flush()   // publish the pixels the C side wrote…
  st.surf.markDirty() // …and tell Cairo the buffer changed so the next blit re-samples it

/** The retained-surface counterpart to [[OrbitCanvas]]. The app creates its **own** Cairo surface
  * once (sized in device pixels, see [[DevicePixelRatio]]), draws into it with the full raw Cairo
  * API, and hands it to a `surface` widget; a [[SurfaceHandle]] re-blits it on demand. It redraws
  * when the theme flips (so it recolours with the app) and when the Redraw button bumps a counter —
  * each redraw repaints just this panel, not the window. */
private val SurfacePanel: Component[Unit] =
  view {
    val theme            = useTheme()
    val (gen, setGen, _) = useState(0)

    // Build the surface once, after the runtime has published the display scale. useMemo with no
    // deps runs on mount and never again, so the buffer is allocated a single time, not per render.
    val st = useMemo(
      () => {
        val sx   = DevicePixelRatio.scaleX
        val sy   = DevicePixelRatio.scaleY
        val surf = imageSurfaceCreate(Format.ARGB32, math.ceil(surfaceW * sx).toInt, math.ceil(surfaceH * sy).toInt)
        new SurfaceState(surf, CairoBitmap.wrap(surf), new SurfaceHandle, sx, sy)
      },
      Array(),
    )

    // Redraw whenever the generation or the theme changes, then ask suit to composite the new
    // pixels. Releasing the surface on unmount keeps the app honest about owning it.
    useEffect(() => { paintSurface(st, theme, gen); st.handle.repaint(); () => () }, Array(gen, theme.isDark))
    useEffect(() => () => st.surf.destroy(), Array())

    col(crossAxisAlignment = CrossAxisAlignment.Stretch, spacing = 10)(
      row(mainAxisAlignment = MainAxisAlignment.Center)(
        box(radius = 12, clip = true)(
          surface(st.image, st.handle, width = surfaceW, height = surfaceH),
        ),
      ),
      row(mainAxisAlignment = MainAxisAlignment.Center)(
        Button("Redraw", () => setGen(gen + 1)),
      ),
    )
  }

// ---- video ----------------------------------------------------------------
//
// A stand-in decoder: a background thread that synthesises I420 frames (scrolling colour bars) and
// hands each to the UI thread. It is the shape a real decoder has, and it exercises the two things
// that make video work — `UiThread.post` for the thread hop, and a `video` widget whose frames
// never touch Cairo.
//
// Frames alternate between two plane sets. The worker fills one while the UI thread may still be
// uploading the other, which is what a decoder's frame pool does and what keeps the handover from
// racing the upload.

private val videoW = 320
private val videoH = 180

// The panel matches the frame's 16:9, so Contain has no bars to add. Give it a different shape and
// the frame keeps its own, centred, with the background showing either side — which is what a
// preview monitor should do.
private val videoPanelW = 400.0
private val videoPanelH = videoPanelW * videoH / videoW

// Y, U, V for the eight classic colour bars. Chroma planes are half-size on both axes (4:2:0), so
// each bar's U/V is written at half the resolution of its Y.
private val bars: Array[(Int, Int, Int)] = Array(
  (235, 128, 128), // white
  (210, 16, 146),  // yellow
  (170, 166, 16),  // cyan
  (145, 54, 34),   // green
  (106, 202, 222), // magenta
  (81, 90, 240),   // red
  (41, 240, 110),  // blue
  (16, 128, 128),  // black
)

private final class Planes(w: Int, h: Int):
  val y: Ptr[Byte] = stdlib.malloc(w * h).asInstanceOf[Ptr[Byte]]
  val u: Ptr[Byte] = stdlib.malloc((w / 2) * (h / 2)).asInstanceOf[Ptr[Byte]]
  val v: Ptr[Byte] = stdlib.malloc((w / 2) * (h / 2)).asInstanceOf[Ptr[Byte]]

  def free(): Unit = { stdlib.free(y.asInstanceOf[Ptr[Byte]]); stdlib.free(u.asInstanceOf[Ptr[Byte]]); stdlib.free(v.asInstanceOf[Ptr[Byte]]) }

  /** Draw the bars shifted by `phase` pixels, so successive frames scroll. */
  def render(phase: Int): Unit =
    val barW = w / bars.length
    var row  = 0
    while row < h do
      var col = 0
      while col < w do
        val (by, _, _) = bars((((col + phase) / barW) % bars.length + bars.length) % bars.length)
        y(row * w + col) = by.toByte
        col += 1
      row += 1
    var crow = 0
    while crow < h / 2 do
      var ccol = 0
      while ccol < w / 2 do
        val (_, bu, bv) = bars(((((ccol * 2) + phase) / barW) % bars.length + bars.length) % bars.length)
        u(crow * (w / 2) + ccol) = bu.toByte
        v(crow * (w / 2) + ccol) = bv.toByte
        ccol += 1
      crow += 1

/** The video panel: a `video` widget fed by a worker thread.
  *
  * Worth noticing what is *absent*. Nothing here requests a repaint, and nothing marks the tree
  * dirty — a frame arriving changes no Cairo pixel, because the widget paints a hole and the
  * runtime blits the texture underneath. Playback rides the present the loop already does each
  * vsync. The bars are letterboxed into the panel by [[VideoFit.Contain]], so the 16:9 frame keeps
  * its shape whatever the pane does. */
private val VideoPanel: Component[Unit] =
  view {
    val theme = useTheme()

    // The texture and the plane pool, built once. Creating a texture needs the runtime's renderer,
    // so this cannot run before the window is up — which useMemo-on-mount guarantees.
    val st = useMemo(
      () => {
        val tex = VideoTexture(videoW, videoH, VideoFormat.I420, VideoColorspace.BT601)
        (tex, Array(new Planes(videoW, videoH), new Planes(videoW, videoH)))
      },
      Array(),
    )
    val (tex, pool) = st

    // The "decoder". It owns the planes and the pacing; it never touches the tree, the texture, or
    // any state — it fills a frame and hands it over. That restraint is the whole rule (see the
    // threading guide): a setter called from here would race vdom's bookkeeping, not just a queue.
    useEffect(
      () => {
        @volatile var running = true
        val worker = new Thread(() => {
          var phase = 0
          var slot  = 0
          while running do
            val p = pool(slot)
            p.render(phase)
            UiThread.post(() => tex.update(p.y, videoW, p.u, videoW / 2, p.v, videoW / 2): Unit)
            phase += 3
            slot = (slot + 1) % pool.length
            Thread.sleep(33) // ~30fps
        })
        worker.setDaemon(true)
        worker.start()
        () => running = false
      },
      Array(),
    )

    useEffect(() => () => { tex.destroy(); pool.foreach(_.free()) }, Array())

    col(crossAxisAlignment = CrossAxisAlignment.Stretch, spacing = 10)(
      row(mainAxisAlignment = MainAxisAlignment.Center)(
        box(radius = 12, clip = true)(
          video(tex, fit = VideoFit.Contain, width = videoPanelW, height = videoPanelH),
        ),
      ),
      row(mainAxisAlignment = MainAxisAlignment.Center)(
        text("YUV frames from a worker thread — no Cairo, no repaint", size = 12, color = mutedInk(theme)),
      ),
    )
  }

val App = view {
  val (isDark, setDark, _)     = useState(true)
  val (count, setCount, _)     = useState(0)
  val (checked, setChecked, _) = useState(false)
  val (level, setLevel, _)     = useState(0.4)
  val (details, setDetails, _) = useState(false)
  val (name, setName, _)       = useState("")
  val (live, setLive, _)       = useState(true)
  val (size, setSize, _)       = useState("m")
  val (tab, setTab, _)         = useState("overview")
  val (dialog, setDialog, _)   = useState(false)
  val (menu, setMenu, _)       = useState(false)
  val (lastMenu, setLastMenu, _) = useState("ready")
  val menuRef                  = useRef[RenderObject | Null](null)
  val (colour, setColour, _)   = useState("")
  val (pickedRow, setPickedRow, _) = useState(-1)
  val (notes, setNotes, _)     = useState(
    "This is a multi-line editor that soft-wraps long lines to its width, so a paragraph this " +
      "long flows across as many visual rows as it needs instead of overflowing off the right edge. " +
      "Type into it — the caret, click-to-place, selection, and the arrow keys all move by visual " +
      "rows.\n\nPress Enter for a hard line break; the blank line above stays.",
  )

  // The active theme is one of the built-ins, chosen by the header switch. Everything below
  // — controls, body background, card chrome — paints from it, so the flip restyles the
  // whole window at once.
  val theme = if isDark then Theme.violetDark else Theme.violetLight
  val muted = mutedInk(theme)

  // A rounded, shadowed panel — the styling foundation applied as reusable chrome rather
  // than a baked-in widget. A local def so it captures the active theme.
  def card(children: VNode*): VNode =
    box(
      bg      = theme.surface,
      radius  = 12,
      shadow  = cardShadow(theme),
      padding = EdgeInsets.all(20),
    )(children*)

  ThemeProvider(theme)(
    // The app body: a solid background token behind everything, so the elevated card
    // surfaces lift off it in either scheme.
    box(bg = theme.background)(
      col(spacing = 16)(
        // Header bar: a horizontal gradient with the title and the light/dark switch.
        box(
          bg      = LinearGradient(
            Seq(ColorStop(0, Color.rgb(0x4dabf7)), ColorStop(1, Color.rgb(0x9775fa))),
            begin = Alignment.centerLeft,
            end   = Alignment.centerRight,
          ),
          height  = 56,
          padding = EdgeInsets.symmetric(horizontal = 20, vertical = 14),
        )(
          row(crossAxisAlignment = CrossAxisAlignment.Center, spacing = 12)(
            text("suit — styling & input", size = 22, color = Color.rgb(0x0b1418)),
            spacer(),
            text(s"menu: $lastMenu", color = Color.rgb(0x0b1418)),
            spacer(),
            text(if isDark then "dark" else "light", color = Color.rgb(0x0b1418)),
            Switch(isDark, setDark),
          ),
        ),

        // Application menu bar: top-level menus drawn with the toolkit's own widgets (no OS menu
        // bar), so it is identical on every platform. Click a label to open it; with one open,
        // sweep across the labels to slide between menus. Each item records what was chosen.
        menuBar(
          widgets.menu("File")(close =>
            Seq(
              MenuItem("New", () => { setLastMenu("File ▸ New"); close() }),
              MenuItem("Open…", () => { setLastMenu("File ▸ Open"); close() }),
              MenuItem("Save", () => { setLastMenu("File ▸ Save"); close() }),
            ),
          ),
          widgets.menu("Edit")(close =>
            Seq(
              MenuItem("Undo", () => { setLastMenu("Edit ▸ Undo"); close() }),
              MenuItem("Redo", () => { setLastMenu("Edit ▸ Redo"); close() }),
              MenuItem("Cut", () => { setLastMenu("Edit ▸ Cut"); close() }),
              MenuItem("Copy", () => { setLastMenu("Edit ▸ Copy"); close() }),
              MenuItem("Paste", () => { setLastMenu("Edit ▸ Paste"); close() }),
            ),
          ),
          widgets.menu("View")(close =>
            Seq(
              MenuItem("Zoom In", () => { setLastMenu("View ▸ Zoom In"); close() }),
              MenuItem("Zoom Out", () => { setLastMenu("View ▸ Zoom Out"); close() }),
              MenuItem("Toggle Theme", () => { setDark(!isDark); close() }),
            ),
          ),
          widgets.menu("Help")(close =>
            Seq(
              MenuItem("Documentation", () => { setLastMenu("Help ▸ Documentation"); close() }),
              MenuItem("About suit", () => { setLastMenu("Help ▸ About"); close() }),
            ),
          ),
        ),

        // Body: a scrolling column of cards. The viewport sets `textColor` once; the card
        // labels carry no colour of their own and inherit it through the cascade. There are
        // more cards than fit, so the wheel scrolls the list — clipped to the viewport, with
        // the scroll position living on the viewport itself.
        box(flex = 1, padding = EdgeInsets.all(20), textColor = theme.surfaceText)(
          scrollView(Axis.Vertical)(
            col(crossAxisAlignment = CrossAxisAlignment.Stretch, spacing = 16)(
              // The three interactive cards, then filler cards so the column overflows the
              // viewport and the wheel has something to scroll.
              Seq(
                card(
                  row(crossAxisAlignment = CrossAxisAlignment.Center, spacing = 16)(
                    Button("Increment", () => setCount(count + 1)),
                    text(s"count: $count"),
                    spacer(),
                    Badge(s"$count"),
                  ),
                ),
                card(
                  row(crossAxisAlignment = CrossAxisAlignment.Center, spacing = 16)(
                    Checkbox(checked, setChecked),
                    text(if checked then "enabled" else "disabled"),
                  ),
                ),
                card(
                  col(crossAxisAlignment = CrossAxisAlignment.Stretch, spacing = 10)(
                    text(s"level: ${(level * 100).toInt}%", color = muted),
                    box(width = 260)(
                      Slider(level, setLevel),
                    ),
                    // A determinate progress bar tracks the slider's value, fill animating.
                    ProgressBar(level),
                  ),
                ),
                // A switch (the on/off counterpart to the checkbox) and a single-select radio
                // group, both controlled and themed; a divider rules them apart.
                Card(
                  col(crossAxisAlignment = CrossAxisAlignment.Stretch, spacing = 12)(
                    row(crossAxisAlignment = CrossAxisAlignment.Center, spacing = 16)(
                      Switch(live, setLive),
                      text(if live then "live" else "paused"),
                    ),
                    Divider(false),
                    RadioGroup(Seq("s" -> "Small", "m" -> "Medium", "l" -> "Large"), size, setSize),
                  ),
                ),
                // A tab bar driving a switched panel, with a status callout per tab.
                Card(
                  col(crossAxisAlignment = CrossAxisAlignment.Stretch, spacing = 12)(
                    Tabs(Seq("overview" -> "Overview", "status" -> "Status"), tab, setTab),
                    tab match
                      case "status" => Alert(AlertKind.Success, "All systems nominal.")
                      case _        => text("An overview of the styling, input, and motion systems.", color = muted),
                  ),
                ),
                // A text field: type into it, click/drag to select, arrows/Home/End to move,
                // Ctrl+A to select all. The label below echoes the controlled value.
                card(
                  col(spacing = 10)(
                    col(crossAxisAlignment = CrossAxisAlignment.Stretch)(
                      TextField(name, setName),
                    ),
                    text(if name.isEmpty then "type your name above" else s"hello, $name", color = muted),
                  ),
                ),
                // A multi-line editor that soft-wraps, sitting in the left pane of a splitter. Drag
                // the gutter: the editor re-wraps live to its new pane width (the box's resize
                // notification re-renders it even though the drag never touches its subtree). The
                // caret, click-to-place, selection, and arrow keys all move by the wrapped rows.
                card(
                  col(crossAxisAlignment = CrossAxisAlignment.Stretch, spacing = 10)(
                    text("TextArea in a splitter — drag the gutter; the editor re-wraps to its pane", color = muted),
                    sizedBox(height = 190)(
                      box(radius = 12, clip = true, border = theme.border, borderWidth = 1)(
                        splitter(initial = 0.55, min = 0.25, max = 0.85)(
                          box(bg = theme.background, padding = EdgeInsets.all(8))(
                            col(crossAxisAlignment = CrossAxisAlignment.Stretch)(
                              TextArea(notes, setNotes),
                            ),
                          ),
                          box(bg = theme.surface, padding = EdgeInsets.all(12))(
                            text(
                              "Drag the divider left and right. The editor on the left re-flows its " +
                                "text to whatever width its pane becomes.",
                              color    = muted,
                              maxLines = 0,
                            ),
                          ),
                        ),
                      ),
                    ),
                  ),
                ),
                // Typography: multi-line text. The first paragraph wraps across as many
                // lines as it needs; the second is capped at two lines and trims its tail
                // with an ellipsis; the captions show centre and right alignment. All of it
                // is the same constraint pass that lays out every other widget.
                card(
                  col(crossAxisAlignment = CrossAxisAlignment.Stretch, spacing = 10)(
                    text("Typography", size = 16, align = TextAlign.Center),
                    text(
                      "This paragraph wraps across as many lines as it needs, breaking at word " +
                        "boundaries to fit the width the layout hands it — the very same constraint " +
                        "pass that sizes every other widget in the window.",
                      color    = muted,
                      maxLines = 0,
                    ),
                    Divider(false),
                    text(
                      "Capped at two lines, this paragraph trims its tail and marks the cut with an " +
                        "ellipsis, so the column keeps its rhythm no matter how much text you pour " +
                        "into it here.",
                      color    = muted,
                      maxLines = 2,
                      overflow = TextOverflow.Ellipsis,
                    ),
                    text("right-aligned caption", color = muted, align = TextAlign.Right),
                  ),
                ),
                // Font weights: one variable font (InterVariable), its `wght` axis driven per
                // run. Each line is the same words at a different weight, all rendered from the
                // single embedded font file — FreeType reshapes the outlines for each weight and
                // Cairo caches a distinct face per weight behind the scenes.
                card(
                  col(crossAxisAlignment = CrossAxisAlignment.Stretch, spacing = 6)(
                    text("Font weights — one variable font", color = muted),
                    text("Light — the quick brown fox", weight = FontWeight.Light),
                    text("Regular — the quick brown fox", weight = FontWeight.Normal),
                    text("Medium — the quick brown fox", weight = FontWeight.Medium),
                    text("SemiBold — the quick brown fox", weight = FontWeight.SemiBold),
                    text("Bold — the quick brown fox", weight = FontWeight.Bold),
                  ),
                ),
                // SVG: a vector image rendered straight into the Cairo context through librsvg.
                // The one document draws crisp at any size — here at 24, 48, and 72 px.
                card(
                  col(crossAxisAlignment = CrossAxisAlignment.Stretch, spacing = 10)(
                    text("SVG — vector, crisp at any size", color = muted),
                    row(crossAxisAlignment = CrossAxisAlignment.Center, spacing = 16)(
                      svg(appIcon, width = 24, height = 24),
                      svg(appIcon, width = 48, height = 48),
                      svg(appIcon, width = 72, height = 72),
                    ),
                  ),
                ),
                // Raster image: a JPEG embedded in the binary, decoded once by turbojpeg into a
                // Cairo surface and blitted/scaled by the canvas. The second copy is rounded by a
                // clipping box, the same overflow-hidden path the scroll view and text field use.
                card(
                  col(crossAxisAlignment = CrossAxisAlignment.Stretch, spacing = 10)(
                    text("Raster — a JPEG decoded with turbojpeg", color = muted),
                    row(crossAxisAlignment = CrossAxisAlignment.Center, spacing = 16)(
                      image(photo, width = 160, height = 100),
                      box(radius = 12, clip = true)(
                        image(photo, width = 96, height = 96),
                      ),
                    ),
                  ),
                ),
                // A live canvas: arbitrary 2D drawing through the same Canvas the widgets use,
                // animated by useFrame. The orbiting comet advances a phase held in a ref each
                // frame and repaints; the rest of the window stays still. A clipping box rounds it.
                card(
                  col(crossAxisAlignment = CrossAxisAlignment.Stretch, spacing = 10)(
                    text("Canvas — custom drawing, animated with useFrame", color = muted),
                    box(radius = 12, clip = true)(OrbitCanvas()),
                  ),
                ),
                // An application-owned surface: the app draws into its own Cairo surface with the
                // full raw engine (here a gradient and serif text centred by Cairo's text extents —
                // things suit's Canvas does not expose) and hands it over to be blitted. Redraw
                // re-paints the surface and pokes its handle, so just this panel re-composites.
                card(
                  col(crossAxisAlignment = CrossAxisAlignment.Stretch, spacing = 10)(
                    text("Surface — an app-owned Cairo surface, drawn with raw Cairo", color = muted),
                    SurfacePanel(),
                  ),
                ),
                // Video: the one thing suit does not rasterise. A worker thread synthesises YUV
                // frames and hands them over with UiThread.post; the widget punches a hole and the
                // runtime blits the texture underneath, converting and scaling on the GPU. Nothing
                // here asks for a repaint — the frames ride the vsync present.
                card(
                  col(crossAxisAlignment = CrossAxisAlignment.Stretch, spacing = 10)(
                    text("Video — YUV straight to the GPU, bypassing Cairo", color = muted),
                    VideoPanel(),
                  ),
                ),
                // Cursors: hover each swatch to see the pointer shape change. The runtime resolves
                // the Cursor of whatever is under the pointer (inheriting from ancestors) and sets
                // the platform's own system cursor. The built-in widgets already carry these.
                card(
                  col(crossAxisAlignment = CrossAxisAlignment.Stretch, spacing = 10)(
                    text("Cursors — hover a swatch to change the pointer shape", color = muted),
                    col(spacing = 8)(
                      row(spacing = 8)(cursorRow(theme, muted, cursorSwatchesTop)*),
                      row(spacing = 8)(cursorRow(theme, muted, cursorSwatchesBottom)*),
                    ),
                  ),
                ),
                // A splitter: two panes divided by a draggable gutter. Drag the divider (or focus
                // it and use the arrow keys) to resize; each pane is clipped to its share. Given a
                // bounded height here, it fills the card width and divides it.
                card(
                  col(crossAxisAlignment = CrossAxisAlignment.Stretch, spacing = 10)(
                    text("Splitter — drag the gutter to resize the panes", color = muted),
                    sizedBox(height = 150)(
                      box(radius = 12, clip = true, border = theme.border, borderWidth = 1)(
                        splitter(initial = 0.35, min = 0.15, max = 0.7)(
                          box(bg = theme.background, padding = EdgeInsets.all(12))(
                            text("Sidebar", color = muted),
                          ),
                          box(bg = theme.surface, padding = EdgeInsets.all(12))(
                            text("Content — drag the divider to the left of this pane.", color = muted, maxLines = 0),
                          ),
                        ),
                      ),
                    ),
                  ),
                ),
                // A dropdown select: the field shows the current choice and opens a themed menu of
                // options below it (the same anchored-overlay path as the menu), reporting the
                // chosen value through onChange. The label echoes the controlled value.
                card(
                  col(crossAxisAlignment = CrossAxisAlignment.Stretch, spacing = 10)(
                    text("Select — a dropdown of options", color = muted),
                    row(crossAxisAlignment = CrossAxisAlignment.Center, spacing = 16)(
                      Select(
                        Seq("red" -> "Red", "green" -> "Green", "blue" -> "Blue", "violet" -> "Violet"),
                        colour,
                        setColour,
                        placeholder = "Pick a colour",
                        width       = 180,
                      ),
                      text(if colour.isEmpty then "nothing picked" else s"picked: $colour", color = muted),
                    ),
                  ),
                ),
                // A right-click context menu: a right-press anywhere on the panel opens a menu at
                // the cursor (a left-click passes through). Each item closes the menu when chosen.
                card(
                  col(crossAxisAlignment = CrossAxisAlignment.Stretch, spacing = 10)(
                    text("Context menu — right-click the area below", color = muted),
                    contextMenu(width = 160)(
                      box(
                        bg          = theme.background,
                        radius      = 8,
                        border      = theme.border,
                        borderWidth = 1,
                        padding     = EdgeInsets.all(24),
                      )(
                        center(text("right-click me", color = muted)),
                      ),
                    ) { close =>
                      Seq(
                        MenuItem("Cut", () => close()),
                        MenuItem("Copy", () => close()),
                        MenuItem("Paste", () => close()),
                      )
                    },
                  ),
                ),
                // A data grid: click a header to sort by that column (click again to reverse), and
                // drag the thin handle on a header's right edge to resize the column. Selection and
                // onSelect are in original-row terms, so the highlight stays on the same row across
                // sorts. Given a bounded height, the body scrolls and only builds the visible rows.
                card(
                  col(crossAxisAlignment = CrossAxisAlignment.Stretch, spacing = 10)(
                    text("Data table — click a header to sort, drag an edge to resize", color = muted),
                    sizedBox(height = 170)(
                      box(radius = 12, clip = true, border = theme.border, borderWidth = 1)(
                        dataTable(
                          Seq("id", "name", "role"),
                          Vector(
                            Vector("3", "Carol", "Admin"),
                            Vector("1", "Alice", "User"),
                            Vector("2", "Bob", "User"),
                            Vector("5", "Eve", "Guest"),
                            Vector("4", "Dan", "Admin"),
                          ),
                          selected = pickedRow,
                          onSelect = setPickedRow,
                        ),
                      ),
                    ),
                  ),
                ),
                // A scroll area with a visible, draggable scrollbar — the themed counterpart to the
                // bare scroll view. The bar rides the right edge and appears only because the rows
                // overflow the bounded height; drag it or use the wheel.
                card(
                  col(crossAxisAlignment = CrossAxisAlignment.Stretch, spacing = 10)(
                    text("Scroll area — a visible, draggable scrollbar", color = muted),
                    sizedBox(height = 140)(
                      box(radius = 12, clip = true, border = theme.border, borderWidth = 1)(
                        scrollArea()(
                          col(crossAxisAlignment = CrossAxisAlignment.Stretch, mainAxisSize = MainAxisSize.Min, spacing = 4)(
                            (1 to 24).map(i => box(padding = EdgeInsets.symmetric(horizontal = 12, vertical = 8))(text(s"row $i", color = muted)))*,
                          ),
                        ),
                      ),
                    ),
                  ),
                ),
                // An enter/exit reveal: the button toggles a panel that animates in and out.
                card(
                  col(spacing = 8)(
                    row(crossAxisAlignment = CrossAxisAlignment.Center, spacing = 16)(
                      Button(if details then "Hide details" else "Show details", () => setDetails(!details)),
                    ),
                    DetailPanel(details),
                  ),
                ),
                // A dropdown menu and a tooltip — the anchored overlays. The menu floats just
                // below its trigger (the ref ties it to the trigger's on-screen rectangle) and
                // dismisses on an outside click or Escape; the tooltip appears on hover and is
                // click-through. Both portal into the overlay, so neither is clipped by the card.
                card(
                  row(crossAxisAlignment = CrossAxisAlignment.Center, spacing = 16)(
                    box(ref = menuRef)(
                      Button("Options", () => setMenu(true)),
                    ),
                    Menu(menu, () => setMenu(false), menuRef)(
                      MenuItem("Rename", () => setMenu(false)),
                      MenuItem("Duplicate", () => setMenu(false)),
                      MenuItem("Delete", () => setMenu(false)),
                    ),
                    Tooltip(
                      "Portals into the overlay; click-through.",
                      placement = Placement(side = PopoverSide.Above, gap = 6),
                    )(
                      text("hover me", color = muted),
                    ),
                  ),
                ),
                // A modal dialog: the button opens it, and it floats centred above everything
                // through the overlay layer, traps focus, and closes on the scrim, Escape, or its
                // own Close button. The Dialog node itself can sit anywhere in the tree — it
                // portals its content into the overlay.
                card(
                  row(crossAxisAlignment = CrossAxisAlignment.Center, spacing = 16)(
                    Button("Open dialog", () => setDialog(true)),
                    Dialog(dialog, () => setDialog(false))(
                      col(spacing = 16)(
                        text("A modal dialog", size = 18),
                        text("It floats above the page, dims the rest, and traps focus until you dismiss it.", color = muted, maxLines = 0),
                        row(mainAxisAlignment = MainAxisAlignment.End)(
                          Button("Close", () => setDialog(false)),
                        ),
                      ),
                    ),
                  ),
                ),
              ).concat((1 to 6).map(i => card(text(s"item $i — scroll to see me"))))*,
            ),
          ),
        ),
      ),
    ),
  )
}

@main def main(): Unit =
  Suit.run("suit — styling & input demo", 520, 420)(App())
