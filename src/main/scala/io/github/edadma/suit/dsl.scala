package io.github.edadma.suit

import io.github.edadma.vdom.*

// suit's declarative surface — the builders that produce vdom `VNode`s, the
// equivalent of riposte's HTML DSL but for the render-tree element vocabulary. The
// skeleton ships exactly what the milestone needs: a `box` with a background colour
// and a click handler. The full SwiftUI-flavoured vocabulary (row, col, spacer,
// align, stack, text, padding…) arrives with the layout engine and the DSL phase;
// the naming and ergonomics there are suit's to design — only the constraint protocol
// underneath is fixed.
object dsl:

  /** A coloured box. `bg` paints its background; `onClick` fires when a pointer-press
    * hit-tests to it. Children render inside, filling the box. */
  def box(
      bg:       Color | Null            = null,
      onClick:  (Any => Unit) | Null    = null,
      children: VNode*,
  ): VNode =
    var props = Map.empty[String, Prop]
    if bg != null then props = props.updated("bg", PropValue(bg))
    if onClick != null then props = props.updated("on:click", Handler(onClick))
    VElement("box", props, children.toVector, None)
