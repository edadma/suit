package io.github.edadma.suit

/** The window-close seam. When the user asks to close the window — the title-bar button, ⌘Q, the
  * window manager — the native runtime calls [[requestClose]] with a `proceed` thunk that performs
  * the actual quit. By default it proceeds immediately; an application can install an
  * [[onCloseRequest]] handler (typically in a mount effect) to intervene: show a "save your
  * changes?" modal and call `proceed()` only once the user confirms, so a close can be deferred or
  * vetoed. Because the handler is consulted at close time it should read live state through a ref,
  * not a value captured when it was installed. */
object WindowControl:
  /** Consulted when a window close is requested, given the thunk that actually quits. The default
    * quits at once. Replace it to intervene; restore it (`proceed => proceed()`) on cleanup. */
  var onCloseRequest: (() => Unit) => Unit = proceed => proceed()

  private[suit] def requestClose(proceed: () => Unit): Unit = onCloseRequest(proceed)
