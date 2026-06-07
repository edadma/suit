package io.github.edadma.suit

import io.github.edadma.suit.dsl.*

// The widget library — the salle-equivalent: small reusable controls composed from
// the DSL primitives and vdom hooks. A widget is an ordinary vdom component, so its
// interaction state (hover, pressed) lives in `useState` and survives re-renders, and
// it reconciles in place exactly like an application component. Each widget is purely
// declarative output over `box`/`text`/`row`/`stack`; the render tree, layout, and
// input routing underneath are what give it pixels and behaviour.
//
// These widgets are pointer- and keyboard-driven only. Controls that need text entry
// (a text field) wait on text-input support in the SDL binding; until then they are
// deliberately absent rather than approximated.
object widgets:

  private def clamp01(x: Double): Double =
    if x < 0.0 then 0.0 else if x > 1.0 then 1.0 else x

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
