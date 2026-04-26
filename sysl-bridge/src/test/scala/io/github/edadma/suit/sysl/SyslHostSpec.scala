package io.github.edadma.suit.sysl

import io.github.edadma.trisc.Value
import io.github.edadma.trisc.Value.ClosureVal
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

import scala.collection.mutable

class SyslHostSpec extends AnyFreeSpec with Matchers:

  // Resolve the test-resources directory at runtime — sbt's classloader
  // exposes it via getResource, but the SyslDriver needs a filesystem path
  // because it reads transitive imports off disk.
  private def resourcesDir: String =
    val url = getClass.getClassLoader.getResource("hello.sysl")
    require(url != null, "hello.sysl not on the test classpath")
    val path = java.nio.file.Paths.get(url.toURI).getParent
    path.toString

  "SyslHost" - {
    "executes a sysl program that calls a JVM-installed builtin" in {
      val host = new SyslHost(resourcesDir)
      val log  = mutable.ArrayBuffer.empty[String]

      host.register("host_log", {
        case List(msg) =>
          log += SyslHost.asString(msg)
          SyslHost.unit
        case other => fail(s"host_log: bad args $other")
      })
      host.register("host_now_ms", {
        case Nil   => SyslHost.long(System.currentTimeMillis())
        case other => fail(s"host_now_ms: bad args $other")
      })

      val program = host.compileFile("hello.sysl")
      val result  = host.run(program)

      result shouldBe 56L                                // compute(7, 8) = 56
      log should contain ("hello from sysl")
      log should contain ("starting work")
      log.exists(_.startsWith("result = 56")) shouldBe true
      log.exists(_.startsWith("elapsed ="))   shouldBe true
    }

    "round-trips a closure: sysl hands a handler to the host, host invokes it back" in {
      val host = new SyslHost(resourcesDir)

      // Slot for the closure the sysl program hands us, and a slot for the
      // most recently published count.
      var handler: ClosureVal = null
      var lastPublished: Long = -1L

      host.register("host_register_handler", {
        case List(c: ClosureVal) =>
          handler = c
          SyslHost.unit
        case other => fail(s"host_register_handler: bad args $other")
      })
      host.register("host_publish_count", {
        case List(n) =>
          lastPublished = SyslHost.asLong(n)
          SyslHost.unit
        case other => fail(s"host_publish_count: bad args $other")
      })

      val program = host.compileFile("click.sysl")
      host.run(program)
      handler should not be null
      lastPublished shouldBe -1L                // closure not invoked yet

      // "Click" three times — host fires the handler, sysl-side counter
      // advances, host gets a published value each time.
      val r1 = host.interpreter.invokeClosure(handler, Nil)
      SyslHost.asLong(r1) shouldBe 1L
      lastPublished       shouldBe 1L

      val r2 = host.interpreter.invokeClosure(handler, Nil)
      SyslHost.asLong(r2) shouldBe 2L
      lastPublished       shouldBe 2L

      val r3 = host.interpreter.invokeClosure(handler, Nil)
      SyslHost.asLong(r3) shouldBe 3L
      lastPublished       shouldBe 3L
    }

    "marshals a sysl-built View tree into Scala suit Views, closures intact" in {
      val host = new SyslHost(resourcesDir)
      var captured: io.github.edadma.suit.View = io.github.edadma.suit.Empty

      host.register("host_set_root", {
        case List(v) =>
          captured = SyslView.marshal(v, host.interpreter)
          SyslHost.unit
        case other => fail(s"host_set_root: bad args $other")
      })

      val program = host.compileFile("suit/view.sysl")
      host.run(program)

      // sysl returned `Stack([Text, Text, Button])` — variable-arity slice.
      captured match
        case io.github.edadma.suit.Stack(io.github.edadma.suit.Axis.Horizontal, children, _, _) =>
          children.length shouldBe 3
          children(0) match
            case io.github.edadma.suit.Text("first")  => succeed
            case other => fail(s"children(0): got $other")
          children(1) match
            case io.github.edadma.suit.Text("second") => succeed
            case other => fail(s"children(1): got $other")
          children(2) match
            case io.github.edadma.suit.Button(label, onClick, _) =>
              label shouldBe "Click"
              // Round-trip the closure: each Scala-side click must
              // increment the sysl-side `click_count` global.
              onClick()
              onClick()
              onClick()
            case other => fail(s"children(2): got $other")
        case other =>
          fail(s"expected Stack(Horizontal, …), got $other")
    }

    "marshals Sized / Center / Spacer / Image / Stack2 / Stack3 / Checkbox" in {
      val host = new SyslHost(resourcesDir)
      var captured: io.github.edadma.suit.View = io.github.edadma.suit.Empty

      host.register("host_set_root", {
        case List(v) =>
          captured = SyslView.marshal(v, host.interpreter)
          SyslHost.unit
        case other => fail(s"host_set_root: bad args $other")
      })

      val program = host.compileFile("suit/view2.sysl")
      host.run(program)

      // Top-level: Stack3 → suit Stack(Horizontal, 3 children).
      captured match
        case io.github.edadma.suit.Stack(io.github.edadma.suit.Axis.Horizontal, kids, _, _) =>
          kids.length shouldBe 3

          // kids(0): Sized(Text("title"), 200, 24)
          kids(0) match
            case io.github.edadma.suit.Sized(child, 200, 24) =>
              child match
                case io.github.edadma.suit.Text("title") => succeed
                case other => fail(s"Sized child should be Text(title), got $other")
            case other => fail(s"kids(0): expected Sized, got $other")

          // kids(1): Center(Image("icon.png", 32, 32))
          kids(1) match
            case io.github.edadma.suit.Center(child) =>
              child match
                case io.github.edadma.suit.Image("icon.png", 32, 32) => succeed
                case other => fail(s"Center child: expected Image, got $other")
            case other => fail(s"kids(1): expected Center, got $other")

          // kids(2): Stack2(Checkbox, Button)
          kids(2) match
            case io.github.edadma.suit.Stack(io.github.edadma.suit.Axis.Horizontal, inner, _, _) =>
              inner.length shouldBe 2
              val checkbox = inner(0).asInstanceOf[io.github.edadma.suit.Checkbox]
              checkbox.label shouldBe "agree"
              checkbox.checked shouldBe false
              // Toggle closure with a bool arg — round-trip true and false.
              checkbox.onToggle(true)
              checkbox.onToggle(false)

              val button = inner(1).asInstanceOf[io.github.edadma.suit.Button]
              button.label shouldBe "Submit"
              button.onClick()
              button.onClick()
            case other => fail(s"kids(2): expected Stack2, got $other")

        case other =>
          fail(s"expected outer Stack3 → Stack(Horizontal, 3), got $other")
    }

    // Scope 2 milestone — a sysl-side render pipeline. Sysl owns View,
    // Node, mount, measure, layout, render; the JVM only exposes
    // draw-emit builtins. Validates that the engine's algorithmic core
    // ports cleanly without needing the JVM-side Engine at all.
    "drives a complete sysl-side render pipeline (mount + layout + render)" in {
      val cmds = mutable.ArrayBuffer.empty[String]

      val host = new SyslHost(resourcesDir)
      host.register("host_fill_rect", {
        case List(x, y, w, h, r, g, b, a) =>
          cmds += s"fill ${SyslHost.asLong(x)},${SyslHost.asLong(y)} " +
                  s"${SyslHost.asLong(w)}x${SyslHost.asLong(h)} " +
                  s"rgba(${SyslHost.asLong(r)},${SyslHost.asLong(g)},${SyslHost.asLong(b)},${SyslHost.asLong(a)})"
          SyslHost.unit
        case other => fail(s"host_fill_rect: bad args $other")
      })
      host.register("host_draw_text", {
        case List(x, y, text, r, g, b, a) =>
          cmds += s"text ${SyslHost.asLong(x)},${SyslHost.asLong(y)} " +
                  s"'${SyslHost.asString(text)}'"
          SyslHost.unit
        case other => fail(s"host_draw_text: bad args $other")
      })

      host.run(host.compileFile("engine-mini.sysl"))

      // Two render passes (mount → render, reconcile → render) emit six
      // commands. The reconcile pass reuses the existing Node identities
      // and updates their `view` fields, so the second pass sees the new
      // text content without remounting.
      cmds.size shouldBe 6
      cmds(0) should include ("'Hello, sysl!'")
      cmds(1) should include ("rgba(40,40,55,255)")
      cmds(2) should include ("'Click me'")
      cmds(3) should include ("'Hello, AGAIN!'")
      cmds(4) should include ("rgba(40,40,55,255)")
      cmds(5) should include ("'Click me too'")
    }

    // Variable-arity Stack with flex distribution: a top Text, a flex=1
    // Spacer, a bottom Button inside a 100-tall frame (gap=4) should pin
    // the Button to the bottom edge — the Spacer absorbs the leftover
    // 44px of vertical space.
    "lays out Stack with a flex Spacer (variable arity)" in {
      val frames = mutable.ArrayBuffer.empty[(Int, Int, Int, Int, Int)]

      val host = new SyslHost(resourcesDir)
      host.register("host_fill_rect", {
        case List(x, y, w, h, kind, _, _, _) =>
          frames += ((SyslHost.asLong(x).toInt, SyslHost.asLong(y).toInt,
                      SyslHost.asLong(w).toInt, SyslHost.asLong(h).toInt,
                      SyslHost.asLong(kind).toInt))
          SyslHost.unit
        case other => fail(s"host_fill_rect: bad args $other")
      })
      host.register("host_draw_text", {
        case _ => SyslHost.unit
      })

      host.run(host.compileFile("engine-flex.sysl"))

      // kind: 1=Text, 2=Button, 3=Spacer
      frames.toList shouldBe List(
        (0,  0,  200, 18, 1),  // Text top, natural height 18
        (0,  22, 200, 44, 3),  // Spacer absorbs flex_pool = 100 - 48 - 8 = 44
        (0,  70, 200, 30, 2),  // Button pinned to bottom
      )
    }

    // Full Scope 2.E variant set: Stack of Sized/Center+Image/Box+Checkbox.
    // Verifies measure + layout + render across single-child wrappers
    // (Sized/Center/Box) and the new leaf widgets (Image/Checkbox), all
    // composed through the variable-arity Stack.
    "drives every variant — Image, Sized, Center, Box, Checkbox" in {
      val cmds = mutable.ArrayBuffer.empty[String]

      val host = new SyslHost(resourcesDir)
      host.register("host_fill_rect", {
        case List(x, y, w, h, r, g, b, a) =>
          cmds += f"fill ${SyslHost.asLong(x)},${SyslHost.asLong(y)} " +
                  f"${SyslHost.asLong(w)}x${SyslHost.asLong(h)} " +
                  f"rgba(${SyslHost.asLong(r)},${SyslHost.asLong(g)},${SyslHost.asLong(b)},${SyslHost.asLong(a)})"
          SyslHost.unit
        case other => fail(s"host_fill_rect: bad args $other")
      })
      host.register("host_draw_text", {
        case List(x, y, text, _, _, _, _) =>
          cmds += s"text ${SyslHost.asLong(x)},${SyslHost.asLong(y)} '${SyslHost.asString(text)}'"
          SyslHost.unit
        case other => fail(s"host_draw_text: bad args $other")
      })
      host.register("host_draw_image", {
        case List(x, y, w, h, src) =>
          cmds += s"image ${SyslHost.asLong(x)},${SyslHost.asLong(y)} " +
                  s"${SyslHost.asLong(w)}x${SyslHost.asLong(h)} '${SyslHost.asString(src)}'"
          SyslHost.unit
        case other => fail(s"host_draw_image: bad args $other")
      })

      host.run(host.compileFile("engine-full.sysl"))

      cmds.toList shouldBe List(
        // Sized(Text "title", 200, 24) at row 0 (y=0): Text baseline at y+14
        "text 0,14 'title'",
        // Center(Image 32x32) at row 1 (y=24): centered at x=(200-32)/2=84
        "image 84,24 32x32 'icon.png'",
        // Box at row 2 (y=56): full-frame fill, then padding(4) → child at (4, 60)
        "fill 0,56 200x26 rgba(80,120,200,255)",
        // Checkbox at (4, 60): box LINE_H², checkmark inset by 4, label at x=4+18+6=28
        "fill 4,60 18x18 rgba(60,60,80,255)",
        "fill 8,64 10x10 rgba(220,220,240,255)",
        "text 28,74 'agree'",
      )
    }

    // Probe 1 (full-translation prep): HookValue discriminator with five
    // mixed variants — int / bool / string / struct-payload / closure —
    // stored in a single []HookValue, mutated in place, and read back
    // via match arms. The closure variant gets invoked twice from
    // inside its match arm; captured-state mutations must propagate.
    // Computed total: 8 + 100 + 50 + 700 + 2000 = 2858.
    // Probe 3 (full-translation prep): the OS-facing event/render loop
    // contract. Stubs host_poll_event with a scripted sequence of events
    // and verifies the sysl-side run_loop drains them in order, dispatches
    // each to the right handler, and exits on EVENT_QUIT. This is the
    // shape the eventual OS-layer integration will plug into — the
    // engine's main loop is sysl, the OS provides events + frame timing.
    "probe — main event loop drains events, dispatches, exits on quit" in {
      val records = mutable.ArrayBuffer.empty[String]
      // Scripted event queue: 3 events then quit.
      val events = scala.collection.mutable.Queue[(Long, Long, Long)](
        (2L, 10L, 20L),  // mouse press at (10,20)
        (3L, 0L, 0L),    // mouse release
        (7L, 0L, 0L),    // frame tick
        (1L, 0L, 0L),    // quit
      )
      var lastX, lastY = 0L

      val host = new SyslHost(resourcesDir)
      host.register("host_poll_event", { _ =>
        if events.isEmpty then SyslHost.long(0L)
        else
          val (t, x, y) = events.dequeue()
          lastX = x; lastY = y
          SyslHost.long(t)
      })
      host.register("host_event_x", { _ => SyslHost.long(lastX) })
      host.register("host_event_y", { _ => SyslHost.long(lastY) })
      host.register("host_now_ms",  { _ => SyslHost.long(0L) })
      host.register("host_record", {
        case List(s) => records += SyslHost.asString(s); SyslHost.unit
        case other   => fail(s"host_record: $other")
      })

      host.run(host.compileFile("probes/runtime-loop.sysl"))

      records.toList shouldBe List(
        "press@10,20",
        "release",
        "frame",
        "quit",
      )
    }

    // Probe 2 (full-translation prep): a Node-like struct with a *Self
    // parent pointer + a walk that climbs to find an ancestor matching
    // some predicate. This is the bones of useContext. Three-level chain:
    //   root (kind=1, value=42) <- mid (kind=0) <- leaf (kind=0)
    // find_provider(leaf) walks up via .parent and returns 42.
    "probe — Node parent-pointer ancestor walk (useContext shape)" in {
      var published: Long = -1L
      val host = new SyslHost(resourcesDir)
      host.register("host_publish", {
        case List(n) => published = SyslHost.asLong(n); SyslHost.unit
        case other   => fail(s"host_publish: $other")
      })

      host.run(host.compileFile("probes/parent-walk.sysl"))

      published shouldBe 42L
    }

    // Pins the documented sysl semantic: local primitive `var` is
    // captured by value, local struct/slice is captured by reference.
    // This is the reason useState's cells live in struct/slice slots,
    // not raw ints. The `int=0` result here is the EXPECTED behavior;
    // if it ever flips to 2 the language semantic changed and the
    // hook system probably wants to reconsider its storage shape.
    "documents — closure capture: int by-value, struct/slice by-ref" in {
      var i, s, sl = -1L
      val host = new SyslHost(resourcesDir)
      host.register("host_publish_int", {
        case List(n) => i = SyslHost.asLong(n); SyslHost.unit
        case other   => fail(s"bad: $other")
      })
      host.register("host_publish_struct", {
        case List(n) => s = SyslHost.asLong(n); SyslHost.unit
        case other   => fail(s"bad: $other")
      })
      host.register("host_publish_slice", {
        case List(n) => sl = SyslHost.asLong(n); SyslHost.unit
        case other   => fail(s"bad: $other")
      })

      host.run(host.compileFile("probes/closure-int-vs-struct.sysl"))

      withClue("local int var captures by value: ")    { i  shouldBe 0L }
      withClue("local struct var captures by ref: ")   { s  shouldBe 2L }
      withClue("local slice slot captures by ref: ")   { sl shouldBe 2L }
    }

    // Drill-down: which closure-storage path drops the captured-var
    // mutation? Four storage shapes, one shared `bump` closure, expected
    // captured == 2 at each `host_publish_*` checkpoint.
    "probe — closure capture across storage paths" in {
      var a, b, c, d = -1L
      val host = new SyslHost(resourcesDir)
      host.register("host_publish_a", {
        case List(n) => a = SyslHost.asLong(n); SyslHost.unit
        case other   => fail(s"host_publish_a: bad args $other")
      })
      host.register("host_publish_b", {
        case List(n) => b = SyslHost.asLong(n); SyslHost.unit
        case other   => fail(s"host_publish_b: bad args $other")
      })
      host.register("host_publish_c", {
        case List(n) => c = SyslHost.asLong(n); SyslHost.unit
        case other   => fail(s"host_publish_c: bad args $other")
      })
      host.register("host_publish_d", {
        case List(n) => d = SyslHost.asLong(n); SyslHost.unit
        case other   => fail(s"host_publish_d: bad args $other")
      })

      host.run(host.compileFile("probes/closure-in-cell.sysl"))

      info(s"a=$a b=$b c=$c d=$d (each should be 2)")
      withClue("A — direct var-held closure invocation: ") { a shouldBe 2L }
      withClue("B — closure stored in a struct field: ")   { b shouldBe 2L }
      withClue("C — closure in an enum-variant cell (no slice): ") { c shouldBe 2L }
      withClue("D — closure in an enum-variant cell inside a slice: ") { d shouldBe 2L }
    }

    "probe — HookValue mixed-variant cells incl. closure-typed" in {
      var published: Long = -1L
      val host = new SyslHost(resourcesDir)
      host.register("host_publish", {
        case List(n) => published = SyslHost.asLong(n); SyslHost.unit
        case other   => fail(s"host_publish: bad args $other")
      })

      host.run(host.compileFile("probes/hookvalue.sysl"))

      published shouldBe 2858L
    }

    // The fancy sysl-driven demo (counter + tabbed FormPanel +
    // slider/radio/dropdown/checkbox + modal) compiles cleanly and main()
    // publishes a complete View tree through the marshaler — every new
    // variant (VStack/HStack/Slider/Radio/Dropdown/Tabs/Modal/Box) lands
    // a Scala suit View. Smoke: run main(), assert at least one
    // host_set_root captured a non-empty View. Used to keep the demo
    // wired even when nobody runs the Swing app.
    "compiles and marshals the fancy sysl-driven demo (smoke)" in {
      var captured: io.github.edadma.suit.View = io.github.edadma.suit.Empty

      val host = new SyslHost(resourcesDir)
      host.register("host_set_root", {
        case List(v) =>
          captured = SyslView.marshal(v, host.interpreter)
          SyslHost.unit
        case other => fail(s"host_set_root: bad args $other")
      })

      host.run(host.compileFile("suit/fancy.sysl"))

      // Top-level is a VStack(title, counter row, tabs).
      captured match
        case io.github.edadma.suit.Stack(io.github.edadma.suit.Axis.Vertical, kids, _, _) =>
          kids.length shouldBe 3
          kids(0) match
            case io.github.edadma.suit.Text("Sysl-driven suit demo") => succeed
            case other => fail(s"title: $other")
        case other => fail(s"expected top-level VStack, got $other")
    }

    // Scope 2 finale — a Counter component built with hooks, driven
    // through the sysl engine end-to-end. Mount → render → click →
    // rerender → render across three frames; the Text content is the
    // hook value, so seeing "0", "1", "2" in the command stream proves
    // the full hook + reconcile + dispatch loop closes.
    // useState(int) + useState(bool) + useId + useEffect in one fiber.
    // Effect fires on mount (deps_hash=0) and on each state-change render
    // (4 state changes → 4 more); useId returns the same stable id every
    // render, so all 5 log lines share the prefix `effect[id-1-3]:`.
    "exercises useState(int)+useState(bool)+useId+useEffect" in {
      val records = mutable.ArrayBuffer.empty[String]

      val host = new SyslHost(resourcesDir)
      host.register("host_draw_text", { case _ => SyslHost.unit })
      host.register("host_record", {
        case List(s) => records += SyslHost.asString(s); SyslHost.unit
        case other   => fail(s"host_record: $other")
      })

      host.run(host.compileFiles(Seq(
        "suit/hooks.sysl",
        "suit/engine.sysl",
        "engine-hooks.sysl",
      )))

      // Hook order in fancy_counter: useState(int)=slot 0, useState(bool)=slot 1,
      // useId=slot 2, useEffect=slot 3. So id = id-{fiber_id=1}-{cell_idx=2}.
      // sysl's s"$on" prints bool as 0/1 (not "true"/"false") — that's the
      // language's default str() for bool inside interpolation.
      records.toList shouldBe List(
        "effect[id-1-2]: n=0 on=0",   // mount
        "effect[id-1-2]: n=1 on=0",   // click +
        "effect[id-1-2]: n=2 on=0",   // click +
        "effect[id-1-2]: n=2 on=1",   // toggle
        "effect[id-1-2]: n=3 on=1",   // click +
      )
    }

    // Phase β — useReducer dispatches reduce against fresh cell state,
    // so two dispatches inside the same event handler compose. The
    // probe wires a single Button whose onClick fires INC twice; the
    // counter advances by 2 per click. Three renders → publish [0, 2, 4].
    // Positive control: fn-pointer parameter captured into a returned
    // closure works for the simple case (no name shadow).
    "probe — fn-ptr parameter captured into closure" in {
      val publishes = mutable.ArrayBuffer.empty[Long]
      val host = new SyslHost(resourcesDir)
      host.register("host_publish", {
        case List(n) => publishes += SyslHost.asLong(n); SyslHost.unit
        case other   => fail(s"host_publish: $other")
      })
      host.run(host.compileFile("probes/fnptr-in-closure.sysl"))
      publishes.toList shouldBe List(15L, 107L)
    }

    // Was an `ignore` for the sysl name-shadow bug, fixed at
    // sysl@b2ca927a (VarRefAST now checks locals before globals,
    // companion to the prior CallAST fix). Now flipped back to `in`.
    "probe — name-collision between inner closure + imported fn (was sysl bug, fixed)" in {
      val publishes = mutable.ArrayBuffer.empty[Long]
      val host = new SyslHost(resourcesDir)
      host.register("host_publish", {
        case List(n) => publishes += SyslHost.asLong(n); SyslHost.unit
        case other   => fail(s"host_publish: $other")
      })
      host.register("host_draw_text", { case _ => SyslHost.unit })
      host.run(host.compileFiles(Seq(
        "suit/hooks.sysl",
        "suit/engine.sysl",
        "probes/dispatch-name-shadow.sysl",
      )))
      publishes.toList shouldBe List(100L, 107L)
    }

    // Phase γ — ContextProvider injects an int into the subtree.
    // A leaf reader Component picks it up via use_context. The middle
    // subtree wraps a second provider with the same key, so the
    // inner reader sees 33 instead of 22 (nearest-provider wins,
    // matching React's stacked-Provider semantics).
    //
    // Tree publishes (one per reader render at mount):
    //   outer reader        → 11
    //   nested-inner reader → 33
    //   no-provider reader  → -1 (sentinel for Empty)
    "ContextProvider injects values; nested providers shadow" in {
      val publishes = mutable.ArrayBuffer.empty[Long]

      val host = new SyslHost(resourcesDir)
      host.register("host_draw_text", { case _ => SyslHost.unit })
      host.register("host_publish", {
        case List(n) => publishes += SyslHost.asLong(n); SyslHost.unit
        case other   => fail(s"host_publish: $other")
      })

      host.run(host.compileFiles(Seq(
        "suit/hooks.sysl",
        "suit/engine.sysl",
        "probes/use-context.sysl",
      )))

      publishes.toList shouldBe List(11L, 33L, -1L)
    }

    // Phase γ — controller Component owns state, returns a
    // ContextProvider whose value tracks state. Consumer reads via
    // use_context. State increment → rerender → reconcile updates
    // ContextProviderState.value → consumer sees fresh.
    "ContextProvider value updates propagate to consumer across rerenders" in {
      val publishes = mutable.ArrayBuffer.empty[Long]

      val host = new SyslHost(resourcesDir)
      host.register("host_draw_text", { case _ => SyslHost.unit })
      host.register("host_publish", {
        case List(n) => publishes += SyslHost.asLong(n); SyslHost.unit
        case other   => fail(s"host_publish: $other")
      })

      host.run(host.compileFiles(Seq(
        "suit/hooks.sysl",
        "suit/engine.sysl",
        "probes/context-update.sysl",
      )))

      publishes.toList shouldBe List(10L, 11L, 12L)
    }

    "useReducer dispatch reads fresh state on each call" in {
      val publishes = mutable.ArrayBuffer.empty[Long]

      val host = new SyslHost(resourcesDir)
      host.register("host_draw_text", { case _ => SyslHost.unit })
      host.register("host_publish", {
        case List(n) => publishes += SyslHost.asLong(n); SyslHost.unit
        case other   => fail(s"host_publish: bad args $other")
      })

      host.run(host.compileFiles(Seq(
        "suit/hooks.sysl",
        "suit/engine.sysl",
        "probes/use-reducer.sysl",
      )))

      publishes.toList shouldBe List(0L, 2L, 4L)
    }

    // Phase β — useRef.current persists across rerenders. The probe
    // bumps ref.current on every click and reads it in render(). At
    // ref==3 and ref==6 the component also calls set_n, demonstrating
    // ref + state co-existence in one fiber. Engine rerenders
    // unconditionally so we publish on every click.
    "useRef.current persists across rerenders" in {
      val publishes = mutable.ArrayBuffer.empty[(Long, Long)]

      val host = new SyslHost(resourcesDir)
      host.register("host_draw_text", { case _ => SyslHost.unit })
      host.register("host_publish", {
        case List(n, r) => publishes += ((SyslHost.asLong(n), SyslHost.asLong(r))); SyslHost.unit
        case other      => fail(s"host_publish: bad args $other")
      })

      host.run(host.compileFiles(Seq(
        "suit/hooks.sysl",
        "suit/engine.sysl",
        "probes/use-ref.sysl",
      )))

      publishes.toList shouldBe List(
        (0L, 0L),  // mount
        (0L, 1L),  // click 1
        (0L, 2L),  // click 2
        (1L, 3L),  // click 3 — ref hits 3, set_n(1)
        (1L, 4L),  // click 4
        (1L, 5L),  // click 5
        (2L, 6L),  // click 6 — ref hits 6, set_n(2)
      )
    }

    // Phase α prep — verifies the bridge can hand the driver multiple
    // sources in one compile() call and that intra-module + cross-module
    // visibility both resolve. util.sysl + mathx.sysl share `module
    // probes.multifile`; the top-level driver imports four names from
    // that module and combines them. If this passes, the engine refactor
    // can split engine.sysl from hooks.sysl with confidence.
    "compiles a multi-file module + a top-level driver that imports it" in {
      var published: Long = -1L

      val host = new SyslHost(resourcesDir)
      host.register("host_publish", {
        case List(n) => published = SyslHost.asLong(n); SyslHost.unit
        case other   => fail(s"host_publish: bad args $other")
      })

      val program = host.compileFiles(Seq(
        "probes/multifile/util.sysl",
        "probes/multifile/mathx.sysl",
        "probes/multifile_main.sysl",
      ))
      host.run(program)

      published shouldBe 42L
    }

    "drives two independent Counter components across six frames" in {
      val texts = mutable.ArrayBuffer.empty[String]

      val host = new SyslHost(resourcesDir)
      host.register("host_draw_text", {
        case List(_, _, text, _, _, _, _) =>
          texts += SyslHost.asString(text); SyslHost.unit
        case other => fail(s"host_draw_text: bad args $other")
      })

      host.run(host.compileFiles(Seq(
        "suit/hooks.sysl",
        "suit/engine.sysl",
        "engine-counter.sysl",
      )))

      // Two Counter children → each render emits two Text commands.
      // Click sequence A,A,B,A,B drives counters to (3, 2) over 5 clicks
      // + one initial render = 6 frames × 2 = 12 emissions.
      texts.toList shouldBe List(
        "0", "0",   // initial mount
        "1", "0",   // click A
        "2", "0",   // click A
        "2", "1",   // click B
        "3", "1",   // click A
        "3", "2",   // click B
      )
    }

    // Scope 2.G probe — useState-style hooks. Single-fiber, int-only.
    // The setter captures the cell index and mutates a module-level
    // backing slice — writes propagate across renders, exactly the
    // primitive a real `useState` builds on.
    "supports useState across multiple renders (setter closure mutation)" in {
      val published = mutable.ArrayBuffer.empty[Long]

      val host = new SyslHost(resourcesDir)
      host.register("host_publish", {
        case List(n) => published += SyslHost.asLong(n); SyslHost.unit
        case other   => fail(s"host_publish: bad args $other")
      })

      host.run(host.compileFile("hooks-probe.sysl"))

      published.toList shouldBe List(100L, 7L, 42L)
    }

    // Scope 2.F — sysl-side dispatch fires Button/Checkbox closures via
    // hit-test. The fixture lays out a 3-child Stack, then issues five
    // synthetic InputStates: three matching presses, one hover (no
    // press), and one outside-bounds press. Only the three matching
    // presses must record.
    "dispatches mouse-press events to interactive widgets" in {
      val records = mutable.ArrayBuffer.empty[String]

      val host = new SyslHost(resourcesDir)
      host.register("host_record", {
        case List(name) => records += SyslHost.asString(name); SyslHost.unit
        case other      => fail(s"host_record: bad args $other")
      })

      host.run(host.compileFile("engine-dispatch.sysl"))

      records.toList shouldBe List("button-A", "button-B", "toggle-on")
    }

    // Test 3 (reconcile): pattern-match on (Node-kind, View-kind) to
    // decide reuse vs update vs remount — the reconciler's central
    // decision shape, ported in 30 lines of sysl.
    "pattern-matches a (Node, View) reconcile decision tree" in {
      val host = new SyslHost(resourcesDir)
      var published: Long = -1L

      host.register("host_publish", {
        case List(n) => published = SyslHost.asLong(n); SyslHost.unit
        case other   => fail(s"host_publish: bad args $other")
      })

      val ret = host.run(host.compileFile("reconcile.sysl"))
      // Expected:
      //   a=0  TextNode/Text same content  → 0
      //   b=1  TextNode/Text new content   → 1*10
      //   c=2  TextNode/Button             → 2*100
      //   d=0  ButtonNode/Button same      → 0
      //   e=2  ButtonNode/Text             → 2*10000
      //   f=0  StackNode/Stack             → 0
      //   g=2  StackNode/Text              → 2*1000000
      // Total = 10 + 200 + 20000 + 2000000 = 2020210
      val expected = 0 * 1 + 1 * 10 + 2 * 100 + 0 * 1000 + 2 * 10000 + 0 * 100000 + 2 * 1000000
      ret       shouldBe expected.toLong
      published shouldBe expected.toLong
    }

    // Test 2 (state): do struct mutations through a captured closure
    // propagate to the enclosing scope? Probes whether a hooks-style
    // API (each setter writing to a shared cell) is viable in sysl.
    "propagates struct-field mutations through a captured closure" in {
      val host = new SyslHost(resourcesDir)
      var published: Long = -1L

      host.register("host_publish", {
        case List(n) => published = SyslHost.asLong(n); SyslHost.unit
        case other   => fail(s"host_publish: bad args $other")
      })

      val program = host.compileFile("state.sysl")
      val ret = host.run(program)
      ret       shouldBe 3L
      published shouldBe 3L
    }

    // Test 1 (out of three): can sysl structs reach the JVM intact?
    "marshals a struct-typed field on an enum variant (Box with Color + Insets)" in {
      import io.github.edadma.trisc.Value.{EnumVal, ArrVal, IntVal, StringVal}
      import io.github.edadma.trisc.Cell
      val host = new SyslHost(resourcesDir)
      var captured: Value = Value.IntVal(-1)

      host.register("host_set_root", {
        case List(v) => captured = v; SyslHost.unit
        case other   => fail(s"host_set_root: bad args $other")
      })

      host.run(host.compileFile("suit/box.sysl"))

      // Outer: Box (tag 2) with three fields: child, color, padding.
      captured match
        case e: EnumVal =>
          e.tag shouldBe 2

          // field 0 — child View, expected EnumVal(tag=1, "inside box")
          e.fields(0).value match
            case child: EnumVal =>
              child.tag shouldBe 1
              child.fields(0).value match
                case StringVal(bytes) =>
                  new String(bytes, "UTF-8") shouldBe "inside box"
                case other => fail(s"Text content: $other")
            case other => fail(s"Box.child: $other")

          def cellAt(arr: ArrVal, i: Int): Value = arr.cells(arr.offset + i).value

          // field 1 — Color struct, ArrVal with 4 int cells
          e.fields(1).value match
            case arr: ArrVal =>
              cellAt(arr, 0) shouldBe IntVal(120)   // r
              cellAt(arr, 1) shouldBe IntVal(60)    // g
              cellAt(arr, 2) shouldBe IntVal(200)   // b
              cellAt(arr, 3) shouldBe IntVal(255)   // a
            case other => fail(s"Box.color: $other")

          // field 2 — Insets struct, ArrVal with 4 int cells
          e.fields(2).value match
            case arr: ArrVal =>
              cellAt(arr, 0) shouldBe IntVal(8)     // top
              cellAt(arr, 1) shouldBe IntVal(12)    // right
              cellAt(arr, 2) shouldBe IntVal(8)     // bottom
              cellAt(arr, 3) shouldBe IntVal(12)    // left
            case other => fail(s"Box.padding: $other")
        case other => fail(s"expected EnumVal, got $other")
    }
  }
