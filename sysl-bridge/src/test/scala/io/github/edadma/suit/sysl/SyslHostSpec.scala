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

    // Scope 2 finale — a Counter component built with hooks, driven
    // through the sysl engine end-to-end. Mount → render → click →
    // rerender → render across three frames; the Text content is the
    // hook value, so seeing "0", "1", "2" in the command stream proves
    // the full hook + reconcile + dispatch loop closes.
    "drives a Counter component with use_state across three frames" in {
      val texts = mutable.ArrayBuffer.empty[String]

      val host = new SyslHost(resourcesDir)
      host.register("host_draw_text", {
        case List(_, _, text, _, _, _, _) =>
          texts += SyslHost.asString(text); SyslHost.unit
        case other => fail(s"host_draw_text: bad args $other")
      })

      host.run(host.compileFile("engine-counter.sysl"))

      texts.toList shouldBe List("0", "1", "2")
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
