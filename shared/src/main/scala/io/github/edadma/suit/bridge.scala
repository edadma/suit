package io.github.edadma.suit

import io.github.edadma.vdom

// The bridge from the host-agnostic `vdom` core to suit's public surface. The engine —
// VNode model, reconciler, hooks, scheduler, components, context — lives in package
// `io.github.edadma.vdom` and names no rendering type. This file re-exports the
// consumer-facing part of it under `io.github.edadma.suit`, so an application imports
// only `io.github.edadma.suit.*` (plus `suit.dsl.*` / `suit.widgets.*`) and never has to
// reach into `io.github.edadma.vdom` directly — vdom is an implementation detail, the way
// the browser's DOM engine is to a web page. This mirrors riposte's `bridge.scala`, which
// does the same for the DOM host.
//
// Type aliases carry vdom's implicit conversions for free: an expected `suit.VNode`
// dealiases to `vdom.VNode`, so the `Conversion[String, VNode]` in vdom's companion is
// still found. Case-class construction (`VElement(...)`) needs a term in scope, so each
// constructed type also gets a `val` bound to its companion.
//
// Only the host-agnostic surface is re-exported. The host wiring (`Host`, `Scheduler`,
// `HostConfig`) stays internal to the runtime — `Suit.run` installs it; tests install it
// themselves — and the DOM-shaped prop kinds (`Attr`, `StyleProp`, `RawHtml`, …) have no
// meaning on a pixel canvas, so they are deliberately absent.

// --- VNode model -----------------------------------------------------------

type VNode          = vdom.VNode
type VText          = vdom.VText
type VElement       = vdom.VElement
type VFragment      = vdom.VFragment
type VComponent[P]  = vdom.VComponent[P]
type VProvider[T]   = vdom.VProvider[T]
type VPortal        = vdom.VPortal
type VErrorBoundary = vdom.VErrorBoundary

val VText          = vdom.VText
val VElement       = vdom.VElement
val VFragment      = vdom.VFragment
val VComponent     = vdom.VComponent
val VProvider      = vdom.VProvider
val VPortal        = vdom.VPortal
val VErrorBoundary = vdom.VErrorBoundary
val VEmpty         = vdom.VEmpty

// The typed prop channel the DSL emits values through. `PropValue` wraps a real value
// (a Color, an EdgeInsets, an Alignment, an enum); `Handler` wraps an event callback.
type Prop      = vdom.Prop
type PropValue = vdom.PropValue
type Handler   = vdom.Handler

val PropValue = vdom.PropValue
val Handler   = vdom.Handler

// --- refs ------------------------------------------------------------------

type Ref[T]     = vdom.Ref[T]
type ElementRef = vdom.ElementRef
type BoxRef[T]  = vdom.BoxRef[T]
type FnRef      = vdom.FnRef

val BoxRef = vdom.BoxRef
val FnRef  = vdom.FnRef

// --- components, context, hooks state --------------------------------------

type Component[P]           = vdom.Component[P]
type Component2[A, B]       = vdom.Component2[A, B]
type Component3[A, B, C]    = vdom.Component3[A, B, C]
type Component4[A, B, C, D] = vdom.Component4[A, B, C, D]
type Container              = vdom.Container
type ContainerP[P]          = vdom.ContainerP[P]
type Children               = vdom.Children
type Context[T]             = vdom.Context[T]

type Hooks   = vdom.Hooks
type Cleanup = vdom.Cleanup
type Root    = vdom.Root

val Hooks     = vdom.Hooks
val noCleanup = vdom.noCleanup

// Presence (exit-animation lifecycle), for overlay widgets that animate on close.
type Presence      = vdom.Presence
type PresencePhase = vdom.PresencePhase
val PresencePhase  = vdom.PresencePhase

// --- hook entry points ------------------------------------------------------

def useState[T](initial: => T)(using Hooks): (T, T => Unit, (T => T) => Unit) =
  vdom.useState(initial)

