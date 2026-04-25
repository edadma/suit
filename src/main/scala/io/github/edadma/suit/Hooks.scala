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

final class Hooks:

  // Set by FunctionComponent.attach.
  private[suit] var engine: Engine | Null = null

  private val cells: ArrayBuffer[Any] = ArrayBuffer.empty
  private var index: Int = 0

  private[suit] def beginRender(): Unit = index = 0
  private[suit] def endRender():   Unit = ()

  // Run all effect cleanups stored in this Hooks instance. Called by the
  // reconciler when the owning ComponentNode is unmounted.
  private[suit] def runUnmountCleanups(): Unit =
    var i = 0
    while i < cells.length do
      cells(i) match
        case rec: EffectCell =>
          val cleanup = rec.cleanup
          if cleanup != null then
            rec.cleanup = null
            cleanup()
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


  // -- useContext ---------------------------------------------------------

  def useContext[T](ctx: Context[T]): T =
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
