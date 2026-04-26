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
    // SyslHost reads source files off disk (not via the classloader),
    // so we need a real filesystem path to the test-resources tree.
    // sbt's fork-jvm cwd is the subproject base (sysl-bridge/), so
    // the resources resolve relative to that. The optional first arg
    // overrides for non-sbt launches.
    val resourcesDir =
      if args.nonEmpty then args(0)
      else System.getProperty("user.dir") + "/src/test/resources"

    val host       = new SyslHost(resourcesDir)
    val engineHost = new SyslEngineHost("Suit demo (sysl)", 720, 560, host)

    val program = host.compileFiles(Seq(
      "suit/hooks.sysl",
      "suit/engine.sysl",
      "suit/widgets.sysl",
      "probes/demo.sysl",
    ))

    engineHost.show(program)
