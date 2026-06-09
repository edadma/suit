package io.github.edadma.suit

// The pure editing model behind the multi-line text area.
//
// A text field's hard part is not the pixels — it is the caret arithmetic: where an
// insertion lands, what a Backspace removes, how a selection grows, which character the
// Up arrow lands on when the line above is shorter. None of that needs a device, so it
// lives here as an immutable value type with no rendering and no hooks: every operation
// returns a new [[EditBuffer]], and the widget is a thin wiring layer that holds one in
// `useState` and re-renders the result. Keeping the logic pure is what makes the whole of
// multi-line editing — across-line selection, vertical motion, line-relative Home/End —
// exhaustively unit-testable on the JVM, the same discipline the layout engine follows.

/** An editable string with a caret and a selection anchor, both character offsets into
  * `text`. The selection is the range between `anchor` and `caret` (either may be the
  * larger); when they coincide there is no selection and `caret` is the insertion point.
  * Offsets are always kept within `[0, text.length]`.
  *
  * Every edit and every caret motion returns a fresh buffer — the model is immutable, so a
  * widget drives it as `state = op(state)`. Motions take an `extend` flag: `true` (the
  * Shift modifier) moves the caret while leaving the anchor, growing the selection; `false`
  * collapses the selection by moving both. */
final case class EditBuffer(text: String, caret: Int = 0, anchor: Int = 0):

  val length: Int = text.length

  private def clamp(i: Int): Int = if i < 0 then 0 else if i > length then length else i

  /** The lower and upper ends of the selection, clamped into range. */
  def selLo: Int       = math.min(clamp(anchor), clamp(caret))
  def selHi: Int       = math.max(clamp(anchor), clamp(caret))
  def hasSelection: Boolean = selLo != selHi

  /** The lines, split on `\n`, keeping trailing empties — so a text ending in a newline has
    * a final empty line, and the empty string is a single empty line. The line count is
    * always `(number of \n) + 1`. */
  def lines: Vector[String] = EditBuffer.splitLines(text)

  /** Number of lines (one more than the number of newlines). */
  def lineCount: Int = EditBuffer.countNewlines(text) + 1

  /** The character offset at the start of `line` (clamped to a valid line). */
  def lineStart(line: Int): Int =
    val n = lineCount
    val l = if line < 0 then 0 else if line >= n then n - 1 else line
    var idx     = 0
    var seen    = 0
    var i       = 0
    while seen < l && i < length do
      if text.charAt(i) == '\n' then { seen += 1; if seen == l then idx = i + 1 }
      i += 1
    if l == 0 then 0 else idx

  /** The (line, column) of character offset `i`. Column is the offset from the line start. */
  def lineColOf(i: Int): (Int, Int) =
    val pos = clamp(i)
    var line  = 0
    var start = 0
    var k     = 0
    while k < pos do
      if text.charAt(k) == '\n' then { line += 1; start = k + 1 }
      k += 1
    (line, pos - start)

  /** The character offset of `(line, col)`, with `col` capped to the line's length and the
    * whole result clamped into range. */
  def indexOf(line: Int, col: Int): Int =
    val ls      = lineStart(line)
    val lineLen = EditBuffer.lineLengthAt(text, ls)
    clamp(ls + math.max(0, math.min(col, lineLen)))

  // --- caret motion ---------------------------------------------------------

  /** Move the caret to `i`. With `extend` the anchor stays put (growing the selection);
    * otherwise the anchor follows, collapsing to a plain caret. */
  def moveTo(i: Int, extend: Boolean): EditBuffer =
    val ni = clamp(i)
    EditBuffer(text, ni, if extend then anchor else ni)

  /** Collapse the selection to one end without moving through it (what Left/Right do when a
    * selection exists and Shift is not held). */
  def collapseTo(i: Int): EditBuffer = moveTo(i, extend = false)

  def left(extend: Boolean): EditBuffer =
    if hasSelection && !extend then collapseTo(selLo) else moveTo(clamp(caret) - 1, extend)

  def right(extend: Boolean): EditBuffer =
    if hasSelection && !extend then collapseTo(selHi) else moveTo(clamp(caret) + 1, extend)

  /** Move to the start of the caret's line (Home). */
  def lineHome(extend: Boolean): EditBuffer =
    val (line, _) = lineColOf(caret)
    moveTo(lineStart(line), extend)

  /** Move to the end of the caret's line (End). */
  def lineEnd(extend: Boolean): EditBuffer =
    val (line, _) = lineColOf(caret)
    val ls        = lineStart(line)
    moveTo(ls + EditBuffer.lineLengthAt(text, ls), extend)

  def docStart(extend: Boolean): EditBuffer = moveTo(0, extend)
  def docEnd(extend: Boolean): EditBuffer   = moveTo(length, extend)

  /** Move the caret up one line, keeping its column where the shorter line allows. */
  def up(extend: Boolean): EditBuffer =
    val (line, col) = lineColOf(caret)
    if line == 0 then moveTo(0, extend) else moveTo(indexOf(line - 1, col), extend)

  /** Move the caret down one line, keeping its column where the shorter line allows. */
  def down(extend: Boolean): EditBuffer =
    val (line, col) = lineColOf(caret)
    if line >= lineCount - 1 then moveTo(length, extend) else moveTo(indexOf(line + 1, col), extend)

  /** Select the whole buffer (anchor at the start, caret at the end). */
  def selectAll: EditBuffer = EditBuffer(text, length, 0)

  // --- edits ----------------------------------------------------------------

  /** Replace the selection (or, with none, insert at the caret) with `s`, leaving the caret
    * collapsed just after the inserted text. */
  def insert(s: String): EditBuffer =
    val lo = selLo
    val hi = selHi
    val nt = text.substring(0, lo) + s + text.substring(hi)
    val nc = lo + s.length
    EditBuffer(nt, nc, nc)

  /** Delete the selection, or the character before the caret when there is none. */
  def backspace: EditBuffer =
    if hasSelection then insert("")
    else
      val c = clamp(caret)
      if c == 0 then this
      else EditBuffer(text.substring(0, c - 1) + text.substring(c), c - 1, c - 1)

  /** Delete the selection, or the character after the caret when there is none. */
  def delete: EditBuffer =
    if hasSelection then insert("")
    else
      val c = clamp(caret)
      if c >= length then this
      else EditBuffer(text.substring(0, c) + text.substring(c + 1), c, c)

  /** Insert a newline at the caret (the Enter key). */
  def newline: EditBuffer = insert("\n")

object EditBuffer:

  /** Split on `\n` keeping trailing empty fields, so the line count is stable
    * (`countNewlines + 1`) and a trailing newline yields a final empty line. */
  def splitLines(text: String): Vector[String] =
    val out = Vector.newBuilder[String]
    var start = 0
    var i     = 0
    while i < text.length do
      if text.charAt(i) == '\n' then { out += text.substring(start, i); start = i + 1 }
      i += 1
    out += text.substring(start)
    out.result()

  private def countNewlines(text: String): Int =
    var n = 0
    var i = 0
    while i < text.length do
      if text.charAt(i) == '\n' then n += 1
      i += 1
    n

  /** Length of the line whose first character is at offset `start` — up to the next `\n` or
    * the end of the text. */
  private def lineLengthAt(text: String, start: Int): Int =
    var i = start
    while i < text.length && text.charAt(i) != '\n' do i += 1
    i - start
