---
title: "Installation"
weight: 1
---

suit targets Scala Native and renders through SDL3, so there are three things to have in
place: the **Scala Native toolchain**, the **native SDL3 libraries**, and the **suit
sources** themselves.

[= note =]
suit is in active development and is **not yet published to Maven Central**. You build and
run it from the repository checkout. The sections below describe that workflow; published
artifacts and a one-line dependency will come once the API stabilises.
[= /note =]

## Requirements

- Scala 3 with sbt
- The `sbt-scala-native` and `sbt-scala-native-crossproject` plugins
- LLVM/Clang (the Scala Native toolchain)
- The **SDL3** and **SDL3_ttf** shared libraries on your system

## Install the native libraries

suit's runtime links against system SDL3 via `@link` (through the
[sdl3 bindings](https://sdl3.edadma.dev/)), so the C libraries must be installed. On macOS
with Homebrew:

```bash
brew install sdl3 sdl3_ttf
```

On Linux, install the SDL3 and SDL3_ttf development packages from your distribution (or
build them from source). Only `sdl3` and `sdl3_ttf` are needed — text rendering uses
SDL3_ttf; image and audio are not required.

## Get the sources

suit depends on two sibling repositories, both checked out next to it:

- **[riposte](https://github.com/edadma/riposte)** — supplies the `vdom` core. suit
  consumes its JVM and Native cross-targets as a **source dependency** (`vdomJVM` /
  `vdomNative`), because those targets are not published; only `vdom.js` is on Central.
- **[sdl3](https://github.com/edadma/sdl3)** — the SDL3 bindings, pulled from Maven
  Central (`sdl3` + `sdl3_ttf`).

```bash
git clone https://github.com/edadma/riposte.git
git clone https://github.com/edadma/suit.git
```

The expected layout is the two repos side by side:

```
dev/
├── riposte/      # provides vdomJVM / vdomNative (source dependency)
└── suit/         # this repo
```

suit's `build.sbt` references riposte by relative path:

```scala
.jvmConfigure(_.dependsOn(ProjectRef(file("../riposte"), "vdomJVM")))
.nativeConfigure(_.dependsOn(ProjectRef(file("../riposte"), "vdomNative")))
```

and pulls SDL3 from Central:

```scala
.nativeSettings(
  libraryDependencies ++= Seq(
    "io.github.edadma" %%% "sdl3"     % "0.2.1",
    "io.github.edadma" %%% "sdl3_ttf" % "0.2.1",
  ),
)
```

## Verify the setup

Run the headless layout suite on the JVM — it needs no window, no device, and no native
toolchain, because the layout engine is pure Scala:

```bash
sbt suitJVM/test
```

Then build the native demo and run it (see the [quick start](/getting-started/quick-start/)):

```bash
sbt suitNative/run
```

If a window opens showing the widget demo, the toolchain and SDL3 are wired correctly.
