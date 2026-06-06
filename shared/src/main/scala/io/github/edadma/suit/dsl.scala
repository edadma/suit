package io.github.edadma.suit

import io.github.edadma.vdom.*

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

  // Pointer handlers reach the application as a typed `PointerEvent`. vdom carries a
  // handler as `Any => Unit`, so each registration adapts the typed callback by casting
  // the delivered value — the router only ever sends a PointerEvent under these keys.
  private def pointer(
      props: Map[String, Prop],
      event: String,
      fn:    (PointerEvent => Unit) | Null,
  ): Map[String, Prop] =
    if fn == null then props
    else props.updated(s"on:$event", Handler((a: Any) => fn(a.asInstanceOf[PointerEvent])))

  /** The styled container. `bg`/`border`/`borderWidth` give it appearance; `width`/
    * `height` fix its size (omit to fill a tight parent or wrap a loose one); `padding`
    * insets its child; `flex` makes it expand inside a row/column; `onClick` fires when
    * a pointer-press hit-tests to it. Children render inside, after the padding. */
  def box(
      bg:           Color | Null                  = null,
      border:       Color | Null                  = null,
      borderWidth:  Double                         = 0.0,
      width:        Double                         = Double.NaN,
      height:       Double                         = Double.NaN,
      padding:      EdgeInsets | Null              = null,
      flex:         Int                            = 0,
      onClick:      (PointerEvent => Unit) | Null  = null,
      onMouseDown:  (PointerEvent => Unit) | Null  = null,
      onMouseUp:    (PointerEvent => Unit) | Null  = null,
      onMouseMove:  (PointerEvent => Unit) | Null  = null,
      onMouseEnter: (PointerEvent => Unit) | Null  = null,
      onMouseLeave: (PointerEvent => Unit) | Null  = null,
  )(children: VNode*): VNode =
    var props = Map.empty[String, Prop]
    if bg != null then props = props.updated("bg", PropValue(bg))
    if border != null then props = props.updated("border", PropValue(border))
    if borderWidth != 0.0 then props = props.updated("borderWidth", PropValue(borderWidth))
    props = sized(props, "width", width)
    props = sized(props, "height", height)
    if padding != null then props = props.updated("padding", PropValue(padding))
    if flex != 0 then props = props.updated("flex", PropValue(flex))
    props = pointer(props, "click", onClick)
    props = pointer(props, "mousedown", onMouseDown)
    props = pointer(props, "mouseup", onMouseUp)
    props = pointer(props, "mousemove", onMouseMove)
    props = pointer(props, "mouseenter", onMouseEnter)
    props = pointer(props, "mouseleave", onMouseLeave)
    el("box", props, children)

  /** A run of text in `color` at point `size`. Single-line: it measures and paints as
    * one line through the installed [[TextMeasurer]] and the canvas. */
  def text(
      content: String,
      size:    Double = TextStyle.default.size,
      color:   Color  = TextStyle.default.color,
  ): VNode =
    el("text", Map("content" -> PropValue(content), "size" -> PropValue(size), "color" -> PropValue(color)), Nil)

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

  /** A z-ordered overlay: children stack back-to-front, each positioned by `alignment`. */
  def stack(alignment: Alignment = Alignment.topLeft)(children: VNode*): VNode =
    el("stack", Map("alignment" -> PropValue(alignment)), children)

  /** Positions its child within the available space at `alignment` (a one-child stack). */
  def align(alignment: Alignment)(children: VNode*): VNode =
    el("stack", Map("alignment" -> PropValue(alignment)), children)

  /** Centres its child in the available space. */
  def center(children: VNode*): VNode =
    el("stack", Map("alignment" -> PropValue(Alignment.center)), children)
