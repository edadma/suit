package io.github.edadma.suit

import scala.scalanative.unsafe.*

// The InterVariable font, embedded directly in the binary. Scala Native has no runtime
// resource loading — a native executable has no classpath — so a bundled asset is linked in
// as C data at compile time (the bytes in resources/scala-native/inter_data.c, reached
// through the accessors in inter_font.c) and read from memory here. This is what lets Inter
// be suit's default font with no external file dependency, identical on every platform.
// Inter is a variable font with a `wght` axis; the bytes are loaded into one face per weight
// with FreeType's newMemoryFace (see [[Fonts]]). Inter is under the SIL Open Font License
// (fonts/OFL.txt).
@extern
object InterFont:
  def suit_inter_font_data(): Ptr[Byte] = extern
  def suit_inter_font_size(): CLong     = extern
