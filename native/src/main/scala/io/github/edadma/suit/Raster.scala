package io.github.edadma.suit

import scala.scalanative.unsafe.*
import io.github.edadma.libcairo.{Surface, Format, imageSurfaceCreate}
import io.github.edadma.turbojpeg.{Decoder, PixelFormat}

import java.io.{File, FileInputStream}

// The native raster backend: decode a JPEG with turbojpeg (libjpeg-turbo) straight into a Cairo
// surface that CairoCanvas can blit. turbojpeg appears only here; the render tree, the DSL, and the
// Canvas trait stay platform-neutral behind the shared RasterImage type.
//
// Cairo only decodes PNG itself, so JPEG needs an external decoder. The win over a general decoder
// is that turbojpeg can write its output directly into a buffer we own, in the pixel layout we ask
// for: a JPEG has no alpha, so requesting BGRA yields B,G,R,0xFF per pixel — exactly Cairo's ARGB32
// byte order for an opaque pixel. We hand turbojpeg the Cairo surface's own buffer and stride, so it
// decodes in place with no copy and no per-channel conversion.

/** A Cairo-surface-backed [[RasterImage]]. Holds an ARGB32 surface that [[CairoCanvas.drawImage]]
  * blits into its context. Call [[destroy]] to release the surface when the image is no longer
  * needed (it outlives any single frame, so it is not freed automatically). */
final class CairoBitmap(val surface: Surface, val width: Int, val height: Int) extends RasterImage:
  /** Release the underlying Cairo surface. */
  def destroy(): Unit = surface.destroy()

/** Loads raster images into [[RasterImage]]s on the native backend, decoding JPEG through
  * turbojpeg. Each loader throws `io.github.edadma.turbojpeg.TurboJpegException` if the data isn't a
  * decodable JPEG. (PNG is handled elsewhere by Cairo directly.) */
object Raster:
  /** Decode a JPEG file. */
  def fromFile(path: String): RasterImage = build(readFile(path))

  /** Decode JPEG bytes already in memory (read from disk, or embedded). */
  def fromBytes(data: Array[Byte]): RasterImage = build(data)

  /** Decode JPEG bytes held at a native pointer — for an asset compiled into the binary. The bytes
    * are copied into a managed array (turbojpeg reads from one); `data` need only stay valid for the
    * duration of this call. */
  def fromPtr(data: Ptr[Byte], len: Int): RasterImage =
    val arr = new Array[Byte](len)
    var i   = 0
    while i < len do
      arr(i) = data(i)
      i += 1
    build(arr)

  // Read the header for the dimensions, make an ARGB32 surface, then decode straight into its pixel
  // buffer as BGRA (= opaque ARGB32) at the surface's own stride — no copy, no conversion.
  private def build(jpeg: Array[Byte]): CairoBitmap =
    val dec = Decoder()
    try
      val info    = dec.readHeader(jpeg)
      val surface = imageSurfaceCreate(Format.ARGB32, info.width, info.height)
      dec.decompress(jpeg, surface.getData, surface.getStride, PixelFormat.BGRA)
      surface.markDirty()
      new CairoBitmap(surface, info.width, info.height)
    finally dec.close()

  private def readFile(path: String): Array[Byte] =
    val file = new File(path)
    val len  = file.length().toInt
    val arr  = new Array[Byte](len)
    val in   = new FileInputStream(file)
    try
      var off = 0
      while off < len do
        val n = in.read(arr, off, len - off)
        if n < 0 then off = len else off += n
    finally in.close()
    arr
