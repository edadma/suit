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
