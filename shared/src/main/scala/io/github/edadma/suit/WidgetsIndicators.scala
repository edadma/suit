package io.github.edadma.suit

import io.github.edadma.suit.dsl.*

// The non-interactive (or lightly interactive) display widgets: cards, dividers, badges,
// progress bars, switches, radio groups, alerts, and tabs. They paint status and structure
// from the [[Theme]] in context.
private[suit] trait WidgetsIndicators extends WidgetsSupport:

  /** A surface panel: a themed, rounded, shadowed container that groups related content.
    * It is pure chrome — no state, no interaction — so it is a [[container]] over its
    * children, padding them by the theme's spacing and painting the theme's surface colour,
    * border, and a soft elevation shadow. Use it to lift a block of UI off the background. */
  val Card: Container =
    container { children =>
      val theme = useTheme()
      box(
        bg          = theme.surface,
        border      = theme.border,
        borderWidth = 1,
        radius      = theme.radius,
        shadow      = Shadow(),
        padding     = EdgeInsets.all(theme.spacing * 2),
      )(children*)
    }

  /** A separator rule — a hairline in the theme's border colour for dividing content.
    * `Divider(false)` is a horizontal rule (a full-width 1px line); `Divider(true)` is a
    * vertical one (a full-height 1px line). It takes its length from the cross axis of its
    * parent, so put a horizontal divider in a stretched column and a vertical one in a
    * stretched row. */
  val Divider: Component[Boolean] =
    component[Boolean] { vertical =>
      val theme = useTheme()
      if vertical then box(width = 1, bg = theme.border)()
      else box(height = 1, bg = theme.border)()
    }

  /** A badge: a small rounded pill that labels or counts. It paints the theme's accent
    * with readable ink and rounds to a stadium, sized to its (slightly smaller) label.
    *
    * The top padding runs a hair tighter than the bottom: symmetric padding centres the
    * cap height, but a descender ('g', 'p', 'y') then crowds the bottom edge and the label
    * reads as sitting low. Biasing the text up by a pixel optically centres it in the pill. */
  val Badge: Component[String] =
    component[String] { label =>
      val theme = useTheme()
      box(
        bg      = theme.accent,
        radius  = 999,
        padding = EdgeInsets(top = 1, right = 9, bottom = 3, left = 9),
      )(
        text(label, size = theme.textSize * 0.8, color = theme.onPrimary),
      )
    }

  /** A determinate progress bar over 0..1: a rounded track with an accent fill proportional
    * to `value`. The fill is expressed as a flex split of a row — `value`-worth of accent
    * beside `1 - value`-worth of empty space — so it fills the right fraction of whatever
    * width the track is given, with no pixel measurement. The fill width animates toward the
    * target through [[useTransition]], so a jump to a new value glides. */
  val ProgressBar: Component[Double] =
    component[Double] { value =>
      val theme = useTheme()
      val shown = clamp01(useTransition(clamp01(value), 200))

      // Split a 1000-unit row between filled and empty so the accent occupies exactly the
      // shown fraction of the track's width; drop a zero-width side so it has no stray child.
      val fillFlex = math.round(shown * 1000).toInt
      val restFlex = 1000 - fillFlex
      val fill     = if fillFlex > 0 then Seq(box(flex = fillFlex, bg = theme.accent, radius = 4)()) else Seq.empty
      val rest     = if restFlex > 0 then Seq(box(flex = restFlex)()) else Seq.empty

      box(height = 8, bg = theme.track, radius = 4, clip = true)(
        row(crossAxisAlignment = CrossAxisAlignment.Stretch)(Seq.concat(fill, rest)*),
      )
    }

  /** A switch: a pill-shaped toggle with a sliding thumb, the on/off counterpart to a
    * checkbox. It is controlled — it renders the `on` it is given and reports the flip
    * through `onChange` on a click or on Space while focused. The thumb glides between the
    * ends and the track colour fades between the inactive groove and the accent through
    * [[useTransition]], so the toggle animates rather than snapping. */
  val Switch: Component2[Boolean, Boolean => Unit] =
    component[Boolean, Boolean => Unit] { (on, onChange) =>
      val theme = useTheme()

      // One amount drives both the track tint and the thumb position: 0 = off (left, groove),
      // 1 = on (right, accent).
      val amt   = useTransition(if on then 1.0 else 0.0, 150)
      val track = Color.lerp(theme.track, theme.accent, amt)

      box(
        width     = 44,
        height    = 24,
        bg        = track,
        radius    = 12,
        padding   = EdgeInsets.all(3),
        focusable = true,
        onClick   = _ => onChange(!on),
        onKeyDown = e => if e.scancode == Key.Space then onChange(!on),
      )(
        // The padded inner area is 38×18; the 18×18 thumb slides its left edge from 0 (off)
        // to 20 (on) as alignment x runs -1..1.
        align(Alignment(amt * 2 - 1, 0))(
          box(width = 18, height = 18, bg = theme.surface, radius = 9)(),
        ),
      )
    }

  // One radio button: an outer ring that fills with an accent dot when selected. Kept a
  // private keyed component so each option owns its own selection animation (hooks must be
  // called in a stable order, so the per-option transition can't live in the group's loop).
  private val RadioRow: Component3[Boolean, String, () => Unit] =
    component[Boolean, String, () => Unit] { (selected, label, onSelect) =>
      val theme = useTheme()
      val amt   = useTransition(if selected then 1.0 else 0.0, 120)
      val dot: Seq[VNode] =
        if amt > 0.001 then Seq(center(box(width = 10 * amt, height = 10 * amt, bg = theme.accent, radius = 5, opacity = amt)()))
        else Seq.empty

      row(spacing = 8, crossAxisAlignment = CrossAxisAlignment.Center)(
        box(
          width       = 18,
          height      = 18,
          border      = if selected then theme.accent else theme.border,
          borderWidth = 2,
          radius      = 9,
          focusable   = true,
          onClick     = _ => onSelect(),
          onKeyDown   = e => if e.scancode == Key.Space then onSelect(),
        )(dot*),
        text(label, color = theme.surfaceText),
      )
    }

  /** A radio group: a single-select column of options, each a `(value, label)` pair. It is
    * controlled — the row whose value equals `selected` shows its dot, and clicking a row
    * (or Space while it is focused) reports that row's value through `onChange`. Each option
    * is keyed by its value so it reconciles in place as the selection moves. */
  val RadioGroup: Component3[Seq[(String, String)], String, String => Unit] =
    component[Seq[(String, String)], String, String => Unit] { (options, selected, onChange) =>
      val theme = useTheme()
      col(spacing = theme.spacing)(
        options.map { case (value, label) =>
          RadioRow(value == selected, label, () => onChange(value), value)
        }*,
      )
    }

  /** The meaning of an [[Alert]] — picks which status colour it paints from the theme. */
  enum AlertKind:
    case Info, Success, Warning, Danger

  /** A callout: a tinted, bordered panel that draws attention to a message, coloured by its
    * [[AlertKind]] from the theme's status roles. The background is the status colour at low
    * alpha with a solid status border, so it reads as a coloured banner without overpowering
    * the surrounding surface. */
  val Alert: Component2[AlertKind, String] =
    component[AlertKind, String] { (kind, message) =>
      val theme = useTheme()
      val hue = kind match
        case AlertKind.Info    => theme.info
        case AlertKind.Success => theme.success
        case AlertKind.Warning => theme.warning
        case AlertKind.Danger  => theme.danger

      box(
        bg          = hue.withAlpha(40),
        border      = hue,
        borderWidth = 1,
        radius      = theme.radius,
        padding     = EdgeInsets.all(theme.spacing * 1.5),
      )(
        text(message, color = theme.surfaceText),
      )
    }

  // One tab: a labelled, focusable header that fills with the surface colour and brightens
  // its label as it becomes selected, lifting it off the bar like a card tab. It wraps its
  // label rather than stretching, so the bar lays the tabs end to end. Private + keyed, like
  // RadioRow, so each tab owns its own selection animation.
  private val Tab: Component3[Boolean, String, () => Unit] =
    component[Boolean, String, () => Unit] { (selected, label, onSelect) =>
      val theme = useTheme()
      val amt   = useTransition(if selected then 1.0 else 0.0, 150)
      val fill  = Color.fade(theme.surface, amt)
      val ink   = Color.lerp(theme.surfaceText.withAlpha(150), theme.surfaceText, amt)

      box(
        bg        = fill,
        corners   = BorderRadius.top(theme.radius),
        padding   = EdgeInsets.symmetric(horizontal = 12, vertical = 8),
        focusable = true,
        onClick   = _ => onSelect(),
        onKeyDown = e => if e.scancode == Key.Space || e.scancode == Key.Enter then onSelect(),
      )(
        text(label, color = ink),
      )
    }

  /** A tab bar: a row of selectable headers, each a `(value, label)` pair. It is controlled —
    * the tab whose value equals `selected` is highlighted and underlined, and clicking a tab
    * (or Space/Enter while it is focused) reports that tab's value through `onChange`. Pair it
    * with the caller's own switch on `selected` to swap the panel below. */
  val Tabs: Component3[Seq[(String, String)], String, String => Unit] =
    component[Seq[(String, String)], String, String => Unit] { (tabs, selected, onChange) =>
      row(spacing = 4)(
        tabs.map { case (value, label) =>
          Tab(value == selected, label, () => onChange(value), value)
        }*,
      )
    }
