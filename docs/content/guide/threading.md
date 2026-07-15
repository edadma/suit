---
title: "Threading"
weight: 5
---

suit has one thread rule, and it is the same rule every UI toolkit arrives at:

> **The render tree, the hooks, and every piece of state belong to the thread running the
> frame loop.** Other threads do work; they do not touch the UI. They hand it over.

The handover point is `UiThread.post`, and it is the *only* part of suit that is safe to call
from another thread.

## Why the rule is absolute

It is tempting to assume the seam is about queue safety — that a synchronized queue somewhere
in the runtime would let a worker call a setter directly. It would not.

A `useState` setter, in the vdom core beneath suit, writes its state cell and appends the
component to a global dirty list, with no synchronisation at all. vdom is modelled on the
browser, where single-threadedness is simply a fact of the platform. So a worker thread calling
a setter races the **reconciler's own bookkeeping**, before anything reaches a queue. Two
threads setting state at once can lose an update, or corrupt the dirty list outright. No
amount of thread-safety further down repairs that.

Hence: workers never call setters. They post.

## Posting work

A background thread that has produced something — a decoded video frame, a finished download, a
completed scan — wraps what it wants done in a thunk and posts it:

```scala
// on a decoder thread
val frame = decodeNextFrame()
UiThread.post { () =>
  // now on the UI thread: ordinary, safe, like any event handler
  setCurrentFrame(frame)
}
```

The frame loop drains the queue once per iteration, *before* the scheduler flushes, so the
thunk's state writes commit in the same frame that handles the frame's input — not a frame
later.

Thunks run in the order posted. One that throws is reported to `UiThread.onError` (which prints
a stack trace by default; replace it to route worker failures into your own error handling) and
the remaining thunks still run — a thunk has no enclosing error boundary and nothing to return a
failure to, so an escaping throw would otherwise take the window down.

## Snapshot draining

Each drain runs exactly the work that was queued when it started. A thunk that posts more work
leaves it for the *next* drain rather than extending the current one.

This matters for exactly the case that motivates the seam: a worker feeding frames as fast as
they decode cannot hold the loop inside one drain forever and starve the window of a present.
The loop always gets back to painting.

## `run` and `isCurrent`

For code reachable from either side, `UiThread.run` executes inline when already on the UI
thread and posts otherwise:

```scala
UiThread.run(() => setStatus("done"))
```

Note the two paths differ in *when* the work happens — now, versus on a later frame. Prefer a
plain `post` where that distinction matters.

`UiThread.isCurrent` reports whether the caller may touch the tree directly. Before any thread
has claimed the loop — headless tests, single-threaded scripts — there is nothing to race, so it
reads true and `run` takes the direct path.

[= note =]
Everything else in suit is UI-thread-only, including `Repaint.request` and every
`SurfaceHandle`. A worker that wants a redraw posts a thunk that asks for one.
[= /note =]
