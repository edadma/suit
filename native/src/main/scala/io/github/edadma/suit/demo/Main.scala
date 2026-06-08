package io.github.edadma.suit.demo

import io.github.edadma.suit.*
import io.github.edadma.suit.dsl.*
import io.github.edadma.suit.widgets.*

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
  val menuRef                  = useRef[RenderObject | Null](null)

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
            text(if isDark then "dark" else "light", color = Color.rgb(0x0b1418)),
            Switch(isDark, setDark),
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
                    Tooltip("Portals into the overlay; click-through.")(
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
                        text("It floats above the page, dims the rest, and traps focus until you dismiss it.", color = muted),
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
