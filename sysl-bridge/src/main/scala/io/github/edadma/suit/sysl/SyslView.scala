package io.github.edadma.suit.sysl

import io.github.edadma.suit.{
  View, Text, Button, Stack, Axis, Spacer, Empty, Image, Sized, Center, Checkbox,
}
import io.github.edadma.trisc.{SyslInterpreter, Value}
import io.github.edadma.trisc.Value.*

// Translates a sysl-side `View` enum value (an EnumVal coming out of the
// SyslInterpreter) into a Scala-side `io.github.edadma.suit.View` case
// class instance. The mapping is positional on the enum's variant tag —
// the order of variants in the canonical `view.sysl` is the contract.
//
// Closure fields (Button.on_click, Checkbox.on_toggle) are wrapped so the
// host can invoke them from JVM event handlers — calling back into the
// interpreter via `interpreter.invokeClosure(c, args)`.
//
// `Stack2`/`Stack3` are temporary fixed-arity stand-ins for the eventual
// `Stack(children: []View)` variant, which is currently blocked on a sysl
// analyzer bug (recursive-enum slice fields). When that lands, the StackN
// variants can be retired in favor of the slice form.
object SyslView:

  // Tag-to-variant mapping must match the `enum View` declaration order
  // in view2.sysl (and eventually the unified view.sysl). Positional
  // dispatch is the cost of not having variant-name reflection on
  // EnumVal yet.
  private final val TagEmpty:    Int = 0
  private final val TagText:     Int = 1
  private final val TagButton:   Int = 2
  private final val TagSpacer:   Int = 3
  private final val TagImage:    Int = 4
  private final val TagSized:    Int = 5
  private final val TagCenter:   Int = 6
  private final val TagCheckbox: Int = 7
  private final val TagStack2:   Int = 8
  private final val TagStack3:   Int = 9
  private final val TagStack:    Int = 10

  def marshal(v: Value, interp: SyslInterpreter): View = v match
    case e: EnumVal => marshalEnum(e, interp)
    case other      => sys.error(s"SyslView.marshal: expected enum View, got $other")

  private def marshalEnum(e: EnumVal, interp: SyslInterpreter): View = e.tag match
    case TagEmpty  => Empty
    case TagText   =>
      val content = stringField(e, 0)
      Text(content)
    case TagButton =>
      val label   = stringField(e, 0)
      val click   = closureField(e, 1)
      val onClick: () => Unit = () =>
        val _ = interp.invokeClosure(click, Nil)
        ()
      Button(label, onClick)
    case TagSpacer =>
      Spacer(flex = intField(e, 0))
    case TagImage =>
      Image(
        source = stringField(e, 0),
        width  = intField(e, 1),
        height = intField(e, 2),
      )
    case TagSized =>
      Sized(
        child  = childField(e, 0, interp),
        width  = intField(e, 1),
        height = intField(e, 2),
      )
    case TagCenter =>
      Center(child = childField(e, 0, interp))
    case TagCheckbox =>
      val label   = stringField(e, 0)
      val checked = boolField(e, 1)
      val toggle  = closureField(e, 2)
      val onToggle: Boolean => Unit = b =>
        val _ = interp.invokeClosure(toggle, List(IntVal(if b then 1L else 0L)))
        ()
      Checkbox(label = label, checked = checked, onToggle = onToggle)
    case TagStack2 =>
      val left  = childField(e, 0, interp)
      val right = childField(e, 1, interp)
      Stack(Axis.Horizontal, Array(left, right))
    case TagStack3 =>
      val a = childField(e, 0, interp)
      val b = childField(e, 1, interp)
      val c = childField(e, 2, interp)
      Stack(Axis.Horizontal, Array(a, b, c))
    case TagStack =>
      Stack(Axis.Horizontal, sliceField(e, 0, interp))
    case t =>
      sys.error(s"SyslView.marshal: unknown View tag $t")

  // -- field accessors -------------------------------------------------------

  private def stringField(e: EnumVal, idx: Int): String =
    e.fields(idx).value match
      case StringVal(bytes) => new String(bytes, "UTF-8")
      case other            => sys.error(s"expected string at field $idx, got $other")

  private def intField(e: EnumVal, idx: Int): Int =
    e.fields(idx).value match
      case IntVal(n) => n.toInt
      case other     => sys.error(s"expected int at field $idx, got $other")

  private def boolField(e: EnumVal, idx: Int): Boolean =
    e.fields(idx).value match
      case IntVal(n) => n != 0
      case other     => sys.error(s"expected bool at field $idx, got $other")

  private def closureField(e: EnumVal, idx: Int): ClosureVal =
    e.fields(idx).value match
      case c: ClosureVal => c
      case other         => sys.error(s"expected closure at field $idx, got $other")

  private def childField(e: EnumVal, idx: Int, interp: SyslInterpreter): View =
    marshal(e.fields(idx).value, interp)

  private def sliceField(e: EnumVal, idx: Int, interp: SyslInterpreter): Array[View] =
    e.fields(idx).value match
      case SliceVal(cells, off, len, _) =>
        val out = new Array[View](len)
        var i = 0
        while i < len do
          out(i) = marshal(cells(off + i).value, interp)
          i = i + 1
        out
      case RefSliceVal(cells, len, _) =>
        val out = new Array[View](len)
        var i = 0
        while i < len do
          out(i) = marshal(cells(i).value, interp)
          i = i + 1
        out
      case other =>
        sys.error(s"expected slice at field $idx, got $other")
