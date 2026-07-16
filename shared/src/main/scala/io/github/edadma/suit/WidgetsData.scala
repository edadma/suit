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

      // A list with nothing left to scroll — already at an end, or shorter than its viewport —
      // leaves the wheel unclaimed, so it chains to the view outside instead of dying here.
      def onWheel(e: ScrollEvent): Unit =
        val next = math.max(0.0, math.min(s - e.deltaY * RenderScroll.WheelStep, maxS))
        if next != s then
          setScroll(next)
          e.consume()

      box(
        clip    = true,
        ref     = sizeRef,
        onWheel = onWheel,
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
      val muted = Color.lerp(theme.surfaceText, theme.surface, 0.45)

      // Which column the rows are sorted by (-1 for none) and in which direction, plus any
      // per-column width the user has dragged a column to. All three are owned here: the table is
      // uncontrolled for sort and width, so a caller drops it in and gets sortable, resizable
      // columns with no extra wiring.
      val (sortCol, setSortCol, _)     = useState(-1)
      val (asc, setAsc, _)             = useState(true)
      val (overrides, setOverrides, _) = useState(Map.empty[Int, Double])
      val (hoverCol, setHoverCol, _)   = useState(-1)
      val (dragCol, setDragCol, _)     = useState(-1)
      // The active resize drag: (column, the cursor x at press, the column's width at press). The
      // ref survives re-renders so a move computes its delta against the press, not the last frame.
      val dragStart = useRef[(Int, Double, Double)]((-1, 0.0, 0.0))

      // Size each column to its widest cell among the header and a sample of the rows (scanning
      // every row of a large result would be wasteful and the first screenful is representative),
      // clamped so no column collapses or runs away. A user-dragged width overrides the auto one.
      val cellPad   = 10.0
      val minColW   = 56.0
      val maxColW   = 360.0
      val sampleN   = math.min(p.rows.length, 200)
      val autoWidths: Vector[Double] =
        p.columns.indices.toVector.map { ci =>
          var w = TextMeasurer.installed.measure(p.columns(ci), style).width
          var r = 0
          while r < sampleN do
            val row = p.rows(r)
            if ci < row.length then w = math.max(w, TextMeasurer.installed.measure(row(ci), style).width)
            r += 1
          math.max(minColW, math.min(maxColW, w + 2 * cellPad))
        }
      def colW(ci: Int): Double = overrides.getOrElse(ci, autoWidths(ci))
      val colWidths             = p.columns.indices.toVector.map(colW)
      val contentW              = colWidths.sum

      // The display order: the original row indices permuted by the active sort column (a stable
      // lexicographic string compare — numeric columns sort as text, which a numeric table should
      // pre-format or carry a comparator for later), or identity when nothing is sorted. Selection
      // and `onSelect` stay in *original*-row terms, so the caller's index is stable across sorts.
      val order: IndexedSeq[Int] =
        if sortCol < 0 || sortCol >= p.columns.length then p.rows.indices
        else
          val sorted = p.rows.indices.sortBy(i => if sortCol < p.rows(i).length then p.rows(i)(sortCol) else "")
          if asc then sorted else sorted.reverse

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

      def headerClick(ci: Int): Unit =
        if sortCol == ci then setAsc(!asc)
        else { setSortCol(ci); setAsc(true) }

      // A thin grab handle straddling a column's right edge: a wide invisible hit area with a
      // hairline down its centre that brightens to the accent on hover or drag. Its own (no-op)
      // click keeps a press on the handle from bubbling up to the header cell's sort toggle, and
      // pointer capture keeps the drag alive once the cursor leaves the hairline.
      def resizeHandle(ci: Int): VNode =
        val hot = dragCol == ci || hoverCol == ci
        box(
          width        = 8,
          height       = p.rowHeight,
          focusable    = true,
          onMouseEnter = _ => setHoverCol(ci),
          onMouseLeave = _ => setHoverCol(-1),
          onMouseDown  = e => { setDragCol(ci); dragStart.current = (ci, e.position.x, colW(ci)) },
          onMouseMove = e =>
            if e.button != 0 then
              val (dci, sx, sw) = dragStart.current
              if dci == ci then setOverrides(overrides.updated(ci, math.max(minColW, sw + (e.position.x - sx)))),
          onMouseUp = _ => setDragCol(-1),
          onClick   = _ => (),
        )(
          center(box(width = if hot then 2 else 1, height = p.rowHeight, bg = if hot then theme.accent else theme.border)()),
        )

      // A clickable header cell: the column label (with a sort-direction caret when this is the
      // active sort column) over a resize handle pinned to the right edge. Clicking the cell sorts
      // by it; the handle on the edge resizes it.
      def headerCell(ci: Int): VNode =
        val activeSort = ci == sortCol
        val caret      = if !activeSort then "" else if asc then "▲" else "▼"
        box(width = colWidths(ci), height = p.rowHeight, onClick = _ => headerClick(ci))(
          stack(Alignment.topLeft)(
            align(Alignment.centerLeft)(
              row(crossAxisAlignment = CrossAxisAlignment.Center)(
                box(flex = 1, padding = EdgeInsets.symmetric(horizontal = cellPad, vertical = 0), clip = true)(
                  text(p.columns(ci), color = theme.surfaceText, weight = FontWeight.SemiBold),
                ),
                if activeSort then
                  box(padding = EdgeInsets.symmetric(horizontal = 4, vertical = 0))(
                    text(caret, color = muted, size = theme.textSize * 0.7),
                  )
                else sizedBox()(),
              ),
            ),
            align(Alignment.centerRight)(resizeHandle(ci)),
          ),
        )

      val header: VNode =
        box(width = contentW, height = p.rowHeight, bg = theme.surface)(
          row(crossAxisAlignment = CrossAxisAlignment.Center)(p.columns.indices.map(headerCell)*),
        )

      // Subtle zebra striping: tint alternate rows a hair toward the ink so dense data is easier
      // to track across, with the selected row marked in the accent. The striping follows the
      // displayed position; the selection follows the original row index through `order`.
      val altBg = Color.lerp(theme.surface, theme.surfaceText, 0.05)
      val body: VNode =
        virtualList(order.length, p.rowHeight) { d =>
          val orig    = order(d)
          val rowData = p.rows(orig)
          val isSel   = orig == p.selected
          // A `Paint | Null` (not `Color | Null`): passing a nullable colour to the `Paint | Null`
          // slot would route `null` through the Color→Paint conversion and yield `Solid(null)`,
          // which faults when painted. Building the Solid here keeps `null` meaning "no fill".
          val bg: Paint | Null =
            if isSel then Solid(theme.accent.withAlpha(70))
            else if d % 2 == 1 then Solid(altBg)
            else null
          box(
            width   = contentW,
            height  = p.rowHeight,
            bg      = bg,
            onClick = if p.onSelect != null then (_ => p.onSelect.asInstanceOf[Int => Unit](orig)) else null,
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
    * scroll horizontally.
    *
    * Columns are **sortable and resizable** out of the box, with no extra wiring: clicking a
    * header sorts the rows by that column (a caret shows the direction; clicking again reverses
    * it), and dragging the thin handle on a header's right edge resizes the column. The sort is a
    * lexicographic string compare, so a numeric column should be zero-padded or otherwise
    * pre-formatted to sort as expected. `selected` and `onSelect` are always in terms of the
    * **original** row index, so the caller's selection is stable no matter how the view is
    * sorted. */
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

  // --- scroll area (themed visible scrollbar) --------------------------------

  private val ScrollAreaImpl: ContainerP[(Axis, Boolean, Double)] =
    container[(Axis, Boolean, Double)] { (props, children) =>
      val (axis, both, thickness) = props
      val theme                   = useTheme()
      // The thumb reads as a translucent slug of the ink; the track is a fainter wash of the same,
      // so the bar sits over either light or dark content without a hard-coded grey.
      scrollView(
        axis               = axis,
        both               = both,
        scrollbar          = true,
        scrollbarThumb     = theme.surfaceText.withAlpha(90),
        scrollbarTrack     = theme.surfaceText.withAlpha(20),
        scrollbarThickness = thickness,
      )(children*)
    }

  /** A scrolling viewport with a **visible, draggable scrollbar** — the themed counterpart to the
    * bare [[dsl.scrollView]] (which is wheel-only). The bar rides the trailing edge (the right
    * edge for a vertical area, the bottom for a horizontal one), appears only when the content
    * overflows, and can be dragged to scroll as well as turned by the wheel; its colours come from
    * the active theme. Like `scrollView` it **must be given a bounded size** along the scroll axis
    * — that extent is the viewport it scrolls within. Give it a single content node (wrap several
    * in a `col`/`row`).
    *
    * Pass `both = true` for a viewport that scrolls on **both** axes at once — the content keeps
    * its natural size in either direction and a bar appears on each axis that overflows. Useful
    * for content with a fixed intrinsic size larger than the viewport (a document page, an image,
    * a wide table) that should neither wrap nor shrink to fit. */
  def scrollArea(axis: Axis = Axis.Vertical, both: Boolean = false, thickness: Double = 8.0)(
      children: VNode*,
  ): VNode =
    ScrollAreaImpl((axis, both, thickness))(children*)
