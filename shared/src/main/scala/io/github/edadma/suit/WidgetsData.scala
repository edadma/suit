package io.github.edadma.suit

import io.github.edadma.suit.dsl.*

// The data-display widgets: a vertically virtualized list, a virtualized data grid built on
// it, and a resizable splitter. The first two only build the rows under the viewport so a
// large result stays cheap; the splitter divides an area into two draggable panes.
private[suit] trait WidgetsData extends WidgetsSupport:

  // --- virtualized list ------------------------------------------------------

  private case class VirtualListProps(itemCount: Int, itemExtent: Double, overscan: Int, builder: Int => VNode)

  // Until the first layout populates the size ref, the list has no measured viewport. It
  // renders against this fallback height so there is content on the first frame; the ref-read
  // re-render then corrects it to the real viewport — the same measure-one-frame-late dance the
  // anchored overlays use.
  private val VirtualFallbackHeight = 600.0

  private val VirtualListImpl: Component[VirtualListProps] =
    component[VirtualListProps] { p =>
      val (scroll, setScroll, _) = useState(0.0)
      val (_, _, bumpTick)       = useState(0)
      val sizeRef                = useRef[RenderObject | Null](null)

      // One re-render after mount so the slice is recomputed against the now-laid-out viewport
      // height (the ref is null on the first render, before any layout has run).
      useEffect(() => { bumpTick(t => t + 1); noCleanup }, Array())

      val viewportH = sizeRef.current match
        case r: RenderObject if r.size.height > 0 => r.size.height
        case _                                    => VirtualFallbackHeight

      val range = VirtualWindow.visibleRange(scroll, viewportH, p.itemExtent, p.itemCount, p.overscan)
      val maxS  = VirtualWindow.maxScroll(viewportH, p.itemExtent, p.itemCount)
      val s     = math.max(0.0, math.min(scroll, maxS))

      val items: Seq[VNode] =
        (range.first until range.last).map(i => sizedBox(height = p.itemExtent)(p.builder(i)))

      box(
        clip    = true,
        ref     = sizeRef,
        onWheel = e => setScroll(math.max(0.0, math.min(s - e.deltaY * RenderScroll.WheelStep, maxS))),
      )(
        positioned(0, range.offsetY)(
          col(mainAxisSize = MainAxisSize.Min)(items*),
        ),
      )
    }

  /** A vertically virtualized list: only the items under the viewport (plus a little overscan)
    * are ever built, so a list of many thousands of fixed-height rows costs the handful on
    * screen rather than all of them. `builder(i)` produces item `i` on demand; `itemExtent` is
    * each item's (fixed) height, which is what makes the windowing exact (see [[VirtualWindow]]).
    * The wheel scrolls it. It fills the space its parent gives it and **must be given a bounded
    * height** (a flex slot, a fixed height, or a sized box) — that height is the viewport it
    * windows against. */
  def virtualList(itemCount: Int, itemExtent: Double, overscan: Int = 3)(builder: Int => VNode): VNode =
    VirtualListImpl(VirtualListProps(itemCount, itemExtent, overscan, builder))

  // --- data table ------------------------------------------------------------

  private case class DataTableProps(
      columns:   Seq[String],
      rows:      IndexedSeq[IndexedSeq[String]],
      selected:  Int,
      onSelect:  (Int => Unit) | Null,
      rowHeight: Double,
  )

  private val DataTableImpl: Component[DataTableProps] =
    component[DataTableProps] { p =>
      val theme = useTheme()
      val style = TextStyle(size = theme.textSize, color = theme.surfaceText)

      // Size each column to its widest cell among the header and a sample of the rows (scanning
      // every row of a large result would be wasteful and the first screenful is representative),
      // clamped so no column collapses or runs away. The table's content width is their sum.
      val cellPad   = 10.0
      val minColW   = 56.0
      val maxColW   = 360.0
      val sampleN   = math.min(p.rows.length, 200)
      val colWidths: Vector[Double] =
        p.columns.indices.toVector.map { ci =>
          var w = TextMeasurer.installed.measure(p.columns(ci), style).width
          var r = 0
          while r < sampleN do
            val row = p.rows(r)
            if ci < row.length then w = math.max(w, TextMeasurer.installed.measure(row(ci), style).width)
            r += 1
          math.max(minColW, math.min(maxColW, w + 2 * cellPad))
        }
      val contentW = colWidths.sum

      // The cells fill their column widths but are only one line tall; centring the whole row
      // within the (taller) row band vertically centres the text the way a table cell does —
      // the band box lays its single child at its top-left, so a bare row would sit at the top.
      def cells(values: Int => String, bold: Boolean): VNode =
        align(Alignment.centerLeft)(
          row(crossAxisAlignment = CrossAxisAlignment.Center)(
            p.columns.indices.map { ci =>
              box(width = colWidths(ci), padding = EdgeInsets.symmetric(horizontal = cellPad, vertical = 0), clip = true)(
                text(values(ci), color = theme.surfaceText, weight = if bold then FontWeight.SemiBold else FontWeight.Normal),
              )
            }*,
          ),
        )

      val header: VNode =
        box(width = contentW, height = p.rowHeight, bg = theme.surface)(
          cells(ci => p.columns(ci), bold = true),
        )

      // Subtle zebra striping: tint alternate rows a hair toward the ink so dense data is easier
      // to track across, with the selected row marked in the accent.
      val altBg = Color.lerp(theme.surface, theme.surfaceText, 0.05)
      val body: VNode =
        virtualList(p.rows.length, p.rowHeight) { i =>
          val rowData = p.rows(i)
          val isSel   = i == p.selected
          // A `Paint | Null` (not `Color | Null`): passing a nullable colour to the `Paint | Null`
          // slot would route `null` through the Color→Paint conversion and yield `Solid(null)`,
          // which faults when painted. Building the Solid here keeps `null` meaning "no fill".
          val bg: Paint | Null =
            if isSel then Solid(theme.accent.withAlpha(70))
            else if i % 2 == 1 then Solid(altBg)
            else null
          box(
            width   = contentW,
            height  = p.rowHeight,
            bg      = bg,
            onClick = if p.onSelect != null then (_ => p.onSelect.asInstanceOf[Int => Unit](i)) else null,
          )(
            cells(ci => if ci < rowData.length then rowData(ci) else "", bold = false),
          )
        }

      scrollView(Axis.Horizontal)(
        sizedBox(width = contentW)(
          col(crossAxisAlignment = CrossAxisAlignment.Stretch, mainAxisSize = MainAxisSize.Max)(
            header,
            box(height = 1, bg = theme.border)(),
            box(flex = 1)(body),
          ),
        ),
      )
    }

  /** A data grid: a header row of `columns` over a virtualized body of string `rows` (each row
    * a sequence of cell strings, indexed to match the columns). Columns auto-size to their
    * content; the body scrolls with the wheel and only builds the rows on screen (see
    * [[virtualList]]), so a large result set stays cheap. `selected` marks a row in the accent
    * and `onSelect` fires the clicked row's index. It **must be given a bounded height** — put it
    * in a flex slot or a sized box — since that height is the scrolling viewport. Wide tables
    * scroll horizontally. */
  def dataTable(
      columns:   Seq[String],
      rows:      IndexedSeq[IndexedSeq[String]],
      selected:  Int                  = -1,
      onSelect:  (Int => Unit) | Null = null,
      rowHeight: Double               = 28.0,
  ): VNode =
    DataTableImpl(DataTableProps(columns, rows, selected, onSelect, rowHeight))

  // --- splitter --------------------------------------------------------------

  private case class SplitterProps(
      axis:     Axis,
      initial:  Double,
      min:      Double,
      max:      Double,
      gutter:   Double,
      onResize: (Double => Unit) | Null,
      first:    VNode,
      second:   VNode,
  )

  // The fraction is held in flex weights rather than pixels, so the panes resize purely through
  // the flex layout and the split survives a window resize: the first pane gets `frac` of the free
  // space, the second the rest, scaled to a fixed integer total. clampFrac keeps both non-zero.
  private val SplitterScale = 1000

  private val SplitterImpl: Component[SplitterProps] =
    component[SplitterProps] { p =>
      val theme                  = useTheme()
      val (frac, setFrac, _)     = useState(clampFrac(p.initial, p.min, p.max))
      val (hover, setHover, _)   = useState(false)
      val (drag, setDrag, _)     = useState(false)
      val containerRef           = useRef[RenderObject | Null](null)

      // The gutter's drag reports the cursor's position as a fraction of the whole splitter, read
      // from the container's laid-out origin and size — the same position-based mapping the slider
      // uses, lifted to the splitter so the press point lands under the cursor with no jump. The
      // pointer is captured on press, so a drag keeps updating even once it leaves the thin gutter.
      def posFrac(e: PointerEvent): Double =
        containerRef.current match
          case c: RenderObject =>
            val o = c.absoluteOffset
            val raw = p.axis match
              case Axis.Horizontal => if c.size.width <= 0 then frac else (e.position.x - o.x) / c.size.width
              case Axis.Vertical   => if c.size.height <= 0 then frac else (e.position.y - o.y) / c.size.height
            clampFrac(raw, p.min, p.max)
          case null => frac

      def update(f: Double): Unit =
        val c = clampFrac(f, p.min, p.max)
        setFrac(c)
        if p.onResize != null then p.onResize(c)

      val firstFlex  = math.max(1, math.round(frac * SplitterScale).toInt)
      val secondFlex = math.max(1, SplitterScale - firstFlex)

      // The gutter brightens to the accent while hovered or dragged; the grip is a short bar
      // across its centre, oriented across the split.
      val gutterColor = if drag || hover then theme.accent else theme.border
      val gripInk     = theme.surfaceText.withAlpha(if drag || hover then 140 else 70)
      val step        = 0.02

      val gutter =
        box(
          bg           = gutterColor,
          width        = if p.axis == Axis.Horizontal then p.gutter else Double.NaN,
          height       = if p.axis == Axis.Vertical then p.gutter else Double.NaN,
          focusable    = true,
          onMouseDown  = e => { setDrag(true); update(posFrac(e)) },
          onMouseMove  = e => if e.button != 0 then update(posFrac(e)),
          onMouseUp    = _ => setDrag(false),
          onMouseEnter = _ => setHover(true),
          onMouseLeave = _ => setHover(false),
          onKeyDown = e =>
            p.axis match
              case Axis.Horizontal =>
                e.scancode match
                  case Key.Left  => update(frac - step)
                  case Key.Right => update(frac + step)
                  case _         => ()
              case Axis.Vertical =>
                e.scancode match
                  case Key.Up   => update(frac - step)
                  case Key.Down => update(frac + step)
                  case _        => (),
        )(
          center(
            box(
              width  = if p.axis == Axis.Horizontal then 2 else 24,
              height = if p.axis == Axis.Horizontal then 24 else 2,
              bg     = gripInk,
              radius = 1,
            )(),
          ),
        )

      val firstPane  = box(flex = firstFlex, clip = true)(p.first)
      val secondPane = box(flex = secondFlex, clip = true)(p.second)

      box(ref = containerRef)(
        p.axis match
          case Axis.Horizontal =>
            row(crossAxisAlignment = CrossAxisAlignment.Stretch, mainAxisSize = MainAxisSize.Max)(firstPane, gutter, secondPane)
          case Axis.Vertical =>
            col(crossAxisAlignment = CrossAxisAlignment.Stretch, mainAxisSize = MainAxisSize.Max)(firstPane, gutter, secondPane),
      )
    }

  /** A resizable split of two panes with a draggable gutter between them — the layout primitive a
    * sidebar-plus-content or an editor-plus-preview window is built from. `axis` chooses the
    * arrangement: `Axis.Horizontal` (the default) puts the panes side by side with a vertical
    * gutter; `Axis.Vertical` stacks them with a horizontal gutter.
    *
    * It is **uncontrolled**: it owns the split position, starting at `initial` (the first pane's
    * fraction of the area, `0..1`) and clamped between `min` and `max` so neither pane can be
    * dragged shut. Drag the gutter to resize, or focus it and use the arrow keys; pass `onResize`
    * to observe (e.g. to persist) the fraction. The panes resize through the flex layout, so the
    * split holds its proportion when the window resizes. Each pane is clipped to its share, so
    * content that outgrows it is cut rather than spilling across the gutter.
    *
    * Give the splitter a **bounded size** (a flex slot, a fixed height, or a sized box) — it fills
    * the area it is given and divides that.
    *
    * ```scala
    * splitter(initial = 0.25, min = 0.15, max = 0.5)(
    *   sidebarContent,   // the first (left) pane
    *   mainContent,      // the second (right) pane fills the rest
    * )
    * ```
    */
  def splitter(
      axis:     Axis             = Axis.Horizontal,
      initial:  Double           = 0.5,
      min:      Double           = 0.1,
      max:      Double           = 0.9,
      gutter:   Double           = 6.0,
      onResize: (Double => Unit) | Null = null,
  )(first: VNode, second: VNode): VNode =
    SplitterImpl(SplitterProps(axis, initial, min, max, gutter, onResize, first, second))
