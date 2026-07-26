package io.github.edadma.suit

import org.scalatest.funsuite.AnyFunSuite

import java.awt.Font
import java.io.File
import java.nio.file.{Files, Path}

import scala.jdk.CollectionConverters.*
import scala.util.Using

// Every glyph a widget paints has to exist in the font suit ships, or it renders as the platform's
// missing-glyph box — a visible defect that no type check or layout test catches, because the string
// is perfectly valid and merely unrenderable. Inter is a text face with thin symbol coverage, so the
// tempting codepoint is often the one it lacks: the Select chevron once used U+25BE (small down
// triangle, absent) where the data table's sort caret already used a scaled-down U+25BC (present).
//
// This scans the library's own sources for non-ASCII characters in string literals and asserts the
// bundled fonts can display them, so a new widget glyph is checked the moment it lands rather than
// when someone notices a box on screen.
class FontCoverageSpec extends AnyFunSuite:

  // The repo root, found by walking up from the working directory until the layout appears — sbt
  // runs tests from the build root, but that is a convention, not a guarantee.
  private val repoRoot: Path =
    Iterator
      .iterate(new File(".").getCanonicalFile)(_.getParentFile)
      .takeWhile(_ != null)
      .find(d => new File(d, "fonts/InterVariable.ttf").isFile)
      .map(_.toPath)
      .getOrElse(fail("cannot locate the suit repo root (no fonts/InterVariable.ttf above the working directory)"))

  private def load(name: String): Font =
    Font.createFont(Font.TRUETYPE_FONT, repoRoot.resolve(s"fonts/$name").toFile)

  private val sans = load("InterVariable.ttf")
  private val mono = load("JetBrainsMono.ttf")

  // A string literal on a line of code, with `//` comments and doc prose dropped — prose is written
  // for the reader and may hold any character; only what the app can paint is at issue here.
  private val Literal = """"((?:[^"\\\n]|\\.)*)"""".r

  private case class Glyph(ch: Char, where: String)

  private def glyphsUnder(dir: Path): Seq[Glyph] =
    if !Files.isDirectory(dir) then Nil
    else
      Using.resource(Files.walk(dir)) { walk =>
        walk
          .iterator
          .asScala
          .filter(p => p.toString.endsWith(".scala"))
          .toSeq
          .flatMap { p =>
            Files
              .readAllLines(p)
              .asScala
              .zipWithIndex
              .filterNot((line, _) => line.trim.startsWith("*") || line.trim.startsWith("//"))
              .flatMap { (line, i) =>
                val code = line.split("//").head
                Literal
                  .findAllMatchIn(code)
                  .flatMap(_.group(1).filter(_ > ''))
                  .map(ch => Glyph(ch, s"${repoRoot.relativize(p)}:${i + 1}"))
              }
          }
      }

  private def report(g: Glyph): String = f"U+${g.ch.toInt}%04X '${g.ch}' at ${g.where}"

  test("every non-ASCII glyph in the shared widgets is in the bundled sans face"):
    val missing = glyphsUnder(repoRoot.resolve("shared/src/main/scala")).filterNot(g => sans.canDisplay(g.ch))
    assert(missing.isEmpty, s"Inter cannot display:\n${missing.map(report).distinct.mkString("\n")}")

  test("every non-ASCII glyph in the native runtime and demo is in the bundled sans face"):
    val missing = glyphsUnder(repoRoot.resolve("native/src/main/scala")).filterNot(g => sans.canDisplay(g.ch))
    assert(missing.isEmpty, s"Inter cannot display:\n${missing.map(report).distinct.mkString("\n")}")

  // The specific pair the Select chevron turns on, pinned so the reason the widget uses the scaled
  // U+25BC survives a later "tidy up" that swaps in the small-triangle codepoint again.
  test("Inter has the full-size down triangle the Select chevron scales, but not the small one"):
    assert(sans.canDisplay('▼'), "U+25BC ▼ — the glyph Select and the sort caret scale down")
    assert(!sans.canDisplay('▾'), "U+25BE ▾ — absent, which is why it is not used")

  test("the mono face covers the box-drawing and arrow glyphs a terminal-style view needs"):
    val needed = "─│┌┐└┘├┤┬┴┼←↑→↓"
    val missing = needed.filterNot(mono.canDisplay)
    assert(missing.isEmpty, s"JetBrains Mono cannot display: ${missing.map(c => f"U+${c.toInt}%04X").mkString(", ")}")
