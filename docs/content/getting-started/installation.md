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
- The **SDL3**, **Cairo**, **FreeType**, **librsvg**, and **libjpeg-turbo** shared libraries on
  your system

## Install the native libraries

suit's runtime links against system libraries via `@link`: **SDL3** (window, input, present,
through the [sdl3 bindings](https://sdl3.edadma.dev/)), **Cairo** (the drawing engine),
**FreeType** (font loading), **librsvg** (SVG rendered straight into the Cairo context), and
**libjpeg-turbo** (JPEG decoding). On macOS with Homebrew:

```bash
brew install sdl3 cairo librsvg jpeg-turbo
```

Cairo depends on FreeType (and librsvg pulls in glib), so Homebrew resolves those alongside. On
Linux, install the SDL3, Cairo, FreeType, librsvg, and libjpeg-turbo development packages from
your distribution (or build them from source).

## Get the sources

suit depends on two sibling repositories, both checked out next to it:

- **[riposte](https://github.com/edadma/riposte)** — supplies the `vdom` core. The Native
  build pulls `vdom` from Maven Central, but the **JVM test build** consumes `vdomJVM` as a
  **source dependency** (that target is not published), so the checkout must be present to run
  the headless suite.
- **[sdl3](https://github.com/edadma/sdl3)**, **[libcairo](https://github.com/edadma/libcairo)**,
  **[freetype](https://github.com/edadma/freetype)**, **[librsvg](https://github.com/edadma/librsvg)**,
  and **[turbojpeg](https://github.com/edadma/turbojpeg)** — the SDL3, Cairo, FreeType, librsvg,
  and libjpeg-turbo bindings, all pulled from Maven Central.

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

suit's `build.sbt` references riposte by relative path for the JVM test build:

```scala
.jvmConfigure(_.dependsOn(ProjectRef(file("../riposte"), "vdomJVM")))
```

and pulls `vdom` and the native bindings from Central for the Native build:

```scala
.nativeSettings(
  libraryDependencies ++= Seq(
    "io.github.edadma" %%% "vdom"      % "0.3.1",
    "io.github.edadma" %%% "sdl3"      % "0.2.6",
    "io.github.edadma" %%% "libcairo"  % "0.0.7",
    "io.github.edadma" %%% "freetype"  % "0.0.6",
    "io.github.edadma" %%% "librsvg"   % "0.0.4",
    "io.github.edadma" %%% "turbojpeg" % "0.0.1",
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
