package io.github.edadma.suit

/** The file-dialog seam. Presenting a native Open/Save/folder panel needs the runtime's own window
  * — on macOS a panel with no parent window blocks the calling thread (a modal run loop), whereas one
  * parented to the window is a sheet that leaves the app running and redrawing while it is up. The
  * window lives in the native runtime, so this object is the seam: the runtime installs [[impl]] with
  * the real window (see the native `Suit.run`), and an application calls [[open]] / [[save]] /
  * [[folder]] without ever naming a platform type.
  *
  * The callback runs on the UI thread — the same thread the app's hooks and event handlers run on, so
  * it may read and set state directly, no cross-thread hop needed. Before a window exists (or on a
  * backend with no dialog support) the default impl reports [[Result.Failed]], so a caller always
  * hears back exactly once. */
object FileDialog:

  /** One entry in a dialog's filter list. `name` is what the user reads ("Kutter projects"); `pattern`
    * is a semicolon-separated extension list ("mp4;mov;mkv"), with no dots and no globs — or the
    * single string `"*"` for all files. Filters are advisory: a platform may ignore them, and even
    * one that honours them may let the user defeat them, so never treat a returned path as matching. */
  final case class Filter(name: String, pattern: String)

  /** Which panel [[show]] presents. */
  enum Kind:
    case OpenFile, SaveFile, OpenFolder

  /** A pending dialog request. `filters` are ignored for a folder panel; `allowMany` is ignored for a
    * save panel. `defaultLocation` is a directory when it ends in a separator, otherwise a directory
    * plus a pre-filled name. `title` / `accept` / `cancel` override the panel's default labels. Every
    * field past `kind` is a hint a platform may ignore. */
  final case class Request(
      kind:            Kind,
      filters:         Seq[Filter] = Nil,
      defaultLocation: String      = null,
      allowMany:       Boolean     = false,
      title:           String      = null,
      accept:          String      = null,
      cancel:          String      = null,
  )

  /** What the user did with a file dialog. Cancelling is a normal outcome, distinct from failure. */
  enum Result:
    /** One or more chosen paths; never empty. A save path may not exist yet, and on any platform the
      * file may have been deleted or replaced since it was chosen. */
    case Chosen(paths: Seq[String])

    /** The user dismissed the panel without choosing. */
    case Cancelled

    /** The panel could not be shown, or failed while up; `message` is the runtime's error text. */
    case Failed(message: String)

  /** The runtime installs the real presenter here (it knows the window); until then every request is
    * answered [[Result.Failed]] so a caller is never left without a reply. */
  private[suit] var impl: (Request, Result => Unit) => Unit =
    (_, callback) => callback(Result.Failed("no window: a file dialog needs a running suit window"))

  /** Present the panel `request` describes and deliver the outcome to `callback` (on the UI thread). */
  def show(request: Request)(callback: Result => Unit): Unit = impl(request, callback)

  /** Ask the user to pick an existing file (or several, with `allowMany`). */
  def open(
      filters:         Seq[Filter] = Nil,
      defaultLocation: String      = null,
      allowMany:       Boolean     = false,
      title:           String      = null,
  )(callback: Result => Unit): Unit =
    show(Request(Kind.OpenFile, filters, defaultLocation, allowMany, title))(callback)

  /** Ask the user to name a file to write; the panel runs its own overwrite prompt. */
  def save(
      filters:         Seq[Filter] = Nil,
      defaultLocation: String      = null,
      title:           String      = null,
  )(callback: Result => Unit): Unit =
    show(Request(Kind.SaveFile, filters, defaultLocation, false, title))(callback)

  /** Ask the user to pick a folder (or several, with `allowMany`); filters do not apply. */
  def folder(
      defaultLocation: String  = null,
      allowMany:       Boolean = false,
      title:           String  = null,
  )(callback: Result => Unit): Unit =
    show(Request(Kind.OpenFolder, Nil, defaultLocation, allowMany, title))(callback)
