package io.github.edadma.suit.demo

import scala.scalanative.unsafe.*

// The demo JPEG embedded in the binary (bytes in resources/scala-native/demo_jpg_data.c), reached
// through these accessors and decoded with Raster.fromPtr. Same compiled-in-asset pattern as the
// bundled font — Scala Native has no classpath, so a demo asset is linked in as C data.
@extern
object DemoImage:
  def suit_demo_jpg_data(): Ptr[Byte] = extern
  def suit_demo_jpg_size(): CLong     = extern
