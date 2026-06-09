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
    textWeight:    Int                 = 0,       // 0 = inherit; 100–900 (see FontWeight)
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

`textColor` / `textSize` / `textWeight` seed a **text-style cascade**: descendant `text` that
doesn't fix its own colour, size, or weight inherits these, CSS-style, from the nearest ancestor
that set them. `textWeight` is a numeric weight (`100`–`900`, see `FontWeight`) driven through
the bundled variable font's `wght` axis.
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
    weight:   Int          = 0,                  // 0 = inherit; 100–900 (see FontWeight)
    align:    TextAlign    = TextAlign.Left,     // Left | Center | Right
    maxLines: Int          = 1,                  // 1 = single line; 0 = unlimited
    overflow: TextOverflow = TextOverflow.Clip,  // Clip | Ellipsis
    softWrap: Boolean      = true,
): VNode
```

A run of text. `size`, `color`, and `weight` are **optional**: omit any and it is inherited from
the nearest enclosing `box` that sets `textSize` / `textColor` / `textWeight`, falling back to the
default text style if nothing in the tree sets one. `weight` is a numeric font weight (`100`–`900`,
e.g. `FontWeight.Bold`) rendered through the bundled variable font's `wght` axis — one Inter file
serves every weight.

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
text("heavier", weight = FontWeight.Bold)        // 700; FontWeight has Thin…Black (100–900)
```

## svg

```scala
def svg(image: SvgImage, width: Double = Double.NaN, height: Double = Double.NaN): VNode
```

A scalable vector image. It sizes to `width` / `height` when given, otherwise to the SVG's own
intrinsic size, each clamped to the constraints; being vectors, it stays crisp at any size —
librsvg renders it straight into the Cairo context, with no intermediate raster. It is a leaf, so
wrap it in a `box` to give an icon a background, padding, or a click handler.

Load an `SvgImage` from the platform loader (native `Svg`):

```scala
val icon = Svg.fromString("""<svg viewBox="0 0 64 64">…</svg>""") // or Svg.fromFile(path)

svg(icon, width = 24, height = 24)   // the one document, drawn crisp at any size
svg(icon, width = 72, height = 72)
```

`Svg.fromString` / `Svg.fromFile` / `Svg.fromBytes` throw `RsvgException` on a parse error. The
loader is native-only (it wraps librsvg); `SvgImage` itself is platform-neutral, so a headless
test can supply its own stand-in.

## image

```scala
def image(image: RasterImage, width: Double = Double.NaN, height: Double = Double.NaN): VNode
```

A raster (bitmap) image. It sizes to `width` / `height` when given, otherwise to the image's own
pixel size, each clamped to the constraints, and scales to whatever box it ends up in. It is a
leaf, so wrap it in a `box` for a background, padding, rounded corners (`clip = true`), or a click
handler.

Load a `RasterImage` from the platform loader (native `Raster`), which decodes JPEG through
turbojpeg (libjpeg-turbo). PNG is handled separately by Cairo, so for raster the loader is your
JPEG path.

```scala
val photo = Raster.fromFile("photo.jpg")   // or Raster.fromBytes(bytes)

image(photo)                       // at the image's own pixel size
image(photo, width = 96, height = 96)
box(radius = 12, clip = true)(image(photo, width = 96, height = 96))  // rounded
```

`Raster.fromFile` / `Raster.fromBytes` throw if the image can't be decoded. The loader is
native-only (it wraps turbojpeg + Cairo); `RasterImage` itself is platform-neutral, so a headless
test can supply its own stand-in.

## canvas

```scala
def canvas(
    width:  Double = Double.NaN,
    height: Double = Double.NaN,
    ref:    Ref[RenderObject | Null] | Null = null,
    focusable: Boolean = false,
    // pointer / wheel / key handlers — see the Input guide
)(draw: (Canvas, Size) => Unit): VNode
```

A direct drawing surface — the toolkit's `<canvas>`. `draw` is handed the **same `Canvas`** the
built-in widgets paint through, plus the surface's `Size`, and issues the drawing for the current
frame. It draws in a **local** coordinate space whose origin is the canvas's own top-left
(`0..width` × `0..height`), and the drawing is clipped to the canvas's bounds, so it cannot spill
past the edges. It sizes to `width` / `height` when given, otherwise **fills** the space its
parent offers.

