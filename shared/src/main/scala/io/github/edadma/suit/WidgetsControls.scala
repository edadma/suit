package io.github.edadma.suit

import io.github.edadma.suit.dsl.*

// The interactive controls: buttons, checkboxes, sliders, and the text editors. Each is a
// controlled vdom component — it renders what it is given and reports changes through a
// callback — and paints from the [[Theme]] in context.
private[suit] trait WidgetsControls extends WidgetsSupport:

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
    * [[TextMeasurer]], so the geometry is exact and JVM-testable. The content is clipped to the
    * field and scrolls horizontally to keep the caret in view once the text outgrows it. The
    * caret blinks while focused and snaps solid for a full interval after any edit or move. */
  val TextField: Component2[String, String => Unit] =
    component[String, String => Unit] { (value, onChange) =>
      val theme                    = useTheme()
      val (caret, setCaret, _)     = useState(0)
      val (anchor, setAnchor, _)   = useState(0)
      val (focused, setFocused, _) = useState(false)

      // The caret blinks while the field is focused. `blinkOn` flips on an interval; bumping
      // `blinkEpoch` restarts that interval's phase (and `restartBlink` also forces the caret
      // solid), so any edit or caret move shows a solid caret for a full interval before it
      // resumes blinking — the behaviour every text field has. The interval is gated on focus,
      // so an unfocused field arms no timer and leaves the clock idle.
      val (blinkOn, setBlinkOn, updateBlinkOn) = useState(true)
      val (blinkEpoch, _, bumpBlink)           = useState(0)
      def restartBlink(): Unit                 = { setBlinkOn(true); bumpBlink(_ + 1) }
      useInterval(() => updateBlinkOn(b => !b), 530, enabled = focused, restartKeys = Array(blinkEpoch))

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

      // Scroll the content horizontally so the caret stays in view once the text outgrows the
      // field. The field's laid-out width is read back from a ref (the render object persists
      // across renders, so its size from the last layout is available here) and reduced by the
      // padding to the visible text width; `scrollX` is the smallest left shift that keeps the
      // caret inside that width, so a caret near the start anchors the text left and a caret past
      // the right edge pulls the text along. The shift applies to every layer together.
      val fieldRef = useRef[RenderObject | Null](null)
      val contentW = fieldRef.current match
        case r: RenderObject => math.max(0.0, r.size.width - 2 * padX)
        case null            => 0.0
      val caretX  = prefixW(c)
      val scrollX = if contentW <= 0.0 then 0.0 else math.max(0.0, caretX - contentW + 2.0)

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

      def setCollapsed(i: Int): Unit = { setCaret(i); setAnchor(i); restartBlink() }

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
        restartBlink()

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
          case Key.A if e.ctrl           => { setAnchor(0); setCaret(len); restartBlink() }
          case _                         => ()

      // The visual layers, back to front: a selection highlight, the text, the caret. Each
      // is positioned along x by left padding measured to the relevant index.
      val selLayer: Seq[VNode] =
        if hasSel then
          Seq(
            box(padding = EdgeInsets(0, 0, 0, prefixW(selLo) - scrollX))(
              box(width = prefixW(selHi) - prefixW(selLo), height = lineH, bg = theme.accent.withAlpha(80))(),
            ),
          )
        else Seq.empty

      val caretLayer: Seq[VNode] =
        if focused && blinkOn then
          Seq(
            box(padding = EdgeInsets(0, 0, 0, prefixW(c) - scrollX))(
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
        ref         = fieldRef,
        onMouseDown = e => setCollapsed(indexAtX(e.local.x - padX + scrollX)),
        onMouseMove = e => if e.button != 0 then setCaret(indexAtX(e.local.x - padX + scrollX)),
        onTextInput = e => replaceSel(e.text),
        onKeyDown   = onKey,
        onFocus     = () => { setFocused(true); restartBlink() },
        onBlur      = () => setFocused(false),
      )(
        stack(Alignment.centerLeft)(
          Seq.concat(
            selLayer,
            Seq(box(padding = EdgeInsets(0, 0, 0, -scrollX))(text(value, color = theme.surfaceText))),
            caretLayer,
          )*,
        ),
      )
    }

  /** A multi-line text editor: a focusable, bordered box that edits a string spanning
    * many lines. Like [[TextField]] it is **controlled** — it renders the `value` it is
    * given and reports edits through `onChange` — but the caret moves in two dimensions:
    * Enter splits a line, Up/Down move between lines (keeping the column where a shorter
    * line allows), Home/End jump to the line's ends, and Shift extends a selection across
    * line breaks. Backspace/Delete remove, Ctrl+A selects all, a click places the caret and
    * a drag selects. All the caret arithmetic lives in the pure [[EditBuffer]]; this widget
    * is the wiring that holds one in state and paints it.
    *
    * It sizes to its content height (one line's height per line of text) rather than
    * scrolling itself, so put it in a [[dsl.scrollView]] for a fixed-height editor that
    * scrolls — the click-to-caret math reads the pointer in the editor's own coordinates, so
    * it stays correct however far the enclosing viewport is scrolled. Give it a tight width
    * (a `Stretch` column, or a `box` width) to fill a pane. The caret blinks while focused
    * and snaps solid for a full interval after any edit or move. */
  val TextArea: Component2[String, String => Unit] =
    component[String, String => Unit] { (value, onChange) =>
      val theme                    = useTheme()
      val (caret, setCaret, _)     = useState(0)
      val (anchor, setAnchor, _)   = useState(0)
      val (focused, setFocused, _) = useState(false)

      // The caret blinks while focused; bumping the epoch restarts the interval's phase so a
      // solid caret shows for a full interval after each edit or move (see [[TextField]]).
      val (blinkOn, setBlinkOn, updateBlinkOn) = useState(true)
      val (blinkEpoch, _, bumpBlink)           = useState(0)
      def restartBlink(): Unit                 = { setBlinkOn(true); bumpBlink(_ + 1) }
      useInterval(() => updateBlinkOn(b => !b), 530, enabled = focused, restartKeys = Array(blinkEpoch))

      val buf   = EditBuffer(value, caret, anchor)
      val lines = buf.lines

      val style = TextStyle(size = theme.textSize, color = theme.surfaceText)
      val padX  = 8.0
      val padY  = 6.0
      // A uniform line height from a non-empty sample, so blank lines keep the rhythm and an
      // empty editor still has a full-height caret.
      val lineH      = TextMeasurer.installed.measure("Xy", style).height
      val contentH   = math.max(lineH, lines.length * lineH)

      def prefixW(line: Int, col: Int): Double =
        val s = lines(line)
        TextMeasurer.installed.measure(s.substring(0, math.max(0, math.min(col, s.length))), style).width
      def lineW(line: Int): Double = TextMeasurer.installed.measure(lines(line), style).width

      // Resolve a pointer in the editor's content space (pointer minus padding) to a caret
      // index: the row from the y, then the nearest character boundary on that row from the x.
      def colAtX(line: Int, x: Double): Int =
        val s     = lines(line)
        var best  = 0
        var bestD = math.abs(x - 0.0)
        var i     = 1
        while i <= s.length do
          val d = math.abs(prefixW(line, i) - x)
          if d < bestD then { bestD = d; best = i }
          i += 1
        best
      def indexAt(localX: Double, localY: Double): Int =
        val ln  = math.max(0, math.min((((localY - padY) / lineH).toInt), lines.length - 1))
        buf.indexOf(ln, colAtX(ln, localX - padX))

      // Drive the model: apply a pure op, report a text change if any, and move the caret —
      // the single path every key and pointer edit funnels through.
      def edit(op: EditBuffer => EditBuffer): Unit =
        val nb = op(buf)
        if nb.text != value then onChange(nb.text)
        setCaret(nb.caret)
        setAnchor(nb.anchor)
        restartBlink()

      def onKey(e: KeyEvent): Unit =
        e.scancode match
          case Key.Backspace       => edit(_.backspace)
          case Key.Delete          => edit(_.delete)
          case Key.Enter           => edit(_.newline)
          case Key.Left            => edit(_.left(e.shift))
          case Key.Right           => edit(_.right(e.shift))
          case Key.Up              => edit(_.up(e.shift))
          case Key.Down            => edit(_.down(e.shift))
          case Key.Home if e.ctrl  => edit(_.docStart(e.shift))
          case Key.End if e.ctrl   => edit(_.docEnd(e.shift))
          case Key.Home            => edit(_.lineHome(e.shift))
          case Key.End             => edit(_.lineEnd(e.shift))
          case Key.A if e.ctrl     => edit(_.selectAll)
          case _                   => ()

      // The character grid: one fixed-height row per line so every line's top sits at
      // `line * lineH`, which the caret and selection geometry below rely on exactly.
      val textLayer: VNode =
        col(mainAxisSize = MainAxisSize.Min)(
          lines.map(l => sizedBox(height = lineH)(text(l, color = theme.surfaceText, size = theme.textSize)))*,
        )

      // The selection highlight: one band per spanned line, from the selection's start column
      // on its first line (0 on later lines) to its end column on its last line (the line's
      // full width on earlier lines, with a sliver for an empty line so it stays visible).
      val selLayer: Seq[VNode] =
        if !buf.hasSelection then Seq.empty
        else
          val (loLine, loCol) = buf.lineColOf(buf.selLo)
          val (hiLine, hiCol) = buf.lineColOf(buf.selHi)
          (loLine to hiLine).map { ln =>
            val startX = if ln == loLine then prefixW(ln, loCol) else 0.0
            val endX   = if ln == hiLine then prefixW(ln, hiCol) else math.max(lineW(ln), 4.0)
            positioned(startX, ln * lineH)(
              box(width = math.max(0.0, endX - startX), height = lineH, bg = theme.accent.withAlpha(80))(),
            )
          }.toSeq

      val caretLayer: Seq[VNode] =
        if focused && blinkOn then
          val (line, c0) = buf.lineColOf(caret)
          Seq(positioned(prefixW(line, c0), line * lineH)(box(width = 2, height = lineH, bg = theme.surfaceText)()))
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
        onMouseDown = e => edit(_.collapseTo(indexAt(e.local.x, e.local.y))),
        onMouseMove = e => if e.button != 0 then edit(b => b.moveTo(indexAt(e.local.x, e.local.y), extend = true)),
        onTextInput = e => edit(_.insert(e.text)),
        onKeyDown   = onKey,
        onFocus     = () => { setFocused(true); restartBlink() },
        onBlur      = () => setFocused(false),
      )(
        sizedBox(height = contentH)(
          stack(Alignment.topLeft)(
            Seq.concat(selLayer, Seq(textLayer), caretLayer)*,
          ),
        ),
      )
    }
