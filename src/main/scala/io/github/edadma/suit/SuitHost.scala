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

  // The tag names the kind of RenderObject to create. The skeleton knows one visual
  // element, `box`; the DSL widens this vocabulary (row, col, text, …) as the layout
  // engine and widgets land. Unknown tags fall back to a box so an early DSL addition
  // renders as a plain rectangle rather than crashing.
  def createElement(tag: String, namespace: String | Null): AnyRef = tag match
    case "box" => new RenderBox
    case _     => new RenderBox

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
  // colour, a size, an enum — handed over by vdom's PropValue channel with their
  // actual types, never stringified. On removal the reconciler passes `null`, which
  // resets the field.
  def setProperty(node: AnyRef, name: String, value: Any): Unit =
    (ro(node), name) match
      case (b: RenderBox, "bg") =>
        b.background = value match
          case c: Color => c
          case _        => null
        b.markDirty()
      case _ => ()

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
