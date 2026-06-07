package io.github.edadma.suit

// The overlay environment: what a widget needs from the runtime to project itself above
// the application. Overlay widgets (dialogs, and later menus and tooltips) do not draw
// in place — they portal their content into a dedicated overlay layer that paints on top
// of everything and is hit before it, and a modal additionally traps keyboard focus. Both
// the overlay layer and the focus manager live in the runtime, so they are handed down the
// tree through context rather than reached for globally, the same way the theme is.

/** The handle an overlay widget reads from [[OverlayContext]]: the [[RenderOverlay]] layer to
  * portal content into, and the [[FocusManager]] a modal uses to trap focus. `Suit.run`
  * provides a populated value; outside a running app — or a test that does not wire one — both
  * are null and overlay widgets render nothing rather than failing. */
final case class OverlayEnv(overlay: RenderObject | Null, focus: FocusManager | Null)

object OverlayEnv:
  /** No overlay layer available — the context default. */
  val empty: OverlayEnv = OverlayEnv(null, null)

/** The context the [[OverlayEnv]] travels in. The runtime sets it once around the whole app;
  * overlay widgets read it with [[useOverlay]]. */
val OverlayContext: Context[OverlayEnv] = createContext(OverlayEnv.empty)

/** The overlay environment in scope — the runtime's overlay layer and focus manager, or an
  * [[OverlayEnv.empty]] with no overlay available. Overlay widgets call this to find where to
  * portal their content. */
def useOverlay()(using Hooks): OverlayEnv = useContext(OverlayContext)
