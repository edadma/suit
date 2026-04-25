package io.github.edadma.suit

import scala.collection.mutable.ArrayBuffer

// React-style hooks for function components. Each FunctionComponent owns a
// single Hooks instance whose state cells live for the lifetime of the
// underlying ComponentNode (the "fiber"). Inside a render closure, hook
// calls are made in a fixed order; each call binds positionally to the next
// slot. Same rules of hooks as React: don't call them inside conditionals
// or loops.
//
// Effects do NOT run during render — they're queued onto the engine's
// pending-effects buffer and flushed after the commit phase finishes.
object Hooks:
  // Process-wide monotonic counter for useId. A single counter shared across
  // engines is fine for our needs — IDs only need to be unique within a
  // session.
  private var idSeq: Long = 0
  private[suit] def nextId(): String =
    idSeq = idSeq + 1
    "suit-" + idSeq

  private val noopChange: () => Unit = () => ()

  // Compute the current eased value of an in-flight transition. Lives in
  // the companion so the cell shape is private to this file.
  private[suit] def transitionCurrent(cell: TransitionCell, now: Long): Double =
    if cell.durationMs <= 0 then cell.target
    else
      val elapsed = (now - cell.startMs).toDouble
      val raw     = elapsed / cell.durationMs.toDouble
      if raw >= 1.0 then cell.target
      else
        val t  = if raw < 0.0 then 0.0 else raw
        val u  = 1.0 - t
        // easeOutCubic: 1 - (1 - t)^3
        val k  = 1.0 - u * u * u
        cell.startValue + (cell.target - cell.startValue) * k

// Anything that owns a Hooks instance — both FunctionComponent and
// MemoComponent today. The reconciler's unmount path runs cleanups on any
// node whose widget is a HookCarrier.
trait HookCarrier:
  def hooks: Hooks


