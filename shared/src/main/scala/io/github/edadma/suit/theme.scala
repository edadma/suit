package io.github.edadma.suit

// The theme: design tokens plus the context that carries them down the tree.
//
// Styling in suit is a general system, not a look baked into each control. A `Theme`
// is a flat record of named values — colours, a base spacing unit, a default corner
// radius, a base text size — and the built-in widgets paint *only* from the theme they
// read out of context. Swapping the provided theme restyles every stock control at
// once, the way changing a stylesheet restyles a web page, with no widget code touched.
//
// This is the colour/metric counterpart to the text cascade (see [[TextStyleAttrs]]):
// the theme flows through vdom's context (an explicit provider boundary), while text
// style flows through the render tree (implicit, nearest-ancestor inheritance). Both
// keep styling out of the widgets and resolvable off-device.

/** The design tokens the widget library paints from. An application overrides any of
  * these and wraps its UI in a [[ThemeProvider]] to retheme every built-in control.
  *
  * The colour roles follow a small surface/primary/accent palette: `primary` (and its
  * hover/active shades) is the call-to-action fill with `onPrimary` as its readable ink;
  * `surface` is a panel/control background with `surfaceText` as its default ink and
  * `border` as its outline; `accent` marks selection (a checked box, a slider thumb) and
  * `track` is an inactive groove. The metric tokens — `radius`, `spacing`, `textSize` —
  * are the defaults controls round, pad, and size their text by. */
final case class Theme(
    primary:       Color,
    primaryHover:  Color,
    primaryActive: Color,
    onPrimary:     Color,
    surface:       Color,
    surfaceText:   Color,
    border:        Color,
    accent:        Color,
    track:         Color,
    radius:        Double,
    spacing:       Double,
    textSize:      Double,
)

object Theme:
  /** The stock dark theme — a blue primary on dark surfaces. It is the context default,
    * so widgets used without a [[ThemeProvider]] still look consistent. */
  val default: Theme = Theme(
    primary       = Color.rgb(0x4dabf7),
    primaryHover  = Color.rgb(0x74c0fc),
    primaryActive = Color.rgb(0x339af0),
    onPrimary     = Color.rgb(0x0b1418),
    surface       = Color.rgb(0x2b3035),
    surfaceText   = Color.rgb(0xf1f3f5),
    border        = Color.rgb(0x495057),
    accent        = Color.rgb(0x4dabf7),
    track         = Color.rgb(0x495057),
    radius        = 6.0,
    spacing       = 8.0,
    textSize      = 16.0,
  )

/** The context the active [[Theme]] travels in. [[useTheme]] reads it; [[ThemeProvider]]
  * sets it for a subtree. Its default is [[Theme.default]], so a read with no enclosing
  * provider yields the stock theme rather than failing. */
val ThemeContext: Context[Theme] = createContext(Theme.default)

/** Wrap a subtree so its widgets paint from `theme`. Nesting overrides for the inner
  * part of the tree, exactly like a nested stylesheet scope. */
def ThemeProvider(theme: Theme)(children: VNode*): VNode =
  val child = children match
    case Seq(one) => one
    case many     => VFragment(many.toVector)
  ThemeContext.provide(theme, child)

/** The active theme at this point in the tree — the nearest enclosing [[ThemeProvider]]'s
  * value, or [[Theme.default]] if there is none. Widgets call this instead of hard-coding
  * colours, so they restyle with the provided theme. */
def useTheme()(using Hooks): Theme = useContext(ThemeContext)
