package io.github.edadma.suit.sysl

import io.github.edadma.suit.{Engine, SwingHost}

// Live demo: a counter UI defined in sysl, marshaled through SyslView
// into Scala suit View values, and rendered by the existing SwingHost.
//
// Run with:
//   sbt 'suitSysl/Test/runMain io.github.edadma.suit.sysl.SyslSwingDemo'
//
// State is owned on the sysl side; each click fires a sysl closure that
// mutates the count and re-publishes a fresh View via the host_set_root
// extern. The JVM side simply accepts the new root and the suit Engine
// reconciles the two trees on the next frame.
object SyslSwingDemo:

  def main(args: Array[String]): Unit =
    // Read counter.sysl off the classpath as a string — under sbt's forked
    // runMain the resource may live inside a JAR, so we can't use a
    // filesystem path here. compile(Map) skips the driver's import walk
    // entirely; this program is self-contained.
    val stream = getClass.getClassLoader.getResourceAsStream("suit/counter.sysl")
    require(stream != null, "suit/counter.sysl not on the classpath")
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

    val program = host.compile(Map("counter" -> source))
    host.run(program)

    // The first run calls host_set_root(build_root()) inside main(), so the
    // engine already has a root by the time the window appears.
    val swing = new SwingHost("Sysl-driven suit counter", 360, 200, engine)
    swing.show()
