package io.github.edadma.suit.sysl

// Run the sysl demo in a real Swing window. Lives in src/test so the
// demo.sysl file (under src/test/resources/probes/) is on the
// classpath; SyslEngineHost itself is in src/main and can be reused
// by anything that wants to drive a sysl run_loop with Swing.
//
// Run with:
//   sbt "suitSysl/Test/runMain io.github.edadma.suit.sysl.DemoMain"
object DemoMain:
  def main(args: Array[String]): Unit =
    val url = getClass.getClassLoader.getResource("hello.sysl")
    require(url != null, "hello.sysl not on classpath — run via sbt suitSysl/Test/runMain")
    val resourcesDir = java.nio.file.Paths.get(url.toURI).getParent.toString

    val host       = new SyslHost(resourcesDir)
    val engineHost = new SyslEngineHost("Suit demo (sysl)", 720, 560, host)

    val program = host.compileFiles(Seq(
      "suit/hooks.sysl",
      "suit/engine.sysl",
      "suit/widgets.sysl",
      "probes/demo.sysl",
    ))

    engineHost.show(program)
