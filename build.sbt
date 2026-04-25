ThisBuild / scalaVersion := "3.8.3"
ThisBuild / organization := "io.github.edadma"
ThisBuild / version      := "0.0.1"

// The suit library — pure Scala UI toolkit, no sysl dependency.
lazy val suit = project
  .in(file("."))
  .settings(
    name := "suit",
    scalacOptions ++= Seq(
      "-deprecation",
      "-feature",
      "-unchecked",
      "-Werror",
    ),
    fork := true,
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % "3.2.19" % Test,
    ),
  )

// Optional bridge module that lets a sysl program drive the suit toolkit
// through the SyslInterpreter. Lives in its own subproject so the suit
// library stays clean — `sbt suit/test` doesn't touch any sysl code, the
// published `suit` artifact has zero sysl dependency.
//
// Dependency on trisc is via a sibling worktree — for this to compile, the
// trisc repo must be checked out at /Users/ed/dev/trisc/. If it isn't, just
// use `sbt suit/test` and don't touch this module.
lazy val triscSyslJVM = ProjectRef(file("../trisc"), "syslJVM")

lazy val suitSysl = project
  .in(file("sysl-bridge"))
  .dependsOn(suit)
  .dependsOn(triscSyslJVM)
  .settings(
    name := "suit-sysl",
    scalacOptions ++= Seq(
      "-deprecation",
      "-feature",
      "-unchecked",
      "-Werror",
    ),
    fork := true,
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % "3.2.19" % Test,
    ),
  )
