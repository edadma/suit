ThisBuild / scalaVersion := "3.8.3"
ThisBuild / organization := "io.github.edadma"
ThisBuild / version      := "0.0.1"

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
