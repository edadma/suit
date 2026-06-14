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

      // Undo/redo stacks on the ref (survive re-renders); runs of typing coalesce (see
      // [[EditHistory]]).
      val history = useRef(new EditHistory)

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

      // The single text-changing funnel: record the prior state for undo (coalescing a run of
      // typing), report the new text, and collapse the caret after it.
      def commit(newText: String, newCaret: Int, coalesce: Boolean = false): Unit =
        if newText != value then
          history.current.record(EditSnapshot(value, c, a), coalesce)
          onChange(newText)
        setCollapsed(newCaret)

      def replaceSel(insert: String, coalesce: Boolean = false): Unit =
        commit(value.substring(0, selLo) + insert + value.substring(selHi), selLo + insert.length, coalesce)

      def backspace(): Unit =
        if hasSel then replaceSel("")
        else if c > 0 then commit(value.substring(0, c - 1) + value.substring(c), c - 1)

      def del(): Unit =
        if hasSel then replaceSel("")
        else if c < len then commit(value.substring(0, c) + value.substring(c + 1), c)

      // Word boundaries from index `i`: skip a run of whitespace, then a run of non-whitespace.
      def wordLeftIdx(i: Int): Int =
        var j = i
        while j > 0 && value.charAt(j - 1).isWhitespace do j -= 1
        while j > 0 && !value.charAt(j - 1).isWhitespace do j -= 1
        j
      def wordRightIdx(i: Int): Int =
        var j = i
        while j < len && value.charAt(j).isWhitespace do j += 1
        while j < len && !value.charAt(j).isWhitespace do j += 1
        j

      // Delete the word to the left of the caret (Option/Ctrl+Backspace).
      def deleteWordLeft(): Unit =
        if hasSel then replaceSel("")
        else if c > 0 then commit(value.substring(0, wordLeftIdx(c)) + value.substring(c), wordLeftIdx(c))

      def applySnapshot(s: EditSnapshot): Unit =
        if s.text != value then onChange(s.text)
        setCaret(s.caret)
        setAnchor(s.anchor)
        restartBlink()
      def undo(): Unit = history.current.undo(EditSnapshot(value, c, a)).foreach(applySnapshot)
      def redo(): Unit = history.current.redo(EditSnapshot(value, c, a)).foreach(applySnapshot)

      def moveTo(i: Int, extend: Boolean): Unit =
        history.current.breakRun() // a caret move ends any open typing run
        val ni = clampIdx(i, len)
        setCaret(ni)
        if !extend then setAnchor(ni)
        restartBlink()

      // A single-line field holds no newlines, so a pasted multi-line string flattens to spaces.
      def paste(): Unit = replaceSel(Clipboard.installed.get().replace('\n', ' '))
      def copy(): Unit  = if hasSel then Clipboard.installed.set(value.substring(selLo, selHi))
      def cut(): Unit   = if hasSel then { copy(); replaceSel("") }

      def onKey(e: KeyEvent): Unit =
        // The conventional shortcut modifier: Ctrl elsewhere, ⌘ on macOS. Word delete is the
        // platform's other convention — Option on macOS, Ctrl elsewhere.
        val primary = e.ctrl || e.meta
        val wordMod = e.alt || e.ctrl
        e.scancode match
          case Key.Z if primary && e.shift     => redo()
          case Key.Z if primary                => undo()
          case Key.Backspace if wordMod        => deleteWordLeft()
          case Key.Backspace             => backspace()
          case Key.Delete                => del()
          case Key.C if primary          => copy()
          case Key.X if primary          => cut()
          case Key.V if primary          => paste()
          case Key.Left if wordMod       => moveTo(wordLeftIdx(c), e.shift)
          case Key.Right if wordMod      => moveTo(wordRightIdx(c), e.shift)
          case Key.Left if !e.shift && hasSel  => setCollapsed(selLo)
          case Key.Left                  => moveTo(c - 1, e.shift)
          case Key.Right if !e.shift && hasSel => setCollapsed(selHi)
          case Key.Right                 => moveTo(c + 1, e.shift)
          case Key.Home                  => moveTo(0, e.shift)
          case Key.End                   => moveTo(len, e.shift)
          case Key.A if primary          => { setAnchor(0); setCaret(len); restartBlink() }
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
        onTextInput = e => replaceSel(e.text, coalesce = true),
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
    * Enter splits a line, Up/Down move between rows (keeping the column where a shorter
    * row allows), Home/End jump to the row's ends, and Shift extends a selection across
    * line breaks. Backspace/Delete remove, Ctrl+A selects all, a click places the caret and
    * a drag selects. The character arithmetic lives in the pure [[EditBuffer]]; this widget
    * adds the visual layer — wrapping and the row geometry — and wires it to the routers.
    *
    * Long lines **soft-wrap** to the editor's width, so a logical line can span several visual
    * rows; the caret, click-to-place, selection, and Up/Down/Home/End all work in those visual
    * rows. It sizes to its (wrapped) content height rather than scrolling itself, so put it in a
    * [[dsl.scrollView]] for a fixed-height editor that scrolls — the click-to-caret math reads the
    * pointer in the editor's own coordinates, so it stays correct however far the enclosing
    * viewport is scrolled. Give it a **bounded width** (a `Stretch` column, or a `box` width) to
    * wrap into. The caret blinks while focused and snaps solid for a full interval after any edit
    * or move. */
  val TextArea: Component2[String, String => Unit] =
    component[String, String => Unit] { (value, onChange) =>
      val theme                    = useTheme()
      val (caret, setCaret, _)     = useState(0)
      val (anchor, setAnchor, _)   = useState(0)
      val (focused, setFocused, _) = useState(false)

      // The undo/redo stacks survive re-renders on the ref. Edits record the prior state here;
      // runs of typing coalesce into one undo step (see [[EditHistory]]).
      val history = useRef(new EditHistory)

      // The caret blinks while focused; bumping the epoch restarts the interval's phase so a
      // solid caret shows for a full interval after each edit or move (see [[TextField]]).
      val (blinkOn, setBlinkOn, updateBlinkOn) = useState(true)
      val (blinkEpoch, _, bumpBlink)           = useState(0)
      def restartBlink(): Unit                 = { setBlinkOn(true); bumpBlink(_ + 1) }
      useInterval(() => updateBlinkOn(b => !b), 530, enabled = focused, restartKeys = Array(blinkEpoch))

      val buf   = EditBuffer(value, caret, anchor)
      val lines = buf.lines

      val style    = TextStyle(size = theme.textSize, color = theme.surfaceText)
      val measurer = TextMeasurer.installed
      val padX     = 8.0
      val padY     = 6.0
      // A uniform line height from a non-empty sample, so blank lines keep the rhythm and an
      // empty editor still has a full-height caret.
      val lineH = measurer.measure("Xy", style).height

      // The editor soft-wraps, so a logical line may occupy several **visual rows**. The wrap width
      // is the editor's laid-out content width, read from a ref one frame after layout (the same
      // measure-one-frame-late dance the lists and overlays use); until it is known the text lays
      // out unwrapped on the first frame, then re-wraps. A bounded width is part of the widget's
      // contract, so this resolves immediately in practice.
      val sizeRef          = useRef[RenderObject | Null](null)
      val lastWidth        = useRef(-1.0)
      val (_, _, bumpTick) = useState(0)
      useEffect(() => { bumpTick(t => t + 1); noCleanup }, Array())
      val availW = sizeRef.current match
        case r: RenderObject if r.size.width > 0 => math.max(1.0, r.size.width - 2 * padX)
        case _                                   => Double.PositiveInfinity

      // Re-wrap when the editor's own width changes — including a splitter drag that resizes the
      // pane without re-rendering this widget. A `resize` notification only re-renders (which re-
      // reads the width above) when the width actually moved, so a height change from the re-wrap
      // itself does not loop.
      def onResize(sz: Size): Unit =
        if math.abs(sz.width - lastWidth.current) > 0.5 then
          lastWidth.current = sz.width
          bumpTick(t => t + 1)

      def prefixW(line: Int, col: Int): Double =
        val s = lines(line)
        measurer.measure(s.substring(0, math.max(0, math.min(col, s.length))), style).width

      // The visual layout: each logical line wrapped into `(startCol, endCol)` segments, then
      // flattened into a single top-to-bottom list of visual rows. Every row's top sits at
      // `globalRow * lineH`, which the caret, selection, and click geometry below rely on exactly.
      val rowsByLine: Vector[Vector[(Int, Int)]] =
        lines.indices.toVector.map { line =>
          val s      = lines(line)
          val starts = RenderText.wrapColumns(s, style, availW, measurer)
          starts.indices.toVector.map { j =>
            (starts(j), if j + 1 < starts.length then starts(j + 1) else s.length)
          }
        }
      val firstRowOfLine: Vector[Int]          = rowsByLine.scanLeft(0)(_ + _.length).init
      val flatRows: Vector[(Int, Int, Int)]    =
        rowsByLine.zipWithIndex.flatMap { case (segs, line) => segs.map((a, b) => (line, a, b)) }
      val totalRows = flatRows.length
      val contentH  = math.max(lineH, totalRows * lineH)

      // The visual row (global index) and the x within that row for a caret offset: find which
      // segment of the offset's logical line holds the column, then measure from the segment start.
      def caretRowX(off: Int): (Int, Double) =
        val (line, col) = buf.lineColOf(off)
        val segs        = rowsByLine(line)
        var j           = 0
        while j + 1 < segs.length && segs(j + 1)._1 <= col do j += 1
        (firstRowOfLine(line) + j, prefixW(line, col) - prefixW(line, segs(j)._1))

      // The nearest character boundary within a visual row to a target x (measured from the row's
      // start, since every row paints at content x = 0).
      def colInRowAtX(line: Int, start: Int, end: Int, targetX: Double): Int =
        val base  = prefixW(line, start)
        var best  = start
        var bestD = math.abs(targetX)
        var c     = start + 1
        while c <= end do
          val d = math.abs((prefixW(line, c) - base) - targetX)
          if d < bestD then { bestD = d; best = c }
          c += 1
        best

      def indexAt(localX: Double, localY: Double): Int =
        val gr                 = math.max(0, math.min(((localY - padY) / lineH).toInt, totalRows - 1))
        val (line, start, end) = flatRows(gr)
        buf.indexOf(line, colInRowAtX(line, start, end, localX - padX))

      // Drive the model: apply a pure op, report a text change if any, and move the caret —
      // the single path every key and pointer edit funnels through. A text-changing edit records
      // the prior state for undo (`coalesce` folds a run of typing into one step); a caret-only
      // op (a move/selection) records nothing but ends any open typing run.
      def edit(op: EditBuffer => EditBuffer, coalesce: Boolean = false): Unit =
        val nb = op(buf)
        if nb.text != value then
          history.current.record(EditSnapshot(value, caret, anchor), coalesce)
          onChange(nb.text)
        else history.current.breakRun()
        setCaret(nb.caret)
        setAnchor(nb.anchor)
        restartBlink()

      // Restore an undo/redo snapshot: the text (through onChange) and the selection.
      def applySnapshot(s: EditSnapshot): Unit =
        if s.text != value then onChange(s.text)
        setCaret(s.caret)
        setAnchor(s.anchor)
        restartBlink()
      def undo(): Unit = history.current.undo(EditSnapshot(value, caret, anchor)).foreach(applySnapshot)
      def redo(): Unit = history.current.redo(EditSnapshot(value, caret, anchor)).foreach(applySnapshot)

      // Vertical motion and Home/End follow **visual** rows, not logical lines, so the caret moves
      // by what the eye sees through a wrapped line. The target column on the destination row keeps
      // the caret's current x; Home/End jump to the visual row's ends.
      def moveVert(delta: Int, extend: Boolean): Unit =
        val (gr, xr) = caretRowX(caret)
        val tr       = gr + delta
        if tr < 0 then edit(_.moveTo(0, extend))
        else if tr >= totalRows then edit(_.moveTo(buf.length, extend))
        else
          val (line, start, end) = flatRows(tr)
          edit(_.moveTo(buf.indexOf(line, colInRowAtX(line, start, end, xr)), extend))

      def visualHome(extend: Boolean): Unit =
        val (gr, _)        = caretRowX(caret)
        val (line, start, _) = flatRows(gr)
        edit(_.moveTo(buf.indexOf(line, start), extend))

      def visualEnd(extend: Boolean): Unit =
        val (gr, _)      = caretRowX(caret)
        val (line, _, end) = flatRows(gr)
        edit(_.moveTo(buf.indexOf(line, end), extend))

      // A multi-line editor keeps newlines on paste. Copy/cut act on the current selection.
      def paste(): Unit = edit(_.insert(Clipboard.installed.get()))
      def copy(): Unit  = if buf.hasSelection then Clipboard.installed.set(buf.text.substring(buf.selLo, buf.selHi))
      def cut(): Unit   = if buf.hasSelection then { copy(); edit(_.insert("")) }

      def onKey(e: KeyEvent): Unit =
        // The conventional shortcut modifier: Ctrl elsewhere, ⌘ on macOS. Word delete is the
        // platform's other convention — Option on macOS, Ctrl elsewhere.
        val primary = e.ctrl || e.meta
        val wordMod = e.alt || e.ctrl
        e.scancode match
          case Key.Z if primary && e.shift     => redo()
          case Key.Z if primary                => undo()
          case Key.Backspace if wordMod        => edit(_.deleteWordLeft)
          case Key.Backspace       => edit(_.backspace)
          case Key.Delete          => edit(_.delete)
          case Key.Enter           => edit(_.newline)
          case Key.C if primary    => copy()
          case Key.X if primary    => cut()
          case Key.V if primary    => paste()
          case Key.Left if wordMod  => edit(_.wordLeft(e.shift))
          case Key.Right if wordMod => edit(_.wordRight(e.shift))
          case Key.Left            => edit(_.left(e.shift))
          case Key.Right           => edit(_.right(e.shift))
          case Key.Up              => moveVert(-1, e.shift)
          case Key.Down            => moveVert(1, e.shift)
          case Key.Home if primary => edit(_.docStart(e.shift))
          case Key.End if primary  => edit(_.docEnd(e.shift))
          case Key.Home            => visualHome(e.shift)
          case Key.End             => visualEnd(e.shift)
          case Key.A if primary    => edit(_.selectAll)
          case _                   => ()

      // One fixed-height row per visual row, each painting its line's segment substring.
      val textLayer: VNode =
        col(mainAxisSize = MainAxisSize.Min)(
          flatRows.map { (line, start, end) =>
            sizedBox(height = lineH)(text(lines(line).substring(start, end), color = theme.surfaceText, size = theme.textSize))
          }*,
        )

      // The selection highlight: one band per visual row the selection touches, clipped to that
      // row's columns. A row whose selection runs off its end (the break continues onto the next
      // row or line) gets a small sliver past the text so the break reads as selected.
      val selLayer: Seq[VNode] =
        if !buf.hasSelection then Seq.empty
        else
          flatRows.zipWithIndex.flatMap { case ((line, start, end), r) =>
            val lso   = buf.lineStart(line)
            val rowLo = lso + start
            val rowHi = lso + end
            if buf.selHi <= rowLo || buf.selLo >= rowHi then Seq.empty
            else
              val sCol      = math.max(start, math.min(end, buf.selLo - lso))
              val continues = buf.selHi > rowHi
              val eCol      = if continues then end else math.max(start, math.min(end, buf.selHi - lso))
              val base      = prefixW(line, start)
              val x0        = prefixW(line, sCol) - base
              val x1raw     = prefixW(line, eCol) - base
              val x1        = if continues then math.max(x1raw, x0 + 4.0) else x1raw
              Seq(positioned(x0, r * lineH)(
                box(width = math.max(0.0, x1 - x0), height = lineH, bg = theme.accent.withAlpha(80))(),
              ))
          }.toSeq

      val caretLayer: Seq[VNode] =
        if focused && blinkOn then
          val (gr, xr) = caretRowX(caret)
          Seq(positioned(xr, gr * lineH)(box(width = 2, height = lineH, bg = theme.surfaceText)()))
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
        ref         = sizeRef,
        onResize    = onResize,
        onMouseDown = e => edit(_.collapseTo(indexAt(e.local.x, e.local.y))),
        onMouseMove = e => if e.button != 0 then edit(b => b.moveTo(indexAt(e.local.x, e.local.y), extend = true)),
        onTextInput = e => edit(_.insert(e.text), coalesce = true),
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
