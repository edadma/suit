package io.github.edadma.suit

import io.github.edadma.librsvg.{Handle, handleNewFromString, handleNewFromFile, handleNewFromData}

// The native SVG backend: load a document with librsvg and wrap its handle as an SvgImage that
// CairoCanvas can render into its context. This is the only place librsvg appears — the render
// tree, the DSL, and the Canvas trait all stay platform-neutral behind the shared SvgImage type.

/** A Cairo/librsvg-backed [[SvgImage]]. Holds a librsvg handle that [[CairoCanvas.drawSvg]]
  * renders into its context. Call [[destroy]] to release the handle when the image is no longer
  * needed (it outlives any single frame, so it is not freed automatically). */
final class CairoSvg(val handle: Handle) extends SvgImage:
  def intrinsicSize: Option[Size] = handle.intrinsicSize.map { case (w, h) => Size(w, h) }

  /** Release the underlying librsvg handle. */
  def destroy(): Unit = handle.destroy()

/** Loads SVG documents into [[SvgImage]]s on the native backend. Each loader throws
  * `io.github.edadma.librsvg.RsvgException` if the source can't be parsed. */
object Svg:
  /** Load from a string of SVG markup. */
  def fromString(svg: String): SvgImage = new CairoSvg(handleNewFromString(svg))

  /** Load from a file path (plain `.svg` or gzip-compressed `.svgz`). */
  def fromFile(path: String): SvgImage = new CairoSvg(handleNewFromFile(path))

  /** Load from raw bytes (plain `.svg` or gzip-compressed `.svgz`). */
  def fromBytes(data: Array[Byte]): SvgImage = new CairoSvg(handleNewFromData(data))
