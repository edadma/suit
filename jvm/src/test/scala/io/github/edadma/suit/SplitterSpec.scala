package io.github.edadma.suit

import org.scalatest.funsuite.AnyFunSuite
import io.github.edadma.vdom.{Host, Ref, Scheduler}
import io.github.edadma.suit.dsl.*
import io.github.edadma.suit.widgets.*

// Headless tests for the splitter widget and the layout chain a real editor-plus-preview
// window is built from (scriptura's GUI). The splitter is a vdom component, so these mount
// it for real through `SuitHostConfig`, lay the tree out at a window size, and assert on the
// resulting render tree — the same off-device path the runtime takes, minus SDL.
//
// The point they pin down: the splitter divides a *bounded* area, handing each pane a tight
// cross-axis extent, so a pane's content (a flex column whose last child is a button, or a
// page wider than the pane) stays within the window — and reflows when the window size
// changes. A fixed window that never reflowed on resize was what put scriptura's Run button
// and its preview scrollbar off the bottom and right edges of a too-large window.
class SplitterSpec extends AnyFunSuite:

  /** Mount `app` into a root of `size`, flush the scheduler, and lay the tree out — the
    * headless equivalent of `Suit.run`'s setup, minus the device. Returns the root. */
  private def mount(app: VNode, size: Size): RenderRoot =
    Host.config = new SuitHostConfig
    val root = new RenderRoot(size)
    createRoot(root).render(app)
    Scheduler.flushSync()
    root.layout(Constraints.tight(size))
    root

  /** Re-lay the tree at a new window size, the way the runtime's resize handler does:
    * update `windowSize` (which `RenderRoot.layout` lays its child tight to) and re-layout. */
  private def resize(root: RenderRoot, size: Size): Unit =
    root.windowSize = size
    root.layout(Constraints.tight(size))

  private def allObjects(o: RenderObject): List[RenderObject] =
    o :: o.children.toList.flatMap(allObjects)

  private def onlyScroll(root: RenderObject): RenderScroll =
    allObjects(root).collectFirst { case s: RenderScroll => s }.get

  /** A faithful reduction of scriptura's window: a padded splitter whose left pane is a
    * stretch column of [a flex editor area, a fixed-height log, a centred button] and whose
    * right pane is a both-ways scroll area holding a page wider and taller than the pane.
    * The editor stand-in is deliberately taller than any pane so a pane that failed to bound
    * its height would let the column (and the button under it) overflow the window. The
    * button and the preview scroll are tagged with refs so the assertions can find them. */
  private def scripturaLike(
      buttonRef: Ref[RenderObject | Null],
      pageW:     Double = 816,
      pageH:     Double = 1056,
  ): VNode =
    val button = box(ref = buttonRef, width = 80, height = 32)()
    box(padding = EdgeInsets.all(8))(
      splitter(axis = Axis.Horizontal, initial = 0.4)(
        col(crossAxisAlignment = CrossAxisAlignment.Stretch, spacing = 8)(
          box(flex = 1, clip = true)(sizedBox(height = 4000)(box()())),
          sizedBox(height = 140)(box()()),
          row(mainAxisAlignment = MainAxisAlignment.Center)(button),
        ),
        box(flex = 1, clip = true)(
          scrollArea(both = true)(
            padding(EdgeInsets.all(16))(
              col(crossAxisAlignment = CrossAxisAlignment.Start, mainAxisSize = MainAxisSize.Min)(
                sizedBox(width = pageW, height = pageH)(box()()),
              ),
            ),
          ),
        ),
      ),
    )

  test("the splitter bounds each pane's height, so a column's trailing button stays in the window"):
    val buttonRef = new Ref[RenderObject | Null](null)
    val root      = mount(scripturaLike(buttonRef), Size(1600, 1000))
    val button    = buttonRef.current.asInstanceOf[RenderObject]
    // The button sits at the bottom of the left column; its lower edge must fall within the
    // window, not below it — which only holds if the pane (and the column) is height-bounded.
    val bottom = button.absoluteOffset.y + button.size.height
    assert(bottom <= 1000.0, s"button bottom $bottom overflows the 1000px window")

  test("a preview page wider than its pane leaves the scroll area something to scroll across"):
    // With the split at 0.4 the preview pane is ~0.6 of ~1584px ≈ 950px; a 816px page plus
    // 32px padding fits, so there is no horizontal overflow.
    val wide = onlyScroll(mount(scripturaLike(new Ref[RenderObject | Null](null)), Size(1600, 1000)))
    assert(wide.maxScrollX == 0.0)
    // Narrow the whole window so the preview pane drops below the page width: now the page
    // overflows horizontally and the scroll area reports room to scroll — the precondition for
    // the horizontal bar to paint.
    val narrow = onlyScroll(mount(scripturaLike(new Ref[RenderObject | Null](null)), Size(700, 1000)))
    assert(narrow.maxScrollX > 0.0, s"a page wider than the pane should overflow; got ${narrow.maxScrollX}")

  test("shrinking the window reflows the panes and keeps the button on-screen"):
    val buttonRef = new Ref[RenderObject | Null](null)
    val root      = mount(scripturaLike(buttonRef), Size(1600, 1000))
    val button    = buttonRef.current.asInstanceOf[RenderObject]
    resize(root, Size(1200, 700))
    val bottom = button.absoluteOffset.y + button.size.height
    assert(bottom <= 700.0, s"after resize, button bottom $bottom overflows the 700px window")
