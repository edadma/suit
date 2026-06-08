package io.github.edadma.suit

import org.scalatest.funsuite.AnyFunSuite
import RecordingCanvas.Command

// Headless tests for repaint boundaries and partial-frame compositing. The whole point is
// that an animating canvas redraws without re-rasterising the static UI around it; this
// suite proves the decision logic (which boundary a change marks, which regions a partial
// frame repaints) and the in-place boundary repaint, all on the JVM against RecordingCanvas.
class RepaintSpec extends AnyFunSuite:

  // A root holding a static box beside a canvas, with every boundary's repaint flag cleared
  // so each test starts from a settled frame.
  private def scene(): (RenderRoot, RenderBox, RenderCanvas) =
    val root   = new RenderRoot(Size(200, 100))
    val box    = new RenderBox
    val canvas = new RenderCanvas
    root.insertChild(box, null)
    root.insertChild(canvas, null)
    root.clearRepaintFlags()
    root.dirty = false
    (root, box, canvas)

  test("a change inside a canvas marks only the canvas, not the whole scene"):
    val (root, _, canvas) = scene()
    canvas.markDirty()
    assert(canvas.needsRepaint)  // the canvas region will be repainted
    assert(!root.needsRepaint)   // but the scene is not re-rasterised
    assert(root.dirty)           // a frame is still requested

  test("a change in the static UI marks the root for a full repaint"):
    val (root, box, _) = scene()
    box.markDirty()
    assert(root.needsRepaint) // nearest boundary above a plain box is the root
    assert(root.dirty)

  test("dirtyBoundaries lists the dirty nested boundary and never the root"):
    val (root, _, canvas) = scene()
    canvas.needsRepaint = true
    assert(root.dirtyBoundaries == List(canvas))

  test("dirtyBoundaries does not descend into a dirty boundary"):
    val root  = new RenderRoot(Size(200, 100))
    val outer = new RenderCanvas
    val inner = new RenderCanvas
    root.insertChild(outer, null)
    outer.insertChild(inner, null)
    root.clearRepaintFlags()
    outer.needsRepaint = true
    inner.needsRepaint = true
    // The outer's repaint already covers the inner, so only the outer is listed.
    assert(root.dirtyBoundaries == List(outer))
    // With the outer clean, the inner is found on its own.
    outer.needsRepaint = false
    inner.needsRepaint = true
    assert(root.dirtyBoundaries == List(inner))

  test("invalidateLiveSurfaces marks canvases but leaves the static scene cached"):
    val (root, _, canvas) = scene()
    root.invalidateLiveSurfaces()
    assert(canvas.needsRepaint) // a live surface
    assert(!root.needsRepaint)  // the root is a boundary but not a live surface

  test("clearRepaintFlags resets every boundary after a full frame"):
    val (root, _, canvas) = scene()
    root.needsRepaint = true
    canvas.needsRepaint = true
    root.clearRepaintFlags()
    assert(!root.needsRepaint && !canvas.needsRepaint)

  test("repaintBoundary clips to the boundary, clears it, and paints only that subtree"):
    val (root, box, canvas) = scene()
    box.background = Solid(Color(50, 60, 70)) // if the box were painted it would show here
    canvas.painter = (c, s) => c.fillRect(Rect(0, 0, s.width, s.height), Solid(Color(1, 2, 3)))
    root.layout(Constraints.tight(root.windowSize))
    val rec = new RecordingCanvas
    Compositor.repaintBoundary(rec, canvas, Color(9, 9, 9))
    val region = Rect(0, 0, 200, 100) // root stacks children full-window
    assert(rec.commands.toList == List(
      Command.PushClip(region, BorderRadius.zero),         // confine to the canvas region
      Command.FillRect(region, Solid(Color(9, 9, 9))),     // clear it to the window background
      Command.PushClip(region, BorderRadius.zero),         // the canvas's own paint bracket
      Command.PushTranslate(0, 0),
      Command.FillRect(region, Solid(Color(1, 2, 3))),     // the app's drawing, in local coords
      Command.PopTranslate,
      Command.PopClip,
      Command.PopClip,
    ))
    // Nothing the static box would have drawn (its background fill) appears.
    assert(!rec.commands.exists {
      case Command.FillRect(_, Solid(Color(50, 60, 70, _))) => true
      case _                                                => false
    })
