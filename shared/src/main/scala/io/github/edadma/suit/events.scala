package io.github.edadma.suit

// Input — the bridge from raw device events to the render tree's handlers.
//
// The runtime polls SDL for mouse, wheel, and keyboard events and feeds them here;
// the routers hit-test the render tree (for pointer events) or consult the focus
// owner (for key events) and fire the matching handler. Keeping the routing logic in
// pure Scala — it needs only `hitTest`, the parent chain, and the handler maps, all
// off-device — means the dispatch model (bubbling, pointer capture, hover, focus) is
// unit-tested on the JVM the same way layout and paint are.
//
// Dispatch bubbles. A hit lands on the deepest object under the cursor, but handlers
// are usually registered on a composite's outer object (a button's frame, a slider's
// track), while the cursor actually sits over an inner decoration with no handler of
// its own. So an event walks up the parent chain from the hit to the nearest ancestor
// that has a handler for it, and fires there — the analogue of DOM event bubbling, in
// the single-listener-per-object form suit uses. The event's `local`/`size` are
// resolved against that receiving object, so a slider reading `local.x / size.width`
// measures against its whole track no matter which inner pixel was hit.

/** A pointer event delivered to a handler. `position` is in absolute (window)
  * coordinates; `local` is the same point relative to the receiving object's top-left,
  * and `size` is that object's size — together they let a handler work in its own
  * coordinate space (a slider maps `local.x / size.width` to a fraction). `button` is
  * 1 = left, 2 = middle, 3 = right for button events, and the held button during a
  * capture drag; it is 0 for a plain hover move and for enter/leave. */
final case class PointerEvent(position: Offset, local: Offset, size: Size, button: Int = 0):
  def x: Double      = position.x
  def y: Double      = position.y
  def localX: Double = local.x
  def localY: Double = local.y

/** A wheel/scroll event: where the cursor was and how far the wheel turned. Positive
  * `deltaY` is a downward/away scroll, matching SDL's convention. */
final case class ScrollEvent(position: Offset, deltaX: Double, deltaY: Double)

/** A keyboard event delivered to the focused object. `scancode` is the physical key
  * (see [[Key]] for the common names); `repeat` is true for the auto-repeat events a
  * held key produces; `shift` / `ctrl` report whether those modifiers were held (the
  * runtime fills them from the event's modifier bitmask), which a text field needs to tell
  * a caret move from a selection extend. Text entry — the Unicode a key press produces
  * under the active layout — is a separate concern delivered as [[TextInputEvent]]s, not
  * derived from scancodes here. */
final case class KeyEvent(scancode: Int, repeat: Boolean = false, shift: Boolean = false, ctrl: Boolean = false)

/** A run of typed text delivered to the focused object. This is the layout-resolved
  * Unicode a key press produces (so a composed or shifted character arrives as the actual
  * string), the counterpart to a [[KeyEvent]]: editing keys (Backspace, arrows) come as
  * key events, the characters they sit between come as text-input events. */
final case class TextInputEvent(text: String)

/** The common physical-key scancodes, named. These are the standard USB-HID usage
  * codes SDL reports as scancodes — a stable cross-platform numbering, not an SDL
  * implementation detail — so naming the handful suit's widgets react to keeps key
  * handling readable without leaking the backend. */
object Key:
  val A         = 4 // letters are USB-HID 4..29 (a..z); only the ones widgets react to are named
  val Enter     = 40
  val Escape    = 41
  val Backspace = 42
  val Tab       = 43
  val Space     = 44
  val Right     = 79
  val Left      = 80
  val Down      = 81
  val Up        = 82
  val Home      = 74
  val End       = 77
  val Delete    = 76
  val PageUp    = 75
  val PageDown  = 78

/** Owns which object currently has keyboard focus and notifies objects as focus moves.
  * The pointer router asks it to update focus on a press; the key router asks it which
  * object should receive a key. Focus changes fire `focus` / `blur` handlers (with no
  * payload) so a widget can re-style itself. */
