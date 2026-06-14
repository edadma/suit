package io.github.edadma.suit

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.BeforeAndAfterEach

// The window-close seam: by default a close proceeds at once; a handler can defer or veto it.
class WindowControlSpec extends AnyFunSuite with BeforeAndAfterEach:

  override def afterEach(): Unit = WindowControl.onCloseRequest = proceed => proceed()

  test("the default handler proceeds with the quit immediately"):
    var quit = false
    WindowControl.requestClose(() => quit = true)
    assert(quit)

  test("a handler can defer the quit until the app confirms later"):
    var quit                     = false
    var deferred: () => Unit     = () => ()
    WindowControl.onCloseRequest = proceed => deferred = proceed
    WindowControl.requestClose(() => quit = true)
    assert(!quit)  // vetoed for now — the app is showing its confirmation
    deferred()     // the user confirms
    assert(quit)
