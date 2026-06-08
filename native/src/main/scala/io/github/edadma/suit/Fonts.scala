package io.github.edadma.suit

import scala.collection.mutable
import io.github.edadma.freetype.*
import io.github.edadma.libcairo.{FontFace, fontFaceCreateForFTFace}

// The font cache behind text rendering. Inter is a *variable* font — a single file whose
// `wght` axis can be set to any weight from 100 to 900 — so one set of glyph outlines serves
// every weight. To render a run at a given weight, FreeType's variation API reshapes the
// outlines for that weight; the catch is that Cairo caches its scaled fonts per FreeType face,
// so a face whose variation is changed in place would keep handing back the previously-cached
// glyphs. The fix is one FreeType face *per weight*: each face has its `wght` axis pinned once,
// at creation, and stays fixed for its lifetime, so each becomes a distinct Cairo font face
// with its own correct cache entry. The faces are built lazily — a UI uses only a handful of
// weights — and shared between the measurer and the canvas so a string measures exactly as it
// paints.
//
// A non-variable font (a `fontPath` to a plain face, or one with no `wght` axis) has nothing to
// vary, so every weight resolves to the single base face.
final class Fonts private (ftLib: Library, openFace: () => Either[Int, Face], baseFace: Face):

  // Whether the base face exposes a `wght` axis, that axis's index among the font's variation
  // axes, and the design-coordinate defaults for every axis (so a weighted face keeps the
  // other axes — Inter's optical-size axis, for instance — at their defaults).
  private val (variable, wghtIndex, axisDefaults) = discover(baseFace)
  private val defaultWeight: Int = if variable && wghtIndex >= 0 then axisDefaults(wghtIndex).toInt else FontWeight.Normal

  private val baseCairo: FontFace = fontFaceCreateForFTFace(baseFace.faceptr, 0)

  // Every FreeType face opened (the base plus one per non-default weight), kept so they can be
  // released at shutdown; the Cairo faces keyed by weight, built on first use of that weight.
  private val faces = mutable.ListBuffer[Face](baseFace)
  private val cache = mutable.Map.empty[Int, FontFace]

  /** The Cairo font face that renders `weight`. A non-variable font, or the font's own default
    * weight, resolves to the base face; any other weight gets a face with its `wght` axis pinned
    * to that value, built once and cached. */
  def faceFor(weight: Int): FontFace =
    if !variable || wghtIndex < 0 || weight == defaultWeight then baseCairo
    else cache.getOrElseUpdate(weight, build(weight))

  private def build(weight: Int): FontFace =
    openFace() match
      case Right(f) =>
        val coords = axisDefaults.clone()
        coords(wghtIndex) = weight.toDouble
        f.setVarDesignCoordinates(coords.toIndexedSeq)
        faces += f
        fontFaceCreateForFTFace(f.faceptr, 0)
      // If a face fails to open mid-run (it shares the base's bytes, so this is unlikely), fall
      // back to the base weight rather than crashing the frame.
      case Left(_) => baseCairo

  private def discover(face: Face): (Boolean, Int, Array[Double]) =
    face.getMMVar match
      case Right(mm) =>
        val n    = mm.numAxis
        val defs = new Array[Double](n)
        var idx  = -1
        var i    = 0
        while i < n do
          val a = mm.axis(i)
          defs(i) = a.default
          if a.tagString == "wght" then idx = i
          i += 1
        ftLib.doneMMVar(mm)
        (true, idx, defs)
      case Left(_) => (false, -1, Array.empty[Double])

  /** Release every FreeType face. Call at shutdown, after the Cairo contexts that draw with
    * them are gone. */
  def close(): Unit =
    faces.foreach(_.doneFace)
    faces.clear()
    cache.clear()

object Fonts:
  /** Open a font and prepare it for weighted rendering. `openFace` is called once now (for the
    * base face, which also reveals the variation axes) and again for each extra weight; it
    * reads the same source each time — the embedded Inter bytes or a `fontPath` file. */
  def open(ftLib: Library, openFace: () => Either[Int, Face]): Either[String, Fonts] =
    openFace() match
      case Right(base) => Right(new Fonts(ftLib, openFace, base))
      case Left(err)   => Left(s"cannot load font ($err)")