def useEffect(body: () => Cleanup, deps: Array[Any] | Null)(using Hooks): Unit =
  vdom.useEffect(body, deps)

def useLayoutEffect(body: () => Cleanup, deps: Array[Any] | Null)(using Hooks): Unit =
  vdom.useLayoutEffect(body, deps)

def useRef[T](initial: T)(using Hooks): Ref[T] = vdom.useRef(initial)

def useMemo[T](compute: () => T, deps: Array[Any])(using Hooks): T = vdom.useMemo(compute, deps)

def useCallback[F](fn: F, deps: Array[Any])(using Hooks): F = vdom.useCallback(fn, deps)

def useReducer[S, A](reducer: (S, A) => S, initial: S)(using Hooks): (S, A => Unit) =
  vdom.useReducer(reducer, initial)

def useId()(using Hooks): String = vdom.useId()

def useTransition(target: Double, durationMs: Int)(using Hooks): Double =
  vdom.useTransition(target, durationMs)

def useContext[T](ctx: Context[T])(using Hooks): T = vdom.useContext(ctx)

def useSyncExternalStore[T](
    subscribe:   (() => Unit) => (() => Unit),
    getSnapshot: () => T,
)(using Hooks): T = vdom.useSyncExternalStore(subscribe, getSnapshot)

def useImperativeHandle[T](ref: Ref[T], factory: () => T, deps: Array[Any] | Null)(using Hooks): Unit =
  vdom.useImperativeHandle(ref, factory, deps)

def useDeferredValue[T](value: T)(using Hooks): T = vdom.useDeferredValue(value)

def useDebouncedValue[T](value: T, delayMs: Int)(using Hooks): T =
  vdom.useDebouncedValue(value, delayMs)

def useThrottledValue[T](value: T, intervalMs: Int)(using Hooks): T =
  vdom.useThrottledValue(value, intervalMs)

def usePresence(open: Boolean, exitMs: Int)(using Hooks): Presence =
  vdom.usePresence(open, exitMs)

/** Run `cb` once per animation frame for as long as the calling component is mounted — suit's
  * `requestAnimationFrame`. It rides the same frame seam the motion hooks use (`Suit.run`'s
  * loop is the clock), so each tick fires once per loop iteration; `cb` receives the current
  * time in milliseconds (the same clock `useTransition` eases over), letting it advance by a
  * real delta. After each tick it requests a repaint ([[Repaint]]), so a [[RenderCanvas]] whose
  * `draw` reads state the callback advanced re-runs that frame. Unmounting stops the loop.
  *
  * It advances state imperatively and repaints — it does **not** re-render the component, so
  * there is no reconcile per frame. Hold the animated state in a `useRef` (its identity is
  * stable, so the canvas's `draw` closure can read it without changing), step it in the
  * callback, and read it back in `draw`:
  * {{{
  * val sim = useRef(World.initial)
  * useFrame { now => sim.current = sim.current.step(dt) }
  * canvas(){ (c, size) => sim.current.render(c, size) }
  * }}}
  * If the callback also needs the vnode tree to change (animating a widget's props), call a
  * `useState` setter from inside it — that re-renders as usual, on top of the repaint. */
def useFrame(cb: Double => Unit)(using Hooks): Unit =
  useEffect(
    () =>
      var cancelled = false
      def tick(): Unit =
        if !cancelled then
          cb(vdom.Transition.now())
          Repaint.request() // redraw this frame even though the tree did not change
          vdom.Transition.requestFrame(() => tick())
      vdom.Transition.requestFrame(() => tick())
      () => cancelled = true,
    Array(),
  )

