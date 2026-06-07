---
title: "DSL"
weight: 1
---

```scala
import io.github.edadma.suit.dsl.*
```

The declarative surface — the builders that produce vdom `VNode`s, the render-tree
equivalent of riposte's HTML DSL. Each builder emits an element whose tag selects a
RenderObject kind and whose props are **typed values** (a `Color`, an `EdgeInsets`, an
`Alignment`, a layout enum), never strings.

Configuration goes in the first parameter list and children in the second, so nesting reads
cleanly:

```scala
col(spacing = 8)(
  box(bg = Color.rgb(0x222831))(text("hi")),
  spacer(),
  box(bg = Color.rgb(0x222831))(text("bye")),
)
```

Sizes are plain `Double`s; `Double.NaN` means "unset" (left to the layout), so call sites
stay free of `Some(...)`.

## box

The styled container — suit's workhorse rectangle.

```scala
def box(
    bg:          Paint | Null        = null,   // a Color flows in as a solid; or a gradient
    border:      Paint | Null        = null,
    borderWidth: Double              = 0.0,
    radius:      Double              = 0.0,    // uniform corner radius
    corners:     BorderRadius | Null = null,   // per-corner control (overrides radius)
    shadow:      Shadow | Null       = null,
    opacity:     Double              = 1.0,
    width:       Double              = Double.NaN,
    height:      Double              = Double.NaN,
    padding:     EdgeInsets | Null   = null,
    clip:          Boolean             = false,  // overflow hidden — clip children to the box
    ignorePointer: Boolean             = false,  // click-through: the box and subtree take no pointer
    textColor:     Color | Null        = null,   // text-style cascade — see below
    textSize:      Double              = Double.NaN,
    flex:          Int                 = 0,
    focusable:     Boolean             = false,
    acceptsText:   Boolean             = false,  // open text input while focused (text fields)
    ref: Ref[RenderObject | Null] | Null = null, // bind the live render object into a useRef box
    // pointer / wheel / key / focus handlers — see the Input guide
    onClick: (PointerEvent => Unit) | Null = null,
    /* onMouseDown, onMouseUp, onMouseMove, onMouseEnter, onMouseLeave,
       onWheel, onKeyDown, onKeyUp, onTextInput, onFocus, onBlur */
)(children: VNode*): VNode
```

`bg` / `border` give it a fill and outline — pass a `Color` (it converts to a solid paint)
or a `LinearGradient` / `RadialGradient`; `borderWidth` sets the outline weight. `radius`
rounds all four corners uniformly, or `corners` sets each independently; `shadow` casts a
drop shadow; `opacity` fades the whole box (children included). `width` / `height` fix its
size (omit to fill a tight parent or wrap a loose one); `padding` insets its child; `clip`
hides anything the children draw outside the box (rounded corners included); `flex` makes it
expand inside a row/column.

`textColor` / `textSize` seed a **text-style cascade**: descendant `text` that doesn't fix
its own colour or size inherits these, CSS-style, from the nearest ancestor that set them.
`ignorePointer` makes the box and its whole subtree transparent to hit-testing, so a click
passes straight through to whatever is behind (a floating tooltip uses this). `ref` binds the
live render object into a `useRef[RenderObject | Null](null)` box once it mounts, so a parent
can read its laid-out position and size — an overlay anchored to a trigger does exactly this.
See the [input guide](/guide/input/) for the handlers.

## text

```scala
def text(
    content:  String,
    size:     Double       = Double.NaN,
    color:    Color | Null = null,
    align:    TextAlign    = TextAlign.Left,     // Left | Center | Right
    maxLines: Int          = 1,                  // 1 = single line; 0 = unlimited
    overflow: TextOverflow = TextOverflow.Clip,  // Clip | Ellipsis
    softWrap: Boolean      = true,
): VNode
```

A run of text. `size` and `color` are **optional**: omit either and it is inherited from the
nearest enclosing `box` that sets `textSize` / `textColor`, falling back to the default text
style if nothing in the tree sets one.

It is **single-line by default**. Set `maxLines` to something other than `1` (use `0` for
unlimited) and it **word-wraps** to the width the layout gives it, breaking at spaces and
hard-breaking any single word too wide for a line; explicit `\n`s always start a new line, and
`softWrap = false` breaks *only* at those. `maxLines` caps the number of lines; `overflow =
TextOverflow.Ellipsis` then trims the dropped tail and marks it with `…` (it also truncates a
single over-wide line). `align` positions each line horizontally within the measured block. The
lines are computed once during layout and replayed by paint, so the two passes always agree.

