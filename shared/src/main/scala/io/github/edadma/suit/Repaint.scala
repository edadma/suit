package io.github.edadma.suit

// The "please redraw" seam — the one thing a frame-driven animation needs that the
// reconciler does not provide.
//
// suit is retained-mode with a dirty-gated frame loop: the tree is re-rasterised only when
// something marks it dirty (a layout/prop change bubbles `markDirty` to the root, and the
// loop repaints when the root's flag is set). That covers every change expressed *through*
// the vnode tree. But an imperative animation — a canvas whose `draw` reads a mutable
// simulation that a `useFrame` callback advances — changes nothing in the tree: the painter
// closure and its props are identical frame to frame (Scala even caches a capture-free
// closure to one instance), so no `markDirty` ever fires and the surface would freeze.
//
// `Repaint.request` is how such code asks for the next frame to be drawn anyway. The runtime
// installs it to set the render root's dirty flag; a headless test installs its own. It is a
// process-global injectable, like the scheduler and motion seams, so the shared code that
// calls it (`useFrame`) stays host-agnostic and JVM-testable.
object Repaint:
  /** Request that the current frame be re-drawn even though the vnode tree did not change.
    * Installed by `Suit.run` (sets the root dirty); defaults to a no-op so a forgotten
    * install simply means no extra repaint rather than a crash. */
  var request: () => Unit = () => ()
