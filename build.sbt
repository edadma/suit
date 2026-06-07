import sbtcrossproject.CrossPlugin.autoImport.{crossProject, CrossType}

ThisBuild / scalaVersion := "3.8.4"
ThisBuild / organization := "io.github.edadma"
ThisBuild / version      := "0.0.1-SNAPSHOT"

// suit — a declarative, reactive UI toolkit for Scala Native that renders through
// SDL3. The vdom core (https://github.com/edadma/riposte) supplies the Widget and
// Element layers: the VNode model, the reconciler, and the hooks runtime. suit adds
// the layer vdom deliberately leaves to its host — the RenderObject tree that lays
// out, paints, and hit-tests — implemented over SDL3. This is the same division of
// labour Flutter draws between its framework and its rendering library.
//
// The build crosses two platforms:
//   - Native is the product: the SDL3 host and the runnable application.
//   - JVM exists only to test. The constraint-layout engine, the render tree, and the
//     geometry are pure Scala with no SDL dependency, so they live in `shared` and can
//     be exercised headlessly against a `RecordingCanvas` under plain `sbt suitJVM/test`
//     — no device, no window, no native toolchain. This mirrors how vdom's reconciler
//     and salle's components are tested off-device, and is the whole reason the layout
//     engine was kept FFI-free (no Yoga, no C solver).
//
// vdom is pulled live from the sibling riposte checkout as a source dependency (its
// JVM and Native cross-targets, project ids `vdomJVM` / `vdomNative`), so the core and
// this host iterate in lockstep with no publish round-trip. Those two vdom targets are
// unpublished (only `vdom.js` is on Central, for riposte's POM), so the source
// ProjectRef is the supported way to consume them until the HostConfig surface
// stabilises and `vdom.native` graduates to Maven Central. SDL3 comes from Central;
// scala-native finds the Homebrew-installed libSDL3 via the binding's `@link("SDL3")`.
lazy val suit = crossProject(JVMPlatform, NativePlatform)
  .crossType(CrossType.Full)
  .in(file("."))
  .settings(
    name := "suit",
    scalacOptions ++= Seq(
      "-deprecation",
      "-feature",
      "-unchecked",
      "-language:implicitConversions",
    ),
  )
  .jvmConfigure(_.dependsOn(ProjectRef(file("../riposte"), "vdomJVM")))
  .nativeConfigure(_.dependsOn(ProjectRef(file("../riposte"), "vdomNative")))
  .jvmSettings(
    // Headless layout/render tests only — the JVM build ships nothing.
    libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.19" % Test,
  )
  .nativeSettings(
    libraryDependencies ++= Seq(
      "io.github.edadma" %%% "sdl3"     % "0.2.1",
      "io.github.edadma" %%% "sdl3_ttf" % "0.2.1",
    ),
  )

lazy val suitJVM    = suit.jvm
lazy val suitNative = suit.native

// Root aggregator: no sources, never published; lets a repo-root task fan out to both
// platform projects. `sbt suitNative/run` launches the demo; `sbt suitJVM/test` runs
// the headless layout suite.
lazy val root = project
  .in(file("."))
  .aggregate(suitJVM, suitNative)
  .settings(
    name           := "suit-root",
    publish / skip := true,
  )
