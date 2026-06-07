package io.github.edadma.suit

import scala.scalanative.unsafe.*

// The Inter 18pt Regular font, embedded directly in the binary. Scala Native has no runtime
// resource loading — a native executable has no classpath — so a bundled asset is linked in
// as C data at compile time (see resources/scala-native/inter_font.c) and read from memory
// here. This is what lets Inter be suit's default font with no external file dependency,
// identical on every platform. The bytes are loaded into a face with FreeType's
// newMemoryFace (see [[Suit]]); Inter is under the SIL Open Font License (fonts/OFL.txt).
@extern
object InterFont:
  def suit_inter_font_data(): Ptr[Byte] = extern
  def suit_inter_font_size(): CLong     = extern
