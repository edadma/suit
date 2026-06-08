// The implementation half of the vendored stb_image (public domain; the header is the
// upstream stb_image.h, do not edit it). stb is a header-only library: defining
// STB_IMAGE_IMPLEMENTATION here, in exactly one translation unit, emits the decoder bodies
// into the binary. Scala Native compiles every .c under resources/scala-native into the
// executable, so this links with no shared library and no @link — the same way the embedded
// font is compiled in. Reached from Scala through the StbImage extern (see Raster.scala).
#define STB_IMAGE_IMPLEMENTATION
#include "stb_image.h"