final class Hooks:

  // Set by FunctionComponent.attach.
  private[suit] var engine: Engine | Null = null

  // Optional hook fired by every state-cell write. Memoized components use
  // it to mark themselves dirty so the reconciler doesn't bail past a
  // genuine internal-state change. Default no-op for plain components.
  private[suit] var onStateChange: () => Unit = Hooks.noopChange

  // Contexts this fiber has read via useContext. Tracked so that when a
  // provider's value changes, the reconciler can invalidate subscribers
  // (necessary for memoized consumers, harmless for everyone else).
  private[suit] val subscribedContexts: scala.collection.mutable.HashSet[Context[?]] =
    scala.collection.mutable.HashSet.empty

  // Mark the owning component dirty without going through a state setter.
  // Used by the reconciler to wake up memoized consumers when a context
  // value changes.
  private[suit] def invalidate(): Unit =
    onStateChange()
    val e = engine
    if e != null then e.requestRender()

  private val cells: ArrayBuffer[Any] = ArrayBuffer.empty
  private var index: Int = 0

  private[suit] def beginRender(): Unit = index = 0
  private[suit] def endRender():   Unit = ()

  // Run all effect cleanups stored in this Hooks instance. Called by the
  // reconciler when the owning ComponentNode is unmounted.
  private[suit] def runUnmountCleanups(): Unit =
    val e = engine
    var i = 0
    while i < cells.length do
      cells(i) match
        case rec: EffectCell =>
          val cleanup = rec.cleanup
          if cleanup != null then
            rec.cleanup = null
            cleanup()
        case t: TransitionCell =>
          // If the transition was still in flight, release its slot in the
          // engine's animationCount so we don't leave the host spinning.
          if t.active && e != null then
            t.active = false
            e.animationCount = e.animationCount - 1
        case _ => ()
      i = i + 1


  // -- useState -----------------------------------------------------------

  // Returns a 3-tuple: the current value, a value-setter, and an updater
  // that takes a `prev => next` function. Use `_` to discard whichever you
  // don't need at the call site:
  //
  //   val (count, setCount, _)             = hooks.useState(0)   // value-only setter
  //   val (count, _, updateCount)          = hooks.useState(0)   // updater-only
  //   val (count, setCount, updateCount)   = hooks.useState(0)   // both
  //
  // Sysl: same destructuring (`val (count, set_count, _) = hooks.use_state(0)`).
  def useState[T](initial: T): (T, T => Unit, (T => T) => Unit) =
    val i = index
    if i >= cells.length then cells += initial
    val current = cells(i).asInstanceOf[T]
    val setter:  T => Unit       = v  => cellSet(i, v)
    val updater: (T => T) => Unit = fn => cellSet(i, fn(cellGet[T](i)))
    index = index + 1
    (current, setter, updater)

  // Internal — read/write a state cell.
  private[suit] def cellGet[T](slot: Int): T = cells(slot).asInstanceOf[T]
  private[suit] def cellSet[T](slot: Int, value: T): Unit =
    val prev = cells(slot)
    cells(slot) = value
    if prev != value then
      onStateChange()
      val e = engine
      if e != null then e.requestRender()


  // -- useReducer ---------------------------------------------------------

  def useReducer[S, A](reducer: (S, A) => S, initial: S): (S, A => Unit) =
    val (state, _, updateState) = useState(initial)
    val dispatch: A => Unit = action => updateState(prev => reducer(prev, action))
    (state, dispatch)


  // -- useRef -------------------------------------------------------------

  def useRef[T](initial: T): Ref[T] =
    val i = index
    if i >= cells.length then cells += new Ref[T](initial)
    val r = cells(i).asInstanceOf[Ref[T]]
    index = index + 1
    r


  // -- useMemo / useCallback ---------------------------------------------

  def useMemo[T](compute: () => T, deps: Array[Any]): T =
    val i = index
    if i >= cells.length then
      val cell = new MemoCell(deps, compute())
      cells += cell
      index = index + 1
      cell.value.asInstanceOf[T]
    else
      val cell = cells(i).asInstanceOf[MemoCell]
      if !sameDeps(cell.deps, deps) then
        cell.deps  = deps
        cell.value = compute()
      index = index + 1
      cell.value.asInstanceOf[T]

  // Convenience around useMemo for stabilizing function references.
  def useCallback[F](fn: F, deps: Array[Any]): F =
    useMemo(() => fn, deps)


  // -- useEffect / useLayoutEffect ---------------------------------------

  // Queue a side-effect to run after the current render commits. The body
  // returns an optional cleanup closure that runs before the next body or
  // when the component unmounts. Pass `null` cleanup if there's nothing to
  // tear down. `deps` of `null` means "run every render"; an empty array
  // means "run once on mount".
  def useEffect(body: () => () => Unit, deps: Array[Any]): Unit =
    scheduleHookedEffect(body, deps, layout = false)

  // Same shape as `useEffect`, but the body fires *after layout, before
  // render*. Use this for code that has to inspect measured bounds and
  // mutate state before the user sees the frame (e.g. positioning a
  // tooltip, scrolling a freshly-mounted item into view).
  def useLayoutEffect(body: () => () => Unit, deps: Array[Any]): Unit =
    scheduleHookedEffect(body, deps, layout = true)

  private def scheduleHookedEffect(
      body:   () => () => Unit,
      deps:   Array[Any],
      layout: Boolean,
  ): Unit =
    val i = index
    index = index + 1
    val e = engine
    if e == null then return
    val schedule: EffectCell => Unit =
      if layout then e.scheduleLayoutEffect else e.scheduleEffect
    if i >= cells.length then
      val rec = new EffectCell(deps, null, body)
      cells += rec
      schedule(rec)
    else
      val rec = cells(i).asInstanceOf[EffectCell]
      if deps == null || !sameDeps(rec.deps, deps) then
        rec.deps     = deps
        rec.nextBody = body
        schedule(rec)


  // -- useId --------------------------------------------------------------

  // Returns a stable unique identifier for this hook slot — generated once
  // on first render, then returned unchanged on every subsequent render of
  // the same fiber. Useful for accessibility-style label/input pairing or
  // any case where you need a unique string but have nothing data-derived
  // to use.
  def useId(): String =
    val i = index
    if i >= cells.length then cells += Hooks.nextId()
    val id = cells(i).asInstanceOf[String]
    index = index + 1
    id


  // -- useTransition ------------------------------------------------------

  // Drives a value toward `target` over `durationMs`, returning the eased
  // current value on every render. When `target` changes, the transition
  // restarts from wherever the value was at that moment. While in flight
  // the hook holds a slot in the engine's `animationCount`, which keeps the
  // host's lazy frame loop ticking until the value settles.
  //
  // `easeOutCubic` is hard-coded as a sensible default; a richer API can
  // take an `easing: Double => Double` parameter once it's needed.
  def useTransition(target: Double, durationMs: Int): Double =
    val i = index
    index = i + 1
    val e = engine
    val now: Long = if e == null then 0L else e.nowMs
    if i >= cells.length then
      cells += new TransitionCell(target, target, now, durationMs, false)
      return target

    val cell = cells(i).asInstanceOf[TransitionCell]

    if cell.target != target then
      val curr = Hooks.transitionCurrent(cell, now)
      cell.startValue = curr
      cell.target     = target
      cell.startMs    = now
      cell.durationMs = durationMs
      if !cell.active then
        cell.active = true
        if e != null then e.animationCount = e.animationCount + 1

    val current = Hooks.transitionCurrent(cell, now)
    if current == cell.target && cell.active then
      cell.active = false
      if e != null then e.animationCount = e.animationCount - 1
    current


  // -- useContext ---------------------------------------------------------

  def useContext[T](ctx: Context[T]): T =
    subscribedContexts += ctx
    val e = engine
    if e == null then ctx.default
    else e.contextValue(ctx)


  private def sameDeps(a: Array[Any], b: Array[Any]): Boolean =
    if a == null || b == null then false
    else if a.length != b.length then false
    else
      var i = 0
      var eq = true
      while i < a.length && eq do
        if a(i) != b(i) then eq = false
        i = i + 1
      eq


// A simple mutable cell for useRef. `current` may be reassigned freely;
// changes do NOT trigger a re-render.
final class Ref[T](var current: T)


// Internal record stored in a Hooks cell for one useEffect call.
private[suit] final class EffectCell(
    var deps:     Array[Any],
    var cleanup:  () => Unit,
    var nextBody: () => () => Unit,
)


// Internal record for one useMemo / useCallback call.
private final class MemoCell(
    var deps:  Array[Any],
    var value: Any,
)


// Internal record for one useTransition call. `active` tracks whether the
// hook is currently holding a slot in the engine's animation counter.
private[suit] final class TransitionCell(
    var startValue: Double,
    var target:     Double,
    var startMs:    Long,
    var durationMs: Int,
    var active:     Boolean,
)
