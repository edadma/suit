package io.github.edadma.suit

// Function-component wrapper. The user writes a render closure that takes a
// Hooks context and returns a View; each render of the parent calls the
// `component(name)(...)` factory which produces a fresh wrapper carrying
// the latest closure (so it captures the latest props). The reconciler
// matches by `name` (its widgetId), keeps the old wrapper alive, and
// `updateProps` swaps the closure in. The closure executes against the
// kept-alive Hooks instance, so positional state cells line up across
// renders and hooks behave like in React.
final class FunctionComponent(val name: String) extends Widget:

  private[suit] var fn:    Hooks => View = FunctionComponent.emptyFn
  private[suit] val hooks: Hooks         = new Hooks
  private val id: Long = name.hashCode.toLong

  def widgetId(): Long = id

  def render(): View =
    hooks.beginRender()
    val v = fn(hooks)
    hooks.endRender()
    v

  override def attach(engine: Engine): Unit =
    hooks.engine = engine

  override def updateProps(next: Widget): Unit = next match
    case fc: FunctionComponent => fn = fc.fn
    case _                     => ()

object FunctionComponent:
  private val emptyFn: Hooks => View = _ => Empty


// Public factory. Use like:
//
//   val view: View = Component(component("Counter") { hooks =>
//     val (count, setCount) = hooks.useState(0)
//     ...
//   })
//
// The factory builds a fresh Widget each call; the reconciler keeps the
// previously-mounted instance alive and copies the new closure onto it via
// `updateProps`, so call-site closures (which capture the latest props) are
// always what gets executed against the persistent hooks state.
def component(name: String)(render: Hooks => View): Widget =
  val fc = new FunctionComponent(name)
  fc.fn = render
  fc
