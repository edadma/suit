---
title: "Motion"
weight: 4
---

suit animates the same way it lays out and paints: through a small seam, driven by the
runtime, testable off-device. There is no animation framework bolted on — motion is two
vdom hooks (`useTransition` and `usePresence`) plus a frame clock the runtime pumps each
iteration. A component animates by *describing* the value it wants over time; the hook eases
toward it and asks for the frames it needs.

## The frame clock

vdom never animates on its own. It expresses motion by asking the host for two things: a
**frame** (run this callback before the next paint) and a **timer** (run this after a delay).
In a browser those are `requestAnimationFrame` and `setTimeout`. suit's runtime is already a
frame loop, so the loop *is* the clock: it installs a `FrameClock` and calls `pump()` once
per iteration, firing the due frames and timers.

The payoff is that repaint stays **dirty-gated**. An in-flight transition re-renders each
frame, which marks the tree dirty, so the loop repaints every frame *while something is
moving* — and the moment everything settles, no frames are pending, nothing marks dirty, and
the loop goes quiet. Idle UIs cost nothing.

Because the clock is just a `now` function and two queues, the whole motion path runs
headlessly on the JVM: a test installs a `FrameClock` over a hand-advanced time and pumps it
between flushes, stepping an animation frame by frame with no window and no real waiting.

## useTransition

```scala
def useTransition(target: Double, durationMs: Int)(using Hooks): Double
```

Animate a value toward `target` over `durationMs`, returning the eased current value on every
render (easeOutCubic). When `target` changes, the transition restarts from wherever the value
is at that instant. While in flight it drives its own re-renders by requesting frames; on
reaching the target it settles **exactly** on it and stops.

Drive it from state and feed the result into a style prop:

```scala
val (hover, setHover, _) = useState(false)
val amt = useTransition(if hover then 1.0 else 0.0, 120)
box(
  bg           = Color.lerp(theme.surface, theme.primary, amt),
  onMouseEnter = _ => setHover(true),
  onMouseLeave = _ => setHover(false),
)(/* … */)
```

This is exactly how the built-in widgets animate: the button blends its fill through hover
and press amounts, the checkbox scales and fades its mark, and the slider thumb glides toward
its value. Settled, each lands precisely on the target token — so animation never changes what
a test sees.

## usePresence

```scala
type Presence = (mounted: Boolean, phase: PresencePhase) // PresencePhase: Enter | Open | Exit
def usePresence(open: Boolean, exitMs: Int)(using Hooks): Presence
```

A reconciler unmounts a subtree the instant its condition turns false, which leaves no chance
to animate a close. `usePresence` bridges that: drive it with an `open` flag and the exit
duration, render the element only while `mounted` is true, and key your enter/exit animation
off `phase`.

On open the phase runs `Enter` → (one frame later) `Open`, giving two committed states to
animate between; on close it becomes `Exit` and stays mounted for `exitMs` before `mounted`
flips to false. Re-opening mid-exit cancels the pending unmount and re-enters.

```scala
val DetailPanel = component[Boolean] { open =>
  val p   = usePresence(open, exitMs = 220)
  val amt = useTransition(if p.phase == PresencePhase.Open then 1.0 else 0.0, 220)
  if p.mounted then
    box(opacity = amt, padding = EdgeInsets(top = 16 * (1 - amt), right = 0, bottom = 0, left = 0))(
      text("I fade and rise in, then ease back out before unmounting."),
    )
  else VEmpty
}
```

Pair it with `useTransition` for the actual fade/slide (as above), and it becomes the
foundation any dismissible overlay — dialog, drawer, toast, tooltip — will animate on.

[= note =]
Call the hooks **unconditionally**, before any early return — `useTransition` and
`usePresence` bind to hook cells positionally, like every other hook. In the example `amt` is
computed above the `if p.mounted` so it runs on every render.
[= /note =]
