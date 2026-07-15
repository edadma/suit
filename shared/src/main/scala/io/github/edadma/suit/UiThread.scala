package io.github.edadma.suit

import scala.collection.mutable
import scala.util.control.NonFatal

// The seam for getting work from a background thread onto the thread that owns the UI.
//
// suit is single-threaded by construction, and so is the vdom core beneath it: a `useState`
// setter writes its state cell and appends to a global dirty list with no synchronisation
// whatsoever (vdom is modelled on the browser, where that is simply true). Calling a setter
// from a worker thread therefore does not race the *scheduler queue* — it races the reconciler's
// own bookkeeping, and no amount of thread-safety down in the frame loop can repair that.
//
// So the rule is the one every UI toolkit lands on: **the render tree and every hook belong to
// the UI thread.** A worker that has produced something — a decoded frame, a finished download,
// a scan result — does not touch state itself. It hands a thunk to `post`, and the frame loop
// runs that thunk on the UI thread on its next iteration, where calling a setter is ordinary and
// safe. This is `SwingUtilities.invokeLater` / Flutter's platform-channel hop, in miniature.
//
// Only `post` is safe to call from another thread; it is the whole cross-thread surface. Nothing
// else in suit is, including `Repaint.request` — a worker that wants a redraw posts a thunk that
// asks for one.
object UiThread:

  // Work waiting to run, and the buffer a drain is currently running. The two are swapped under
  // the lock so that posting stays a brief append and the thunks themselves run *outside* the
  // lock — otherwise a thunk that calls back into `post` would deadlock, and a slow thunk would
  // block every worker in the process.
  private val lock              = new AnyRef
  private var inbox             = mutable.ArrayBuffer.empty[() => Unit]
  private var running           = mutable.ArrayBuffer.empty[() => Unit]
  @volatile private var owner: Thread | Null = null

  /** Where a posted thunk's escaped exception goes. A thunk runs on the frame loop with no
    * enclosing error boundary and nothing to return a failure to, so a throw that propagated
    * would take the window down with it; instead it is reported here and the remaining thunks
    * still run. Replace this to route worker failures into an application's own error handling.
    * The default prints the stack trace to stderr. */
  var onError: Throwable => Unit = _.printStackTrace()

  /** Record the calling thread as the UI thread. The runtime does this as the frame loop starts;
    * a headless test that cares about thread identity calls it from whichever thread plays the
    * loop's part. */
  private[suit] def claim(): Unit = owner = Thread.currentThread()

  /** Whether the caller is on the UI thread and may touch the tree, hooks, and state directly.
    * Before any thread has claimed ownership there is no loop running and nothing to race, so
    * this reads true — which keeps single-threaded headless code working untouched. */
  def isCurrent: Boolean =
    owner match
      case t: Thread => t eq Thread.currentThread()
      case null      => true

  /** Run `fn` on the UI thread as soon as the frame loop can get to it. Safe to call from any
    * thread — this is the *only* part of suit that is. The thunk may freely set state, mutate the
    * tree, and request repaints, because by the time it runs it is on the UI thread like any
    * event handler. Thunks run in the order posted; one that throws is reported to [[onError]]
    * without disturbing the others. */
  def post(fn: () => Unit): Unit = lock.synchronized { inbox += fn }

  /** Run `fn` immediately when already on the UI thread, otherwise [[post]] it. For code that can
    * be reached from either side and wants the direct path when it is available — note the two
    * cases differ in *when* the work happens (now versus a later frame), so prefer plain `post`
    * where that matters. */
  def run(fn: () => Unit): Unit = if isCurrent then fn() else post(fn)

  /** Run everything posted so far, on the calling thread, and report whether anything ran. The
    * runtime calls this once per frame-loop iteration, before draining the scheduler, so a thunk's
    * state writes are committed by the same flush that handles the frame's input.
    *
    * The queue is taken as a snapshot: a thunk that posts more work leaves it for the *next*
    * drain rather than extending this one, so a worker feeding frames at speed cannot starve the
    * loop of a present. */
  private[suit] def drain(): Boolean =
    lock.synchronized {
      val t = inbox
      inbox = running // always empty here: the previous drain cleared it
      running = t
    }
    if running.isEmpty then false
    else
      var i = 0
      while i < running.length do
        try running(i).apply()
        catch case NonFatal(e) => onError(e)
        i += 1
      running.clear()
      true

  /** Whether any posted work is waiting. */
  def pending: Boolean = lock.synchronized { inbox.nonEmpty }

  /** Drop all posted work and forget the owning thread. For tests, which share one process and
    * must not leak a queue (or a stale owner) into the next case. */
  private[suit] def reset(): Unit =
    lock.synchronized { inbox.clear() }
    running.clear()
    owner = null
