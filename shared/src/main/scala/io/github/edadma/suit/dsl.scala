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

  private def el(tag: String, props: Map[String, Prop], children: Seq[VNode]): VNode =
    VElement(tag, props, children.toVector, None)

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
    * `textColor`/`textSize` set a text-style cascade: descendant `text` that doesn't fix
    * its own colour/size inherits these (CSS-like inheritance, see [[TextStyleAttrs]]). */
  def box(
      bg:           Paint | Null                  = null,
      border:       Paint | Null                  = null,
      borderWidth:  Double                         = 0.0,
      radius:       Double                         = 0.0,
      corners:      BorderRadius | Null            = null,
      shadow:       Shadow | Null                  = null,
      opacity:      Double                         = 1.0,
      width:        Double                         = Double.NaN,
      height:       Double                         = Double.NaN,
      padding:      EdgeInsets | Null              = null,
      clip:         Boolean                        = false,
      textColor:    Color | Null                   = null,
      textSize:     Double                         = Double.NaN,
      flex:         Int                            = 0,
      focusable:    Boolean                        = false,
      acceptsText:  Boolean                        = false,
      onClick:      (PointerEvent => Unit) | Null  = null,
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
    if textColor != null then props = props.updated("textColor", PropValue(textColor))
    props = sized(props, "textSize", textSize)
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
    el("box", props, children)

  /** A run of text. `color` and `size` are optional: omit either and it is **inherited**
    * from the nearest enclosing container that sets a text colour/size (`box`'s
    * `textColor`/`textSize`), falling back to the [[TextStyle.default]] if nothing in the
    * tree sets one. Single-line: it measures and paints as one line through the installed
    * [[TextMeasurer]] and the canvas. */
  def text(
      content: String,
      size:    Double       = Double.NaN,
      color:   Color | Null = null,
  ): VNode =
    var props = Map[String, Prop]("content" -> PropValue(content))
    props = sized(props, "size", size)
    if color != null then props = props.updated("color", PropValue(color))
    el("text", props, Nil)

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

  /** A scrolling viewport over its content along `axis` (vertical by default). The
    * viewport fills the space its parent gives it; the content takes its natural extent
    * along the scroll axis and is clipped to the viewport, so anything past the edges is
    * hidden rather than overflowing. The wheel scrolls it with no extra wiring — scroll
    * position lives on the viewport and persists across re-renders. Give it a single
    * content node (wrap several in a `col`/`row`). */
  def scrollView(axis: Axis = Axis.Vertical)(children: VNode*): VNode =
    el("scroll", Map("axis" -> PropValue(axis)), children)

  /** A z-ordered overlay: children stack back-to-front, each positioned by `alignment`. */
  def stack(alignment: Alignment = Alignment.topLeft)(children: VNode*): VNode =
    el("stack", Map("alignment" -> PropValue(alignment)), children)

  /** Positions its child within the available space at `alignment` (a one-child stack). */
  def align(alignment: Alignment)(children: VNode*): VNode =
    el("stack", Map("alignment" -> PropValue(alignment)), children)

  /** Centres its child in the available space. */
  def center(children: VNode*): VNode =
    el("stack", Map("alignment" -> PropValue(Alignment.center)), children)
