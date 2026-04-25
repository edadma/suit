package io.github.edadma.suit

// Immutable descriptions of UI. The application produces a View tree from its
// state on every state change; the reconciler diffs the new tree against the
// live Node tree and mutates Nodes to match.
//
// Maps to a sysl tagged-union enum:
//
//   enum View
//       Text(content: string)
//       Button(label: string, on_click: () -> unit, enabled: bool)
//       Input(value: string, placeholder: string, on_change: (string) -> unit, width: int)
//       Checkbox(label: string, checked: bool, on_toggle: (bool) -> unit, enabled: bool)
//       Stack(axis: Axis, children: []View, padding: Insets, gap: int)
//       Spacer(flex: int)
//       Component(widget: &Widget)
//       Fragment(children: []View)
//       Keyed(key: any, child: View)
//       ContextProvider(ctx: &Context, value: any, child: View)
//       Empty
sealed trait View

// Stack orientation. Maps to a sysl simple enum.
enum Axis:
  case Vertical
  case Horizontal

// --- View variants ---------------------------------------------------------

final case class Text(
    content: String,
) extends View

final case class Button(
    label:   String,
    onClick: () => Unit,
    enabled: Boolean = true,
) extends View

final case class Input(
    value:       String,
    placeholder: String         = "",
    onChange:    String => Unit = Input.noop,
    width:       Int            = 160,
) extends View

object Input:
  val noop: String => Unit = noopImpl
  private def noopImpl(s: String): Unit = ()

final case class Checkbox(
    label:    String,
    checked:  Boolean,
    onToggle: Boolean => Unit = Checkbox.noop,
    enabled:  Boolean         = true,
) extends View

object Checkbox:
  val noop: Boolean => Unit = noopImpl
  private def noopImpl(b: Boolean): Unit = ()

final case class Stack(
    axis:     Axis,
    children: Array[View],
    padding:  Insets = Insets.Zero,
    gap:      Int    = 6,
) extends View

final case class Spacer(flex: Int = 1) extends View

// A bitmap image drawn at fixed `width` × `height`. `source` is a string
// identifier — the host's `Renderer` decides how to resolve it (classpath
// resource → filesystem path → fallback). The fixed-size shape means
// layout doesn't need to wait for the image to finish loading.
final case class Image(
    source: String,
    width:  Int,
    height: Int,
) extends View

// A horizontal slider with a draggable thumb. `value` is constrained to
// `[min, max]`. Clicks anywhere on the track jump the thumb; mouse drags
// while held update continuously; arrow keys nudge by one when focused.
final case class Slider(
    value:    Int,
    min:      Int            = 0,
    max:      Int            = 100,
    onChange: Int => Unit    = Slider.noop,
    width:    Int            = 200,
    enabled:  Boolean        = true,
) extends View

object Slider:
  val noop: Int => Unit = noopImpl
  private def noopImpl(i: Int): Unit = ()

// A radio-style single-select control. Visually identical to Checkbox but
// with a circular indicator. `selected` is owned by the caller (typically a
// Stack of Radios sharing one `useState[Int]`); clicking fires `onSelect`,
// at which point the caller re-renders with the new selection.
final case class Radio(
    label:    String,
    selected: Boolean,
    onSelect: () => Unit    = Radio.noop,
    enabled:  Boolean       = true,
) extends View

object Radio:
  val noop: () => Unit = noopImpl
  private def noopImpl(): Unit = ()

// A vertically-scrolling viewport. The viewport occupies `height` pixels of
// vertical space; the child is given as much height as it asks for. When
// the child's natural height exceeds the viewport, the mouse wheel scrolls
// it. Scroll position persists on the corresponding ScrollNode.
final case class Scroll(child: View, height: Int) extends View

// "Teleports" `child` into the engine's overlay layer — an above-everything
// layer rendered after the main tree. Portals escape any clipping, layout
// constraints, and z-order of their original tree position. Used as the
// primitive behind modals, tooltips, and context menus. The portal site in
// the main tree itself measures to zero, so it doesn't push siblings around.
final case class Portal(child: View) extends View

