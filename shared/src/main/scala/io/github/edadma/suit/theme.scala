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
  * `background` is the app body behind everything, `surface` is an elevated panel/control
  * on top of it with `surfaceText` as its default ink and `border` as its outline; `accent`
  * marks selection (a checked box, a slider thumb) and `track` is an inactive groove. The
  * status roles — `info`/`success`/`warning`/`danger` — colour a callout or badge by meaning
  * rather than by hand. The metric tokens — `radius`, `spacing`, `textSize` — are the defaults
  * controls round, pad, and size their text by. `isDark` records the colour scheme so an app
  * (or a future control) can branch on it — pick an icon variant, soften a shadow — without
  * inspecting luminance. */
final case class Theme(
    primary:       Color,
    primaryHover:  Color,
    primaryActive: Color,
    onPrimary:     Color,
    background:    Color,
    surface:       Color,
    surfaceText:   Color,
    border:        Color,
    accent:        Color,
    track:         Color,
    info:          Color,
    success:       Color,
    warning:       Color,
    danger:        Color,
    radius:        Double,
    spacing:       Double,
    textSize:      Double,
    isDark:        Boolean,
)

object Theme:
  // A dark-scheme theme from its four accent-driven colours; the neutral surfaces and the
  // status palette are shared by every dark built-in, so only the brand colours vary.
  private def darkScheme(primary: Color, primaryHover: Color, primaryActive: Color, accent: Color): Theme =
    Theme(
      primary       = primary,
      primaryHover  = primaryHover,
      primaryActive = primaryActive,
      onPrimary     = Color.rgb(0x0b1418),
      background    = Color.rgb(0x1a1d20),
      surface       = Color.rgb(0x2b3035),
      surfaceText   = Color.rgb(0xf1f3f5),
      border        = Color.rgb(0x495057),
      accent        = accent,
      track         = Color.rgb(0x495057),
      info          = Color.rgb(0x4dabf7),
      success       = Color.rgb(0x51cf66),
      warning       = Color.rgb(0xffd43b),
      danger        = Color.rgb(0xff6b6b),
      radius        = 6.0,
      spacing       = 8.0,
      textSize      = 16.0,
      isDark        = true,
    )

  // A light-scheme theme: white surfaces on a faint-grey body, dark ink, and deeper status
  // colours that keep their contrast against white.
  private def lightScheme(primary: Color, primaryHover: Color, primaryActive: Color, accent: Color): Theme =
    Theme(
      primary       = primary,
      primaryHover  = primaryHover,
      primaryActive = primaryActive,
      onPrimary     = Color.white,
      background    = Color.rgb(0xf1f3f5),
      surface       = Color.white,
      surfaceText   = Color.rgb(0x212529),
      border        = Color.rgb(0xdee2e6),
      accent        = accent,
      track         = Color.rgb(0xdee2e6),
      info          = Color.rgb(0x228be6),
      success       = Color.rgb(0x2f9e44),
      warning       = Color.rgb(0xf08c00),
      danger        = Color.rgb(0xe03131),
      radius        = 6.0,
      spacing       = 8.0,
      textSize      = 16.0,
      isDark        = false,
    )

  /** The stock dark theme — a blue primary on dark surfaces. */
  val dark: Theme = darkScheme(Color.rgb(0x4dabf7), Color.rgb(0x74c0fc), Color.rgb(0x339af0), Color.rgb(0x4dabf7))

  /** The stock light theme — a blue primary on white surfaces. The light counterpart to [[dark]]. */
  val light: Theme = lightScheme(Color.rgb(0x228be6), Color.rgb(0x1c7ed6), Color.rgb(0x1971c2), Color.rgb(0x228be6))

  /** A violet-accented dark theme. */
  val violetDark: Theme = darkScheme(Color.rgb(0x9775fa), Color.rgb(0xb197fc), Color.rgb(0x845ef7), Color.rgb(0x9775fa))

  /** A violet-accented light theme — the light counterpart to [[violetDark]]. */
  val violetLight: Theme = lightScheme(Color.rgb(0x7048e8), Color.rgb(0x6741d9), Color.rgb(0x5f3dc4), Color.rgb(0x7048e8))

  /** The context default — the stock [[dark]] theme, so widgets used without a [[ThemeProvider]]
    * still look consistent. */
  val default: Theme = dark

  /** Every built-in theme, light and dark across the stock accents — handy for a theme picker. */
  val builtIns: List[Theme] = List(dark, light, violetDark, violetLight)

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
