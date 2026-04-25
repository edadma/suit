package io.github.edadma.suit

import scala.collection.mutable.ArrayBuffer

// Internal render-tree nodes. Nodes are mutable instances created and managed
// by the Reconciler. Every Node holds:
//   - a reference to the View it was last reconciled from (carries app props
//     like text, callbacks, enabled-flag — re-read on each render),
//   - persistent UI state that must survive re-renders (hover, focus, caret
//     position, layout bounds).
//
// The split between View (immutable description) and Node (mutable state) is
// the same one React draws between elements and fibers. Translates to a sysl
// tagged-union enum where each variant carries a ref to the corresponding
// per-node struct. There are no virtual methods on Node — layout, render and
// event dispatch all pattern-match externally in Engine.
sealed trait Node:

  // Bounds set by the layout pass; consumed by render and hit testing.
  var bounds: Rect = Rect(0, 0, 0, 0)

  // Stable identity used by keyed-children reconciliation. `null` means the
  // child wasn't tagged with a Keyed wrapper and falls back to position-based
  // matching.
  var key: Any = null


// --- Leaves -----------------------------------------------------------------

final class TextNode(var view: Text) extends Node

final class ButtonNode(var view: Button) extends Node:
  var hover:   Boolean = false
  var pressed: Boolean = false

final class InputNode(var view: Input) extends Node:
  // Cursor and focus state must persist across re-renders, so they live on
  // the node, not on the View. The actual character buffer lives on the View
  // (controlled-input pattern: app owns the value, node fires onChange).
  var cursor:  Int     = view.value.length
  var hover:   Boolean = false
  var focused: Boolean = false
  // Horizontal pixel offset that scrolls the visible portion of the value
  // when it's wider than the field. Adjusted by the renderer to keep the
  // caret in view; clamped to >= 0.
  var scrollX: Int     = 0

final class CheckboxNode(var view: Checkbox) extends Node:
  var hover:   Boolean = false
  var focused: Boolean = false

final class SpacerNode(var view: Spacer) extends Node


// --- Containers -------------------------------------------------------------

final class StackNode(var view: Stack) extends Node:
  // The reconciler keeps this in sync with `view.children` — same length, same
  // ordering, same kinds where reusable.
  val children: ArrayBuffer[Node] = ArrayBuffer.empty


// Wraps a stateful Widget. The reconciler expands the widget by calling
// `widget.render()` and reconciles the produced View into `child`. The
// widget instance itself is held so subsequent renders can preserve its
// internal state.
final class ComponentNode(var widget: Widget) extends Node:
  var child: Node | Null = null


// Pushes a context value during reconciliation/render of its child subtree.
final class ContextProviderNode(var ctx: Context[?], var value: Any) extends Node:
  var child: Node | Null = null


// Holds the rendered subtree of an ErrorBoundary — either the child or, if
// rendering threw, the fallback view.
final class ErrorBoundaryNode(var view: ErrorBoundary) extends Node:
  var child: Node | Null = null


// A vertical scroll viewport. `scrollY` is the top of the visible window
// (in content coordinates); `contentHeight` is set during layout from the
// child's measured height and used to clamp scrollY.
final class ScrollNode(var view: Scroll) extends Node:
  var child:         Node | Null = null
  var scrollY:       Int         = 0
  var contentHeight: Int         = 0
