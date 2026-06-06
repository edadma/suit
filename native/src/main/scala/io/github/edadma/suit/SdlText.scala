package io.github.edadma.suit

import scala.collection.mutable
import io.github.edadma.sdl3_ttf.{Font, openFont}

// The SDL-backed half of text support: opening fonts and measuring strings. Rendering
// lives in SdlCanvas; both draw from the same FontBook so a string measures and paints
// at identical sizes.

/** Caches fonts opened from one file, keyed by integer point size. A render tree may
  * mix sizes (a heading, body text), and reopening the face per call would be wasteful,
  * so each distinct size is opened once and kept until the book is closed. */
final class FontBook(path: String):
  private val fonts = mutable.Map.empty[Int, Font]

  /** The font for `ptSize` (rounded to whole points, at least 1), opening it on first
    * use. */
  def at(ptSize: Double): Font =
    val key = ptSize.toInt.max(1)
    fonts.getOrElseUpdate(key, openFont(path, key.toDouble))

  /** Close every opened font. Call at shutdown, before `ttfQuit`. */
  def close(): Unit =
    fonts.valuesIterator.foreach(_.close())
    fonts.clear()

/** Measures text with real font metrics via sdl3_ttf — the measurer the runtime
  * installs so the layout pass sizes text the way it will actually paint. An empty
  * string still reports the font's line height, so a blank text node reserves a line
  * rather than collapsing. */
final class SdlTextMeasurer(book: FontBook) extends TextMeasurer:
  def measure(text: String, style: TextStyle): Size =
    val font = book.at(style.size)
    if text.isEmpty then Size(0.0, font.height.toDouble)
    else
      val (w, h) = font.size(text)
      Size(w.toDouble, h.toDouble)