It is a leaf in the render tree, but it still takes pointer, wheel, and key handlers (and
`focusable`), so an interactive surface — a sim you can click into, a sketch pad — works. Because
the same `Canvas` seam backs it, the draw routine is testable against a `RecordingCanvas` exactly
as suit's own widgets are.

```scala
canvas(width = 400, height = 300) { (c, size) =>
  c.fillRect(Rect(0, 0, size.width, size.height), Color.rgb(0x101418))
  c.fillCircle(Offset(size.width / 2, size.height / 2), 40, Color.rgb(0x6c5ce7))
}
```

The `Canvas` both draws and **measures** text: `c.drawText(origin, text, style)` places a line
with its top-left at `origin`, and `c.measureText(text, style)` returns the line's `Size` without
drawing — using the same measurer, so the two agree. That lets a painter centre its own labels:

```scala
canvas(height = 120) { (c, size) =>
  val style = TextStyle(48, Color.white, FontWeight.Bold)
  val m     = c.measureText("PAUSED", style)
  c.drawText(Offset((size.width - m.width) / 2, (size.height - m.height) / 2), "PAUSED", style)
}
```

For vector outlines, build a `Path` (move/line/arc/close, or `Path.polyline` for a point list) and
`strokePath`/`fillPath` it — one stroked path, so corners join cleanly, unlike stroking each edge
with a separate `line`:

```scala
canvas(width = 120, height = 120) { (c, _) =>
  val ship = Path.polyline(Seq(Offset(60, 20), Offset(80, 90), Offset(60, 75), Offset(40, 90)))
  c.strokePath(ship, Color.white, 2.0, LineJoin.Round)
}
```

To **animate**, drive it with [`useFrame`](#useframe): keep the changing state in a `useRef`,
advance it in the callback, and read it back in `draw`.

```scala
val phase = useRef(0.0)
useFrame(_ => phase.current += 0.03)
canvas(height = 120) { (c, size) =>
  val x = size.width / 2 + math.cos(phase.current) * 40
  c.fillCircle(Offset(x, size.height / 2), 8, Color.rgb(0x9c6bff))
}
```

### useFrame

```scala
def useFrame(cb: Double => Unit)(using Hooks): Unit
```

suit's `requestAnimationFrame`: `cb` runs once per frame for as long as the calling component is
mounted, receiving the current time in milliseconds (the same clock the motion hooks ease over).
After each tick it requests a repaint, so a `canvas` whose `draw` reads state the callback advanced
re-runs that frame. It advances state **imperatively** and repaints — it does *not* re-render the
component, so there is no reconcile per frame; hold the animated state in a `useRef` (whose
identity is stable, so the `draw` closure reads it without changing). If the callback also needs
the vnode tree to change, call a `useState` setter from inside it as usual.

### useInterval

```scala
def useInterval(
    cb:          () => Unit,
    ms:          Int,
    enabled:     Boolean    = true,
    restartKeys: Array[Any] = Array(),
)(using Hooks): Unit
```

suit's `setInterval`: `cb` runs every `ms` milliseconds while the component is mounted, riding the
same timer seam the motion hooks use. `enabled` gates it — while `false` no timer is armed, so an
idle component leaves the clock idle rather than pinning it active. The interval re-arms from *now*
whenever `ms`, `enabled`, or any value in `restartKeys` changes, which lets a phase restart on
demand (the text-field caret bumps a counter in `restartKeys` so it stays solid for a full interval
after each keystroke). Unlike `useFrame` it does not request a repaint; the usual `cb` flips a
`useState`, which re-renders on its own.

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

## constrainedBox

```scala
def constrainedBox(maxWidth: Double = Double.NaN, maxHeight: Double = Double.NaN)(children: VNode*): VNode
```

Caps its child to a maximum *without* forcing it — the child sizes to its content but never
exceeds `maxWidth`/`maxHeight` (Flutter's `ConstrainedBox`). Omit an axis to leave it uncapped.
Use it to bound a block of wrapping text or a panel so it grows with its content up to a limit
rather than sprawling to the full width. (`sizedBox` pins an exact size; `constrainedBox` only
sets a ceiling.)

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