// Places `child` at fixed coordinates (relative to the parent's bounds) and
// at its natural measured size. Combined with Portal, this gives "anywhere
// on the screen" positioning for tooltips and menus.
final case class AbsolutePosition(x: Int, y: Int, child: View) extends View

// Centers its child within the parent's bounds at the child's natural
// measured size. Like AbsolutePosition, the centered node overrides its own
// bounds to match the child's positioned rect, so a Backdrop wrapping a
// Center can correctly distinguish "click inside child" from "click on the
// surrounding empty space". The primary use is `modal()`.
final case class Center(child: View) extends View

// Stretches its child to fill the engine's whole viewport. Useful as a
// modal backdrop inside a Portal — also handy for fade overlays. Captures
// clicks via its `onBackdropClick` handler (e.g. close-on-outside-click).
final case class Backdrop(
    child: View,
    color: Color           = Backdrop.defaultColor,
    onBackdropClick: () => Unit = Backdrop.noop,
) extends View

object Backdrop:
  val defaultColor: Color    = Color(0, 0, 0, 96)
  val noop:         () => Unit = noopImpl
  private def noopImpl(): Unit = ()

// Wraps a stateful, user-defined Widget.
final case class Component(widget: Widget) extends View

// Splices children into the parent's children list at this position. Useful
// for components that want to return multiple sibling views without an extra
// wrapper. Sysl analog: `[]View`.
final case class Fragment(children: Array[View]) extends View

// Tags a sibling view with a stable key so the reconciler can preserve its
// state across reorders/inserts/removes. Pass-through: the reconciler
// unwraps Keyed when reconciling and uses the key only for matching.
final case class Keyed(key: Any, child: View) extends View

// Forwards a `Ref` to the Node produced by `child`. After the reconciler
// mounts/updates the child, it sets `ref.current` to that node. Use it
// together with `Hooks.useRef` and `Engine.focusNode` for the "auto-focus
// this input on mount" / "scroll this row into view" patterns. The user is
// responsible for nulling the ref on unmount via a useEffect cleanup if
// they care; the ref otherwise goes stale.
final case class WithRef(ref: Ref[Node | Null], child: View) extends View

// Pushes a context value while rendering its child subtree. `useContext(ctx)`
// inside the subtree returns this value. Outside any provider, useContext
// returns the context's default. Stored as Any to avoid threading a type
// parameter through the View enum; the typed wrapper `Context.provide`
// constructs this.
final case class ContextProvider(ctx: Context[?], value: Any, child: View) extends View

// Catches any throwable raised while reconciling/rendering `child` and
// substitutes `fallback(t)` in its place. Without an enclosing
// ErrorBoundary, an exception bubbles up and the host crashes — same as
// React's behaviour. The fallback view itself is *not* protected; throw
// from there at your peril.
final case class ErrorBoundary(fallback: Throwable => View, child: View) extends View

case object Empty extends View


// --- Stateful widget trait -------------------------------------------------

trait Widget:
  def render(): View
  def widgetId(): Long
  def attach(engine: Engine): Unit       = ()
  def updateProps(next: Widget): Unit    = ()

  // Opt-in bailout: when false, the reconciler skips this widget's `render()`
  // and instead walks the existing child Node tree to look for dirty
  // descendants (so state changes inside a memoized subtree still take
  // effect). Default `true` — most widgets always render.
  def shouldRender: Boolean              = true


// --- Context API -----------------------------------------------------------

// A typed context handle. Create with `createContext(default)`. Provide a
// value with `ctx.provide(value, child)`. Read with `hooks.useContext(ctx)`.
final class Context[T](val default: T):
  def provide(value: T, child: View): View = ContextProvider(this, value, child)

def createContext[T](default: T): Context[T] = new Context[T](default)
