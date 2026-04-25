package io.github.edadma.suit

import io.github.edadma.suit.testkit.TestHost
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class SuitSpec extends AnyFreeSpec with Matchers:

  // -------------------------------------------------------------------------
  // Basic widget rendering
  // -------------------------------------------------------------------------

  "Text" - {
    "renders its content into the draw list" in {
      val host = new TestHost
      host.render(Text("hello"))
      host.findText("hello") should not be empty
      host.renderedText should include("hello")
    }
  }

  "Stack" - {
    "renders all children in declaration order" in {
      val host = new TestHost
      host.render(Stack(
        axis = Axis.Vertical,
        children = Array(Text("one"), Text("two"), Text("three")),
      ))
      host.texts.map(_.view.content) shouldBe Seq("one", "two", "three")
    }
  }

  // -------------------------------------------------------------------------
  // Button — click + hover + disabled
  // -------------------------------------------------------------------------

  "Button" - {
    "fires onClick when clicked" in {
      var clicked = 0
      val host = new TestHost
      host.render(Button("go", () => clicked = clicked + 1))
      host.click(host.findButton("go").get)
      clicked shouldBe 1
    }

    "tracks hover state from mouse position" in {
      val host = new TestHost
      host.render(Button("hover", () => ()))
      val btn = host.findButton("hover").get
      btn.hover shouldBe false
      host.moveMouseTo(btn)
      btn.hover shouldBe true
    }

    "ignores clicks when disabled" in {
      var clicked = 0
      val host = new TestHost
      host.render(Button("nope", () => clicked = clicked + 1, enabled = false))
      host.click(host.findButton("nope").get)
      clicked shouldBe 0
    }
  }

  // -------------------------------------------------------------------------
  // Input — focus + typing + caret + scroll
  // -------------------------------------------------------------------------

  "Input" - {
    "takes focus when clicked" in {
      val host = new TestHost
      host.render(Input(value = ""))
      val node = host.inputs.head
      host.click(node)
      node.focused shouldBe true
      host.focusedNode shouldBe node
    }

    "fires onChange and inserts typed characters at the caret" in {
      var current = ""
      val host = new TestHost
      // Controlled input — re-render with the latest value after each change.
      def view(): View = Input(value = current,
                                onChange = s => { current = s; host.engine.setRoot(view()) })
      host.render(view())
      host.click(host.inputs.head)
      host.typeText("abc")
      current shouldBe "abc"
    }

    "removes a character on Backspace" in {
      var current = "ab"
      val host = new TestHost
      def view(): View = Input(value = current,
                                onChange = s => { current = s; host.engine.setRoot(view()) })
      host.render(view())
      host.click(host.inputs.head)
      host.press(host.Key.End)
      host.press(host.Key.Backspace)
      current shouldBe "a"
    }
  }

  // -------------------------------------------------------------------------
  // Checkbox
  // -------------------------------------------------------------------------

  "Checkbox" - {
    "fires onToggle with the new value when clicked" in {
      var checked = false
      val host = new TestHost
      def view(): View = Checkbox(label = "subscribe", checked = checked,
                                  onToggle = b => { checked = b; host.engine.setRoot(view()) })
      host.render(view())
      host.click(host.findCheckbox("subscribe").get)
      checked shouldBe true
      host.click(host.findCheckbox("subscribe").get)
      checked shouldBe false
    }
  }

  // -------------------------------------------------------------------------
  // Reconciler
  // -------------------------------------------------------------------------

  "Reconciler" - {
    "preserves component state across re-renders with matching widgetId" in {
      val host = new TestHost
      val widget = component("CounterX") { hooks =>
        val (n, _, update) = hooks.useState(0)
        Stack(Axis.Horizontal, Array(
          Text(s"$n"),
          Button("+", () => update(_ + 1)),
        ))
      }
      host.render(Component(widget))
      host.click(host.findButton("+").get)
      host.click(host.findButton("+").get)
      host.findText("2") should not be empty
    }

    "remounts when the kind changes (state is dropped)" in {
      val host = new TestHost
      host.render(Text("first"))
      host.findText("first") should not be empty
      host.render(Button("second", () => ()))
      host.findText("first") shouldBe empty
      host.findButton("second") should not be empty
    }
  }

  // -------------------------------------------------------------------------
  // Keys
  // -------------------------------------------------------------------------

  "Keys" - {
    "preserve per-row state across reorders" in {
      val host = new TestHost
      var rows = Seq("a", "b", "c")

      val Row = (label: String) => component("Row") { hooks =>
        val (clicks, _, updateClicks) = hooks.useState(0)
        Stack(Axis.Horizontal, Array(
          Text(label),
          Button("click-" + label, () => updateClicks(_ + 1)),
          Text(clicks.toString),
        ))
      }

      def view(): View = Stack(
        axis = Axis.Vertical,
        children = rows.map(r => Keyed(r, Component(Row(r))): View).toArray,
      )

      host.render(view())
      host.click(host.findButton("click-b").get)   // bump b's count to 1

      // Reorder: move b to the front.
      rows = Seq("b", "a", "c")
      host.render(view())

      // The text "1" must still be on screen, paired with row b's button.
      host.findText("1") should not be empty
    }
  }

  // -------------------------------------------------------------------------
  // Hooks
  // -------------------------------------------------------------------------

  "useState" - {
    "value setter commits a literal" in {
      var seen = -1
      val host = new TestHost
      val w = component("S1") { hooks =>
        val (n, set, _) = hooks.useState(0)
        seen = n
        Button("set5", () => set(5))
      }
      host.render(Component(w))
      seen shouldBe 0
      host.click(host.findButton("set5").get)
      seen shouldBe 5
    }

    "functional updater reads current cell each call" in {
      var seen = -1
      val host = new TestHost
      val w = component("S2") { hooks =>
        val (n, _, update) = hooks.useState(0)
        seen = n
        Button("inc", () => update(_ + 1))
      }
      host.render(Component(w))
      host.click(host.findButton("inc").get)
      host.click(host.findButton("inc").get)
      host.click(host.findButton("inc").get)
      seen shouldBe 3
    }
  }

  "useReducer" - {
    "reduces actions into state" in {
      sealed trait Act
      case object Inc extends Act
      case object Dec extends Act
      var seen = -1
      val host = new TestHost
      val w = component("R1") { hooks =>
        val (n, dispatch) = hooks.useReducer[Int, Act](
          (s, a) => a match { case Inc => s + 1; case Dec => s - 1 },
          0,
        )
        seen = n
        Stack(Axis.Horizontal, Array(
          Button("+", () => dispatch(Inc)),
          Button("-", () => dispatch(Dec)),
        ))
      }
      host.render(Component(w))
      host.click(host.findButton("+").get)
      host.click(host.findButton("+").get)
      host.click(host.findButton("-").get)
      seen shouldBe 1
    }
  }

  "useMemo" - {
    "recomputes only when deps change" in {
      var computeCount = 0
      val host = new TestHost
      val w = component("M1") { hooks =>
        val (n, _, update) = hooks.useState(0)
        val (force, setForce, _) = hooks.useState(0)
        val _doubled = hooks.useMemo(() => { computeCount = computeCount + 1; n * 2 },
                                     deps = Array[Any](n))
        Stack(Axis.Horizontal, Array(
          Button("inc-n",     () => update(_ + 1)),
          Button("force-rer", () => setForce(force + 1)),
        ))
      }
      host.render(Component(w))           // initial render: 1 compute
      val initial = computeCount
      host.click(host.findButton("force-rer").get)  // re-render, n unchanged
      computeCount shouldBe initial
      host.click(host.findButton("inc-n").get)      // re-render, n changed
      computeCount shouldBe (initial + 1)
    }
  }

  "useRef" - {
    "carries a stable cell across renders without triggering re-render" in {
      var renders = 0
      val host = new TestHost
      val w = component("Ref1") { hooks =>
        renders = renders + 1
        val ref = hooks.useRef(0)
        ref.current = ref.current + 1
        val (_, _, update) = hooks.useState(0)
        Button("touch", () => update(_ + 1))
      }
      host.render(Component(w))
      val rendersAfterFirst = renders
      // Mutating ref.current shouldn't have caused a re-render — useState was
      // the only thing that did.
      rendersAfterFirst shouldBe 1
      host.click(host.findButton("touch").get)
      renders shouldBe 2
    }
  }

  // -------------------------------------------------------------------------
  // Effects
  // -------------------------------------------------------------------------

  "useEffect" - {
    "runs body after the first commit" in {
      val log = scala.collection.mutable.ArrayBuffer.empty[String]
      val host = new TestHost
      val w = component("E1") { hooks =>
        log += "render"
        hooks.useEffect(() => { log += "effect"; () => () }, deps = Array.empty[Any])
        Text("e")
      }
      host.render(Component(w))
      log.toSeq shouldBe Seq("render", "effect")
    }

    "runs cleanup on unmount" in {
      val log = scala.collection.mutable.ArrayBuffer.empty[String]
      val host = new TestHost
      val w = component("E2") { hooks =>
        hooks.useEffect(() => { () => log += "cleanup" }, deps = Array.empty[Any])
        Text("c")
      }
      host.render(Component(w))
      host.render(Text("gone"))   // remount: kind change → unmount old subtree
      log.toSeq shouldBe Seq("cleanup")
    }

    "re-runs body when deps change" in {
      val log = scala.collection.mutable.ArrayBuffer.empty[String]
      val host = new TestHost
      val w = component("E3") { hooks =>
        val (n, _, update) = hooks.useState(0)
        hooks.useEffect(() => { log += s"body=$n"; () => log += s"cleanup=$n" },
                        deps = Array[Any](n))
        Button("inc", () => update(_ + 1))
      }
      host.render(Component(w))
      host.click(host.findButton("inc").get)
      log.toSeq shouldBe Seq("body=0", "cleanup=0", "body=1")
    }

    "cleanup ordering is child-first when a subtree unmounts" in {
      val log = scala.collection.mutable.ArrayBuffer.empty[String]
      val host = new TestHost

      val Child = component("Child") { hooks =>
        hooks.useEffect(() => () => log += "child-cleanup", Array.empty[Any])
        Text("child")
      }
      val Parent = component("Parent") { hooks =>
        hooks.useEffect(() => () => log += "parent-cleanup", Array.empty[Any])
        Stack(Axis.Vertical, Array(Component(Child)))
      }

      host.render(Component(Parent))
      host.render(Text("gone"))
      log.toSeq shouldBe Seq("child-cleanup", "parent-cleanup")
    }
  }

  "useLayoutEffect" - {
    "fires after layout, before useEffect" in {
      val log = scala.collection.mutable.ArrayBuffer.empty[String]
      val host = new TestHost
      val w = component("LE1") { hooks =>
        hooks.useLayoutEffect(() => { log += "layout"; () => () }, Array.empty[Any])
        hooks.useEffect       (() => { log += "effect"; () => () }, Array.empty[Any])
        Text("le")
      }
      host.render(Component(w))
      log.toSeq shouldBe Seq("layout", "effect")
    }

    "sees committed layout bounds before they're painted" in {
      val host = new TestHost
      var seenWidth = -1
      val w = component("LE2") { hooks =>
        val ref = hooks.useRef[Node | Null](null)
        hooks.useLayoutEffect(
          () => {
            val n = ref.current
            if n != null then seenWidth = n.bounds.w
            () => ()
          },
          Array.empty[Any],
        )
        WithRef(ref, Text("measure-me"))
      }
      host.render(Component(w))
      seenWidth should be > 0
    }
  }

  // -------------------------------------------------------------------------
  // useId / useContext
  // -------------------------------------------------------------------------

  "useId" - {
    "is stable across re-renders" in {
      var firstId  = ""
      var secondId = ""
      val host = new TestHost
      val w = component("Id1") { hooks =>
        val id = hooks.useId()
        if firstId.isEmpty then firstId = id else secondId = id
        val (_, _, update) = hooks.useState(0)
        Button("rerender", () => update(_ + 1))
      }
      host.render(Component(w))
      host.click(host.findButton("rerender").get)
      secondId shouldBe firstId
    }

    "differs between sibling fibers" in {
      val ids = scala.collection.mutable.ArrayBuffer.empty[String]
      val host = new TestHost
      // Each call to the factory returns a fresh widget — that's how the
      // reconciler ends up with two distinct ComponentNodes (and two Hooks).
      def Sib: Widget = component("IdSibling") { hooks =>
        ids += hooks.useId()
        Text("x")
      }
      host.render(Stack(Axis.Vertical,
        children = Array(Component(Sib), Component(Sib))))
      ids.distinct.size shouldBe 2
    }
  }

  "useContext" - {
    "reads the value from the nearest provider" in {
      val Theme = createContext("default")
      var seen = ""
      val host = new TestHost
      val w = component("Ctx1") { hooks =>
        seen = hooks.useContext(Theme)
        Text("c")
      }
      host.render(Theme.provide("dark", Component(w)))
      seen shouldBe "dark"
    }

    "falls back to the default outside any provider" in {
      val Theme = createContext("default")
      var seen = ""
      val host = new TestHost
      val w = component("Ctx2") { hooks =>
        seen = hooks.useContext(Theme)
        Text("c")
      }
      host.render(Component(w))
      seen shouldBe "default"
    }
  }

  // -------------------------------------------------------------------------
  // Refs forwarding
  // -------------------------------------------------------------------------

  "WithRef" - {
    "sets ref.current to the produced node after mount" in {
      val ref = new Ref[Node | Null](null)
      val host = new TestHost
      host.render(WithRef(ref, Input(value = "")))
      ref.current shouldBe a[InputNode]
    }
  }

  "Engine.focusNode" - {
    "moves focus to the given node" in {
      val ref = new Ref[Node | Null](null)
      val host = new TestHost
      host.render(WithRef(ref, Input(value = "")))
      val n = ref.current
      n should not be null
      host.engine.focusNode(n.asInstanceOf[Node])
      host.focusedNode shouldBe n
    }
  }

  // -------------------------------------------------------------------------
  // Portals + AbsolutePosition + Backdrop
  // -------------------------------------------------------------------------

  "Portal" - {
    "registers with the engine and renders its child via the overlay layer" in {
      val host = new TestHost
      host.render(Stack(Axis.Vertical, Array(
        Text("base"),
        Portal(Text("on-top")),
      )))
      host.engine.portalNodes.size shouldBe 1
      // Both texts should be findable through the testkit walk.
      host.findText("base")    should not be empty
      host.findText("on-top")  should not be empty
    }

    "unregisters and tears down its overlay when removed from the tree" in {
      val host = new TestHost
      host.render(Stack(Axis.Vertical, Array(Portal(Text("o")))))
      host.engine.portalNodes.size shouldBe 1
      host.render(Stack(Axis.Vertical, Array(Text("just base"))))
      host.engine.portalNodes.size shouldBe 0
      host.findText("o") shouldBe empty
    }
  }

  "AbsolutePosition" - {
    "places its child at the declared coordinates regardless of layout flow" in {
      val host = new TestHost
      host.render(Portal(AbsolutePosition(50, 100, Text("anchor"))))
      val txt = host.findText("anchor").get
      txt.bounds.x shouldBe 50
      txt.bounds.y shouldBe 100
    }
  }

  "Backdrop" - {
    "fires onBackdropClick when clicked outside its child" in {
      var closed = false
      val host = new TestHost
      host.render(Portal(Backdrop(
        onBackdropClick = () => closed = true,
        child = AbsolutePosition(100, 100, Button("inside", () => ())),
      )))
      // Click far from the button.
      host.clickAt(5, 5)
      closed shouldBe true
    }

    "does NOT fire onBackdropClick when clicked inside its child, and consumes the click" in {
      var closed = false
      var inside = false
      val host = new TestHost
      host.render(Stack(Axis.Vertical, Array(
        // Main-tree button at (0,0). It must NOT fire when the backdrop
        // consumes the press.
        Button("main", () => inside = true),
        Portal(Backdrop(
          onBackdropClick = () => closed = true,
          child = AbsolutePosition(200, 200, Button("dialog", () => inside = true)),
        )),
      )))
      val dialogBtn = host.findButton("dialog").get
      host.click(dialogBtn)
      closed shouldBe false   // click was inside the child
      inside shouldBe true    // dialog button fired
    }
  }

  // -------------------------------------------------------------------------
  // useTransition
  // -------------------------------------------------------------------------

  "useTransition" - {
    "returns the target unchanged when nothing has started transitioning" in {
      val host = new TestHost
      var observed = -1.0
      val Comp = component("trans-init") { hooks =>
        observed = hooks.useTransition(0.5, 200)
        Text("v=" + observed)
      }
      host.render(Component(Comp))
      observed shouldBe 0.5
      host.engine.animationCount shouldBe 0
    }

    "begins animating to a new target and reaches it after the duration" in {
      val host = new TestHost
      // Caller-controlled target lives in test scope so re-renders pick it up.
      var target  = 0.0
      var lastVal = -1.0
      val Comp = component("trans") { hooks =>
        lastVal = hooks.useTransition(target, 100)
        Text("v=" + lastVal)
      }
      // Initial render: target=0.0 → no transition.
      host.render(Component(Comp))
      lastVal shouldBe 0.0
      host.engine.animationCount shouldBe 0

      // Change target → animation starts.
      target = 1.0
      host.setTime(0)
      host.render(Component(Comp))
      host.engine.animationCount shouldBe 1
      // Halfway through, value is between start and target (eased > 0.5).
      host.advanceTime(50)
      host.frame()
      lastVal should (be > 0.0 and be < 1.0)
      host.engine.animationCount shouldBe 1

      // Past duration: settles exactly at target and releases its slot.
      host.advanceTime(200)
      host.frame()
      lastVal shouldBe 1.0
      host.engine.animationCount shouldBe 0
    }

    "drives the engine's animating flag while in flight" in {
      val host = new TestHost
      var target = 0.0
      val Comp = component("trans-anim") { hooks =>
        val _ = hooks.useTransition(target, 100)
        Text("x")
      }
      host.render(Component(Comp))
      target = 1.0
      host.setTime(0)
      host.render(Component(Comp))
      host.engine.animating shouldBe true
      host.advanceTime(500)
      host.frame()
      host.engine.animating shouldBe false
    }
  }

  // -------------------------------------------------------------------------
  // Image
  // -------------------------------------------------------------------------

  "Image" - {
    "measures to its declared (width, height)" in {
      val host = new TestHost
      // Wrap in AbsolutePosition so the bounds reflect the Image's natural
      // size rather than Stack's cross-axis stretch.
      host.render(Portal(AbsolutePosition(0, 0,
        Image(source = "icon.png", width = 32, height = 32))))
      val n = host.images.head
      n.bounds.w shouldBe 32
      n.bounds.h shouldBe 32
    }

    "emits a DrawImage command carrying the source string" in {
      val host = new TestHost
      host.render(Image(source = "logo.png", width = 64, height = 32))
      host.drawList.collect { case di: DrawImage => di }.headOption match
        case Some(DrawImage(_, src)) => src shouldBe "logo.png"
        case None                    => fail("expected DrawImage command")
    }
  }

  // -------------------------------------------------------------------------
  // Slider
  // -------------------------------------------------------------------------

  "Slider" - {
    "emits onChange with a value computed from mouseX on press" in {
      var current = 0
      val host = new TestHost
      host.render(Slider(value = 0, min = 0, max = 100, onChange = v => current = v, width = 100))
      val s = host.sliders.head
      // Press at the right edge → max value.
      host.clickAt(s.bounds.x + s.bounds.w - 1, s.bounds.y + s.bounds.h / 2)
      current shouldBe 100
    }

    "clamps emitted values to [min, max] when the drag goes off the track" in {
      var current = 50
      val host = new TestHost
      host.render(Slider(value = current, min = 0, max = 100,
        onChange = v => current = v, width = 100))
      val s = host.sliders.head
      // Press inside, then drag well past the left edge — value clamps to min.
      host.engine.input.mouseX        = s.bounds.x + 10
      host.engine.input.mouseY        = s.bounds.y + s.bounds.h / 2
      host.engine.input.mouseDown     = true
      host.engine.input.mousePressed  = true
      host.engine.input.hadEvents     = true
      host.frame()
      // Now move the mouse far left while still held.
      host.engine.input.mouseX        = s.bounds.x - 200
      host.engine.input.hadEvents     = true
      host.frame()
      // Release.
      host.engine.input.mouseDown     = false
      host.engine.input.mouseReleased = true
      host.engine.input.hadEvents     = true
      host.settle()
      current shouldBe 0
    }

    "becomes focused on click and responds to arrow keys" in {
      var current = 50
      val host = new TestHost
      host.render(Slider(value = current, min = 0, max = 100,
        onChange = v => current = v, width = 200))
      val s = host.sliders.head
      host.click(s)
      s.focused shouldBe true
      // Re-render with the freshly-emitted value so keyboard nudge is from 50.
      host.render(Slider(value = 50, min = 0, max = 100,
        onChange = v => current = v, width = 200))
      host.press(host.Key.Right)
      current shouldBe 51
      host.render(Slider(value = 51, min = 0, max = 100,
        onChange = v => current = v, width = 200))
      host.press(host.Key.Left)
      current shouldBe 50
    }

    "ignores clicks when disabled" in {
      var current = 25
      val host = new TestHost
      host.render(Slider(value = current, min = 0, max = 100,
        onChange = v => current = v, width = 100, enabled = false))
      val s = host.sliders.head
      host.clickAt(s.bounds.x + s.bounds.w - 1, s.bounds.y + s.bounds.h / 2)
      current shouldBe 25
    }
  }

  // -------------------------------------------------------------------------
  // Radio
  // -------------------------------------------------------------------------

  "Radio" - {
    "fires onSelect when an unselected radio is clicked" in {
      var picked = -1
      val host = new TestHost
      host.render(Stack(Axis.Vertical, Array(
        Radio("a", selected = false, onSelect = () => picked = 0),
        Radio("b", selected = false, onSelect = () => picked = 1),
      )))
      host.click(host.findRadio("b").get)
      picked shouldBe 1
    }

    "does NOT fire onSelect when an already-selected radio is clicked" in {
      var fires = 0
      val host = new TestHost
      host.render(Radio("only", selected = true, onSelect = () => fires = fires + 1))
      host.click(host.findRadio("only").get)
      fires shouldBe 0
    }
  }

  // -------------------------------------------------------------------------
  // Tabs helper
  // -------------------------------------------------------------------------

  "tabs" - {
    "renders a button per label and a content panel" in {
      val host = new TestHost
      host.render(tabs(
        labels   = Array("One", "Two", "Three"),
        selected = 0,
        onSelect = _ => (),
        content  = Text("body0"),
      ))
      host.findText("body0") should not be empty
      host.buttons.size shouldBe 3
    }

    "fires onSelect with the clicked tab's index" in {
      var picked = -1
      val host = new TestHost
      host.render(tabs(
        labels   = Array("One", "Two", "Three"),
        selected = 0,
        onSelect = i => picked = i,
        content  = Text("body0"),
      ))
      host.click(host.findButton("Two").get)
      picked shouldBe 1
    }
  }

  // -------------------------------------------------------------------------
  // Dropdown helper
  // -------------------------------------------------------------------------

  "dropdown" - {
    "starts closed (no menu portal)" in {
      val host = new TestHost
      host.render(dropdown("Apple", Array("Apple", "Banana"), _ => ()))
      host.engine.portalNodes.size shouldBe 0
    }

    "opens on trigger click and closes after selection" in {
      var chosen = ""
      val host = new TestHost
      host.render(dropdown("Apple", Array("Apple", "Banana"),
        onChange = s => chosen = s))
      host.click(host.findButton("Apple ▾").get)
      host.engine.portalNodes.size shouldBe 1
      host.click(host.findButton("Banana").get)
      chosen shouldBe "Banana"
      host.engine.portalNodes.size shouldBe 0
    }
  }

  // -------------------------------------------------------------------------
  // Overlays — modal / tooltip / contextMenu helpers
  // -------------------------------------------------------------------------

  "modal" - {
    "renders nothing when open=false" in {
      val host = new TestHost
      host.render(modal(open = false, onClose = () => (), child = Text("hidden")))
      host.engine.portalNodes.size shouldBe 0
      host.findText("hidden") shouldBe empty
    }

    "centers its child in the viewport when open=true" in {
      val host = new TestHost   // default 800x600 viewport in TestHost
      host.render(modal(open = true, onClose = () => (), child = Text("dialog")))
      host.engine.portalNodes.size shouldBe 1
      val txt = host.findText("dialog").get
      // Centered: text width should sit on the viewport's vertical centerline
      // (within a few px of slack since the centerline math is integer).
      val cx = txt.bounds.x + txt.bounds.w / 2
      val cy = txt.bounds.y + txt.bounds.h / 2
      math.abs(cx - host.engine.width  / 2) should be < 4
      math.abs(cy - host.engine.height / 2) should be < 4
    }

    "fires onClose when its backdrop is clicked outside the child" in {
      var closed = false
      val host = new TestHost
      host.render(modal(open = true,
        onClose = () => closed = true, child = Button("inside", () => ())))
      host.clickAt(2, 2)   // far from the centered dialog
      closed shouldBe true
    }
  }

  "tooltip" - {
    "renders nothing when open=false" in {
      val host = new TestHost
      host.render(tooltip(open = false, x = 10, y = 10, Text("tip")))
      host.engine.portalNodes.size shouldBe 0
      host.findText("tip") shouldBe empty
    }

    "places its child at the requested coordinates when open=true" in {
      val host = new TestHost
      host.render(tooltip(open = true, x = 50, y = 75, Text("tip")))
      val txt = host.findText("tip").get
      txt.bounds.x shouldBe 50
      txt.bounds.y shouldBe 75
    }

    "does NOT swallow clicks — the underlying UI still receives them" in {
      var hit = 0
      val host = new TestHost
      host.render(Stack(Axis.Vertical, Array(
        Button("under", () => hit = hit + 1),
        tooltip(open = true, x = 300, y = 300, Text("tip")),
      )))
      host.click(host.findButton("under").get)
      hit shouldBe 1
    }
  }

  "contextMenu" - {
    "renders nothing when open=false" in {
      val host = new TestHost
      host.render(contextMenu(open = false, x = 0, y = 0,
        onClose = () => (), child = Text("menu")))
      host.engine.portalNodes.size shouldBe 0
    }

    "fires onClose on outside click and does NOT propagate to the underlying UI" in {
      var closed = false
      var under  = 0
      val host = new TestHost
      host.render(Stack(Axis.Vertical, Array(
        Button("under", () => under = under + 1),
        contextMenu(open = true, x = 300, y = 300,
          onClose = () => closed = true, child = Text("menu")),
      )))
      host.clickAt(2, 2)
      closed shouldBe true
      under shouldBe 0           // backdrop swallowed the click
    }
  }

  // -------------------------------------------------------------------------
  // ErrorBoundary
  // -------------------------------------------------------------------------

  "ErrorBoundary" - {
    "shows the fallback when its child throws on mount" in {
      val host = new TestHost
      val Boomer = component("Boomer") { _ =>
        throw new RuntimeException("boom")
      }
      host.render(ErrorBoundary(
        fallback = t => Text("caught: " + t.getMessage),
        child    = Component(Boomer),
      ))
      host.findText("caught: boom") should not be empty
    }

    "renders the child when nothing throws" in {
      val host = new TestHost
      host.render(ErrorBoundary(
        fallback = _ => Text("fallback"),
        child    = Text("happy"),
      ))
      host.findText("happy")    should not be empty
      host.findText("fallback") shouldBe empty
    }
  }

  // -------------------------------------------------------------------------
  // Tab cycling
  // -------------------------------------------------------------------------

  "Tab cycling" - {
    "moves focus to the first focusable when nothing is focused" in {
      val host = new TestHost
      host.render(Stack(Axis.Vertical, Array(
        Input(value = ""),
        Checkbox("c", false),
      )))
      host.press(host.Key.Tab)
      host.focusedNode shouldBe a[InputNode]
    }

    "moves to the next focusable on subsequent Tab presses" in {
      val host = new TestHost
      host.render(Stack(Axis.Vertical, Array(
        Input(value = ""),
        Checkbox("c", false),
      )))
      host.press(host.Key.Tab)
      host.press(host.Key.Tab)
      host.focusedNode shouldBe a[CheckboxNode]
    }

    "wraps around to the first when the last is focused" in {
      val host = new TestHost
      host.render(Stack(Axis.Vertical, Array(
        Input(value = ""),
        Checkbox("c", false),
      )))
      host.press(host.Key.Tab)
      host.press(host.Key.Tab)
      host.press(host.Key.Tab)
      host.focusedNode shouldBe a[InputNode]
    }

    "moves backwards with Shift+Tab" in {
      val host = new TestHost
      host.render(Stack(Axis.Vertical, Array(
        Input(value = ""),
        Checkbox("c", false),
      )))
      host.press(host.Key.Tab)         // focus first
      host.engine.input.keyShiftDown = true
      host.press(host.Key.Tab)         // shift-tab → wrap to last
      host.engine.input.keyShiftDown = false
      host.focusedNode shouldBe a[CheckboxNode]
    }
  }

  // -------------------------------------------------------------------------
  // Scroll
  // -------------------------------------------------------------------------

  "Scroll" - {
    "scrolls its child on mouse wheel" in {
      val host = new TestHost
      // Tall content (10 stacked Text rows) inside a 100-px viewport.
      val rows = (0 until 10).map(i => Text("row " + i): View).toArray
      host.render(Scroll(
        child  = Stack(Axis.Vertical, gap = 0, children = rows),
        height = 100,
      ))
      // Find the ScrollNode and remember its initial scrollY.
      val sn = host.findAllOfType[ScrollNode].head
      sn.scrollY shouldBe 0
      // Wheel down with cursor over the scroll area.
      val r = sn.bounds
      host.engine.input.mouseX = r.x + 5
      host.engine.input.mouseY = r.y + 5
      host.engine.input.wheelDeltaY = 90f
      host.engine.input.hadEvents   = true
      host.settle()
      sn.scrollY should be > 0
    }

    "clamps scrollY at the bottom of the content" in {
      val host = new TestHost
      val rows = (0 until 6).map(i => Text("row " + i): View).toArray
      host.render(Scroll(
        child  = Stack(Axis.Vertical, gap = 0, children = rows),
        height = 100,
      ))
      val sn = host.findAllOfType[ScrollNode].head
      val r = sn.bounds
      host.engine.input.mouseX = r.x + 5
      host.engine.input.mouseY = r.y + 5
      // Try to scroll way past content end.
      host.engine.input.wheelDeltaY = 99999f
      host.engine.input.hadEvents   = true
      host.settle()
      val maxScroll = (sn.contentHeight - r.h).max(0)
      sn.scrollY shouldBe maxScroll
    }
  }

  // -------------------------------------------------------------------------
  // Context-aware memo
  // -------------------------------------------------------------------------

  "context-aware memo" - {
    "memoized consumer re-renders when its context value changes" in {
      val Theme = createContext("light")
      var renderCount = 0
      var seen = ""

      case class P()
      val Consumer = memo[P]("Consumer") { (_, hooks) =>
        renderCount = renderCount + 1
        seen = hooks.useContext(Theme)
        Text(seen)
      }

      val host = new TestHost
      host.render(Theme.provide("light", Component(Consumer(P()))))
      val afterFirst = renderCount
      seen shouldBe "light"

      host.render(Theme.provide("dark",  Component(Consumer(P()))))
      renderCount should be > afterFirst    // memo bailout was bypassed
      seen shouldBe "dark"
    }
  }

  // -------------------------------------------------------------------------
  // memo — prop-bailout
  // -------------------------------------------------------------------------

  case class GreetProps(name: String)

  "memo" - {
    "skips render when props are unchanged" in {
      var renderCount = 0
      val Greet = memo[GreetProps]("Greet") { (props, _) =>
        renderCount = renderCount + 1
        Text("hi " + props.name)
      }
      val host = new TestHost
      host.render(Component(Greet(GreetProps("ada"))))
      val afterFirst = renderCount
      host.render(Component(Greet(GreetProps("ada"))))   // same props
      renderCount shouldBe afterFirst
    }

    "renders when props change" in {
      var renderCount = 0
      val Greet = memo[GreetProps]("Greet2") { (props, _) =>
        renderCount = renderCount + 1
        Text("hi " + props.name)
      }
      val host = new TestHost
      host.render(Component(Greet(GreetProps("ada"))))
      val afterFirst = renderCount
      host.render(Component(Greet(GreetProps("alan"))))  // changed
      renderCount shouldBe (afterFirst + 1)
      host.findText("hi alan") should not be empty
    }

    "renders when its own state changes (even though props unchanged)" in {
      var renderCount = 0
      case class P()
      val Inc = memo[P]("Inc") { (_, hooks) =>
        renderCount = renderCount + 1
        val (n, _, update) = hooks.useState(0)
        Stack(Axis.Horizontal, Array(
          Text(n.toString),
          Button("+", () => update(_ + 1)),
        ))
      }
      val host = new TestHost
      host.render(Component(Inc(P())))
      val afterMount = renderCount
      host.click(host.findButton("+").get)
      renderCount shouldBe (afterMount + 1)
      host.findText("1") should not be empty
    }

    "lets a descendant render even when a memoized parent bails out" in {
      // Parent is memoed and never gets new props. Child has its own state
      // that changes via a button click. The child's render must still fire.
      var parentRenders = 0
      var childRenders  = 0
      case class PP()

      val Child = component("MemoChild") { hooks =>
        childRenders = childRenders + 1
        val (n, _, update) = hooks.useState(0)
        Stack(Axis.Horizontal, Array(
          Text("child=" + n),
          Button("bump", () => update(_ + 1)),
        ))
      }

      val Parent = memo[PP]("MemoParent") { (_, _) =>
        parentRenders = parentRenders + 1
        Stack(Axis.Vertical, Array(
          Text("parent"),
          Component(Child),
        ))
      }

      val host = new TestHost
      host.render(Component(Parent(PP())))
      val parentAfterMount = parentRenders
      val childAfterMount  = childRenders

      host.click(host.findButton("bump").get)

      parentRenders shouldBe parentAfterMount        // parent must NOT re-render
      childRenders  shouldBe (childAfterMount + 1)   // child must re-render
      host.findText("child=1") should not be empty
    }
  }

  // -------------------------------------------------------------------------
  // Fragment
  // -------------------------------------------------------------------------

  "Fragment" - {
    "splices children into the parent's children list" in {
      val host = new TestHost
      host.render(Stack(
        axis = Axis.Vertical,
        children = Array(
          Text("one"),
          Fragment(Array(Text("two"), Text("three"))),
          Text("four"),
        ),
      ))
      host.texts.map(_.view.content) shouldBe Seq("one", "two", "three", "four")
    }
  }