/** Run `cb` every `ms` milliseconds while the calling component is mounted — suit's
  * `setInterval`. It rides the same one-shot timer seam the motion hooks use
  * (`vdom.Timers`, which `Suit.run`'s loop pumps), self-rescheduling after each fire, so it
  * keeps the same cadence as `useTransition` / `usePresence` and is just as testable
  * headlessly (a `FrameClock` over a hand-advanced clock fires it deterministically). Unlike
  * [[useFrame]] it does not request a repaint — the typical use is to flip a `useState`, which
  * already re-renders; pass a no-side-effect `cb` only if it drives a repaint another way.
  *
  * `enabled` gates it: while false no timer is armed, so an idle field stops the loop instead
  * of pinning the clock active. The interval re-arms from now whenever `ms`, `enabled`, or any
  * value in `restartKeys` changes — bump a counter in `restartKeys` to restart the phase, which
  * is how a caret blink stays solid for a full interval after each keystroke. */
def useInterval(
    cb:          () => Unit,
    ms:          Int,
    enabled:     Boolean    = true,
    restartKeys: Array[Any] = Array(),
)(using Hooks): Unit =
  useEffect(
    () =>
      if !enabled then noCleanup
      else
        var cancelled                   = false
        var cancel: (() => Unit) | Null = null
        def arm(): Unit =
          cancel = vdom.Timers.schedule(
            () =>
              if !cancelled then
                cb()
                arm(),
            ms,
          )
        arm()
        () =>
          cancelled = true
          val c = cancel
          if c != null then c()
    ,
    restartKeys ++ Array[Any](ms, enabled),
  )

// --- component / container / context builders -------------------------------

def component[P](render: P => (Hooks ?=> VNode)): Component[P] = vdom.component(render)
def component[A, B](render: (A, B) => (Hooks ?=> VNode)): Component2[A, B] = vdom.component(render)
def component[A, B, C](render: (A, B, C) => (Hooks ?=> VNode)): Component3[A, B, C] = vdom.component(render)
def component[A, B, C, D](render: (A, B, C, D) => (Hooks ?=> VNode)): Component4[A, B, C, D] =
  vdom.component(render)

def view(render: Hooks ?=> VNode): Component[Unit] = vdom.view(render)

extension (c: Component[Unit]) def apply(): VNode = c.apply(())

def memo[P](c: Component[P]): Component[P]                             = vdom.memo(c)
def memo[A, B](c: Component2[A, B]): Component2[A, B]                  = vdom.memo(c)
def memo[A, B, C](c: Component3[A, B, C]): Component3[A, B, C]         = vdom.memo(c)
def memo[A, B, C, D](c: Component4[A, B, C, D]): Component4[A, B, C, D] = vdom.memo(c)
def memo(c: Container): Container                                     = vdom.memo(c)
def memo[P](c: ContainerP[P]): ContainerP[P]                          = vdom.memo(c)

def container(render: Children => (Hooks ?=> VNode)): Container = vdom.container(render)
def container[P](render: (P, Children) => (Hooks ?=> VNode)): ContainerP[P] = vdom.container(render)

def createContext[T](default: T): Context[T] = vdom.createContext(default)

// --- portal -----------------------------------------------------------------

/** Render `child` into a different part of the render tree — suit's overlay layer — while
  * leaving its logical place here untouched (context, events, and re-renders still flow from
  * here). The `target` is a host node, which on suit is a [[RenderObject]]; overlay widgets
  * pass the overlay layer they read from [[OverlayContext]]. This is how a dialog or menu
  * escapes an ancestor's clip or scroll and paints above the rest of the window. */
def portal(target: RenderObject, child: VNode): VNode = vdom.VPortal(target, child)

// --- entry point ------------------------------------------------------------

// The container is a `RenderObject` (the render root) for callers; vdom's `Root` mutates
// it through the installed `SuitHostConfig`. `Suit.run` does this for you — these are for
// advanced/embedding use.
def createRoot(container: RenderObject): Root = vdom.createRoot(container)

def render(vnode: VNode, container: RenderObject): Root = vdom.render(vnode, container)
