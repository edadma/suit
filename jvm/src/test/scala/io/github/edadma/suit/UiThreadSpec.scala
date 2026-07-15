package io.github.edadma.suit

import java.util.concurrent.{CountDownLatch, TimeUnit}
import scala.collection.mutable
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.BeforeAndAfterEach
import io.github.edadma.vdom.{Host, Scheduler}
import io.github.edadma.suit.dsl.*

// Tests for the cross-thread seam. The point of `UiThread` is that a worker never touches state
// itself — vdom's setters write a state cell and a global dirty list with no synchronisation, so a
// worker calling one corrupts the reconciler's bookkeeping, not merely a queue. These cases pin
// the contract that makes that safe: a post from any thread, drained in order on the UI thread,
// snapshotted so a re-posting thunk cannot starve the frame loop, and isolated so one bad thunk
// does not take the others (or the window) with it. The last case is the real subject — a worker
// driving a setter through `post` and the value landing committed.
class UiThreadSpec extends AnyFunSuite with BeforeAndAfterEach:

  override def beforeEach(): Unit = UiThread.reset()

  override def afterEach(): Unit =
    UiThread.reset()
    UiThread.onError = _.printStackTrace()

  // Run `body` on a fresh thread and wait for it to finish, so a test observes a genuine
  // cross-thread post rather than a same-thread one dressed up as one.
  private def onWorker(body: => Unit): Unit =
    val t = new Thread(() => body)
    t.start()
    t.join()

  test("a thunk posted from a worker runs on the draining thread, not the worker"):
    val ran = new Array[Thread](1)
    onWorker(UiThread.post(() => ran(0) = Thread.currentThread()))
    assert(ran(0) == null)  // nothing runs until the loop drains
    assert(UiThread.pending)
    assert(UiThread.drain())
    assert(ran(0) eq Thread.currentThread())

  test("drain runs thunks in the order they were posted"):
    val seen = mutable.ListBuffer.empty[Int]
    UiThread.post(() => seen += 1)
    UiThread.post(() => seen += 2)
    UiThread.post(() => seen += 3)
    UiThread.drain()
    assert(seen.toList == List(1, 2, 3))

  test("drain reports whether it had anything to run"):
    assert(!UiThread.drain())
    assert(!UiThread.pending)
    UiThread.post(() => ())
    assert(UiThread.drain())
    assert(!UiThread.drain()) // the queue is empty again

  test("every post from many concurrent workers arrives exactly once"):
    // The race the seam exists to survive: several decoder-like threads posting at once. A plain
    // ArrayBuffer append here would lose or duplicate entries, or throw outright.
    val threads = 8
    val each    = 500
    val start   = new CountDownLatch(1)
    val done    = new CountDownLatch(threads)
    val counts  = mutable.Map.empty[Int, Int].withDefaultValue(0)

    for t <- 0 until threads do
      new Thread(() => {
        start.await()
        for i <- 0 until each do UiThread.post(() => counts(t) += 1)
        done.countDown()
      }).start()

    start.countDown()
    assert(done.await(10, TimeUnit.SECONDS))
    UiThread.drain()
    assert(counts.keySet == (0 until threads).toSet)
    assert((0 until threads).forall(t => counts(t) == each))

  test("a thunk that posts more work leaves it for the next drain"):
    // Snapshot semantics: a worker feeding frames as fast as they decode must not be able to hold
    // the loop inside one drain forever and starve the present.
    val seen = mutable.ListBuffer.empty[String]
    UiThread.post { () =>
      seen += "first"
      UiThread.post(() => seen += "second")
    }
    UiThread.drain()
    assert(seen.toList == List("first")) // the re-post did not extend this drain
    UiThread.drain()
    assert(seen.toList == List("first", "second"))

  test("a throwing thunk is reported and the rest still run"):
    val seen   = mutable.ListBuffer.empty[String]
    val caught = mutable.ListBuffer.empty[String]
    UiThread.onError = e => caught += e.getMessage
    UiThread.post(() => seen += "before")
    UiThread.post(() => throw new RuntimeException("decoder blew up"))
    UiThread.post(() => seen += "after")
    UiThread.drain()
    assert(seen.toList == List("before", "after"))
    assert(caught.toList == List("decoder blew up"))

  test("isCurrent distinguishes the owning thread from a worker"):
    UiThread.claim()
    assert(UiThread.isCurrent)
    val onOther = new Array[Boolean](1)
    onWorker(onOther(0) = UiThread.isCurrent)
    assert(!onOther(0))

  test("isCurrent is true when no thread has claimed the loop"):
    // Headless, single-threaded code has no loop and nothing to race; `run` should take the
    // direct path rather than queue work no one will ever drain.
    assert(UiThread.isCurrent)
    val ran = new Array[Boolean](1)
    UiThread.run(() => ran(0) = true)
    assert(ran(0))
    assert(!UiThread.pending)

  test("run executes inline on the UI thread but posts from a worker"):
    UiThread.claim()
    val direct = new Array[Boolean](1)
    UiThread.run(() => direct(0) = true)
    assert(direct(0))     // ran now, not queued
    assert(!UiThread.pending)

    val fromWorker = new Array[Boolean](1)
    onWorker(UiThread.run(() => fromWorker(0) = true))
    assert(!fromWorker(0)) // deferred instead
    assert(UiThread.pending)
    UiThread.drain()
    assert(fromWorker(0))

  test("a worker drives a state update through post and it commits"):
    // The whole reason the seam exists: a background producer hands the UI a value, and the
    // component re-renders with it. The worker touches no hook and no tree — only `post`.
    Host.config = new SuitHostConfig
    UiThread.claim()
    val setter  = new Array[String => Unit](1)
    val painted = mutable.ListBuffer.empty[String]

    val app = view {
      val (frame, setFrame, _) = useState("none")
      setter(0) = setFrame
      painted += frame
      text(s"frame: $frame")
    }

    val root = new RenderRoot(Size(200, 100))
    createRoot(root).render(app())
    Scheduler.flushSync()
    assert(painted.toList == List("none"))

    onWorker(UiThread.post(() => setter(0)("decoded-42")))
    Scheduler.flushSync()
    assert(painted.toList == List("none")) // nothing has reached the UI thread yet

    UiThread.drain()  // the loop picks the work up ...
    Scheduler.flushSync() // ... and the setter's re-render commits in the same frame
    assert(painted.toList == List("none", "decoded-42"))
