package io.github.edadma.suit

import scala.collection.mutable
import io.github.edadma.vdom.{Transition, Timers}

// The motion clock that drives vdom's animation hooks (`useTransition`, `usePresence`).
//
// vdom never animates on its own; it expresses motion by asking the host for two things
// through indirection seams: animation frames (`Transition.requestFrame` /
// `cancelFrame`, with `Transition.now` for the current time) and one-shot timers
// (`Timers.schedule`). A browser host wires those to `requestAnimationFrame` and
// `setTimeout`. suit's runtime is already a frame loop, so the loop itself is the clock:
// a FrameClock installs the seams over in-memory queues, and the loop calls `pump()`
// once per iteration to fire the frame callbacks and any elapsed timers.
//
// Because the clock is just a `now` function plus two queues, the whole motion path is
// exercised headlessly on the JVM: a test installs a FrameClock over a hand-advanced
// time and calls `pump()` between flushes, stepping any animation frame by frame with no
// window and no wall-clock waiting. This keeps animation as testable as layout and paint.
final class FrameClock(now: () => Double):

  // One id space for both frames and timers: any positive int both distinguishes a live
  // registration (vdom treats a frame id < 0 as "none") and serves as the cancel handle.
  private var nextId = 1
  private val frames = mutable.Map.empty[Int, () => Unit]
  private val timers = mutable.Map.empty[Int, (Double, () => Unit)]

  /** Wire this clock into vdom's motion seams. Call once, before the first mount; the
    * runtime does this during `Suit.run` setup. */
  def install(): Unit =
    Transition.now = () => now()
    Transition.requestFrame = fn =>
      val id = nextId
      nextId += 1
      frames(id) = fn
      id
    Transition.cancelFrame = id => frames.remove(id): Unit
    Timers.schedule = (fn, delayMs) =>
      val id = nextId
      nextId += 1
      timers(id) = (now() + delayMs, fn)
      () => timers.remove(id): Unit

  /** Whether any frame callback or timer is still pending — i.e. the UI is in motion and
    * the loop has reason to keep ticking. When this is false the loop falls back to
    * repainting only on a dirty event. */
  def active: Boolean = frames.nonEmpty || timers.nonEmpty

  /** Fire every frame callback registered up to now and every timer whose deadline has
    * elapsed against `now`. The pending frames are snapshotted and cleared first, so a
    * callback that re-requests a frame (an animation still in flight) is enqueued for the
    * NEXT pump rather than this one — one pump advances an animation by exactly one frame.
    * The callbacks only enqueue re-renders through the scheduler; the caller drains the
    * scheduler afterward to commit them. Returns whether anything fired. */
  def pump(): Boolean =
    var fired = false

    if timers.nonEmpty then
      val t   = now()
      val due = timers.iterator.collect { case (id, (deadline, fn)) if deadline <= t => (id, fn) }.toArray
      var i   = 0
      while i < due.length do
        val (id, fn) = due(i)
        timers.remove(id)
        fn()
        fired = true
        i += 1

    if frames.nonEmpty then
      val due = frames.valuesIterator.toArray
      frames.clear()
      var i = 0
      while i < due.length do
        due(i).apply()
        fired = true
        i += 1

    fired
