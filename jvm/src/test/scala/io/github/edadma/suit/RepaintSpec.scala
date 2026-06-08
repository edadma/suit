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

  test("repaintBoundary clears the boundary and paints only that subtree"):
    val (root, box, canvas) = scene()
    box.background = Solid(Color(50, 60, 70)) // if the box were painted it would show here
    canvas.painter = (c, s) => c.fillRect(Rect(0, 0, s.width, s.height), Solid(Color(1, 2, 3)))
    root.layout(Constraints.tight(root.windowSize))
    val rec = new RecordingCanvas
    Compositor.repaintBoundary(rec, canvas, Color(9, 9, 9))
    val region = Rect(0, 0, 200, 100) // root stacks children full-window; no clipping ancestors
    assert(rec.commands.toList == List(
      Command.FillRect(region, Solid(Color(9, 9, 9))),     // clear the canvas region to the bg
      Command.PushClip(region, BorderRadius.zero),         // the canvas's own paint bracket
      Command.PushTranslate(0, 0),
      Command.FillRect(region, Solid(Color(1, 2, 3))),     // the app's drawing, in local coords
      Command.PopTranslate,
      Command.PopClip,
    ))
    // Nothing the static box would have drawn (its background fill) appears.
    assert(!rec.commands.exists {
      case Command.FillRect(_, Solid(Color(50, 60, 70, _))) => true
      case _                                                => false
    })

  test("partialFrame re-composites the overlay over an animating boundary beneath it"):
    // A full-window canvas with an overlay (a dialog) on top of it. A partial frame repaints the
    // canvas region, then must paint the overlay back over that region — otherwise the animation
    // bleeds through the open dialog.
    val root    = new RenderRoot(Size(200, 100))
    val canvas  = new RenderCanvas
    val overlay = new RenderOverlay
    val dialog  = new RenderBox
    dialog.background = Solid(Color(7, 7, 7))
    canvas.painter    = (c, s) => c.fillRect(Rect(0, 0, s.width, s.height), Solid(Color(1, 2, 3)))
    root.insertChild(canvas, null)
    overlay.insertChild(dialog, null)
    root.insertChild(overlay, null)
    root.clearRepaintFlags()
    root.layout(Constraints.tight(root.windowSize))
    canvas.needsRepaint = true

    val rec = new RecordingCanvas
    Compositor.partialFrame(rec, root.dirtyBoundaries, overlay, Color(9, 9, 9))
    val region = Rect(0, 0, 200, 100)
    val cmds   = rec.commands.toList
    val canvasFill  = cmds.indexOf(Command.FillRect(region, Solid(Color(1, 2, 3)))) // the animation
    val overlayFill = cmds.indexOf(Command.FillRect(region, Solid(Color(7, 7, 7)))) // the dialog
    assert(canvasFill >= 0 && overlayFill >= 0)
    assert(overlayFill > canvasFill)                                       // dialog re-drawn on top
    assert(cmds(overlayFill - 1) == Command.PushClip(region, BorderRadius.zero)) // clipped to the region
    assert(cmds(overlayFill + 1) == Command.PopClip)
    assert(!canvas.needsRepaint)                                           // boundary flag cleared

  test("partialFrame with an empty overlay just repaints the boundaries"):
    val (root, _, canvas) = scene()
    canvas.painter = (c, s) => c.fillRect(Rect(0, 0, s.width, s.height), Solid(Color(1, 2, 3)))
    root.layout(Constraints.tight(root.windowSize))
    canvas.needsRepaint = true
    val overlay = new RenderOverlay // no children: nothing on top
    val rec     = new RecordingCanvas
    Compositor.partialFrame(rec, root.dirtyBoundaries, overlay, Color(9, 9, 9))
    // No clip/paint pairs beyond the boundary's own — same output as repaintBoundary alone.
    assert(!rec.commands.exists { case Command.FillRect(_, Solid(Color(7, 7, 7, _))) => true; case _ => false })
    assert(!canvas.needsRepaint)

  test("repaintBoundary replays an ancestor clip so a scrolled canvas stays confined"):
    // root -> scroll (viewport) -> canvas, with the canvas scrolled partly above the viewport.
    val root   = new RenderRoot(Size(200, 100))
    val scroll = new RenderScroll()
    val canvas = new RenderCanvas
    root.insertChild(scroll, null)
    scroll.insertChild(canvas, null)
    root.clearRepaintFlags()
    canvas.height  = Some(300) // taller than the 100px viewport, so there is scroll range
    canvas.painter = (c, s) => c.fillRect(Rect(0, 0, s.width, s.height), Solid(Color(1, 2, 3)))
    root.layout(Constraints.tight(root.windowSize))
    // Scroll so the canvas's top is above the viewport (negative absolute y).
    scroll.scrollBy(30)
    root.layout(Constraints.tight(root.windowSize))
    val rec = new RecordingCanvas
    Compositor.repaintBoundary(rec, canvas, Color(9, 9, 9))
    // The first command is the scroll viewport clip — without it the canvas would paint
    // over the chrome above the viewport instead of being clipped away.
    val viewport = Rect.at(scroll.absoluteOffset, scroll.size)
    assert(rec.commands.head == Command.PushClip(viewport, BorderRadius.zero))
    assert(rec.commands.last == Command.PopClip)
    // The canvas painted at its scrolled (negative) origin, inside that clip.
    assert(canvas.absoluteOffset.y < 0)
