package io.github.edadma.suit

import org.scalatest.funsuite.AnyFunSuite

// Tests for the pure multi-line editing model. No device, no hooks — just the caret
// arithmetic, which is where a text editor's correctness actually lives.
class EditBufferSpec extends AnyFunSuite:

  // --- line geometry -------------------------------------------------------

  test("splitLines keeps trailing empties so the line count is stable"):
    assert(EditBuffer.splitLines("") == Vector(""))
    assert(EditBuffer.splitLines("a") == Vector("a"))
    assert(EditBuffer.splitLines("a\nb") == Vector("a", "b"))
    assert(EditBuffer.splitLines("a\n") == Vector("a", ""))
    assert(EditBuffer.splitLines("\n") == Vector("", ""))

  test("lineCount is one more than the newline count"):
    assert(EditBuffer("").lineCount == 1)
    assert(EditBuffer("a\nb\nc").lineCount == 3)
    assert(EditBuffer("a\n").lineCount == 2)

  test("lineColOf and indexOf are inverse"):
    val b = EditBuffer("ab\ncde\nf")
    assert(b.lineColOf(0) == (0, 0))
    assert(b.lineColOf(2) == (0, 2)) // end of first line
    assert(b.lineColOf(3) == (1, 0)) // start of second line
    assert(b.lineColOf(6) == (1, 3)) // end of second line
    assert(b.lineColOf(7) == (2, 0)) // start of third line
    assert(b.indexOf(1, 0) == 3)
    assert(b.indexOf(1, 2) == 5)
    assert(b.indexOf(2, 0) == 7)

  test("indexOf caps the column at the line's length"):
    val b = EditBuffer("ab\ncde")
    assert(b.indexOf(0, 99) == 2) // first line is 2 long → its end
    assert(b.indexOf(1, 99) == 6) // second line ends at the document end

  // --- editing -------------------------------------------------------------

  test("insert at the caret"):
    val b = EditBuffer("ac", 1, 1).insert("b")
    assert(b.text == "abc")
    assert(b.caret == 2 && b.anchor == 2)

  test("insert replaces a selection"):
    val b = EditBuffer("abc", 3, 0).insert("X") // whole thing selected
    assert(b.text == "X")
    assert(b.caret == 1)

  test("newline splits the line"):
    val b = EditBuffer("abcd", 2, 2).newline
    assert(b.text == "ab\ncd")
    assert(b.lineColOf(b.caret) == (1, 0))

  test("backspace removes the char before the caret, joining lines at a boundary"):
    assert(EditBuffer("abc", 3, 3).backspace.text == "ab")
    val joined = EditBuffer("ab\ncd", 3, 3).backspace // caret at start of 2nd line
    assert(joined.text == "abcd")

  test("delete removes the char after the caret"):
    assert(EditBuffer("abc", 0, 0).delete.text == "bc")
    val joined = EditBuffer("ab\ncd", 2, 2).delete // caret at end of 1st line
    assert(joined.text == "abcd")

  test("backspace and delete clear a selection without removing extra"):
    assert(EditBuffer("abcd", 3, 1).backspace.text == "ad")
    assert(EditBuffer("abcd", 3, 1).delete.text == "ad")

  // --- motion --------------------------------------------------------------

  test("left/right move and, without shift, collapse a selection to the right end"):
    assert(EditBuffer("abc", 1, 1).right(false).caret == 2)
    assert(EditBuffer("abc", 1, 1).left(false).caret == 0)
    // a selection 1..3 collapses to its near end on an unshifted arrow
    assert(EditBuffer("abc", 3, 1).left(false).caret == 1)
    assert(EditBuffer("abc", 3, 1).right(false).caret == 3)

  test("shift+arrow extends the selection (anchor stays)"):
    val b = EditBuffer("abc", 3, 3).left(true)
    assert(b.caret == 2 && b.anchor == 3 && b.hasSelection)

  test("up and down keep the column where the target line allows"):
    val b = EditBuffer("abcdef\nxy\nghijkl")
    val atCol4 = b.indexOf(0, 4)            // line 0, col 4
    val down1  = b.copy(caret = atCol4, anchor = atCol4).down(false)
    assert(down1.lineColOf(down1.caret) == (1, 2)) // short middle line clamps col to 2
    val down2 = down1.down(false)
    assert(down2.lineColOf(down2.caret) == (2, 2)) // column is taken from the now-current line

  test("up from the first line goes to the start, down from the last goes to the end"):
    val b = EditBuffer("abc\ndef", 1, 1)
    assert(b.up(false).caret == 0)
    val last = EditBuffer("abc\ndef", 5, 5)
    assert(last.down(false).caret == 7)

  test("Home/End act on the caret's line; doc start/end span the buffer"):
    val b = EditBuffer("abc\ndef", 5, 5) // line 1, col 1
    assert(b.lineHome(false).caret == 4)
    assert(b.lineEnd(false).caret == 7)
    assert(b.docStart(false).caret == 0)
    assert(b.docEnd(false).caret == 7)

  test("selectAll spans the whole buffer"):
    val b = EditBuffer("abc\ndef").selectAll
    assert(b.selLo == 0 && b.selHi == 7 && b.hasSelection)

  test("deleteWordLeft removes the word (and leading spaces) before the caret"):
    val b = EditBuffer("hello world", 11, 11) // caret at end
    val r = b.deleteWordLeft
    assert(r.text == "hello ")
    assert(r.caret == 6)
    assert(r.deleteWordLeft.text == "") // a second delete takes the space run and "hello"

  test("deleteWordLeft at the start of the buffer is a no-op"):
    assert(EditBuffer("abc", 0, 0).deleteWordLeft.text == "abc")

  test("deleteWordLeft with a selection just removes the selection"):
    val b = EditBuffer("alpha beta", 0, 5) // selects "alpha"
    assert(b.deleteWordLeft.text == " beta")

  test("wordLeft moves to the start of the previous word; wordRight to the end of the next"):
    val end = EditBuffer("hello world foo", 15, 15) // caret at end
    assert(end.wordLeft(false).caret == 12)                       // start of "foo"
    assert(end.wordLeft(false).wordLeft(false).caret == 6)        // start of "world"
    val start = EditBuffer("hello world", 0, 0)
    assert(start.wordRight(false).caret == 5)                     // end of "hello"
    assert(start.wordRight(false).wordRight(false).caret == 11)   // end of "world"

  test("a word move with extend keeps the anchor so it selects"):
    val sel = EditBuffer("alpha beta", 10, 10).wordLeft(true) // from the end
    assert(sel.caret == 6 && sel.anchor == 10 && sel.hasSelection)
