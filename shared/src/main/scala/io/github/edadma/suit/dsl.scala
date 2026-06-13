package io.github.edadma.suit

// suit's declarative surface — the builders that produce vdom `VNode`s, the equivalent
// of riposte's HTML DSL but for the render-tree element vocabulary. Each builder emits
// a `VElement` whose tag selects a RenderObject kind and whose props are typed values
// carried by vdom's PropValue channel (never stringified): a `Color`, an `EdgeInsets`,
// an `Alignment`, a layout enum. The vocabulary is SwiftUI-flavoured and ours to
// shape; only the constraint protocol underneath is fixed.
//
// Configuration goes in the first parameter list, children in the second, so nesting
// reads cleanly: `col(spacing = 8)(box(...)(), spacer(), box(...)())`. Sizes are given
// as plain `Double`s with `Double.NaN` standing for "unset" (left to the layout to
// decide), which keeps call sites free of `Some(...)`.
object dsl:

  private def el(
      tag:      String,
      props:    Map[String, Prop],
      children: Seq[VNode],
      ref:      ElementRef | Null = null,
  ): VNode =
    VElement(tag, props, children.toVector, None, ref)

  private def sized(props: Map[String, Prop], name: String, v: Double): Map[String, Prop] =
    if v.isNaN then props else props.updated(name, PropValue(v))

  // Input handlers reach the application as typed events (`PointerEvent`, `ScrollEvent`,
  // `KeyEvent`). vdom carries a handler as `Any => Unit`, so each registration adapts
  // the typed callback by casting the delivered value — the routers only ever send the
  // matching event type under each key.
  private def typed[E](
      props: Map[String, Prop],
      event: String,
      fn:    (E => Unit) | Null,
  ): Map[String, Prop] =
    if fn == null then props
    else props.updated(s"on:$event", Handler((a: Any) => fn.asInstanceOf[E => Unit](a.asInstanceOf[E])))

  // Focus and blur carry no payload, so their callbacks are nullary.
  private def focus(
      props: Map[String, Prop],
      event: String,
      fn:    (() => Unit) | Null,
  ): Map[String, Prop] =
    if fn == null then props
    else props.updated(s"on:$event", Handler((_: Any) => fn.asInstanceOf[() => Unit]()))

  /** The styled container. `bg`/`border`/`borderWidth`/`radius`/`shadow`/`opacity` give it
    * appearance (a `Color` flows into `bg`/`border` as a solid paint, or pass a gradient);
    * `width`/`height` fix its size (omit to fill a tight parent or wrap a loose one);
    * `padding` insets its child; `flex` makes it expand inside a row/column; `onClick` fires
    * when a pointer-press hit-tests to it. Children render inside, after the padding.
    *
    * `radius` rounds all four corners uniformly; pass `corners` for per-corner control.
    * `textColor`/`textSize`/`textWeight` set a text-style cascade: descendant `text` that
    * doesn't fix its own colour/size/weight inherits these (CSS-like inheritance, see
    * [[TextStyleAttrs]]). `textWeight` is a numeric weight (100–900, see [[FontWeight]]).
    *
    * `ref` binds the live [[RenderObject]] into a `useRef` box once it mounts, so a parent can
    * read its laid-out position and size (an overlay anchored to a trigger does this).
    * `ignorePointer` makes the box and its subtree click-through (see
    * [[RenderObject.ignorePointer]]). */
  def box(
      bg:            Paint | Null                  = null,
      border:        Paint | Null                  = null,
      borderWidth:   Double                         = 0.0,
      radius:        Double                         = 0.0,
      corners:       BorderRadius | Null            = null,
      shadow:        Shadow | Null                  = null,
      opacity:       Double                         = 1.0,
      width:         Double                         = Double.NaN,
      height:        Double                         = Double.NaN,
      padding:       EdgeInsets | Null              = null,
      clip:          Boolean                        = false,
      ignorePointer: Boolean                        = false,
      textColor:     Color | Null                   = null,
      textSize:      Double                         = Double.NaN,
      textWeight:    Int                            = 0,
      flex:          Int                            = 0,
      focusable:     Boolean                        = false,
      acceptsText:   Boolean                        = false,
      ref:           Ref[RenderObject | Null] | Null = null,
      onClick:       (PointerEvent => Unit) | Null  = null,
      onMouseDown:  (PointerEvent => Unit) | Null  = null,
      onMouseUp:    (PointerEvent => Unit) | Null  = null,
      onMouseMove:  (PointerEvent => Unit) | Null  = null,
      onMouseEnter: (PointerEvent => Unit) | Null  = null,
      onMouseLeave: (PointerEvent => Unit) | Null  = null,
      onWheel:      (ScrollEvent => Unit)     | Null = null,
      onKeyDown:    (KeyEvent => Unit)        | Null = null,
      onKeyUp:      (KeyEvent => Unit)        | Null = null,
      onTextInput:  (TextInputEvent => Unit)  | Null = null,
      onFocus:      (() => Unit)              | Null = null,
      onBlur:       (() => Unit)              | Null = null,
  )(children: VNode*): VNode =
    var props = Map.empty[String, Prop]
    if bg != null then props = props.updated("bg", PropValue(bg))
    if border != null then props = props.updated("border", PropValue(border))
    if borderWidth != 0.0 then props = props.updated("borderWidth", PropValue(borderWidth))
    val br: BorderRadius | Null = if corners != null then corners else if radius != 0.0 then BorderRadius.all(radius) else null
    if br != null then props = props.updated("borderRadius", PropValue(br))
    if shadow != null then props = props.updated("shadow", PropValue(shadow))
    if opacity != 1.0 then props = props.updated("opacity", PropValue(opacity))
    props = sized(props, "width", width)
    props = sized(props, "height", height)
    if padding != null then props = props.updated("padding", PropValue(padding))
    if clip then props = props.updated("clip", PropValue(true))
    if ignorePointer then props = props.updated("ignorePointer", PropValue(true))
    if textColor != null then props = props.updated("textColor", PropValue(textColor))
    props = sized(props, "textSize", textSize)
    if textWeight != 0 then props = props.updated("textWeight", PropValue(textWeight))
    if flex != 0 then props = props.updated("flex", PropValue(flex))
    if focusable then props = props.updated("focusable", PropValue(true))
    if acceptsText then props = props.updated("acceptsText", PropValue(true))
    props = typed[PointerEvent](props, "click", onClick)
    props = typed[PointerEvent](props, "mousedown", onMouseDown)
    props = typed[PointerEvent](props, "mouseup", onMouseUp)
    props = typed[PointerEvent](props, "mousemove", onMouseMove)
    props = typed[PointerEvent](props, "mouseenter", onMouseEnter)
    props = typed[PointerEvent](props, "mouseleave", onMouseLeave)
    props = typed[ScrollEvent](props, "wheel", onWheel)
    props = typed[KeyEvent](props, "keydown", onKeyDown)
    props = typed[KeyEvent](props, "keyup", onKeyUp)
    props = typed[TextInputEvent](props, "textinput", onTextInput)
    props = focus(props, "focus", onFocus)
    props = focus(props, "blur", onBlur)
    val eref: ElementRef | Null = if ref != null then BoxRef(ref) else null
    el("box", props, children, eref)

  /** A run of text. `color`, `size`, and `weight` are optional: omit any and it is
    * **inherited** from the nearest enclosing container that sets that text property (`box`'s
    * `textColor`/`textSize`/`textWeight`), falling back to the [[TextStyle.default]] if
    * nothing in the tree sets one. `weight` is a numeric font weight (100–900, see
    * [[FontWeight]]) driven through the font's variable `wght` axis.
    *
    * It is single-line by default. Pass `maxLines` other than 1 (0 = unlimited) to wrap it
    * to the available width; `softWrap = false` then breaks only at explicit `\n`s. `overflow
    * = Ellipsis` trims the dropped tail and marks it with `…` (this also truncates a single
    * over-wide line); `align` positions each line within the measured block. */
  def text(
      content:  String,
      size:     Double       = Double.NaN,
      color:    Color | Null = null,
      weight:   Int          = 0,
      align:    TextAlign    = TextAlign.Left,
      maxLines: Int          = 1,
      overflow: TextOverflow = TextOverflow.Clip,
      softWrap: Boolean      = true,
  ): VNode =
    var props = Map[String, Prop]("content" -> PropValue(content))
    props = sized(props, "size", size)
    if color != null then props = props.updated("color", PropValue(color))
    if weight != 0 then props = props.updated("weight", PropValue(weight))
    if align != TextAlign.Left then props = props.updated("align", PropValue(align))
    if maxLines != 1 then props = props.updated("maxLines", PropValue(maxLines))
    if overflow != TextOverflow.Clip then props = props.updated("overflow", PropValue(overflow))
    if !softWrap then props = props.updated("softWrap", PropValue(false))
    el("text", props, Nil)

  /** A scalable vector image. It sizes to `width`/`height` when given, otherwise to the SVG's
    * own intrinsic size; being vectors it stays crisp at any size. Pass an [[SvgImage]] from the
    * platform loader (`Svg.fromString` / `Svg.fromFile` on Native). It is a leaf — wrap it in a
    * `box` to give an icon a background, padding, or click handler. */
  def svg(image: SvgImage, width: Double = Double.NaN, height: Double = Double.NaN): VNode =
    var props = Map[String, Prop]("image" -> PropValue(image))
    props = sized(props, "width", width)
    props = sized(props, "height", height)
    el("svg", props, Nil)

  /** A raster (bitmap) image. It sizes to `width`/`height` when given, otherwise to the image's
    * own pixel size, and scales to whatever box it ends up in. Pass a [[RasterImage]] from the
    * platform loader (`Raster.fromFile` / `Raster.fromBytes` on Native, which decodes JPEG/PNG/
    * etc.). It is a leaf — wrap it in a `box` for a background, padding, rounding (`clip = true`),
    * or a click handler. */
  def image(image: RasterImage, width: Double = Double.NaN, height: Double = Double.NaN): VNode =
    var props = Map[String, Prop]("image" -> PropValue(image))
    props = sized(props, "width", width)
    props = sized(props, "height", height)
    el("image", props, Nil)

  /** A direct drawing surface — the toolkit's `<canvas>`. `draw` is handed the same [[Canvas]]
    * suit's widgets paint through, plus the surface's [[Size]], and issues the drawing for the
    * current frame in a **local** coordinate space (origin at the canvas's top-left); the drawing
    * is clipped to the canvas's bounds. It sizes to `width`/`height` when given, otherwise fills
    * the space its parent offers. It is a leaf in the tree but takes pointer/key handlers, so an
    * interactive surface works; to animate, advance state in a `useFrame` callback (which
    * repaints) and read it in `draw`.
    *
    * ```scala
    * canvas(width = 400, height = 300) { (c, size) =>
    *   c.fillRect(Rect(0, 0, size.width, size.height), Color.rgb(0x101418))
    *   c.fillCircle(Offset(size.width / 2, size.height / 2), 40, Color.rgb(0x6c5ce7))
    * }
    * ```
    */
  def canvas(
      width:        Double                         = Double.NaN,
      height:       Double                         = Double.NaN,
      ref:          Ref[RenderObject | Null] | Null = null,
      onClick:      (PointerEvent => Unit) | Null  = null,
      onMouseDown:  (PointerEvent => Unit) | Null  = null,
      onMouseUp:    (PointerEvent => Unit) | Null  = null,
      onMouseMove:  (PointerEvent => Unit) | Null  = null,
      onMouseEnter: (PointerEvent => Unit) | Null  = null,
      onMouseLeave: (PointerEvent => Unit) | Null  = null,
      onWheel:      (ScrollEvent => Unit)     | Null = null,
      onKeyDown:    (KeyEvent => Unit)        | Null = null,
      onKeyUp:      (KeyEvent => Unit)        | Null = null,
      focusable:    Boolean                        = false,
  )(draw: (Canvas, Size) => Unit): VNode =
    var props = Map[String, Prop]("draw" -> PropValue(draw))
    props = sized(props, "width", width)
    props = sized(props, "height", height)
    if focusable then props = props.updated("focusable", PropValue(true))
    props = typed[PointerEvent](props, "click", onClick)
    props = typed[PointerEvent](props, "mousedown", onMouseDown)
    props = typed[PointerEvent](props, "mouseup", onMouseUp)
    props = typed[PointerEvent](props, "mousemove", onMouseMove)
    props = typed[PointerEvent](props, "mouseenter", onMouseEnter)
    props = typed[PointerEvent](props, "mouseleave", onMouseLeave)
    props = typed[ScrollEvent](props, "wheel", onWheel)
    props = typed[KeyEvent](props, "keydown", onKeyDown)
    props = typed[KeyEvent](props, "keyup", onKeyUp)
    val eref: ElementRef | Null = if ref != null then BoxRef(ref) else null
    el("canvas", props, Nil, eref)

  /** A wrapper around an application-owned image surface — the retained counterpart to [[canvas]].
    * Where a canvas hands you suit's [[Canvas]] each frame, a surface lets you keep your *own*
    * drawing surface, draw into it whenever and however you like (with the full underlying graphics
    * API, e.g. raw Cairo on the native backend), and have suit blit it to the screen. Pass a
    * [[RasterImage]] that wraps the surface (`CairoBitmap.wrap(surface)` on Native) and a
    * [[SurfaceHandle]]; after each redraw call `handle.repaint()` to composite the new pixels.
    *
    * It re-blits only when poked, not every frame, so a static richly-drawn panel is cheap. It
    * sizes to `width`/`height` when given, otherwise to the surface's pixel size, and is a leaf
    * that takes pointer/key handlers so an interactive panel works. For a sharp result on a HiDPI
    * display, make the surface in device pixels (see [[DevicePixelRatio]]) and pass logical
    * `width`/`height`.
    *
    * ```scala
    * val sx   = DevicePixelRatio.scaleX
    * val surf = imageSurfaceCreate(Format.ARGB32, (400 * sx).toInt, (300 * sx).toInt)
    * val img  = CairoBitmap.wrap(surf)
    * val h    = useRef(new SurfaceHandle).current
    *
    * def redraw(): Unit =
    *   val cr = surf.create
    *   cr.scale(sx, sx)         // draw in logical units
    *   // ... full raw Cairo ...
    *   cr.destroy()
    *   surf.flush(); surf.markDirty()
    *   h.repaint()
    *
    * surface(img, h, width = 400, height = 300)
    * ```
    */
  def surface(
      image:        RasterImage,
      handle:       SurfaceHandle | Null            = null,
      width:        Double                          = Double.NaN,
      height:       Double                          = Double.NaN,
      ref:          Ref[RenderObject | Null] | Null = null,
      onClick:      (PointerEvent => Unit) | Null   = null,
      onMouseDown:  (PointerEvent => Unit) | Null   = null,
      onMouseUp:    (PointerEvent => Unit) | Null   = null,
      onMouseMove:  (PointerEvent => Unit) | Null   = null,
      onMouseEnter: (PointerEvent => Unit) | Null   = null,
      onMouseLeave: (PointerEvent => Unit) | Null   = null,
      onWheel:      (ScrollEvent => Unit)     | Null = null,
      onKeyDown:    (KeyEvent => Unit)        | Null = null,
      onKeyUp:      (KeyEvent => Unit)        | Null = null,
      focusable:    Boolean                          = false,
  ): VNode =
    var props = Map[String, Prop]("image" -> PropValue(image))
    if handle != null then props = props.updated("handle", PropValue(handle))
    props = sized(props, "width", width)
    props = sized(props, "height", height)
    if focusable then props = props.updated("focusable", PropValue(true))
    props = typed[PointerEvent](props, "click", onClick)
    props = typed[PointerEvent](props, "mousedown", onMouseDown)
    props = typed[PointerEvent](props, "mouseup", onMouseUp)
    props = typed[PointerEvent](props, "mousemove", onMouseMove)
    props = typed[PointerEvent](props, "mouseenter", onMouseEnter)
    props = typed[PointerEvent](props, "mouseleave", onMouseLeave)
    props = typed[ScrollEvent](props, "wheel", onWheel)
    props = typed[KeyEvent](props, "keydown", onKeyDown)
    props = typed[KeyEvent](props, "keyup", onKeyUp)
    val eref: ElementRef | Null = if ref != null then BoxRef(ref) else null
    el("surface", props, Nil, eref)

  /** A horizontal stack. Children are laid left to right; flexible children share the
    * leftover width. See [[MainAxisAlignment]] / [[CrossAxisAlignment]] / [[MainAxisSize]]. */
  def row(
      mainAxisAlignment:  MainAxisAlignment  = MainAxisAlignment.Start,
      crossAxisAlignment: CrossAxisAlignment = CrossAxisAlignment.Start,
      mainAxisSize:       MainAxisSize       = MainAxisSize.Max,
      spacing:            Double             = 0.0,
      flex:               Int                = 0,
  )(children: VNode*): VNode =
    el("row", flexProps(mainAxisAlignment, crossAxisAlignment, mainAxisSize, spacing, flex), children)

  /** A vertical stack. Children are laid top to bottom; flexible children share the
    * leftover height. */
  def col(
      mainAxisAlignment:  MainAxisAlignment  = MainAxisAlignment.Start,
      crossAxisAlignment: CrossAxisAlignment = CrossAxisAlignment.Start,
      mainAxisSize:       MainAxisSize       = MainAxisSize.Max,
      spacing:            Double             = 0.0,
      flex:               Int                = 0,
  )(children: VNode*): VNode =
    el("col", flexProps(mainAxisAlignment, crossAxisAlignment, mainAxisSize, spacing, flex), children)

  private def flexProps(
      main:    MainAxisAlignment,
      cross:   CrossAxisAlignment,
      size:    MainAxisSize,
      spacing: Double,
      flex:    Int,
  ): Map[String, Prop] =
    var props = Map.empty[String, Prop]
    props = props.updated("mainAxisAlignment", PropValue(main))
    props = props.updated("crossAxisAlignment", PropValue(cross))
    props = props.updated("mainAxisSize", PropValue(size))
    if spacing != 0.0 then props = props.updated("spacing", PropValue(spacing))
    if flex != 0 then props = props.updated("flex", PropValue(flex))
    props

  /** A flexible empty gap — the replacement for flex-grow. Inside a row or column it
    * eats leftover space in proportion to `flex`, pushing its siblings apart. */
  def spacer(flex: Int = 1): VNode =
    el("box", Map("flex" -> PropValue(flex)), Nil)

  /** Insets its child by `insets` on each side. */
  def padding(insets: EdgeInsets)(children: VNode*): VNode =
    el("padding", Map("padding" -> PropValue(insets)), children)

  /** A fixed-size box with no appearance: forces `width`/`height` onto its child (or
    * occupies that size with no child). Omit an axis to leave it to the parent. */
  def sizedBox(width: Double = Double.NaN, height: Double = Double.NaN)(children: VNode*): VNode =
    el("sizedBox", sized(sized(Map.empty[String, Prop], "width", width), "height", height), children)

  /** Caps its child to a maximum size without forcing it: the child sizes to its content but
    * never exceeds `maxWidth`/`maxHeight` (Flutter's `ConstrainedBox`). Omit an axis to leave it
    * uncapped. Use it to bound a block of wrapping text or a dialog so it grows with its content
    * up to a limit rather than sprawling to the full width. */
  def constrainedBox(maxWidth: Double = Double.NaN, maxHeight: Double = Double.NaN)(children: VNode*): VNode =
    el("sizedBox", sized(sized(Map.empty[String, Prop], "maxWidth", maxWidth), "maxHeight", maxHeight), children)

  /** A scrolling viewport over its content along `axis` (vertical by default). The
    * viewport fills the space its parent gives it; the content takes its natural extent
    * along the scroll axis and is clipped to the viewport, so anything past the edges is
    * hidden rather than overflowing. The wheel scrolls it with no extra wiring — scroll
    * position lives on the viewport and persists across re-renders. Give it a single
    * content node (wrap several in a `col`/`row`). */
  def scrollView(axis: Axis = Axis.Vertical)(children: VNode*): VNode =
    el("scroll", Map("axis" -> PropValue(axis)), children)

  /** Places its child at the absolute pixel offset `(dx, dy)` within the space it is given,
    * laying the child out at its natural size (it may overflow). Fills that space, so dropped
    * into a full-window overlay it positions content at a screen point — what the anchored
    * overlays (menus, tooltips) use to sit beside their trigger. */
  def positioned(dx: Double, dy: Double)(children: VNode*): VNode =
    el("positioned", Map("dx" -> PropValue(dx), "dy" -> PropValue(dy)), children)

  /** A z-ordered overlay: children stack back-to-front, each positioned by `alignment`. */
  def stack(alignment: Alignment = Alignment.topLeft)(children: VNode*): VNode =
    el("stack", Map("alignment" -> PropValue(alignment)), children)

  /** Positions its child within the available space at `alignment` (a one-child stack). */
  def align(alignment: Alignment)(children: VNode*): VNode =
    el("stack", Map("alignment" -> PropValue(alignment)), children)

  /** Centres its child in the available space. */
  def center(children: VNode*): VNode =
    el("stack", Map("alignment" -> PropValue(Alignment.center)), children)
