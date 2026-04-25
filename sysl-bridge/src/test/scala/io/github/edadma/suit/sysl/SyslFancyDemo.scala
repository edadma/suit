package io.github.edadma.suit.sysl

import io.github.edadma.suit.{Engine, SwingHost}

// Live "fancy" demo whose entire UI is defined in sysl. Mirrors the shape
// of Demo.scala's FormPanel + Counter — counter row at the top, then a
// tabbed panel (Form/Animate/About) with slider, radio group, dropdown,
// checkbox, animated bar, and a modal — but all the View construction
// and state mutation lives on the sysl side. Each closure mutates a
// module-level `var` and re-publishes a fresh root via `host_set_root`,
// so the Scala suit Engine just sees a stream of new View trees and
// reconciles them.
//
// Run with:
//   sbt 'suitSysl/Test/runMain io.github.edadma.suit.sysl.SyslFancyDemo'
object SyslFancyDemo:

  def main(args: Array[String]): Unit =
    val stream = getClass.getClassLoader.getResourceAsStream("suit/fancy.sysl")
    require(stream != null, "suit/fancy.sysl not on the classpath")
    val source =
      try scala.io.Source.fromInputStream(stream, "UTF-8").mkString
      finally stream.close()

    val engine = new Engine
    val host   = new SyslHost(".")

    host.register("host_set_root", {
      case List(v) =>
        val view = SyslView.marshal(v, host.interpreter)
        engine.setRoot(view)
        SyslHost.unit
      case other =>
        sys.error(s"host_set_root: bad args $other")
    })

    val program = host.compile(Map("fancy" -> source))
    host.run(program)

    val swing = new SwingHost("Sysl-driven suit demo (fancy)", 720, 560, engine)
    swing.show()
