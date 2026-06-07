package io.github.edadma.suit

import org.scalatest.funsuite.AnyFunSuite
import io.github.edadma.vdom.{Host, Scheduler, Transition, Timers}
import io.github.edadma.suit.widgets.*

// Headless tests for motion. The animation hooks drive themselves through vdom's frame
// and timer seams, and `FrameClock` installs those seams over in-memory queues fed by a
// hand-advanced clock — so an animation can be stepped frame by frame, and run to its
// target, with no window and no real time passing. These tests exercise the clock itself,
// then a widget animating mid-flight, then the presence (exit-delay) path widgets ride.
class MotionSpec extends AnyFunSuite:

  private def allObjects(o: RenderObject): List[RenderObject] = o :: o.children.toList.flatMap(allObjects)
  private def focusableBox(root: RenderObject): RenderBox =
    allObjects(root).collectFirst { case b: RenderBox if b.focusable => b }.get

  // --- the clock -----------------------------------------------------------

  test("a requested frame fires once on the next pump and does not re-arm itself"):
    var t = 0.0
    val clock = new FrameClock(() => t)
    clock.install()
    var fired = 0
    Transition.requestFrame(() => fired += 1)
    assert(clock.active)
    assert(clock.pump())
    assert(fired == 1)
    assert(!clock.active)  // the callback did not request another frame
    assert(!clock.pump())  // so a further pump fires nothing
    assert(fired == 1)

  test("a frame cancelled before the pump never fires"):
    var t = 0.0
    val clock = new FrameClock(() => t)
    clock.install()
    var fired = 0
    val id = Transition.requestFrame(() => fired += 1)
    Transition.cancelFrame(id)
    assert(!clock.active)
    clock.pump()
    assert(fired == 0)

  test("a self-requesting frame advances exactly one step per pump"):
    var t = 0.0
    val clock = new FrameClock(() => t)
    clock.install()
    var steps = 0
    def loop(): Unit =
      steps += 1
      if steps < 3 then Transition.requestFrame(() => loop())
    Transition.requestFrame(() => loop())
    clock.pump(); assert(steps == 1)
    clock.pump(); assert(steps == 2)
    clock.pump(); assert(steps == 3)
    assert(!clock.active)
    clock.pump(); assert(steps == 3)

  test("a timer fires only once its delay has elapsed against the clock"):
    var t = 0.0
    val clock = new FrameClock(() => t)
    clock.install()
    var fired = 0
    Timers.schedule(() => fired += 1, 200)
    assert(clock.active)
    clock.pump(); assert(fired == 0) // t = 0, not due
    t = 199; clock.pump(); assert(fired == 0)
    t = 200; clock.pump(); assert(fired == 1)
    assert(!clock.active)

  test("a cancelled timer never fires"):
    var t = 0.0
    val clock = new FrameClock(() => t)
    clock.install()
    var fired  = 0
    val cancel = Timers.schedule(() => fired += 1, 100)
    cancel()
    assert(!clock.active)
    t = 1000
    clock.pump()
    assert(fired == 0)

  // --- a widget animating --------------------------------------------------

  test("the button tint eases between states rather than snapping"):
    Host.config = new SuitHostConfig
    var t     = 0.0
    val clock = new FrameClock(() => t)
    clock.install()
    val root = new RenderRoot(Size(200, 100))
    createRoot(root).render(Button("OK", () => ()))
    Scheduler.flushSync()
    root.layout(Constraints.tight(Size(200, 100)))
    val pointer = new PointerRouter(root, new FocusManager)

    val primary = Theme.default.primary
    val hover   = Theme.default.primaryHover

    // At rest the fill is exactly the primary token.
    assert(focusableBox(root).background == Solid(primary))

    // Hovering starts the fade. Commit the state change so the transition records its
    // start time, then advance halfway through the 120ms fade and pump one frame.
    pointer.move(Offset(50, 50)) // mouseenter -> setHover(true)
    Scheduler.flushSync()
    t = 60
    clock.pump()
    Scheduler.flushSync()

    // Mid-flight the fill is a blend: neither endpoint, and each channel lies between them.
    focusableBox(root).background match
      case Solid(c) =>
        assert(c != primary && c != hover)
        def between(x: Int, a: Int, b: Int): Boolean = x >= math.min(a, b) && x <= math.max(a, b)
        assert(between(c.r, primary.r, hover.r))
        assert(between(c.g, primary.g, hover.g))
        assert(between(c.b, primary.b, hover.b))
      case other => fail(s"expected a solid fill, got $other")

    // Past the duration it settles exactly on the hover token.
    t = 1000
    clock.pump()
    Scheduler.flushSync()
    assert(focusableBox(root).background == Solid(hover))

  // --- presence (exit-delay) -----------------------------------------------

  test("usePresence holds an element mounted through its exit delay, then drops it"):
    Host.config = new SuitHostConfig
    var t     = 0.0
    val clock = new FrameClock(() => t)
    clock.install()

    var setOpen: Boolean => Unit = _ => ()
    var presence: Presence       = (mounted = false, phase = PresencePhase.Exit)
    val probe = view {
      val (open, set, _) = useState(true)
      setOpen  = set
      presence = usePresence(open, exitMs = 200)
      VEmpty
    }
    val root = new RenderRoot(Size(10, 10))
    createRoot(root).render(probe())
    Scheduler.flushSync()
    assert(presence.mounted)
    assert(presence.phase == PresencePhase.Enter) // just mounted; advances next frame

    // A frame later it is Open (the enter->open transition has two states to animate).
    clock.pump()
    Scheduler.flushSync()
    assert(presence.phase == PresencePhase.Open)

    // Closing keeps it mounted in the Exit phase and arms the exit timer.
    setOpen(false)
    Scheduler.flushSync()
    assert(presence.mounted)
    assert(presence.phase == PresencePhase.Exit)
    assert(clock.active)

    // Before the delay it stays mounted; once the delay elapses it is finally dropped.
    clock.pump() // t = 0, timer not due
    Scheduler.flushSync()
    assert(presence.mounted)
    t = 200
    clock.pump()
    Scheduler.flushSync()
    assert(!presence.mounted)