final class FocusManager:
  private var _focused: RenderObject | Null = null

  // Active focus traps, innermost last — a stack so overlays nest: a menu opened from inside a
  // dialog pushes its own trap over the dialog's, and only the top one governs Tab and Escape
  // until it is released, restoring the one beneath. A single field could not express that
  // nesting; the stack is what makes stacked modals behave.
  private val _traps = scala.collection.mutable.Stack.empty[(RenderObject, () => Unit)]

  def focused: RenderObject | Null        = _focused
  def isFocused(o: RenderObject): Boolean = _focused eq o

  /** The subtree focus is currently trapped within (the innermost active trap), or null when
    * nothing traps it. While a trap is active, Tab traversal is confined to this subtree (see
    * [[focusNext]]) and Escape runs the trap's handler (see [[escape]]) — the behaviour a modal
    * dialog needs. */
  def trapRoot: RenderObject | Null =
    if _traps.isEmpty then null else _traps.top._1

  /** Trap focus within `root`: Tab cycles only inside it and [[escape]] invokes `onEscape`.
    * An overlay pushes a trap when it opens and pops it with [[releaseTrap]] when it closes;
    * traps stack, so opening a second overlay over the first nests rather than replacing it. */
  def trap(root: RenderObject, onEscape: () => Unit): Unit =
    _traps.push((root, onEscape))

  /** Release the innermost focus trap (the topmost overlay closed), uncovering the one beneath
    * if any. A no-op when nothing is trapped. */
  def releaseTrap(): Unit =
    if _traps.nonEmpty then _traps.pop(): Unit

  /** Invoke the innermost trap's escape handler if one is active, returning whether it was —
    * the runtime calls this on the Escape key so a trapping modal closes from anywhere inside
    * it, regardless of which control holds focus, and a plain (untrapped) Escape still routes
    * to the focused object as an ordinary key. With nested traps only the topmost reacts, so
    * Escape peels overlays off one at a time. */
  def escape(): Boolean =
    if _traps.isEmpty then false else { _traps.top._2(); true }

  /** Move focus to `o` (or clear it with `null`). A no-op if `o` is already focused;
    * otherwise fires `blur` on the object losing focus and `focus` on the one gaining
    * it. */
  def focus(o: RenderObject | Null): Unit =
    if !(o eq _focused) then
      val prev = _focused
      _focused = o
      fire(prev, "blur")
      fire(o, "focus")

  def blur(): Unit = focus(null)

  /** A press hit `target`: focus the nearest focusable ancestor (the target itself if
    * it is focusable), or blur if the press landed on nothing focusable — clicking
    * empty space drops focus, clicking a widget takes it. */
  def pointerFocus(target: RenderObject | Null): Unit =
    var n = target
    while n != null && !n.asInstanceOf[RenderObject].focusable do
      n = n.asInstanceOf[RenderObject].parent
    focus(n)

  /** Every focusable object under `root`, in document order — a depth-first preorder walk
    * (each object before its children, siblings in declaration order). This is the order
    * Tab traversal visits, the analogue of the DOM's sequential focus navigation order. */
  def focusables(root: RenderObject): List[RenderObject] =
    val acc = scala.collection.mutable.ListBuffer.empty[RenderObject]
    def walk(o: RenderObject): Unit =
      if o.focusable then acc += o
      o.children.foreach(walk)
    walk(root)
    acc.toList

  /** Move focus to the next focusable object after the current one, wrapping at the end;
    * `backward` (Shift+Tab) reverses direction. With nothing focused it takes the first
    * (or last, going backward); with no focusables at all it is a no-op. This is what the
    * runtime calls on the Tab key. While a focus trap is active the walk is scoped to the
    * trapped subtree instead of `root`, so Tab cannot leave an open modal. */
  def focusNext(root: RenderObject, backward: Boolean = false): Unit =
    val scope = trapRoot match
      case t: RenderObject => t
      case null            => root
    val list = focusables(scope)
    if list.nonEmpty then
      val cur  = list.indexWhere(_ eq _focused)
      val next =
        if cur < 0 then (if backward then list.length - 1 else 0)
        else (cur + (if backward then -1 else 1) + list.length) % list.length
      focus(list(next))

  private def fire(o: RenderObject | Null, event: String): Unit =
    o match
      case r: RenderObject => r.handlers.get(event).foreach(_.apply(()))
      case null            => ()

/** Routes keyboard events to the focused object. Mirrors [[PointerRouter]]: it holds
  * no key state of its own, only the [[FocusManager]] that names the recipient, so the
  * keydown/keyup dispatch is a pure function of focus + the handler maps. */
final class KeyRouter(focus: FocusManager):
  private def fire(event: String, e: KeyEvent): Unit =
    focus.focused match
      case r: RenderObject => r.handlers.get(event).foreach(_.apply(e))
      case null            => ()

  def down(scancode: Int, repeat: Boolean, shift: Boolean = false, ctrl: Boolean = false): Unit =
    fire("keydown", KeyEvent(scancode, repeat, shift, ctrl))
  def up(scancode: Int): Unit = fire("keyup", KeyEvent(scancode))

/** Routes typed text to the focused object, the text-entry sibling of [[KeyRouter]]. The
  * runtime delivers the platform's text-input events here; whichever object holds focus and
  * has a `textinput` handler receives the typed string. */
