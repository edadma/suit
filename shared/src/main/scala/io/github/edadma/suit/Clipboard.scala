package io.github.edadma.suit

/** The system clipboard, as a platform seam — the render layer stays platform-free, so the real
  * clipboard is injected rather than reached for. The native runtime installs an SDL-backed
  * implementation in `Suit.run`; headless tests, and any host without a system clipboard, fall back
  * to the in-memory default below. The text widgets copy and paste through `Clipboard.installed`. */
trait Clipboard:
  /** The clipboard's current text (`""` when empty). */
  def get(): String

  /** Replace the clipboard's text. */
  def set(text: String): Unit

object Clipboard:
  /** An in-memory clipboard — the default until a host installs a real one. Copy/paste works
    * within the app (and in headless tests) even when there is no system clipboard to reach. */
  final class InMemory extends Clipboard:
    private var buffer = ""
    def get(): String           = buffer
    def set(text: String): Unit = buffer = text

  /** The clipboard the widgets use. `Suit.run` swaps in an SDL-backed one at startup. */
  var installed: Clipboard = new InMemory
