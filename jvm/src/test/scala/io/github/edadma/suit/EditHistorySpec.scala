package io.github.edadma.suit

import org.scalatest.funsuite.AnyFunSuite

// The pure undo/redo model: snapshot stacks, typing coalescing, and the redo-clearing rule.
class EditHistorySpec extends AnyFunSuite:

  private def snap(text: String) = EditSnapshot(text, text.length, text.length)

  test("undo restores the recorded state and redo re-applies it"):
    val h = new EditHistory
    h.record(snap("a"), coalesce = false) // about to go a -> ab
    assert(h.undo(snap("ab")) == Some(snap("a")))
    assert(h.redo(snap("a")) == Some(snap("ab")))

  test("undo with nothing recorded returns None"):
    assert(new EditHistory().undo(snap("x")) == None)

  test("a run of coalescing records collapses to one undo step"):
    val h = new EditHistory
    h.record(snap(""), coalesce = true)   // first keystroke of a run records the pre-typing state
    h.record(snap("h"), coalesce = true)  // folded into the run
    h.record(snap("he"), coalesce = true) // folded in
    // one undo reverts the whole run to where it started
    assert(h.undo(snap("hel")) == Some(snap("")))
    assert(h.undo(snap("")) == None) // nothing else recorded

  test("breakRun ends a coalescing run so the next record is its own step"):
    val h = new EditHistory
    h.record(snap("abc"), coalesce = true) // typing run started at "abc"
    h.breakRun()                            // e.g. a caret move
    h.record(snap("abc"), coalesce = true)  // a fresh run — recorded separately
    assert(h.undo(snap("abcX")) == Some(snap("abc")))
    assert(h.undo(snap("abc")) == Some(snap("abc"))) // the earlier run is still there

  test("a new edit clears the redo stack"):
    val h = new EditHistory
    h.record(snap("a"), coalesce = false)
    h.undo(snap("ab"))                  // future now holds "ab"
    h.record(snap("a"), coalesce = false) // a new edit
    assert(h.redo(snap("aX")) == None)  // redo was cleared