```scala
text(
  "A long paragraph that wraps across as many lines as it needs.",
  maxLines = 0,
)
text("Capped at two lines, the rest trimmed…", maxLines = 2, overflow = TextOverflow.Ellipsis)
text("centred", align = TextAlign.Center)
```

## scrollView

```scala
def scrollView(axis: Axis = Axis.Vertical)(children: VNode*): VNode
```

A scrolling viewport over its content. The viewport fills the space its parent gives it; the
content takes its natural extent along the scroll axis and is **clipped** to the viewport, so
anything past the edges is hidden rather than overflowing. The wheel scrolls it with no extra
wiring — the scroll position lives on the viewport and persists across re-renders. Give it a
single content node (wrap several in a `col` / `row`).

```scala
scrollView(Axis.Vertical)(
  col(crossAxisAlignment = CrossAxisAlignment.Stretch, spacing = 12)(
    items.map(card)*,
  ),
)
```

## row / col

```scala
def row(
    mainAxisAlignment:  MainAxisAlignment  = MainAxisAlignment.Start,
    crossAxisAlignment: CrossAxisAlignment = CrossAxisAlignment.Start,
    mainAxisSize:       MainAxisSize       = MainAxisSize.Max,
    spacing:            Double             = 0.0,
    flex:               Int                = 0,
)(children: VNode*): VNode

def col(/* same parameters */)(children: VNode*): VNode
```

A horizontal (`row`) or vertical (`col`) stack. Children are laid along the main axis;
flexible children share the leftover space. The enums:

- `MainAxisAlignment` — `Start`, `End`, `Center`, `SpaceBetween`, `SpaceAround`, `SpaceEvenly`
- `CrossAxisAlignment` — `Start`, `End`, `Center`, `Stretch`
- `MainAxisSize` — `Min` (wrap children), `Max` (fill parent)

See the [layout guide](/guide/layout/) for how the two passes distribute space.

## spacer

```scala
def spacer(flex: Int = 1): VNode
```

A flexible empty gap — the replacement for flex-grow. Inside a row or column it eats
leftover space in proportion to `flex`, pushing its siblings apart.

## padding

```scala
def padding(insets: EdgeInsets)(children: VNode*): VNode
```

Insets its child by `insets` on each side.

## sizedBox

```scala
def sizedBox(width: Double = Double.NaN, height: Double = Double.NaN)(children: VNode*): VNode
```

A fixed-size box with no appearance: forces `width`/`height` onto its child (or occupies
that size with no child). Omit an axis to leave it to the parent.

## stack / align / center

```scala
def stack(alignment: Alignment = Alignment.topLeft)(children: VNode*): VNode
def align(alignment: Alignment)(children: VNode*): VNode
def center(children: VNode*): VNode
```

`stack` is a z-ordered overlay: children stack back-to-front, each positioned by
`alignment`. `align` positions a single child at an alignment (a one-child stack); `center`
is `align(Alignment.center)`.

## positioned

```scala
def positioned(dx: Double, dy: Double)(children: VNode*): VNode
```

Places its child at the absolute pixel offset `(dx, dy)` within the space it is given, laying
the child out at its **natural size** (it may overflow). It fills that space, so dropped into a
full-window overlay it positions content at a screen point — which is how the anchored overlays
(`Menu`, `Tooltip`) sit beside their trigger.

## Geometry & colour values

The DSL takes these plain value types (all pure, no SDL dependency):

```scala
Offset(x, y)                         // a 2-D position or displacement
Size(width, height)
Rect(x, y, width, height)            // .contains(px, py), right/bottom-exclusive
EdgeInsets(top, right, bottom, left) // .all(v), .symmetric(horizontal, vertical)
Alignment(x, y)                      // fractional: (-1,-1) topLeft … (1,1) bottomRight
Color(r, g, b, a = 255)              // .rgb(0xRRGGBB), .parse("#rrggbb[aa]"), .toHex, .withAlpha
```

`Color` provides `black`, `white`, `transparent`, `Color.rgb(hex)` for packed literals, and
`Color.lerp(a, b, t)` to blend two colours (what the motion hooks animate over).

The styling value types `box` paints with:

```scala
Solid(color)                                   // a flat fill
LinearGradient(stops, begin, end)              // stops: Seq[ColorStop]; begin/end: Alignment
RadialGradient(stops, center, radius)
ColorStop(offset, color)                       // offset 0..1 along the gradient
BorderRadius(tl, tr, br, bl)                   // .all(v), .top(v), .bottom(v), .zero
Shadow(color, offset, blur, spread = 0)        // a drop shadow
```

A bare `Color` converts to `Solid` automatically wherever a `Paint` is expected.
