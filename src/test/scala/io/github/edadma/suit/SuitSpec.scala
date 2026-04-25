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
