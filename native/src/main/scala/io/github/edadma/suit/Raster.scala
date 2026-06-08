package io.github.edadma.suit

import scala.scalanative.unsafe.*
import io.github.edadma.libcairo.{Surface, Format, imageSurfaceCreate}

// The native raster backend: decode an image with stb_image (vendored, compiled into the
// binary — see resources/scala-native/stb_image_impl.c) and pack its pixels into a Cairo
// image surface that CairoCanvas can blit. stb appears only here; the render tree, the DSL,
// and the Canvas trait stay platform-neutral behind the shared RasterImage type.
//
// Cairo only decodes PNG itself, so anything else (JPEG above all) needs an external decoder.
// stb hands back tightly-packed RGBA; Cairo's ARGB32 wants premultiplied BGRA in native byte
// order, so the copy below swaps channels and premultiplies as it fills the surface's buffer —
// the same direct-buffer-write-then-mark-dirty path the blurred shadows use.

/** A stb_image bytes wrapper. Holds a stb decoder symbol table reached from Scala. */
@extern
private object StbImage:
  def stbi_load(filename: CString, x: Ptr[CInt], y: Ptr[CInt], channels: Ptr[CInt], desired: CInt): Ptr[Byte] = extern
  def stbi_load_from_memory(
      buffer: Ptr[Byte],
      len: CInt,
      x: Ptr[CInt],
      y: Ptr[CInt],
      channels: Ptr[CInt],
      desired: CInt,
  ): Ptr[Byte]                            = extern
  def stbi_image_free(retval: Ptr[Byte]): Unit = extern
  def stbi_failure_reason(): CString           = extern

/** A Cairo-surface-backed [[RasterImage]]. Holds an ARGB32 surface that [[CairoCanvas.drawImage]]
  * blits into its context. Call [[destroy]] to release the surface when the image is no longer
  * needed (it outlives any single frame, so it is not freed automatically). */
final class CairoBitmap(val surface: Surface, val width: Int, val height: Int) extends RasterImage:
  /** Release the underlying Cairo surface. */
  def destroy(): Unit = surface.destroy()

/** Loads raster images into [[RasterImage]]s on the native backend, decoding JPEG/PNG/BMP/GIF/etc.
  * through stb_image. Each loader throws if the source can't be decoded. */
object Raster:
  /** Decode from a file path. */
  def fromFile(path: String): RasterImage =
    val x  = stackalloc[CInt]()
    val y  = stackalloc[CInt]()
    val ch = stackalloc[CInt]()
    val px = Zone(StbImage.stbi_load(toCString(path), x, y, ch, 4))
    if px == null then throw new RuntimeException(s"suit: cannot decode image '$path': ${failureReason()}")
    build(px, !x, !y)

  /** Decode from encoded bytes already in memory (a `.jpg`/`.png`/… read or embedded). */
  def fromBytes(data: Array[Byte]): RasterImage =
    val len = data.length
    Zone:
      val buf = alloc[Byte](len)
      var i   = 0
      while i < len do
        buf(i) = data(i)
        i += 1
      fromPtr(buf, len)

  /** Decode from encoded bytes held at a native pointer — for an asset compiled into the binary,
    * avoiding a copy. `data` need only stay valid for the duration of this call. */
  def fromPtr(data: Ptr[Byte], len: Int): RasterImage =
    val x  = stackalloc[CInt]()
    val y  = stackalloc[CInt]()
    val ch = stackalloc[CInt]()
    val px = StbImage.stbi_load_from_memory(data, len, x, y, ch, 4)
    if px == null then throw new RuntimeException(s"suit: cannot decode image: ${failureReason()}")
    build(px, !x, !y)

  private def failureReason(): String =
    val r = StbImage.stbi_failure_reason()
    if r == null then "unknown" else fromCString(r)

  // Pack stb's RGBA pixels into a fresh ARGB32 Cairo surface (premultiplied BGRA, native order),
  // mark the buffer dirty so Cairo samples the written pixels, then free stb's buffer.
  private def build(px: Ptr[Byte], w: Int, h: Int): CairoBitmap =
    val surface = imageSurfaceCreate(Format.ARGB32, w, h)
    val dst     = surface.getData
    val stride  = surface.getStride
    var yy      = 0
    while yy < h do
      val rowSrc = yy * w * 4
      val rowDst = yy * stride
      var xx     = 0
      while xx < w do
        val si = rowSrc + xx * 4
        val r  = px(si) & 0xff
        val g  = px(si + 1) & 0xff
        val b  = px(si + 2) & 0xff
        val a  = px(si + 3) & 0xff
        val di = rowDst + xx * 4
        dst(di) = ((b * a + 127) / 255).toByte     // B, premultiplied (exact for opaque a=255)
        dst(di + 1) = ((g * a + 127) / 255).toByte // G
        dst(di + 2) = ((r * a + 127) / 255).toByte // R
        dst(di + 3) = a.toByte                       // A
        xx += 1
      yy += 1
    surface.markDirty()
    StbImage.stbi_image_free(px)
    new CairoBitmap(surface, w, h)
