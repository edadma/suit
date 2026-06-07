package io.github.edadma.suit.demo

import io.github.edadma.suit.*
import io.github.edadma.suit.dsl.*
import io.github.edadma.suit.widgets.*

// A showcase of the styling system, the input model, and motion. The whole UI is wrapped
// in a `ThemeProvider` carrying a custom theme, so the built-in widgets (Button, Checkbox,
// Slider) restyle themselves from it with no widget code touched — that is the point of
// keeping styling general rather than baked in. A gradient header, rounded cards with
// drop shadows, and the text-style cascade (the body sets a `textColor` once and the
// labels inherit it) round out the picture.
//
// Motion runs throughout: the button tint fades on hover and press, the checkbox mark
// scales and fades as it ticks, the slider thumb glides toward its value, and the "Show
// details" panel fades and rises in then eases back out before unmounting (the enter/exit
// path from `usePresence` + `useTransition`). All of it is driven by the runtime's frame
// clock, which repaints each frame only while something is animating and goes quiet once
// it settles.
//
// It exercises the whole pipe: vdom builds the VNode tree, `SuitHostConfig` creates the
// RenderObjects, the installed measurer sizes the text, the constraint protocol lays
// everything out, the frame loop routes pointer, wheel, and keyboard events through the
// routers and the focus manager and pumps the motion clock, the widgets' `useState` writes
// flow through the scheduler seam, the tree is marked dirty, and the loop repaints. Tab is
// not wired here, so click a control to focus it, then use the keyboard (Space/Enter on the
// button and checkbox, arrows on the slider).

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
                ),
              ),
              card(
                row(crossAxisAlignment = CrossAxisAlignment.Center, spacing = 16)(
                  Checkbox(checked, setChecked),
                  text(if checked then "enabled" else "disabled"),
                ),
              ),
              card(
                col(spacing = 10)(
                  text(s"level: ${(level * 100).toInt}%", color = muted),
                  box(width = 260)(
                    Slider(level, setLevel),
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
            ).concat((1 to 6).map(i => card(text(s"item $i — scroll to see me"))))*,
          ),
        ),
      ),
    ),
  )
}

@main def main(): Unit =
  Suit.run("suit — styling & input demo", 520, 420)(App())