final class TextRouter(focus: FocusManager):
  def input(text: String): Unit =
    focus.focused match
      case r: RenderObject => r.handlers.get("textinput").foreach(_.apply(TextInputEvent(text)))
      case null            => ()

/** Routes pointer activity into the render tree rooted at `root`, with bubbling,
  * hover, and pointer capture. It owns the small amount of state a pointer needs:
  * which object owns the current hover (so enter/leave fire only when the owning
  * widget changes, not on every inner pixel), and which object captured the pointer on
  * a press (so a drag keeps reaching the widget it started on even when the cursor
  * leaves it — what a slider needs). If a [[FocusManager]] is supplied, a press also
  * updates focus. */
final class PointerRouter(root: RenderObject, focus: FocusManager | Null = null):
  private var hovered: RenderObject | Null  = null
  private var captured: RenderObject | Null = null
  private var captureButton: Int            = 0

  private def hit(p: Offset): RenderObject | Null = root.hitTest(p, Offset.zero)

  /** The nearest object at or above `target` that has a handler for `event`, or null. */
  private def nearest(target: RenderObject | Null, event: String): RenderObject | Null =
    var n = target
    while n != null && !n.asInstanceOf[RenderObject].handlers.contains(event) do
      n = n.asInstanceOf[RenderObject].parent
    n

  /** The nearest object at or above `target` that takes part in hover (has an enter or
    * leave handler), or null — the unit hover changes are tracked against. */
  private def hoverOwner(target: RenderObject | Null): RenderObject | Null =
    var n = target
    while n != null && {
        val r = n.asInstanceOf[RenderObject]
        !(r.handlers.contains("mouseenter") || r.handlers.contains("mouseleave"))
      }
    do n = n.asInstanceOf[RenderObject].parent
    n

  private def event(owner: RenderObject, p: Offset, button: Int): PointerEvent =
    PointerEvent(p, p - owner.absoluteOffset, owner.size, button)

  /** Bubble a pointer event from the hit object up to its nearest handler. */
  private def bubble(hitTarget: RenderObject | Null, name: String, p: Offset, button: Int): Unit =
    nearest(hitTarget, name) match
      case r: RenderObject => r.handlers(name).apply(event(r, p, button))
      case null            => ()

  /** Fire `name` directly on `owner` (already resolved), used for enter/leave whose
    * owner is the hover unit rather than the raw hit. */
  private def fireOn(owner: RenderObject | Null, name: String, p: Offset): Unit =
    owner match
      case r: RenderObject => r.handlers.get(name).foreach(_.apply(event(r, p, 0)))
      case null            => ()

  /** The cursor moved to `p`. While the pointer is captured, every move goes to the
    * capturing object (the drag); otherwise update hover (firing `mouseleave` on the
    * widget left and `mouseenter` on the one entered when the hover owner changes) then
    * deliver `mousemove`. */
  def move(p: Offset): Unit =
    captured match
      case c: RenderObject =>
        bubble(c, "mousemove", p, captureButton)
      case null =>
        val target = hit(p)
        val owner  = hoverOwner(target)
        if !(owner eq hovered) then
          fireOn(hovered, "mouseleave", p)
          fireOn(owner, "mouseenter", p)
          hovered = owner
        bubble(target, "mousemove", p, 0)

  /** A button went down at `p`: capture the pointer at the hit object, fire `mousedown`,
    * and move focus to the nearest focusable ancestor. */
  def down(p: Offset, button: Int): Unit =
    val target = hit(p)
    captured = target
    captureButton = button
    bubble(target, "mousedown", p, button)
    if focus != null then focus.pointerFocus(target)

  /** A button came up at `p`: fire `mouseup` to the capturing object, then `click` if
    * the press and the release resolve to the same click handler — i.e. the release
    * landed on the widget the press started on. */
  def up(p: Offset, button: Int): Unit =
    val target = hit(p)
    val holder = captured
    bubble(holder, "mouseup", p, button)
    val pressOwner   = nearest(holder, "click")
    val releaseOwner = nearest(target, "click")
    if releaseOwner != null && (releaseOwner eq pressOwner) then
      releaseOwner.asInstanceOf[RenderObject].handlers("click")
        .apply(event(releaseOwner.asInstanceOf[RenderObject], p, button))
    captured = null

  /** The wheel turned by `(dx, dy)` over `p`: bubble a `wheel` event to the nearest
    * scroll handler. */
  def wheel(p: Offset, dx: Double, dy: Double): Unit =
    nearest(hit(p), "wheel") match
      case r: RenderObject => r.handlers("wheel").apply(ScrollEvent(p, dx, dy))
      case null            => ()
