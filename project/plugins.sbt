// suit cross-builds to two platforms. The Native target is the real product — the
// SDL3 host for the vdom core — while the JVM target exists so the pure constraint-
// layout engine can be unit-tested headlessly against a RecordingCanvas, the same way
// vdom's reconciler is tested on the JVM. Only layout/render/geometry code (the
// `shared` sources) crosses to the JVM; the SDL runtime stays Native-only.
addSbtPlugin("org.scala-native"   % "sbt-scala-native"              % "0.5.12")
addSbtPlugin("org.portable-scala" % "sbt-scala-native-crossproject" % "1.3.2")
