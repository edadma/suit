package io.github.edadma.suit.testkit

import io.github.edadma.suit.*

import scala.collection.mutable
import scala.reflect.ClassTag

// Headless host for unit-testing Suit components — the JSDOM equivalent.
//
// A TestHost owns an Engine and exposes:
//   - `render(view)` to commit a view tree and run a frame,
//   - event-injection helpers (click, type, press, focus, hover),
//   - queries over the live Node tree (find by widget kind, by text, …),
//   - draw-list inspection (for snapshot-style assertions),
//   - manual time control via `setTime(ms)` and `advanceTime(ms)`.
//
// Tests should never touch System.currentTimeMillis or Thread.sleep — every
// time-dependent behaviour (caret blink, etc.) is read from `engine.clock`,
// which TestHost replaces with a controllable counter.
final class TestHost(viewportW: Int = 800, viewportH: Int = 600):

  val engine: Engine = new Engine

  private var simNowMs: Long = 0L
  engine.clock = () => simNowMs

  // ---- frame driving ------------------------------------------------------

  // Commit a view, run a frame, then drain any state updates that effects
  // queue up so the post-condition reflects the user's intent.
  def render(view: View): TestHost =
    engine.setRoot(view)
    settle()
    this

  // Run a single frame at the current state (no new view).
  def frame(): TestHost =
    engine.runFrame(viewportW, viewportH)
    this

  // Run frames until the engine reports nothing left to do (or a safety cap
  // to catch infinite-loop bugs in user code).
  def settle(maxFrames: Int = 16): TestHost =
    var i = 0
    while engine.needsFrame && i < maxFrames do
      frame()
      i = i + 1
    this


  // ---- time control -------------------------------------------------------

  def setTime(ms: Long): TestHost = { simNowMs = ms; this }
  def advanceTime(ms: Long): TestHost = { simNowMs = simNowMs + ms; this }


  // ---- mouse --------------------------------------------------------------

  def moveMouseTo(x: Int, y: Int): TestHost =
    engine.input.mouseX    = x
    engine.input.mouseY    = y
    engine.input.hadEvents = true
    settle()
    this

  def moveMouseTo(node: Node): TestHost =
    val r = node.bounds
    moveMouseTo(r.x + r.w / 2, r.y + r.h / 2)

  def clickAt(x: Int, y: Int): TestHost =
    engine.input.mouseX        = x
    engine.input.mouseY        = y
    engine.input.mouseDown     = true
    engine.input.mousePressed  = true
    engine.input.hadEvents     = true
    frame()
    engine.input.mouseDown     = false
    engine.input.mouseReleased = true
    engine.input.hadEvents     = true
    settle()
    this

  def click(node: Node): TestHost =
    val r = node.bounds
    clickAt(r.x + r.w / 2, r.y + r.h / 2)


  // ---- keyboard -----------------------------------------------------------

  def typeText(s: String): TestHost =
    var i = 0
    while i < s.length do
      engine.input.pushChar(s.charAt(i).toInt)
      engine.input.hadEvents = true
      settle()
      i = i + 1
    this

  enum Key:
    case Backspace, Delete, Left, Right, Home, End, Enter, Tab, Space

  def press(key: Key): TestHost =
    key match
      case Key.Backspace => engine.input.keyBackspace = true
      case Key.Delete    => engine.input.keyDelete    = true
      case Key.Left      => engine.input.keyLeft      = true
      case Key.Right     => engine.input.keyRight     = true
      case Key.Home      => engine.input.keyHome      = true
      case Key.End       => engine.input.keyEnd       = true
      case Key.Enter     => engine.input.keyEnter     = true
      case Key.Tab       => engine.input.keyTab       = true
      case Key.Space     => engine.input.keySpace     = true
    engine.input.hadEvents = true
    settle()
    this


  // ---- queries ------------------------------------------------------------

  def root: Node | Null = engine.rootNode

  // Return every node in the tree matching `pred`, in depth-first order.
  def findAll(pred: Node => Boolean): Seq[Node] =
    val out = mutable.ArrayBuffer.empty[Node]
    val r = engine.rootNode
    if r != null then walk(r, pred, out)
    out.toSeq

  // Type-filtered variants for the common case.
  def findAllOfType[T <: Node : ClassTag]: Seq[T] =
    val tag = summon[ClassTag[T]]
    findAll(tag.runtimeClass.isInstance(_)).map(_.asInstanceOf[T])

  private def walk(n: Node, pred: Node => Boolean, out: mutable.ArrayBuffer[Node]): Unit =
    if pred(n) then out += n
    n match
      case s: StackNode =>
        var i = 0
        while i < s.children.length do
          walk(s.children(i), pred, out)
          i = i + 1
      case c: ComponentNode =>
        val ch = c.child
        if ch != null then walk(ch, pred, out)
      case cp: ContextProviderNode =>
        val ch = cp.child
        if ch != null then walk(ch, pred, out)
      case _ => ()


  // Common targeted queries.
  def texts:      Seq[TextNode]     = findAllOfType[TextNode]
  def buttons:    Seq[ButtonNode]   = findAllOfType[ButtonNode]
  def inputs:     Seq[InputNode]    = findAllOfType[InputNode]
  def checkboxes: Seq[CheckboxNode] = findAllOfType[CheckboxNode]

  def findText(content: String): Option[TextNode] =
    texts.find(_.view.content == content)

  def findButton(label: String): Option[ButtonNode] =
    buttons.find(_.view.label == label)

  def findInput(p: Input => Boolean = _ => true): Option[InputNode] =
    inputs.find(n => p(n.view))

  def findInputByPlaceholder(s: String): Option[InputNode] =
    inputs.find(_.view.placeholder == s)

  def findCheckbox(label: String): Option[CheckboxNode] =
    checkboxes.find(_.view.label == label)


  // ---- inspection ---------------------------------------------------------

  def drawList: Seq[DrawCommand] = engine.drawList.toSeq

  // Concatenated rendered text — useful for quick assertions that some
  // string is or isn't on screen.
  def renderedText: String =
    drawList.collect { case DrawText(_, _, t, _) => t }.mkString(" ")

  def focusedNode: Node | Null = engine.focused
