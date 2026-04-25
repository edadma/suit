package io.github.edadma.suit

// Thin View-level helpers that compose Portal + AbsolutePosition + Backdrop
// into the three usual overlay shapes. They're plain functions, not stateful
// widgets — the caller owns `open`/`onClose` state and just stops rendering
// the helper when it should disappear.
//
// Sysl shape: each of these is a `fn` returning `View`. No closures or hooks
// required.

// ----------------------------------------------------------------------------
// Modal
// ----------------------------------------------------------------------------

// A centered dialog with a click-to-dismiss backdrop. The dialog is centered
// in the viewport via the `Center` primitive, and the child is wrapped in a
// `Box` panel so it has a solid background distinct from the dimmed area
// behind it. When `open` is false the helper returns `Empty` and the portal
// is torn down by the reconciler.
def modal(
    open:    Boolean,
    onClose: () => Unit,
    child:   View,
    backdropColor: Color = Backdrop.defaultColor,
    panelColor:    Color = Modal.defaultPanelColor,
    panelBorder:   Color = Modal.defaultPanelBorder,
    padding:       Insets = Insets.all(16),
    radius:        Int = 8,
): View =
  if !open then Empty
  else Portal(Backdrop(
    color           = backdropColor,
    onBackdropClick = onClose,
    child           = Center(Box(
      child   = child,
      color   = panelColor,
      padding = padding,
      radius  = radius,
      border  = panelBorder,
    )),
  ))

private object Modal:
  // Slightly lighter than the default theme bg so the panel reads as a
  // distinct surface against a dimmed backdrop. Apps that want strict
  // theme integration can pass their own panelColor.
  val defaultPanelColor:  Color = Color(36, 36, 48)
  val defaultPanelBorder: Color = Color(80, 80, 100)

// ----------------------------------------------------------------------------
// Tooltip
// ----------------------------------------------------------------------------

// A small label anchored at `(x, y)` in the overlay layer. No backdrop, no
// click handling — tooltips never block the underlying UI. Caller toggles
// `open` from hover state (typically `useState` plus a useEffect that
// schedules a delay).
def tooltip(
    open:  Boolean,
    x:     Int,
    y:     Int,
    child: View,
): View =
  if !open then Empty
  else Portal(AbsolutePosition(x, y, child))

// ----------------------------------------------------------------------------
// Context menu
// ----------------------------------------------------------------------------

// A pop-up menu anchored at `(x, y)` with a transparent backdrop that
// dismisses the menu on outside click. The backdrop *does* swallow the click
// so the underlying UI doesn't see it — that's usually what you want for a
// menu (you don't want to close the menu *and* trigger a button under the
// click). Inside-click behaviour is identical to a normal Portal child.
//
// `Backdrop.defaultColor` is too dark for menus (it would dim the whole UI);
// the default here is fully transparent black so the backdrop is invisible
// but still receives clicks.
def contextMenu(
    open:    Boolean,
    x:       Int,
    y:       Int,
    onClose: () => Unit,
    child:   View,
): View =
  if !open then Empty
  else Portal(Backdrop(
    color           = ContextMenu.transparent,
    onBackdropClick = onClose,
    child           = AbsolutePosition(x, y, child),
  ))

private object ContextMenu:
  val transparent: Color = Color(0, 0, 0, 0)


// ----------------------------------------------------------------------------
// Dropdown
// ----------------------------------------------------------------------------

// A button that, when clicked, opens a context menu of `options`. The
// caller owns `value`/`onChange` (controlled-input pattern). Internal `open`
// state is hooked into a function component so the helper "just works"
// without the caller threading an open-flag through their state. The popup
// uses `contextMenu(...)`, so an outside click dismisses it without leaking
// to underlying widgets.
def dropdown(
    value:    String,
    options:  Array[String],
    onChange: String => Unit,
    width:    Int = 160,
): View = Component(component("suit-dropdown") { hooks =>
  val (open, setOpen, _) = hooks.useState(false)

  // Trigger-button ref, captured during layout so the popup can anchor under
  // its actual screen position regardless of where the dropdown sits in the
  // tree. The layout effect runs every render, but only mutates the ref —
  // refs don't trigger re-renders, so there's no infinite loop. By the time
  // the user clicks the trigger and the component re-renders with `open=true`,
  // the ref has the latest bounds from the last laid-out frame.
  val triggerRef = hooks.useRef[Node | Null](null)
  val anchorRef  = hooks.useRef((0, 0))

  hooks.useLayoutEffect(
    () => {
      val n = triggerRef.current
      if n != null then
        val r = n.bounds
        anchorRef.current = (r.x, r.y + r.h + 4)
      () => ()
    },
    deps = null,
  )

  val items: Array[View] = options.map(opt =>
    Button(
      label   = opt,
      onClick = () =>
        onChange(opt)
        setOpen(false),
    ),
  )
  val menuPanel = Box(
    child   = Stack(
      axis     = Axis.Vertical,
      children = items,
      gap      = 2,
    ),
    color   = Modal.defaultPanelColor,
    border  = Modal.defaultPanelBorder,
    padding = Insets.all(4),
    radius  = 6,
  )

  val (ax, ay) = anchorRef.current

  Stack(
    axis     = Axis.Vertical,
    children = Array(
      WithRef(triggerRef, Button(
        label   = if value.nonEmpty then value + " ▾" else "Select… ▾",
        onClick = () => setOpen(!open),
      )),
      contextMenu(
        open    = open,
        x       = ax,
        y       = ay,
        onClose = () => setOpen(false),
        child   = menuPanel,
      ),
    ),
    gap = 0,
  )
})

// ----------------------------------------------------------------------------
// Tabs
// ----------------------------------------------------------------------------

// A horizontal row of tab buttons followed by `content`. The caller owns
// `selected` and `onSelect`; the helper wires the buttons. The active tab
// is rendered with brackets (`[Foo]`) — a more elaborate visual pass would
// need theme-supplied highlight colors, deferred until the theme grows.
def tabs(
    labels:   Array[String],
    selected: Int,
    onSelect: Int => Unit,
    content:  View,
): View =
  val buttons: Array[View] = labels.zipWithIndex.map { case (l, i) =>
    val shown = if i == selected then "[" + l + "]" else l
    val idx   = i
    Button(label = shown, onClick = () => onSelect(idx))
  }
  Stack(
    axis     = Axis.Vertical,
    children = Array(
      Stack(axis = Axis.Horizontal, children = buttons, gap = 4),
      content,
    ),
    gap = 8,
  )
