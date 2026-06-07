package io.github.edadma.suit

import io.github.edadma.vdom.HostConfig

// Binds the host-agnostic vdom core to suit's RenderObject tree. The reconciler
// treats every host node as an opaque `AnyRef`; here that `AnyRef` is always a
// RenderObject, and each method casts back and performs the real tree edit. This is
// the single place where vdom meets suit's renderer — the analogue of riposte's
// `DomHostConfig`, but creating and mutating retained render objects instead of DOM
// nodes.
//
// Several `HostConfig` methods are DOM-shaped (`setStyle` with CSS declarations,
// `setInnerHtml`, namespaces). On a pixel canvas they have no meaning, so they are
// no-ops; suit's DSL routes everything through typed elements, attributes that name
// real RenderObject fields, and event handlers instead.
final class SuitHostConfig extends HostConfig:

  private inline def ro(node: AnyRef): RenderObject = node.asInstanceOf[RenderObject]

  // --- node creation -------------------------------------------------------

  // The tag names the kind of RenderObject to create — each DSL builder emits the tag
  // for the layout primitive it wants. Unknown tags fall back to a box so an as-yet
  // unmapped builder renders as a plain rectangle rather than crashing.
  def createElement(tag: String, namespace: String | Null): AnyRef = tag match
    case "box"      => new RenderBox
    case "row"      => new RenderFlex(Axis.Horizontal)
    case "col"      => new RenderFlex(Axis.Vertical)
    case "padding"  => new RenderPadding
    case "sizedBox" => new RenderConstrained
    case "stack"      => new RenderStack
    case "positioned" => new RenderPositioned
    case "scroll"   => new RenderScroll
    case "text"     => new RenderText("")
    case _          => new RenderBox

  def createText(text: String): AnyRef    = new RenderText(text)
  def createAnchor(label: String): AnyRef = new RenderAnchor(label)

  // --- tree shape ----------------------------------------------------------

  def parentNode(node: AnyRef): AnyRef | Null = ro(node).parent

  def nextSibling(node: AnyRef): AnyRef | Null =
    val n = ro(node)
    n.parent match
      case p: RenderObject =>
        val i = p.children.indexOf(n)
        if i >= 0 && i + 1 < p.children.length then p.children(i + 1) else null
      case null => null

  def insertBefore(parent: AnyRef, node: AnyRef, before: AnyRef | Null): Unit =
    ro(parent).insertChild(ro(node), before.asInstanceOf[RenderObject | Null])

  def removeNode(node: AnyRef): Unit =
    val n = ro(node)
    n.parent match
      case p: RenderObject => p.removeChild(n)
      case null            => ()

  // Pixel canvas: no namespaces.
  def namespaceURI(node: AnyRef): String | Null = null

  // --- text ----------------------------------------------------------------

  def setText(node: AnyRef, text: String): Unit = node match
    case t: RenderText =>
      t.text = text
      t.markDirty()
    case _ => ()

  // --- props ---------------------------------------------------------------

  // String attributes have no role on a pixel canvas — suit's DSL names real
  // RenderObject fields and sends their typed values through setProperty instead.
  def setAttribute(node: AnyRef, name: String, value: String): Unit = ()
  def removeAttribute(node: AnyRef, name: String): Unit            = ()

  // Typed properties are how the render tree receives real values from the DSL: a
  // colour, a size, an inset, an alignment, an enum — handed over by vdom's PropValue
  // channel with their actual types, never stringified. On removal the reconciler
  // passes `null`, and each conversion below maps that back to the field's default, so
  // dropping a prop restores the unset state.
  def setProperty(node: AnyRef, name: String, value: Any): Unit =
    val obj = ro(node)
    (obj, name) match
      // `flex` is parent data meaningful on any object inside a row/column.
      case (o, "flex")      => o.flex = asInt(value)
      // `focusable` applies to any object that should be able to take keyboard focus.
      case (o, "focusable") => o.focusable = value == true
      // `acceptsText` marks an object that wants text-input while focused (a text field).
      case (o, "acceptsText") => o.acceptsText = value == true
      // `ignorePointer` makes an object (and its subtree) transparent to hit-testing.
      case (o, "ignorePointer") => o.ignorePointer = value == true

      case (b: RenderBox, "bg")           => b.background = asPaintOrNull(value)
      case (b: RenderBox, "border")       => b.border = asPaintOrNull(value)
      case (b: RenderBox, "borderWidth")  => b.borderWidth = asDouble(value)
      case (b: RenderBox, "borderRadius") => b.borderRadius = asRadius(value)
      case (b: RenderBox, "shadow")       => b.shadow = asShadowOrNull(value)
      case (b: RenderBox, "opacity")      => b.opacity = asOpacity(value)
      case (b: RenderBox, "width")        => b.width = asDoubleOpt(value)
      case (b: RenderBox, "height")       => b.height = asDoubleOpt(value)
      case (b: RenderBox, "padding")      => b.padding = asInsets(value)
      case (b: RenderBox, "clip")         => b.clipContent = value == true
      // Text-style cascade carriers: descendant text inherits these unless overridden.
      case (b: RenderBox, "textColor")    => b.textAttrs = b.textAttrs.copy(color = asColorOpt(value))
      case (b: RenderBox, "textSize")     => b.textAttrs = b.textAttrs.copy(size = asDoubleOpt(value))

      case (c: RenderConstrained, "width")  => c.width = asDoubleOpt(value)
      case (c: RenderConstrained, "height") => c.height = asDoubleOpt(value)

      case (p: RenderPadding, "padding") => p.padding = asInsets(value)

      case (s: RenderStack, "alignment") => s.alignment = asAlignment(value)

      case (p: RenderPositioned, "dx") => p.dx = asDouble(value)
      case (p: RenderPositioned, "dy") => p.dy = asDouble(value)

      case (s: RenderScroll, "axis") =>
        s.axis = value match
          case a: Axis => a
          case _       => Axis.Vertical

      case (f: RenderFlex, "mainAxisAlignment") =>
        f.mainAxisAlignment = value match
          case m: MainAxisAlignment => m
          case _                    => MainAxisAlignment.Start
      case (f: RenderFlex, "crossAxisAlignment") =>
        f.crossAxisAlignment = value match
          case c: CrossAxisAlignment => c
          case _                     => CrossAxisAlignment.Start
      case (f: RenderFlex, "mainAxisSize") =>
        f.mainAxisSize = value match
          case s: MainAxisSize => s
          case _               => MainAxisSize.Max
      case (f: RenderFlex, "spacing") => f.spacing = asDouble(value)

      case (t: RenderText, "content") => t.text = asString(value)
      // A text node's own colour/size are explicit overrides; unset (null) means inherit
      // from the cascade rather than snap to a default.
      case (t: RenderText, "size")  => t.explicitSize = asDoubleOpt(value)
      case (t: RenderText, "color") => t.explicitColor = asColorOpt(value)
      // Multi-line controls; on removal each falls back to its single-line default.
      case (t: RenderText, "align") =>
        t.align = value match
          case a: TextAlign => a
          case _            => TextAlign.Left
      case (t: RenderText, "maxLines") => t.maxLines = value match
          case i: Int => i
          case _      => 1
      case (t: RenderText, "overflow") =>
        t.overflow = value match
          case o: TextOverflow => o
          case _               => TextOverflow.Clip
      case (t: RenderText, "softWrap") => t.softWrap = value != false

      case _ => ()
    obj.markDirty()

  // --- typed-property coercions --------------------------------------------
  // The reconciler delivers each prop as `Any` (the value the DSL wrapped in a
  // PropValue) or `null` on removal. These map both cases to a render-tree field.

  private def asColorOpt(v: Any): Option[Color] = v match
    case c: Color => Some(c)
    case _        => None

  // A paint prop arrives as a Paint (the DSL converts a flat Color to Solid before
  // wrapping), but tolerate a bare Color too so a colour set directly still reads.
  private def asPaintOrNull(v: Any): Paint | Null = v match
    case p: Paint => p
    case c: Color => Solid(c)
    case _        => null

  private def asRadius(v: Any): BorderRadius = v match
    case r: BorderRadius => r
    case d: Double       => BorderRadius.all(d)
    case i: Int          => BorderRadius.all(i.toDouble)
    case _               => BorderRadius.zero

  private def asShadowOrNull(v: Any): Shadow | Null = v match
    case s: Shadow => s
    case _         => null

  private def asOpacity(v: Any): Double = v match
    case d: Double => d
    case i: Int    => i.toDouble
    case _         => 1.0

  private def asDoubleOpt(v: Any): Option[Double] = v match
    case d: Double => Some(d)
    case i: Int    => Some(i.toDouble)
    case _         => None

  private def asDouble(v: Any): Double = v match
    case d: Double => d
    case i: Int    => i.toDouble
    case _         => 0.0

  private def asInt(v: Any): Int = v match
    case i: Int    => i
    case d: Double => d.toInt
    case _         => 0

  private def asString(v: Any): String = v match
    case s: String => s
    case _         => ""

  private def asInsets(v: Any): EdgeInsets = v match
    case e: EdgeInsets => e
    case _             => EdgeInsets.zero

  private def asAlignment(v: Any): Alignment = v match
    case a: Alignment => a
    case _            => Alignment.topLeft

  // Inline CSS styles and inner HTML are DOM concepts with no render-tree equivalent.
  def setStyle(node: AnyRef, decls: Map[String, String]): Unit = ()
  def clearStyle(node: AnyRef): Unit                           = ()
  def setInnerHtml(node: AnyRef, html: String): Unit           = ()

  // --- events --------------------------------------------------------------

  // The event name uniquely identifies the handler slot on a RenderObject, so it
  // doubles as the opaque handle the reconciler stores and hands back to
  // `removeListener`. The capture/once/passive flags are DOM event-model details with
  // no analogue in suit's single-dispatch hit-test routing.
  def addListener(
      node:    AnyRef,
      event:   String,
      capture: Boolean,
      once:    Boolean,
      passive: Boolean,
      fn:      Any => Unit,
  ): AnyRef =
    ro(node).handlers(event) = fn
    event

  def removeListener(node: AnyRef, event: String, capture: Boolean, handle: AnyRef): Unit =
    ro(node).handlers.remove(event)
