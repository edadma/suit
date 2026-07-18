package io.github.edadma.suit

import scala.scalanative.unsafe.*

// JetBrains Mono, embedded directly in the binary — suit's monospaced family, the counterpart to
// the proportional Inter (see [[InterFont]]). Scala Native has no runtime resource loading, so the
// font is linked in as C data at compile time (the bytes in resources/scala-native/mono_data.c,
// reached through the accessors in mono_font.c) and read from memory here. Like Inter it is a
// variable font with a `wght` axis, so the same [[Fonts]] machinery loads a face per weight.
// JetBrains Mono is under the SIL Open Font License (fonts/OFL-JetBrainsMono.txt).
@extern
object MonoFont:
  def suit_mono_font_data(): Ptr[Byte] = extern
  def suit_mono_font_size(): CLong     = extern
