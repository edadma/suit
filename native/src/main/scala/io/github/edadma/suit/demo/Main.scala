package io.github.edadma.suit.demo

import io.github.edadma.suit.*
import io.github.edadma.suit.dsl.*
import io.github.edadma.suit.widgets.*

// A showcase of the styling system, the input model, and motion. The whole UI is wrapped
// in a `ThemeProvider` carrying a custom theme, so the built-in widgets (Button, Checkbox,
// Slider, Switch, RadioGroup, ProgressBar, Tabs, Badge, Divider, Alert, Card, Dialog, Menu,
// Tooltip) restyle
// themselves from it with no widget code touched — that is the point of keeping styling
// general rather than baked in. A gradient header, rounded cards with drop shadows, the
// text-style cascade (the body sets a `textColor` once and the labels inherit it), and a
// typography card showing multi-line wrapping, two-line ellipsis, and alignment round out
// the picture.
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

/** A custom theme: a violet primary on slightly cooler surfaces, larger corners than the
  * stock theme. Swapping this one record restyles every control below. */
private val appTheme = Theme.default.copy(
  primary       = Color.rgb(0x9775fa),
  primaryHover  = Color.rgb(0xb197fc),
  primaryActive = Color.rgb(0x845ef7),
  surface       = Color.rgb(0x2b3035),
  accent        = Color.rgb(0x9775fa),
  radius        = 10.0,
)

private val ink   = Color.rgb(0xf1f3f5)
private val muted = Color.rgb(0xadb5bd)

/** A rounded, shadowed panel — the styling foundation applied as reusable chrome rather
  * than a baked-in widget. */
private def card(children: VNode*): VNode =
  box(
    bg      = appTheme.surface,
    radius  = 12,
    shadow  = Shadow(color = Color(0, 0, 0, 110), offset = Offset(0, 4), blur = 12),
    padding = EdgeInsets.all(20),
  )(children*)

/** A detail panel that animates on the way in and out. `usePresence` keeps it mounted
  * through its exit so the close can play, and `useTransition` fades and lifts it: the
  * opacity and a small vertical inset both glide toward the open state and back. This is
  * the enter/exit half of the motion system — the same pair of hooks a Dialog or Tooltip
  * will lean on. */
private val DetailPanel: Component[Boolean] =
  component[Boolean] { open =>
    val p   = usePresence(open, exitMs = 220)
    val amt = useTransition(if p.phase == PresencePhase.Open then 1.0 else 0.0, 220)

    if p.mounted then
      box(padding = EdgeInsets(top = 16 * (1 - amt), right = 0, bottom = 0, left = 0))(
        box(
          bg      = appTheme.surface,
          radius  = 12,
          opacity = amt,
          shadow  = Shadow(color = Color(0, 0, 0, 110), offset = Offset(0, 4), blur = 12),
          padding = EdgeInsets.all(20),
        )(
          text("Now you see me — I fade and rise in, then ease back out before unmounting.", color = muted),
        ),
      )
    else VEmpty
  }

val App = view {
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

  ThemeProvider(appTheme)(
    col(spacing = 16)(
      // Header bar: a horizontal gradient with a title.
      box(
        bg      = LinearGradient(
          Seq(ColorStop(0, Color.rgb(0x4dabf7)), ColorStop(1, Color.rgb(0x9775fa))),
          begin = Alignment.centerLeft,
          end   = Alignment.centerRight,
        ),
        height  = 56,
        padding = EdgeInsets.symmetric(horizontal = 20, vertical = 14),
      )(
        text("suit — styling & input", size = 22, color = Color.rgb(0x0b1418)),
      ),

      // Body: a scrolling column of cards. The viewport sets `textColor` once; the card
      // labels carry no colour of their own and inherit it through the cascade. There are
      // more cards than fit, so the wheel scrolls the list — clipped to the viewport, with
      // the scroll position living on the viewport itself.
      box(flex = 1, padding = EdgeInsets.all(20), textColor = ink)(
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
  )
}

@main def main(): Unit =
  Suit.run("suit — styling & input demo", 520, 420)(App())
