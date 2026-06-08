import xerial.sbt.Sonatype.sonatypeCentralHost
import sbtcrossproject.CrossPlugin.autoImport.{crossProject, CrossType}

ThisBuild / scalaVersion := "3.8.4"
ThisBuild / organization := "io.github.edadma"
ThisBuild / version      := "0.0.2"

// --- Maven Central publishing ----------------------------------------------
// Metadata for the generated POM and the Sonatype Central wiring, mirroring the
// edadma cross-project template. Credentials live outside the repo (in
// ~/.sbt/.../sonatype.sbt), so nothing secret is checked in. Only the Native
// artifact ships — it is the product; the JVM build is the headless test harness
// and the root aggregator both skip publishing (see below).
ThisBuild / organizationName     := "edadma"
ThisBuild / organizationHomepage := Some(url("https://github.com/edadma"))
ThisBuild / licenses             := Seq("ISC" -> url("https://opensource.org/licenses/ISC"))
ThisBuild / versionScheme        := Some("semver-spec")
ThisBuild / homepage             := Some(url("https://github.com/edadma/suit"))
ThisBuild / description :=
  "A declarative, reactive UI toolkit for Scala Native: a constraint-layout render tree over " +
    "the vdom core, drawn through Cairo with SDL3 as the platform layer."
ThisBuild / scmInfo := Some(
  ScmInfo(
    url("https://github.com/edadma/suit"),
    "scm:git@github.com:edadma/suit.git",
  ),
)
ThisBuild / developers := List(
  Developer(
    id = "edadma",
    name = "Edward A. Maxedon, Sr.",
    email = "edadma@gmail.com",
    url = url("https://github.com/edadma"),
  ),
)

ThisBuild / sonatypeCredentialHost := sonatypeCentralHost
ThisBuild / sonatypeProfileName    := "io.github.edadma"
ThisBuild / publishTo              := sonatypePublishToBundle.value
ThisBuild / publishMavenStyle      := true
ThisBuild / Test / publishArtifact := false
ThisBuild / publishConfiguration :=
  publishConfiguration.value.withOverwrite(true).withChecksums(Vector.empty)

// `publishMavenStyle` is read by the publish task rather than another setting, so
// the unused-key linter flags the ThisBuild form; it is genuinely in effect.
Global / excludeLintKeys += publishMavenStyle

// suit — a declarative, reactive UI toolkit for Scala Native that renders through
// SDL3. The vdom core (https://github.com/edadma/riposte) supplies the Widget and
// Element layers: the VNode model, the reconciler, and the hooks runtime. suit adds
// the layer vdom deliberately leaves to its host — the RenderObject tree that lays
// out, paints, and hit-tests. Drawing goes through Cairo (a real anti-aliasing 2D
// engine); SDL3 is the platform layer (window, input, present, texture upload). This
// is the same division of labour Flutter draws between its framework and its renderer.
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
// The Native build consumes the vdom core from Maven Central (`io.github.edadma::vdom`),
// so suit is a self-contained publishable library — sim3d and other apps depend on the
// published artifact with no source checkout. The JVM build is test-only and unpublished;
// since vdom's JVM artifact is unpublished too, it keeps source-depending on the sibling
// riposte checkout's `vdomJVM` for the headless reconciler/hooks substrate the layout tests
// mount on. SDL3 and the other native libs come from Central; scala-native finds the
// Homebrew-installed shared libraries via each binding's `@link`.
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
  // The Native build consumes the vdom core from Maven Central — the published artifact, so
  // suit itself can be published and consumed (by sim3d and others) without a source checkout.
  // The JVM build is test-only and unpublished, and vdom's JVM artifact is unpublished too, so
  // it keeps source-depending on the sibling riposte checkout's `vdomJVM` for the headless
  // reconciler/hooks substrate the layout tests mount on.
  .jvmConfigure(_.dependsOn(ProjectRef(file("../riposte"), "vdomJVM")))
  .jvmSettings(
    // Headless layout/render tests only — the JVM build ships nothing, so it is not published
    // (which also means it never needs vdom's JVM artifact on Central).
    publish / skip      := true,
    publishLocal / skip := true,
    libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.19" % Test,
    // The tests install process-global host seams (the vdom HostConfig, the scheduler, and
    // the motion clock) before driving a mount, so suites must not run concurrently or they
    // clobber one another's seams. ScalaTest is already sequential within a suite; this
    // serializes across them too.
    Test / parallelExecution := false,
  )
  .nativeSettings(
    // SDL3 is the platform layer (window, input, present, texture upload); Cairo is the
    // drawing engine; FreeType loads the font files Cairo renders text from; librsvg renders
    // SVG documents straight into the Cairo context; turbojpeg (libjpeg-turbo) decodes JPEGs.
    // sdl3_ttf is not needed. All bindings come from Central; scala-native finds the
    // Homebrew-installed libSDL3 / libcairo / libfreetype / librsvg / libturbojpeg via each
    // binding's `@link`.
    libraryDependencies ++= Seq(
      "io.github.edadma" %%% "vdom"      % "0.3.1",
      "io.github.edadma" %%% "sdl3"      % "0.2.4",
      "io.github.edadma" %%% "libcairo"  % "0.0.5",
      "io.github.edadma" %%% "freetype"  % "0.0.6",
      "io.github.edadma" %%% "librsvg"   % "0.0.2",
      "io.github.edadma" %%% "turbojpeg" % "0.0.1",
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
