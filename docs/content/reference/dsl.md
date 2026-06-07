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
    bg:          Color | Null     = null,
    border:      Color | Null     = null,
    borderWidth: Double           = 0.0,
    width:       Double           = Double.NaN,
    height:      Double           = Double.NaN,
    padding:     EdgeInsets | Null = null,
    flex:        Int              = 0,
    focusable:   Boolean          = false,
    // pointer / wheel / key / focus handlers — see the Input guide
    onClick: (PointerEvent => Unit) | Null = null,
    /* onMouseDown, onMouseUp, onMouseMove, onMouseEnter, onMouseLeave,
       onWheel, onKeyDown, onKeyUp, onFocus, onBlur */
)(children: VNode*): VNode
```

`bg` / `border` / `borderWidth` give it appearance; `width` / `height` fix its size (omit
to fill a tight parent or wrap a loose one); `padding` insets its child; `flex` makes it
expand inside a row/column. See the [input guide](/guide/input/) for the handlers.

## text

```scala
def text(content: String, size: Double = 16.0, color: Color = Color(0, 0, 0)): VNode
```

A single line of text in `color` at point `size`. It measures and paints as one line
through the installed `TextMeasurer` and the canvas.

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

`Color` provides `black`, `white`, `transparent`, and `Color.rgb(hex)` for packed
literals.
