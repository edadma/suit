package io.github.edadma.suit

// A function component with React.memo-style prop bailout. The render
// closure receives both the props (as data) and a Hooks context, so the
// component still has full hook support. Each parent re-render allocates
// a fresh wrapper carrying the latest props; the reconciler keeps the
// previously-mounted widget alive (preserving its state) and:
//
//   * if the new props equal the old ones AND no internal state change has
//     happened, marks shouldRender = false → reconciler skips render() and
//     just walks the existing child tree to surface any dirty descendants;
//   * otherwise re-renders normally.
//
// Sysl shape: a parameterized struct holding `props: P` plus a function
// pointer; equality is by sysl's structural `==` on P.
final class MemoComponent[P](
    val name: String,
    private[suit] var props: P,
    private[suit] var fn:    (P, Hooks) => View,
) extends Widget with HookCarrier:

  val hooks: Hooks = new Hooks
  private val id:  Long    = name.hashCode.toLong
  private var dirty: Boolean = true   // initial mount is always a render

  def widgetId(): Long = id

  def render(): View =
    hooks.beginRender()
    val v = fn(props, hooks)
    hooks.endRender()
    dirty = false
    v

  override def attach(engine: Engine): Unit =
    hooks.engine        = engine
    hooks.onStateChange = () => { dirty = true }

  override def updateProps(next: Widget): Unit = next match
    case m: MemoComponent[?] =>
      val newProps = m.props.asInstanceOf[P]
      if newProps != props then
        dirty = true
        props = newProps
      // Always adopt the new closure so the latest captures (e.g. event
      // handlers built in the parent) are what the next render sees.
      fn = m.fn.asInstanceOf[(P, Hooks) => View]
    case _ => ()

  override def shouldRender: Boolean = dirty


// Factory mirroring `component(name)(...)` but for memoized components.
// `props` is the type the user defines as a Props case class (or any other
// equality-friendly type); the framework compares it across renders to
// decide when to bail out.
//
// Returns a `P => Widget`. Each call to the returned function constructs a
// fresh wrapper carrying the latest props; the reconciler keeps the
// previously-mounted instance alive and copies the new props onto it.
def memo[P](name: String)(render: (P, Hooks) => View): P => Widget =
  (props: P) => new MemoComponent[P](name, props, render)
