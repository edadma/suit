package io.github.edadma.suit

import io.github.edadma.suit.dsl.*

// The widget library — the salle-equivalent: small reusable controls composed from
// the DSL primitives and vdom hooks. A widget is an ordinary vdom component, so its
// interaction state (hover, pressed) lives in `useState` and survives re-renders, and
// it reconciles in place exactly like an application component. Each widget is purely
// declarative output over `box`/`text`/`row`/`stack`; the render tree, layout, and
// input routing underneath are what give it pixels and behaviour.
//
// Every widget paints from the [[Theme]] it reads out of context ([[useTheme]]) — colours,
// corner radius, spacing — so a [[ThemeProvider]] restyles the whole set at once without
// touching widget code. The controlled widgets (Checkbox, Slider, Switch, RadioGroup, Tabs,
// TextField) never hold their own value: they render what they are given and report changes
// through a callback, leaving the state to the parent.
object widgets:

  private def clamp01(x: Double): Double =
    if x < 0.0 then 0.0 else if x > 1.0 then 1.0 else x

  private def clampIdx(i: Int, n: Int): Int =
    if i < 0 then 0 else if i > n then n else i

  /** A push button: a labelled, focusable rectangle that calls `onPressed` when clicked
    * (a press and release on the button) or activated from the keyboard (Space or Enter
    * while focused). It tints on hover and while held. It paints from the theme in
    * context ([[useTheme]]) — primary fill, `onPrimary` ink, themed corner radius — so a
    * [[ThemeProvider]] restyles it without touching this code.
    *
    * The tint is animated: hover and press each drive a 0..1 amount through
    * [[useTransition]], and the fill is the theme colours blended by those amounts, so the
    * button fades between states rather than snapping. With motion settled the colour is
    * exactly the target theme token. */
  val Button: Component2[String, () => Unit] =
    component[String, () => Unit] { (label, onPressed) =>
      val theme                    = useTheme()
      val (hover, setHover, _)     = useState(false)
      val (pressed, setPressed, _) = useState(false)

      // Animate toward 1 while hovered / held and back to 0 when not; press is a touch
      // quicker than hover so a click reads as crisp while the hover glow is gentle.
      val hoverAmt = useTransition(if hover then 1.0 else 0.0, 120)
      val pressAmt = useTransition(if pressed then 1.0 else 0.0, 90)

      // Blend the resting fill toward the hover tint, then toward the active tint — press
      // layered over hover, so holding always wins and releasing eases back through hover.
      val bg = Color.lerp(Color.lerp(theme.primary, theme.primaryHover, hoverAmt), theme.primaryActive, pressAmt)

      box(
        bg           = bg,
        radius       = theme.radius,
        padding      = EdgeInsets.symmetric(horizontal = 16, vertical = 10),
        focusable    = true,
        onMouseEnter = _ => setHover(true),
        onMouseLeave = _ => setHover(false),
        onMouseDown  = _ => setPressed(true),
        onMouseUp    = _ => setPressed(false),
        onClick      = _ => onPressed(),
        onKeyDown    = e => if e.scancode == Key.Space || e.scancode == Key.Enter then onPressed(),
      )(
        text(label, color = theme.onPrimary),
      )
    }

  /** A checkbox: a small focusable square that toggles between checked and unchecked,
    * calling `onChange` with the new state on a click or on Space while focused. It is a
    * controlled widget — it draws the `checked` it is given and never holds the value
    * itself, so the parent owns the state. The check is a filled inner square (no glyph
    * font dependency), which scales and fades in when ticked and back out when cleared via
    * [[useTransition]]; settled, it is the full 12×12 mark or absent. */
  val Checkbox: Component2[Boolean, Boolean => Unit] =
    component[Boolean, Boolean => Unit] { (checked, onChange) =>
      val theme                = useTheme()
      val (hover, setHover, _) = useState(false)

      // The mark animates between absent (0) and full (1); render it only while it has any
      // presence, growing from a point and fading in as it ticks.
      val markAmt = useTransition(if checked then 1.0 else 0.0, 120)
      val mark: Seq[VNode] =
        if markAmt > 0.001 then
          Seq(center(box(width = 12 * markAmt, height = 12 * markAmt, bg = theme.accent, radius = 2, opacity = markAmt)()))
        else Seq.empty

      box(
        width        = 20,
        height       = 20,
        bg           = theme.surface,
        border       = if hover then theme.accent else theme.border,
        borderWidth  = 2,
        radius       = 4,
        focusable    = true,
        onMouseEnter = _ => setHover(true),
        onMouseLeave = _ => setHover(false),
        onClick      = _ => onChange(!checked),
        onKeyDown    = e => if e.scancode == Key.Space then onChange(!checked),
      )(mark*)
    }

  /** A horizontal slider over the range 0..1: a full-width track with a draggable thumb.
    * It is controlled — it renders the `value` it is given and reports a new value
    * through `onChange` on a press, a drag, or the arrow keys while focused. The new
    * value comes from the press position in the slider's own coordinate space
    * (`local.x / size.width`), which is why the handlers live on the outer track and not
    * on the thumb. */
  val Slider: Component2[Double, Double => Unit] =
    component[Double, Double => Unit] { (value, onChange) =>
      val theme = useTheme()
      val v     = clamp01(value)

      // The reported value is always the controlled one; the thumb's drawn position glides
      // toward it through a short transition, so an arrow-key step slides rather than jumps
      // and a drag trails the cursor by a hair. `onChange` still carries the exact `v`.
      val shown = clamp01(useTransition(v, 90))

      def frac(e: PointerEvent): Double =
        if e.size.width <= 0 then 0.0 else clamp01(e.local.x / e.size.width)

      box(
        height      = 24,
        focusable   = true,
        onMouseDown = e => onChange(frac(e)),
        onMouseMove = e => if e.button != 0 then onChange(frac(e)),
        onKeyDown = e =>
          e.scancode match
            case Key.Left  => onChange(clamp01(v - 0.05))
            case Key.Right => onChange(clamp01(v + 0.05))
            case _         => (),
      )(
        stack(Alignment.center)(
          // The groove: a thin bar stretched to the full width and centred vertically.
          col(crossAxisAlignment = CrossAxisAlignment.Stretch, mainAxisAlignment = MainAxisAlignment.Center)(
            box(height = 4, bg = theme.track, radius = 2)(),
          ),
          // The thumb: positioned by the glided fraction — alignment x = shown*2-1 maps
          // 0..1 to left..right.
          align(Alignment(shown * 2 - 1, 0))(
            box(width = 16, height = 16, bg = theme.accent, border = theme.surface, borderWidth = 2, radius = 8)(),
          ),
        ),
      )
    }

  /** A single-line text field: a focusable, bordered box that edits a string. It is
    * **controlled** — it renders the `value` it is given and reports edits through
    * `onChange`, so the parent owns the text. While focused it receives typed characters
    * (the runtime opens the platform text-input session for it) and editing keys: Backspace
    * and Delete remove, the arrows / Home / End move the caret (with Shift to extend a
    * selection), and Ctrl+A selects all. A click places the caret at the nearest character
    * boundary and a drag selects a range; typing or a delete replaces the selection.
    *
    * Caret and selection positions come from measuring text prefixes through the installed
    * [[TextMeasurer]], so the geometry is exact and JVM-testable. The content is clipped to
    * the field. (The caret is solid rather than blinking, and the view does not yet scroll
    * to keep a caret past the right edge in view — both are later refinements.) */
  val TextField: Component2[String, String => Unit] =
    component[String, String => Unit] { (value, onChange) =>
      val theme                    = useTheme()
      val (caret, setCaret, _)     = useState(0)
      val (anchor, setAnchor, _)   = useState(0)
      val (focused, setFocused, _) = useState(false)

      val len    = value.length
      val c      = clampIdx(caret, len)
      val a      = clampIdx(anchor, len)
      val selLo  = math.min(a, c)
      val selHi  = math.max(a, c)
      val hasSel = selLo != selHi

      val style = TextStyle(size = theme.textSize, color = theme.surfaceText)
      val padX  = 8.0
      val padY  = 6.0

      // Caret/selection geometry from measured prefixes; `lineH` from a non-empty sample so
      // an empty field still has a full-height caret.
      def prefixW(i: Int): Double = TextMeasurer.installed.measure(value.substring(0, i), style).width
      val lineH                   = TextMeasurer.installed.measure(if value.isEmpty then " " else value, style).height

      // The character boundary nearest to `x` (already relative to the text's left edge) —
      // how a click or drag resolves to a caret index.
      def indexAtX(x: Double): Int =
        var best  = 0
        var bestD = math.abs(x)
        var i     = 1
        while i <= len do
          val d = math.abs(prefixW(i) - x)
          if d < bestD then { bestD = d; best = i }
          i += 1
        best

      def setCollapsed(i: Int): Unit = { setCaret(i); setAnchor(i) }

      def replaceSel(insert: String): Unit =
        onChange(value.substring(0, selLo) + insert + value.substring(selHi))
        setCollapsed(selLo + insert.length)

      def backspace(): Unit =
        if hasSel then replaceSel("")
        else if c > 0 then { onChange(value.substring(0, c - 1) + value.substring(c)); setCollapsed(c - 1) }

      def del(): Unit =
        if hasSel then replaceSel("")
        else if c < len then { onChange(value.substring(0, c) + value.substring(c + 1)); setCollapsed(c) }

      def moveTo(i: Int, extend: Boolean): Unit =
        val ni = clampIdx(i, len)
        setCaret(ni)
        if !extend then setAnchor(ni)

      def onKey(e: KeyEvent): Unit =
        e.scancode match
          case Key.Backspace             => backspace()
          case Key.Delete                => del()
          case Key.Left if !e.shift && hasSel  => setCollapsed(selLo)
          case Key.Left                  => moveTo(c - 1, e.shift)
          case Key.Right if !e.shift && hasSel => setCollapsed(selHi)
          case Key.Right                 => moveTo(c + 1, e.shift)
          case Key.Home                  => moveTo(0, e.shift)
          case Key.End                   => moveTo(len, e.shift)
          case Key.A if e.ctrl           => { setAnchor(0); setCaret(len) }
          case _                         => ()

      // The visual layers, back to front: a selection highlight, the text, the caret. Each
      // is positioned along x by left padding measured to the relevant index.
      val selLayer: Seq[VNode] =
        if hasSel then
          Seq(
            box(padding = EdgeInsets(0, 0, 0, prefixW(selLo)))(
              box(width = prefixW(selHi) - prefixW(selLo), height = lineH, bg = theme.accent.withAlpha(80))(),
            ),
          )
        else Seq.empty

      val caretLayer: Seq[VNode] =
        if focused then
          Seq(
            box(padding = EdgeInsets(0, 0, 0, prefixW(c)))(
              box(width = 2, height = lineH, bg = theme.surfaceText)(),
            ),
          )
        else Seq.empty

      box(
        bg          = theme.surface,
        border      = if focused then theme.accent else theme.border,
        borderWidth = if focused then 2 else 1,
        radius      = theme.radius,
        padding     = EdgeInsets.symmetric(horizontal = padX, vertical = padY),
        clip        = true,
        focusable   = true,
        acceptsText = true,
        onMouseDown = e => setCollapsed(indexAtX(e.local.x - padX)),
        onMouseMove = e => if e.button != 0 then setCaret(indexAtX(e.local.x - padX)),
        onTextInput = e => replaceSel(e.text),
        onKeyDown   = onKey,
        onFocus     = () => setFocused(true),
        onBlur      = () => setFocused(false),
      )(
        stack(Alignment.centerLeft)(
          Seq.concat(selLayer, Seq(text(value, color = theme.surfaceText)), caretLayer)*,
        ),
      )
    }

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
    * with readable ink and rounds to a stadium, sized to its (slightly smaller) label. */
  val Badge: Component[String] =
    component[String] { label =>
      val theme = useTheme()
      box(
        bg      = theme.accent,
        radius  = 999,
        padding = EdgeInsets.symmetric(horizontal = 8, vertical = 2),
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
      val fill  = Color.lerp(Color.transparent, theme.surface, amt)
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

  // The modal implementation. Props are a tuple: open flag, close callback, whether a scrim
  // click dismisses, and the exit-animation duration. A friendlier `Dialog(...)` wrapper
  // below names them.
  private val DialogImpl: ContainerP[(Boolean, () => Unit, Boolean, Int)] =
    container[(Boolean, () => Unit, Boolean, Int)] { (props, children) =>
      val (open, onClose, maskClosable, exitMs) = props
      val theme    = useTheme()
      val env      = useOverlay()
      val presence = usePresence(open, exitMs)

      // The scrim and card fade in together, and back out before the dialog unmounts, off the
      // open phase — the same enter/exit pair every dismissible overlay uses.
      val amt   = useTransition(if presence.phase == PresencePhase.Open then 1.0 else 0.0, exitMs)
      val saved = useRef[RenderObject | Null](null)

      // Focus is the other half of "modal": on open, remember whatever held focus, trap focus
      // to the overlay (so Tab cycles inside the dialog and Escape closes it) and move focus to
      // the first control within; on close, release the trap and restore focus to the opener.
      // Keyed on `mounted` so it arms once when the dialog appears and tears down when it goes.
      useEffect(
        () =>
          (env.overlay, env.focus) match
            case (o: RenderObject, f: FocusManager) if presence.mounted =>
              saved.current = f.focused
              f.trap(o, onClose)
              f.focusables(o).headOption.foreach(f.focus)
              () =>
                f.releaseTrap()
                f.focus(saved.current)
            case _ => noCleanup
        ,
        Array(presence.mounted),
      )

      env.overlay match
        case o: RenderObject if presence.mounted =>
          portal(
            o,
            // The scrim: a full-window dimming layer whose own click (outside the card)
            // dismisses when permitted. Its opacity rides the fade.
            box(
              bg      = Color(0, 0, 0, (140 * amt).toInt),
              onClick = _ => if maskClosable then onClose(),
            )(
              center(
                // The card. It swallows clicks so they do not reach the scrim, is focusable so
                // it is the trap's first target, and closes on Escape from anywhere inside it.
                box(
                  bg          = theme.surface,
                  border      = theme.border,
                  borderWidth = 1,
                  radius      = theme.radius,
                  shadow      = Shadow(),
                  opacity     = amt,
                  padding     = EdgeInsets.all(theme.spacing * 2),
                  focusable   = true,
                  onClick     = _ => (),
                  onKeyDown   = e => if e.scancode == Key.Escape then onClose(),
                )(children*),
              ),
            ),
          )
        case _ => VEmpty
    }

  /** A modal dialog: content centred above a dimming scrim that takes over the window until
    * dismissed. It is **controlled** — the caller owns `open` and is told to close through
    * `onClose`, fired by a click on the scrim (when `maskClosable`), the Escape key, or
    * whatever the caller wires inside the body. The dialog is **portaled into the overlay
    * layer** ([[useOverlay]]), so it escapes any clip or scroll of the place that opened it
    * and always paints on top.
    *
    * Opening moves focus into the dialog and traps Tab within it; Escape closes it from
    * anywhere inside; closing restores focus to whatever held it before. The scrim and card
    * fade and the card settles in through [[usePresence]] + [[useTransition]], and the dialog
    * stays mounted through its close animation (`exitMs`) before unmounting. With no overlay
    * layer available — outside a running app — it renders nothing. */
  def Dialog(
      open:         Boolean,
      onClose:      () => Unit,
      maskClosable: Boolean = true,
      exitMs:       Int     = 200,
  )(children: VNode*): VNode =
    DialogImpl((open, onClose, maskClosable, exitMs))(children*)

  // The shared mechanism behind the positioned overlays (Menu, Tooltip). Unlike a modal, these
  // anchor to a trigger rather than centring, so the card is portaled into the overlay and
  // placed against the anchor's on-screen rectangle, read from a `ref` the caller put on the
  // trigger. The card is measured once it lays out (through its own ref) so a later render can
  // flip it above the anchor when it would overflow the bottom and slide it left to stay
  // on-screen; the measure lags layout by a frame, but the open fade hides that settle, and the
  // card is kept invisible until it has a size so it never flashes at the initial guess. A
  // dismissible popover (a menu) gets a full-window click-catcher behind it and traps focus; a
  // passive one (a tooltip) is click-through and never steals focus.
  private def popover(
      anchor:    Ref[RenderObject | Null],
      mounted:   Boolean,
      amt:       Double,
      onDismiss: (() => Unit) | Null,
      trapFocus: Boolean,
      card:      VNode,
  )(using Hooks): VNode =
    val env            = useOverlay()
    val cardRef        = useRef[RenderObject | Null](null)
    val (sz, setSz, _) = useState(Size.zero)
    val dismiss: () => Unit = if onDismiss != null then onDismiss else () => ()

    // Measure the card after each layout, so the next render can position it precisely. The
    // read lags layout by a frame (effects run before the frame's layout); it converges within
    // a couple of frames, which the open fade covers.
    useEffect(
      () =>
        cardRef.current match
          case r: RenderObject => if r.size != sz then setSz(r.size)
          case null            => ()
        noCleanup,
      null,
    )

    // A menu traps focus to the overlay while open (Tab cycles inside, Escape dismisses) and
    // restores it on close; a tooltip is passive and does neither.
    val saved = useRef[RenderObject | Null](null)
    useEffect(
      () =>
        (env.overlay, env.focus) match
          case (o: RenderObject, f: FocusManager) if mounted && trapFocus =>
            saved.current = f.focused
            f.trap(o, dismiss)
            f.focusables(o).headOption.foreach(f.focus)
            () =>
              f.releaseTrap()
              f.focus(saved.current)
          case _ => noCleanup
      ,
      Array(mounted, trapFocus),
    )

    env.overlay match
      case o: RenderObject if mounted =>
        val win = o.size
        val (ax, ay, ah) = anchor.current match
          case r: RenderObject =>
            val off = r.absoluteOffset
            (off.x, off.y, r.size.height)
          case null => (0.0, 0.0, 0.0)

        // Below the anchor by default; flip above when the card would run off the bottom and
        // there is room above. Aligned to the anchor's left edge, slid left to stay on-screen.
        val below = ay + ah
        val top   = if below + sz.height > win.height && ay - sz.height >= 0.0 then ay - sz.height else below
        val left  = math.max(0.0, math.min(ax, win.width - sz.width))

        // Invisible until measured, so the first frame (laid out at the initial guess) never
        // shows; by the time the fade reveals it, it sits at the resolved position.
        val vis = if sz == Size.zero then 0.0 else amt

        // The card swallows clicks (so only outside clicks dismiss) when this popover is
        // dismissible; a tooltip leaves it click-through. The positioner lays the card out at
        // its natural size so the measurement is the real card, not one clamped to the gap
        // below the anchor.
        val swallow: (PointerEvent => Unit) | Null = if onDismiss != null then (_: PointerEvent) => () else null
        val placed = positioned(left, top)(box(ref = cardRef, opacity = vis, onClick = swallow)(card))

        portal(
          o,
          if onDismiss != null then
            // A full-window catcher behind the card — transparent, not dimming — whose click
            // anywhere outside the card dismisses it.
            box(onClick = _ => dismiss())(placed)
          else
            // A tooltip floats above without intercepting: the whole layer is click-through.
            box(ignorePointer = true)(placed),
        )
      case _ => VEmpty

  /** A single row in a [[Menu]] — a focusable item that calls `onSelect` when clicked or
    * activated from the keyboard (Space or Enter). It highlights on hover. Wire `onSelect` to
    * perform the action and close the menu. */
  val MenuItem: Component2[String, () => Unit] =
    component[String, () => Unit] { (label, onSelect) =>
      val theme                = useTheme()
      val (hover, setHover, _) = useState(false)
      val amt                  = useTransition(if hover then 1.0 else 0.0, 90)

      box(
        bg           = Color.lerp(theme.surface, theme.primary, 0.18 * amt),
        radius       = theme.radius * 0.5,
        padding      = EdgeInsets.symmetric(horizontal = theme.spacing * 1.5, vertical = theme.spacing * 0.75),
        focusable    = true,
        onMouseEnter = _ => setHover(true),
        onMouseLeave = _ => setHover(false),
        onClick      = _ => onSelect(),
        onKeyDown    = e => if e.scancode == Key.Space || e.scancode == Key.Enter then onSelect(),
      )(text(label))
    }

  // The dropdown menu implementation. Props: open flag, close callback, the anchor ref (placed
  // on the trigger by the caller), the exit-fade duration, and the menu width.
  private val MenuImpl: ContainerP[(Boolean, () => Unit, Ref[RenderObject | Null], Int, Double)] =
    container[(Boolean, () => Unit, Ref[RenderObject | Null], Int, Double)] { (props, items) =>
      val (open, onClose, anchor, exitMs, width) = props
      val theme    = useTheme()
      val presence = usePresence(open, exitMs)
      val amt      = useTransition(if presence.phase == PresencePhase.Open then 1.0 else 0.0, exitMs)

      // A surface card of stretched items. The fixed width comes through a sizedBox so the
      // column gets a tight cross-axis and its items fill the menu (a plain box would loosen
      // the child and the items would size raggedly to their own text).
      val card = box(
        bg          = theme.surface,
        border      = theme.border,
        borderWidth = 1,
        radius      = theme.radius,
        shadow      = Shadow(),
        clip        = true,
        padding     = EdgeInsets.all(theme.spacing * 0.5),
      )(
        sizedBox(width = width)(
          col(crossAxisAlignment = CrossAxisAlignment.Stretch, mainAxisSize = MainAxisSize.Min, spacing = 2)(items*),
        ),
      )

      popover(anchor, presence.mounted, amt, onDismiss = onClose, trapFocus = true, card = card)
    }

  /** A dropdown menu anchored to a trigger. It is **controlled** — the caller owns `open` and
    * is told to close through `onClose` — and **positioned**: it portals into the overlay layer
    * and floats just below the trigger, flipping above it near the bottom edge and sliding left
    * to stay on-screen. Pass the trigger a `ref` (a `useRef[RenderObject | Null](null)`) and
    * give the same ref here as `anchor`, so the menu can read the trigger's on-screen rectangle.
    *
    * A click anywhere outside the menu dismisses it (a transparent full-window catcher, not a
    * dimming scrim), as does Escape; opening traps Tab within the menu and restores focus on
    * close. The menu fades in and out through [[usePresence]] + [[useTransition]]. With no
    * overlay layer available it renders nothing. Fill it with [[MenuItem]]s. */
  def Menu(
      open:    Boolean,
      onClose: () => Unit,
      anchor:  Ref[RenderObject | Null],
      exitMs:  Int    = 150,
      width:   Double = 180,
  )(items: VNode*): VNode =
    MenuImpl((open, onClose, anchor, exitMs, width))(items*)

  // The tooltip implementation. Props: the label, the hover delay before it shows, and the
  // exit-fade duration. The trigger comes as the children.
  private val TooltipImpl: ContainerP[(String, Int, Int)] =
    container[(String, Int, Int)] { (props, children) =>
      val (label, delayMs, exitMs) = props
      val theme                    = useTheme()
      val anchor                   = useRef[RenderObject | Null](null)
      val (hover, setHover, _)     = useState(false)

      // Show after the pointer has rested on the trigger for `delayMs` (hover intent), and let
      // the same debounce settle a brief unhover so it does not flicker.
      val shown    = useDebouncedValue(hover, delayMs)
      val presence = usePresence(shown, exitMs)
      val amt      = useTransition(if presence.phase == PresencePhase.Open then 1.0 else 0.0, exitMs)

      val card = box(
        bg      = theme.surfaceText,
        radius  = theme.radius * 0.75,
        padding = EdgeInsets.symmetric(horizontal = theme.spacing, vertical = theme.spacing * 0.5),
      )(
        text(label, color = theme.surface),
      )

      // The trigger stays in normal flow, wrapped so it carries the anchor ref and the hover
      // handlers; the tooltip itself portals out through the popover.
      VFragment(
        Vector(
          box(ref = anchor, onMouseEnter = _ => setHover(true), onMouseLeave = _ => setHover(false))(children*),
          popover(anchor, presence.mounted, amt, onDismiss = null, trapFocus = false, card = card),
        ),
      )
    }

  /** A tooltip: a small label that appears beside its trigger on hover. Wrap the trigger as the
    * child; the tooltip attaches the hover tracking and an anchor itself, so callers need wire
    * nothing. It portals into the overlay layer and floats just below the trigger (flipping and
    * sliding to stay on-screen), and is **click-through** — it never intercepts a click meant
    * for what is underneath. It shows after a short hover `delayMs` and fades on both ends
    * through [[usePresence]] + [[useTransition]]. With no overlay layer available it shows
    * nothing (the trigger still renders). */
  def Tooltip(
      label:   String,
      delayMs: Int = 400,
      exitMs:  Int = 120,
  )(trigger: VNode*): VNode =
    TooltipImpl((label, delayMs, exitMs))(trigger*)
