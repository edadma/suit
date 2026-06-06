package io.github.edadma.suit

// Pointer input — the bridge from raw device events to the render tree's handlers.
//
// The runtime polls SDL for mouse events and feeds their positions here; the router
// hit-tests the render tree and fires the matching handler on the object under the
// cursor. Keeping the routing logic in pure Scala (it needs only `hitTest` and the
// handler maps, both off-device) means the press→release→click and hover enter/leave
// state machine is unit-tested on the JVM, the same way layout and paint are.

/** A pointer event delivered to a handler: where it happened (absolute coordinates)
  * and, for button events, which `button` (1 = left, 2 = middle, 3 = right; 0 for
  * motion). Handlers receive this rather than a raw SDL event, so application code
  * never depends on the backend. */
final case class PointerEvent(position: Offset, button: Int = 0):
  def x: Double = position.x
  def y: Double = position.y

/** Routes pointer activity into the render tree rooted at `root`. It owns the small
  * amount of state a pointer needs — which object the cursor is over (for hover) and
  * which object a press started on (so a release on the same object is a click) — and
  * dispatches the derived events (`mousedown`, `mouseup`, `click`, `mousemove`,
  * `mouseenter`, `mouseleave`) to the handler registered under that name. */
final class PointerRouter(root: RenderObject):
  private var hovered: RenderObject | Null = null
  private var pressed: RenderObject | Null = null

  private def hit(p: Offset): RenderObject | Null = root.hitTest(p, Offset.zero)

  private def fire(target: RenderObject | Null, event: String, e: PointerEvent): Unit =
    target match
      case t: RenderObject => t.handlers.get(event).foreach(_.apply(e))
      case null            => ()

  /** The cursor moved to `p`: update hover (firing `mouseleave` on the object left and
    * `mouseenter` on the one entered when the target changes), then `mousemove`. */
  def move(p: Offset): Unit =
    val target = hit(p)
    if !(target eq hovered) then
      fire(hovered, "mouseleave", PointerEvent(p))
      fire(target, "mouseenter", PointerEvent(p))
      hovered = target
    fire(target, "mousemove", PointerEvent(p))

  /** A button went down at `p`: fire `mousedown` and remember the target so a matching
    * release can be recognised as a click. */
  def down(p: Offset, button: Int): Unit =
    val target = hit(p)
    pressed = target
    fire(target, "mousedown", PointerEvent(p, button))

  /** A button came up at `p`: fire `mouseup`, then `click` if the release landed on the
    * same object the press started on. */
  def up(p: Offset, button: Int): Unit =
    val target = hit(p)
    fire(target, "mouseup", PointerEvent(p, button))
    if target != null && (target eq pressed) then fire(target, "click", PointerEvent(p, button))
    pressed = null
