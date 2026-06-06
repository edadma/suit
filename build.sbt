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
// vdom is pulled live from the sibling riposte checkout as a source dependency (its
// Native cross-target, project id `vdomNative`), so the core and this host iterate
// in lockstep with no publish round-trip. SDL3 comes from Maven Central; scala-native
// finds the Homebrew-installed libSDL3 via the binding's `@link("SDL3")`.
lazy val suit = project
  .in(file("."))
  .enablePlugins(ScalaNativePlugin)
  .dependsOn(ProjectRef(file("../riposte"), "vdomNative"))
  .settings(
    name := "suit",
    libraryDependencies += "io.github.edadma" %%% "sdl3" % "0.2.0",
    scalacOptions ++= Seq(
      "-deprecation",
      "-feature",
      "-unchecked",
      "-language:implicitConversions",
    ),
  )
