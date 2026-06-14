package io.github.edadma.suit

/** One point in a text editor's history: the full text and the selection (caret + anchor) at a
  * moment. Undo/redo restore a whole snapshot, so the caret lands where it was. */
final case class EditSnapshot(text: String, caret: Int, anchor: Int)

/** The undo/redo stacks for a text editor. The widget records the state *before* each edit; undo
  * swaps the current state onto the redo stack and restores the most recent past one (redo is the
  * mirror). A new edit clears the redo stack, as every editor does.
  *
  * Consecutive runs of typed characters coalesce into a single undo step: the first keystroke of a
  * run records the pre-typing state, and the rest are folded in, so one undo reverts the whole run
  * rather than a character at a time. Any other edit (delete, paste, newline, a word delete) — or a
  * bare caret move via [[breakRun]] — ends the run, so the next keystroke starts a fresh step. */
final class EditHistory:
  private var past:      List[EditSnapshot] = Nil
  private var future:    List[EditSnapshot] = Nil
  private var coalescing: Boolean           = false

  /** Record `prev` (the state before an edit) as an undo point and clear the redo stack. When
    * `coalesce` is set and the previous record was also coalescing, the snapshot is folded into the
    * open run instead of pushed, so a run of typing undoes as one step. */
  def record(prev: EditSnapshot, coalesce: Boolean): Unit =
    if !(coalesce && coalescing) then past = prev :: past
    future = Nil
    coalescing = coalesce

  /** End any open coalescing run without recording — for a caret move between edits, so the next
    * typed character starts a new undo step rather than joining the previous run. */
  def breakRun(): Unit = coalescing = false

  /** Restore the most recent undo point, pushing `current` onto the redo stack; `None` when there
    * is nothing to undo. Ends any coalescing run. */
  def undo(current: EditSnapshot): Option[EditSnapshot] =
    coalescing = false
    past match
      case h :: t => past = t; future = current :: future; Some(h)
      case Nil    => None

  /** Re-apply the most recently undone point, pushing `current` onto the undo stack; `None` when
    * there is nothing to redo. */
  def redo(current: EditSnapshot): Option[EditSnapshot] =
    coalescing = false
    future match
      case h :: t => future = t; past = current :: past; Some(h)
      case Nil    => None
