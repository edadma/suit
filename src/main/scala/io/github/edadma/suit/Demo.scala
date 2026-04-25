package io.github.edadma.suit

import scala.collection.mutable.ArrayBuffer

// Showcases the React-shaped feature set:
//   * function components (plain `def`s)
//   * hooks (useState with functional update, useReducer, useMemo, useEffect,
//     useRef, useContext)
//   * keys for list reconciliation across reorders/inserts
//   * Fragment for multi-child returns
//   * Context for theme propagation
object Demo:

  // -- Theme context ---------------------------------------------------------

  // Two themes the app can switch between. Carried via a Context so any
  // descendant function component can read it without prop-drilling.
  enum AppTheme:
    case Dark
    case Light

  val ThemeCtx: Context[AppTheme] = createContext(AppTheme.Dark)

  // -- Application state -----------------------------------------------------

  private var theme:        AppTheme            = AppTheme.Dark
  private var items:        ArrayBuffer[String] = ArrayBuffer("Alice", "Bob", "Carol", "Dave")
  private var nextId:       Int                 = 1

  private val engine: Engine = new Engine

  private val rerender: () => Unit = () => engine.setRoot(view())

  // -- App-level handlers ----------------------------------------------------

  private def shuffleItems(): Unit =
    val rng = new scala.util.Random
    items = ArrayBuffer.from(rng.shuffle(items.toSeq))
    rerender()

  private def addItem(): Unit =
    items += ("Item " + nextId)
    nextId = nextId + 1
    rerender()

  private def removeFirst(): Unit =
    if items.nonEmpty then items.remove(0)
    rerender()

  private def toggleTheme(): Unit =
    theme = if theme == AppTheme.Dark then AppTheme.Light else AppTheme.Dark
    applyTheme()
    rerender()

  private def applyTheme(): Unit = theme match
    case AppTheme.Dark =>
      engine.theme.bg              = Color(22, 22, 32)
      engine.theme.fg              = Color(220, 220, 240)
      engine.theme.muted           = Color(140, 140, 160)
      engine.theme.btnBg           = Color(40, 40, 55)
      engine.theme.btnHoverBg      = Color(55, 55, 75)
      engine.theme.btnPressedBg    = Color(30, 30, 45)
      engine.theme.inputBg         = Color(15, 15, 22)
      engine.theme.inputFocusBg    = Color(20, 22, 32)
      engine.theme.inputBorder     = Color(60, 60, 80)
    case AppTheme.Light =>
      engine.theme.bg              = Color(248, 248, 252)
      engine.theme.fg              = Color(30, 30, 40)
      engine.theme.muted           = Color(120, 120, 140)
      engine.theme.btnBg           = Color(225, 225, 235)
      engine.theme.btnHoverBg      = Color(210, 210, 225)
      engine.theme.btnPressedBg    = Color(200, 200, 215)
      engine.theme.inputBg         = Color(255, 255, 255)
      engine.theme.inputFocusBg    = Color(248, 250, 255)
      engine.theme.inputBorder     = Color(200, 200, 215)

  // -- Function components ---------------------------------------------------

  // Plain function component: stateless inline composition.
  private def field(label: String, body: View): View =
    Stack(
      axis = Axis.Horizontal,
      gap  = 8,
      children = Array(Text(label), body),
    )


  // Function component with hooks: useState (with functional update),
  // useReducer for a separate counter, and useMemo to demo memoization.
  private def Counter(label: String, step: Int): Widget =
    component("Counter") { hooks =>
      val (count, setCount, updateCount) = hooks.useState(0)

      // Reducer-driven counter — same fiber holds both useState and useReducer.
      val (history, dispatch) =
        hooks.useReducer[List[Int], CounterAction](
          (state, action) => action match
            case CounterAction.Push(n) => n :: state
            case CounterAction.Clear   => Nil,
          List.empty[Int],
        )

      // Memo-cached derived value. Recomputes only when `count` changes.
      val doubled = hooks.useMemo(() => count * 2, deps = Array(count))

      // Effect: log on mount and whenever count changes; cleanup on unmount.
      hooks.useEffect(
        () => {
          println(s"[$label] count is now $count")
          () => println(s"[$label] cleanup for count=$count")
        },
        deps = Array(count),
      )

      Stack(
        axis = Axis.Horizontal,
        gap  = 8,
        children = Array(
          Text(s"$label: $count (×2 = $doubled)"),
          Button("-" + step, () => updateCount(_ - step)),
          Button("+" + step, () => updateCount(_ + step)),
          Button("Reset",    () => setCount(0)),
          Button("Push",     () => dispatch(CounterAction.Push(count))),
          Button("Clear log", () => dispatch(CounterAction.Clear)),
          Text("log: " + history.mkString(",")),
          Spacer(),
        ),
      )
    }


  // An inner badge component nested inside Row. Used to demonstrate that
  // child-first cleanup ordering — the badge's unmount log must appear
  // *before* its parent Row's unmount log when a row is removed.
  private def RowBadge(label: String): Widget =
    component("RowBadge") { hooks =>
      hooks.useEffect(
        () => {
          println(s"  [Badge $label] mounted")
          () => println(s"  [Badge $label] cleanup (child)")
        },
        deps = Array.empty[Any],
      )
      Text("· badge")
    }


  // A list-row component that takes the item label as a prop. Keys are
  // attached at the call site (in `view`) so the reconciler matches rows
  // across shuffles/inserts and per-row state survives.
  private def Row(label: String): Widget =
    component("Row") { hooks =>
      val (clicks, _, updateClicks) = hooks.useState(0)

      hooks.useEffect(
        () => {
          println(s"[Row $label] mounted")
          () => println(s"[Row $label] cleanup (parent) after $clicks clicks")
        },
        deps = Array.empty[Any],   // run once on mount, cleanup on unmount
      )

      Stack(
        axis = Axis.Horizontal,
        gap  = 8,
        children = Array(
          Text(label),
          Button("Click (" + clicks + ")", () => updateClicks(_ + 1)),
          Component(RowBadge(label)),
          Spacer(),
        ),
      )
    }


  // A function component that auto-focuses its input on mount. Demonstrates
  // ref forwarding (WithRef) plus useLayoutEffect — focus is moved before
  // the first paint so the user sees the focus halo immediately.
  private def AutoFocusInput(): Widget =
    component("AutoFocusInput") { hooks =>
      val (value, setValue, _) = hooks.useState("")
      val nodeRef              = hooks.useRef[Node | Null](null)
      val mountedRef           = hooks.useRef(false)

      // useLayoutEffect runs after layout but before render; if we tried to
      // do this in useEffect, the user would see one frame with the input
      // unfocused before focus moved.
      hooks.useLayoutEffect(
        () => {
          if !mountedRef.current then
            mountedRef.current = true
            val n = nodeRef.current
            val e = hooks.engine
            if e != null && n != null then e.focusNode(n)
          () => { nodeRef.current = null }
        },
        deps = Array.empty[Any],
      )

      WithRef(nodeRef, Input(
        value       = value,
        placeholder = "auto-focused on mount",
        onChange    = setValue,
        width       = 240,
      ))
    }


  // A small "form field" component that uses useId to give each input a
  // stable identity. The id is shown in the label so you can see it persist
  // across re-renders.
  private def IdField(label: String): Widget =
    component("IdField") { hooks =>
      val id                   = hooks.useId()
      val (value, setValue, _) = hooks.useState("")
      Stack(
        axis = Axis.Horizontal,
        gap  = 8,
        children = Array(
          Text(label + " (" + id + "):"),
          Input(value = value, onChange = setValue, width = 200),
        ),
      )
    }


  // A function component that reads from context — no prop drilling needed.
  private def ThemeIndicator(): Widget =
    component("ThemeIndicator") { hooks =>
      val current = hooks.useContext(ThemeCtx)
      val name = current match
        case AppTheme.Dark  => "dark"
        case AppTheme.Light => "light"
      Text("Theme via useContext: " + name)
    }


  // -- View ------------------------------------------------------------------

  private def view(): View =
    ThemeCtx.provide(theme,
      Stack(
        axis    = Axis.Vertical,
        padding = Insets.all(16),
        gap     = 10,
        children = Array(
          Text("Suit demo — keys, hooks, context, fragments"),

          // Two independent counters via the same FunctionComponent factory.
          // Each has its own fiber, so useState slots don't collide.
          Component(Counter("Count", step = 1)),
          Component(Counter("Big",   step = 10)),

          // Context-reading component.
          Component(ThemeIndicator()),

          // Auto-focuses on mount via WithRef + useLayoutEffect.
          Component(AutoFocusInput()),

          // useId — each instance has its own stable id displayed in the label.
          Component(IdField("First")),
          Component(IdField("Second")),

          // Fragment splices its children into this Stack inline. The Greet
          // and Theme buttons sit at the same level as the Counter rows —
          // no extra wrapping Stack needed.
          Fragment(Array(
            Stack(
              axis = Axis.Horizontal,
              gap  = 8,
              children = Array(
                Button("Toggle theme", () => toggleTheme()),
                Button("Add row",      () => addItem()),
                Button("Remove first", () => removeFirst()),
                Button("Shuffle",      () => shuffleItems()),
                Spacer(),
              ),
            ),
          )),

          // Keyed list. Each row uses its label as a key; the reconciler
          // matches rows across shuffles, so each row's click-count and
          // mount-effect identity follow the row, not the position.
          Stack(
            axis    = Axis.Vertical,
            gap     = 4,
            padding = Insets.sym(0, 4),
            children = items.map(label =>
              Keyed(label, Component(Row(label))): View
            ).toArray,
          ),

          Spacer(),
          Text("Try shuffle/remove and watch row click-counts follow the labels."),
        ),
      ))

  def main(args: Array[String]): Unit =
    applyTheme()
    engine.setRoot(view())
    val host = new SwingHost("Suit demo", 720, 560, engine)
    host.show()


// CounterAction needs to live somewhere visible; keeping it next to Demo
// since it's only used by the Counter component above.
sealed trait CounterAction
object CounterAction:
  final case class Push(n: Int) extends CounterAction
  case object Clear              extends CounterAction
