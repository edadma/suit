package io.github.edadma.suit

import io.github.edadma.suit.dsl.*

// The positioned and modal overlays: dialogs, dropdown menus, and tooltips, plus the shared
// `popover` placement mechanism and the [[Placement]] vocabulary they all anchor with. Each
// portals into the overlay layer so it escapes any clip or scroll of the place that opened it.
private[suit] trait WidgetsOverlays extends WidgetsSupport:

  /** Which side of its trigger a positioned overlay (menu, tooltip) prefers to open on. The
    * popover flips to the opposite side when the preferred one would run off-screen. */
  enum PopoverSide:
    case Below, Above, Right, Left

  /** Where the overlay sits along the cross axis relative to its trigger: for `Below`/`Above`
    * this aligns the card's left edge, centre, or right edge to the trigger; for `Right`/`Left`
    * it aligns the top, middle, or bottom. */
  enum PopoverAlign:
    case Start, Center, End

  /** How a positioned overlay places itself against its trigger: the preferred [[PopoverSide]],
    * the `gap` in pixels between trigger and card, and the cross-axis [[PopoverAlign]]. The
    * popover still keeps the card on-screen — it flips to the opposite side when the preferred
    * one overflows and slides along the cross axis — so this expresses the *preference*, not a
    * fixed position. The default (below, flush, left-aligned) is the dropdown-menu placement. */
  case class Placement(
      side:  PopoverSide  = PopoverSide.Below,
      gap:   Double       = 0.0,
      align: PopoverAlign = PopoverAlign.Start,
  )

  // The modal implementation. Props are a tuple: open flag, close callback, whether a scrim
  // click dismisses, and the exit-animation duration. A friendlier `Dialog(...)` wrapper
  // below names them.
  private val DialogImpl: ContainerP[(Boolean, () => Unit, Boolean, Int, Double)] =
    container[(Boolean, () => Unit, Boolean, Int, Double)] { (props, children) =>
      val (open, onClose, maskClosable, exitMs, width) = props
      val theme    = useTheme()
      val env      = useOverlay()
      val presence = usePresence(open, exitMs)

      // The scrim and card fade in together, and back out before the dialog unmounts, off the
      // open phase — the same enter/exit pair every dismissible overlay uses.
      val amt   = useTransition(if presence.phase == PresencePhase.Open then 1.0 else 0.0, exitMs)
      val saved = useRef[RenderObject | Null](null)

      // Focus is the other half of "modal": on open, remember whatever held focus, trap focus
      // to the overlay (so Tab cycles inside the dialog and Escape closes it) and move focus to
      // the first control within; on close, release the trap and restore focus to the opener.
      // Keyed on `mounted` so it arms once when the dialog appears and tears down when it goes.
      useEffect(
        () =>
          (env.overlay, env.focus) match
            case (o: RenderObject, f: FocusManager) if presence.mounted =>
              saved.current = f.focused
              f.trap(o, onClose)
              f.focusables(o).headOption.foreach(f.focus)
              () =>
                f.releaseTrap()
                f.focus(saved.current)
            case _ => noCleanup
        ,
        Array(presence.mounted),
      )

      env.overlay match
        case o: RenderObject if presence.mounted =>
          portal(
            o,
            // The scrim: a full-window dimming layer whose own click (outside the card)
            // dismisses when permitted. Its opacity rides the fade.
            box(
              bg      = Color(0, 0, 0, (140 * amt).toInt),
              onClick = _ => if maskClosable then onClose(),
            )(
              center(
                // The card. It swallows clicks so they do not reach the scrim, is focusable so
                // it is the trap's first target, and closes on Escape from anywhere inside it.
                box(
                  bg          = theme.surface,
                  border      = theme.border,
                  borderWidth = 1,
                  radius      = theme.radius,
                  shadow      = Shadow(),
                  opacity     = amt,
                  padding     = EdgeInsets.all(theme.spacing * 2),
                  focusable   = true,
                  onClick     = _ => (),
                  onKeyDown   = e => if e.scancode == Key.Escape then onClose(),
                )(constrainedBox(maxWidth = width)(children*)),
              ),
            ),
          )
        case _ => VEmpty
    }

  /** A modal dialog: content centred above a dimming scrim that takes over the window until
    * dismissed. It is **controlled** — the caller owns `open` and is told to close through
    * `onClose`, fired by a click on the scrim (when `maskClosable`), the Escape key, or
    * whatever the caller wires inside the body. The dialog is **portaled into the overlay
    * layer** ([[useOverlay]]), so it escapes any clip or scroll of the place that opened it
    * and always paints on top.
    *
    * Opening moves focus into the dialog and traps Tab within it; Escape closes it from
    * anywhere inside; closing restores focus to whatever held it before. The scrim and card
    * fade and the card settles in through [[usePresence]] + [[useTransition]], and the dialog
    * stays mounted through its close animation (`exitMs`) before unmounting. With no overlay
    * layer available — outside a running app — it renders nothing.
    *
    * The content is capped to `width` pixels — sized to its content but never wider — so body
    * text has a width to wrap into; pass that text `maxLines` other than 1, since `text` is
    * single-line by default. Pass `width = Double.NaN` to leave it uncapped (a long single line
    * then grows the card unbounded). */
  def Dialog(
      open:         Boolean,
      onClose:      () => Unit,
      maskClosable: Boolean = true,
      exitMs:       Int     = 200,
      width:        Double  = 420,
  )(children: VNode*): VNode =
    DialogImpl((open, onClose, maskClosable, exitMs, width))(children*)

  // The shared mechanism behind the positioned overlays (Menu, Tooltip). Unlike a modal, these
  // anchor to a trigger rather than centring, so the card is portaled into the overlay and
  // placed against the anchor's on-screen rectangle, read from a `ref` the caller put on the
  // trigger. The card is measured once it lays out (through its own ref) so a later render can
  // flip it above the anchor when it would overflow the bottom and slide it left to stay
  // on-screen; the measure lags layout by a frame, but the open fade hides that settle, and the
  // card is kept invisible until it has a size so it never flashes at the initial guess. A
  // dismissible popover (a menu) gets a full-window click-catcher behind it and traps focus; a
  // passive one (a tooltip) is click-through and never steals focus.
  // `point`, when non-null, anchors the card at a free screen point (a zero-size rectangle there)
  // instead of against the trigger ref — what a context menu, which opens at the cursor rather
  // than beside a widget, passes. The ref path is unchanged for every other caller.
  private def popover(
      anchor:    Ref[RenderObject | Null],
      mounted:   Boolean,
      amt:       Double,
      onDismiss: (() => Unit) | Null,
      trapFocus: Boolean,
      card:      VNode,
      placement: Placement      = Placement(),
      point:     Offset | Null  = null,
  )(using Hooks): VNode =
    val env            = useOverlay()
    val cardRef        = useRef[RenderObject | Null](null)
    val (sz, setSz, _) = useState(Size.zero)
    val dismiss: () => Unit = if onDismiss != null then onDismiss else () => ()

    // Measure the card after each layout, so the next render can position it precisely. The
    // read lags layout by a frame (effects run before the frame's layout); it converges within
    // a couple of frames, which the open fade covers.
    useEffect(
      () =>
        cardRef.current match
          case r: RenderObject => if r.size != sz then setSz(r.size)
          case null            => ()
        noCleanup,
      null,
    )

    // A menu traps focus to the overlay while open (Tab cycles inside, Escape dismisses) and
    // restores it on close; a tooltip is passive and does neither.
    val saved = useRef[RenderObject | Null](null)
    useEffect(
      () =>
        (env.overlay, env.focus) match
          case (o: RenderObject, f: FocusManager) if mounted && trapFocus =>
            saved.current = f.focused
            f.trap(o, dismiss)
            f.focusables(o).headOption.foreach(f.focus)
            () =>
              f.releaseTrap()
              f.focus(saved.current)
          case _ => noCleanup
      ,
      Array(mounted, trapFocus),
    )

    env.overlay match
      case o: RenderObject if mounted =>
        val win = o.size
        val (ax, ay, aw, ah) = point match
          case p: Offset => (p.x, p.y, 0.0, 0.0)
          case null =>
            anchor.current match
              case r: RenderObject =>
                val off = r.absoluteOffset
                (off.x, off.y, r.size.width, r.size.height)
              case null => (0.0, 0.0, 0.0, 0.0)

        // Place the card on the preferred side of the trigger, flipping to the opposite side
        // when it would run off-screen and there is room the other way; along the cross axis it
        // aligns to the trigger per the placement and slides back on-screen if it would overflow.
        val gap = placement.gap
        def clamp(v: Double, max: Double): Double = math.max(0.0, math.min(v, max))
        val crossX = placement.align match
          case PopoverAlign.Start  => ax
          case PopoverAlign.Center => ax + (aw - sz.width) / 2
          case PopoverAlign.End    => ax + aw - sz.width
        val crossY = placement.align match
          case PopoverAlign.Start  => ay
          case PopoverAlign.Center => ay + (ah - sz.height) / 2
          case PopoverAlign.End    => ay + ah - sz.height

        val (left, top) = placement.side match
          case PopoverSide.Below =>
            val belowTop = ay + ah + gap
            val aboveTop = ay - gap - sz.height
            val y        = if belowTop + sz.height > win.height && aboveTop >= 0.0 then aboveTop else belowTop
            (clamp(crossX, win.width - sz.width), y)
          case PopoverSide.Above =>
            val aboveTop = ay - gap - sz.height
            val belowTop = ay + ah + gap
            val y        = if aboveTop < 0.0 && belowTop + sz.height <= win.height then belowTop else aboveTop
            (clamp(crossX, win.width - sz.width), y)
          case PopoverSide.Right =>
            val rightLeft = ax + aw + gap
            val leftLeft  = ax - gap - sz.width
            val x         = if rightLeft + sz.width > win.width && leftLeft >= 0.0 then leftLeft else rightLeft
            (x, clamp(crossY, win.height - sz.height))
          case PopoverSide.Left =>
            val leftLeft  = ax - gap - sz.width
            val rightLeft = ax + aw + gap
            val x         = if leftLeft < 0.0 && rightLeft + sz.width <= win.width then rightLeft else leftLeft
            (x, clamp(crossY, win.height - sz.height))

        // Invisible until measured, so the first frame (laid out at the initial guess) never
        // shows; by the time the fade reveals it, it sits at the resolved position.
        val vis = if sz == Size.zero then 0.0 else amt

        // The card swallows clicks (so only outside clicks dismiss) when this popover is
        // dismissible; a tooltip leaves it click-through. The positioner lays the card out at
        // its natural size so the measurement is the real card, not one clamped to the gap
        // below the anchor.
        val swallow: (PointerEvent => Unit) | Null = if onDismiss != null then (_: PointerEvent) => () else null
        val placed = positioned(left, top)(box(ref = cardRef, opacity = vis, onClick = swallow)(card))

        portal(
          o,
          if onDismiss != null then
            // A full-window catcher behind the card — transparent, not dimming — whose click
            // anywhere outside the card dismisses it.
            box(onClick = _ => dismiss())(placed)
          else
            // A tooltip floats above without intercepting: the whole layer is click-through.
            box(ignorePointer = true)(placed),
        )
      case _ => VEmpty

  /** A single row in a [[Menu]] — a focusable item that calls `onSelect` when clicked or
    * activated from the keyboard (Space or Enter). It highlights on hover. Wire `onSelect` to
    * perform the action and close the menu. */
  val MenuItem: Component2[String, () => Unit] =
    component[String, () => Unit] { (label, onSelect) =>
      val theme                = useTheme()
      val (hover, setHover, _) = useState(false)
      val amt                  = useTransition(if hover then 1.0 else 0.0, 90)

      box(
        bg           = Color.lerp(theme.surface, theme.primary, 0.18 * amt),
        radius       = theme.radius * 0.5,
        padding      = EdgeInsets.symmetric(horizontal = theme.spacing * 1.5, vertical = theme.spacing * 0.75),
        focusable    = true,
        onMouseEnter = _ => setHover(true),
        onMouseLeave = _ => setHover(false),
        onClick      = _ => onSelect(),
        onKeyDown    = e => if e.scancode == Key.Space || e.scancode == Key.Enter then onSelect(),
      )(text(label))
    }

  // The dropdown menu implementation. Props: open flag, close callback, the anchor ref (placed
  // on the trigger by the caller), the exit-fade duration, and the menu width.
  private val MenuImpl: ContainerP[(Boolean, () => Unit, Ref[RenderObject | Null], Int, Double, Placement)] =
    container[(Boolean, () => Unit, Ref[RenderObject | Null], Int, Double, Placement)] { (props, items) =>
      val (open, onClose, anchor, exitMs, width, placement) = props
      val theme    = useTheme()
      val presence = usePresence(open, exitMs)
      val amt      = useTransition(if presence.phase == PresencePhase.Open then 1.0 else 0.0, exitMs)

      // A surface card of stretched items. The fixed width comes through a sizedBox so the
      // column gets a tight cross-axis and its items fill the menu (a plain box would loosen
      // the child and the items would size raggedly to their own text).
      val card = box(
        bg          = theme.surface,
        border      = theme.border,
        borderWidth = 1,
        radius      = theme.radius,
        shadow      = Shadow(),
        clip        = true,
        padding     = EdgeInsets.all(theme.spacing * 0.5),
      )(
        sizedBox(width = width)(
          col(crossAxisAlignment = CrossAxisAlignment.Stretch, mainAxisSize = MainAxisSize.Min, spacing = 2)(items*),
        ),
      )

      popover(anchor, presence.mounted, amt, onDismiss = onClose, trapFocus = true, card = card, placement = placement)
    }

  /** A dropdown menu anchored to a trigger. It is **controlled** — the caller owns `open` and
    * is told to close through `onClose` — and **positioned**: it portals into the overlay layer
    * and floats just below the trigger, flipping above it near the bottom edge and sliding left
    * to stay on-screen. Pass the trigger a `ref` (a `useRef[RenderObject | Null](null)`) and
    * give the same ref here as `anchor`, so the menu can read the trigger's on-screen rectangle.
    *
    * A click anywhere outside the menu dismisses it (a transparent full-window catcher, not a
    * dimming scrim), as does Escape; opening traps Tab within the menu and restores focus on
    * close. The menu fades in and out through [[usePresence]] + [[useTransition]]. With no
    * overlay layer available it renders nothing. Fill it with [[MenuItem]]s. Pass a [[Placement]]
    * to prefer a different side or add a gap; it still flips and slides to stay on-screen. */
  def Menu(
      open:      Boolean,
      onClose:   () => Unit,
      anchor:    Ref[RenderObject | Null],
      exitMs:    Int       = 150,
      width:     Double    = 180,
      placement: Placement = Placement(),
  )(items: VNode*): VNode =
    MenuImpl((open, onClose, anchor, exitMs, width, placement))(items*)

  // A muted ink for secondary text (placeholder, chevron): the surface text blended part-way
  // toward the surface so it reads as quieter without hard-coding a grey.
  private def overlayMuted(theme: Theme): Color = Color.lerp(theme.surfaceText, theme.surface, 0.45)

  // The card chrome the dropdown overlays (Select, context menu) share with the Menu: a bordered,
  // shadowed surface of stretched items at a fixed width, so the items fill the card rather than
  // sizing raggedly to their own text.
  private def overlayCard(theme: Theme, width: Double, items: Seq[VNode]): VNode =
    box(
      bg          = theme.surface,
      border      = theme.border,
      borderWidth = 1,
      radius      = theme.radius,
      shadow      = Shadow(),
      clip        = true,
      padding     = EdgeInsets.all(theme.spacing * 0.5),
    )(
      sizedBox(width = width)(
        col(crossAxisAlignment = CrossAxisAlignment.Stretch, mainAxisSize = MainAxisSize.Min, spacing = 2)(items*),
      ),
    )

  private case class SelectProps(
      options:     Seq[(String, String)],
      selected:    String,
      onChange:    String => Unit,
      placeholder: String,
      width:       Double,
      exitMs:      Int,
  )

  private val SelectImpl: Component[SelectProps] =
    component[SelectProps] { p =>
      val theme              = useTheme()
      val (open, setOpen, _) = useState(false)
      val anchor             = useRef[RenderObject | Null](null)
      val presence           = usePresence(open, p.exitMs)
      val amt                = useTransition(if presence.phase == PresencePhase.Open then 1.0 else 0.0, p.exitMs)
      val muted              = overlayMuted(theme)

      // The currently-selected option's label, or the placeholder when the value matches none of
      // the options (the unselected state) — shown muted so it reads as a prompt, not a value.
      val current       = p.options.find(_._1 == p.selected)
      val selectedLabel = current.map(_._2).getOrElse(p.placeholder)

      // A field-styled trigger: looks like a text input, borders in the accent while open, and
      // toggles the dropdown on click or Space/Enter (Escape closes). The chevron marks it as a
      // dropdown — a text glyph, since SVG loading is native-only and this widget is shared code.
      val trigger =
        box(
          bg          = theme.surface,
          border      = if open then theme.accent else theme.border,
          borderWidth = 1,
          radius      = theme.radius,
          padding     = EdgeInsets.symmetric(horizontal = 10, vertical = 8),
          width       = p.width,
          focusable   = true,
          ref         = anchor,
          onClick     = _ => setOpen(!open),
          onKeyDown = e =>
            e.scancode match
              case Key.Space | Key.Enter => setOpen(!open)
              case Key.Escape            => setOpen(false)
              case _                     => (),
        )(
          row(crossAxisAlignment = CrossAxisAlignment.Center)(
            text(selectedLabel, color = if current.isEmpty then muted else theme.surfaceText),
            spacer(),
            text("▾", color = muted),
          ),
        )

      val items = p.options.map { case (v, l) => MenuItem(l, () => { p.onChange(v); setOpen(false) }) }
      val card  = overlayCard(theme, p.width, items)

      VFragment(
        Vector(
          trigger,
          popover(anchor, presence.mounted, amt, onDismiss = () => setOpen(false), trapFocus = true,
            card = card, placement = Placement(gap = 4)),
        ),
      )
    }

  /** A dropdown select: a field-styled trigger showing the current choice, which opens a menu of
    * `options` (each a `value -> label` pair) on click or keyboard. It is **controlled** — the
    * caller owns `selected` (the chosen value) and is told the new value through `onChange`. When
    * `selected` matches no option the `placeholder` shows, muted, as a prompt. The dropdown
    * portals into the overlay layer just below the trigger (flipping above near the bottom edge),
    * dismisses on an outside click or Escape, and traps focus while open — the same anchored-
    * overlay mechanism as [[Menu]]. `width` fixes both the trigger and the dropdown. */
  def Select(
      options:     Seq[(String, String)],
      selected:    String,
      onChange:    String => Unit,
      placeholder: String = "Select…",
      width:       Double = 200,
      exitMs:      Int    = 150,
  ): VNode =
    SelectImpl(SelectProps(options, selected, onChange, placeholder, width, exitMs))

  private case class ContextMenuProps(
      width:     Double,
      placement: Placement,
      trigger:   VNode,
      items:     (() => Unit) => Seq[VNode],
  )

  private val ContextMenuImpl: Component[ContextMenuProps] =
    component[ContextMenuProps] { p =>
      val theme              = useTheme()
      val (open, setOpen, _) = useState(false)
      val (pt, setPt, _)     = useState(Offset.zero)
      val anchor             = useRef[RenderObject | Null](null) // unused: the popover anchors at `pt`
      val presence           = usePresence(open, 150)
      val amt                = useTransition(if presence.phase == PresencePhase.Open then 1.0 else 0.0, 150)

      // The items are built against a `close` callback so a selected item can dismiss the menu —
      // the menu owns its own open state (a left-click never opens it), so the caller has no
      // open flag to flip; `close` is how it reaches in.
      val card = overlayCard(theme, p.width, p.items(() => setOpen(false)))

      // The trigger sits in normal flow; a right-press (button 3) records the cursor point and
      // opens the menu there. Other buttons pass through untouched.
      val wrapped = box(
        onMouseDown = e => if e.button == 3 then { setPt(e.position); setOpen(true) },
      )(p.trigger)

      // The point stays pinned for as long as the menu is mounted — including the exit fade after
      // a close — so the card animates out where it opened rather than snapping to the top-left
      // (the anchor ref is a placeholder; only `point` ever positions this popover).
      VFragment(
        Vector(
          wrapped,
          popover(anchor, presence.mounted, amt, onDismiss = () => setOpen(false), trapFocus = true,
            card = card, placement = p.placement, point = if presence.mounted then pt else null),
        ),
      )
    }

  /** A right-click context menu around a `trigger`. A right-press anywhere on the trigger opens a
    * menu **at the cursor** (not beside the trigger); a left-click passes through untouched. The
    * menu portals into the overlay layer, dismisses on an outside click or Escape, and traps focus
    * while open. The `items` are built from a `close` callback so a selected item can close the
    * menu — fill them with [[MenuItem]]s whose `onSelect` does its work then calls `close`:
    *
    * ```scala
    * contextMenu()(text("right-click me")) { close =>
    *   Seq(
    *     MenuItem("Cut",  () => { cut();  close() }),
    *     MenuItem("Copy", () => { copy(); close() }),
    *   )
    * }
    * ```
    */
  def contextMenu(
      width:     Double    = 200,
      placement: Placement = Placement(),
  )(trigger: VNode)(items: (() => Unit) => Seq[VNode]): VNode =
    ContextMenuImpl(ContextMenuProps(width, placement, trigger, items))

  // The tooltip implementation. Props: the label, the hover delay before it shows, and the
  // exit-fade duration. The trigger comes as the children.
  private val TooltipImpl: ContainerP[(String, Int, Int, Placement)] =
    container[(String, Int, Int, Placement)] { (props, children) =>
      val (label, delayMs, exitMs, placement) = props
      val theme                    = useTheme()
      val anchor                   = useRef[RenderObject | Null](null)
      val (hover, setHover, _)     = useState(false)

      // Show after the pointer has rested on the trigger for `delayMs` (hover intent), and let
      // the same debounce settle a brief unhover so it does not flicker.
      val shown    = useDebouncedValue(hover, delayMs)
      val presence = usePresence(shown, exitMs)
      val amt      = useTransition(if presence.phase == PresencePhase.Open then 1.0 else 0.0, exitMs)

      val card = box(
        bg      = theme.surfaceText,
        radius  = theme.radius * 0.75,
        padding = EdgeInsets.symmetric(horizontal = theme.spacing, vertical = theme.spacing * 0.5),
      )(
        text(label, color = theme.surface),
      )

      // The trigger stays in normal flow, wrapped so it carries the anchor ref and the hover
      // handlers; the tooltip itself portals out through the popover.
      VFragment(
        Vector(
          box(ref = anchor, onMouseEnter = _ => setHover(true), onMouseLeave = _ => setHover(false))(children*),
          popover(anchor, presence.mounted, amt, onDismiss = null, trapFocus = false, card = card, placement = placement),
        ),
      )
    }

  /** A tooltip: a small label that appears beside its trigger on hover. Wrap the trigger as the
    * child; the tooltip attaches the hover tracking and an anchor itself, so callers need wire
    * nothing. It portals into the overlay layer and floats just below the trigger (flipping and
    * sliding to stay on-screen), and is **click-through** — it never intercepts a click meant
    * for what is underneath. It shows after a short hover `delayMs` and fades on both ends
    * through [[usePresence]] + [[useTransition]]. With no overlay layer available it shows
    * nothing (the trigger still renders). Pass a [[Placement]] to prefer a different side (a
    * tooltip often reads better above its trigger) or add a gap. */
  def Tooltip(
      label:     String,
      delayMs:   Int       = 400,
      exitMs:    Int       = 120,
      placement: Placement = Placement(),
  )(trigger: VNode*): VNode =
    TooltipImpl((label, delayMs, exitMs, placement))(trigger*)
