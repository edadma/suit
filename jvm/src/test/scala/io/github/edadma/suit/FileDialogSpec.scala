package io.github.edadma.suit

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.BeforeAndAfterEach

// The file-dialog seam: the runtime installs a presenter that knows the window; an app calls
// open/save/folder platform-neutrally, and always hears back exactly once. The live SDL panel is
// integration-only (it can't be popped headlessly), so these cover the seam and the request mapping.
class FileDialogSpec extends AnyFunSuite with BeforeAndAfterEach:

  override def afterEach(): Unit =
    FileDialog.impl = (_, callback) => callback(FileDialog.Result.Failed("no window: a file dialog needs a running suit window"))

  test("the default impl reports Failed rather than throwing, and calls back exactly once"):
    var calls: List[FileDialog.Result] = Nil
    FileDialog.open()(r => calls = r :: calls)
    assert(calls.size == 1)
    assert(calls.head.isInstanceOf[FileDialog.Result.Failed])

  test("open forwards an OpenFile request with its filters and allowMany"):
    var got: FileDialog.Request = null
    FileDialog.impl = (req, cb) => got = req
    val filters = Seq(FileDialog.Filter("Kutter projects", "kutter"))
    FileDialog.open(filters = filters, allowMany = true, title = "Open project")(_ => ())
    assert(got.kind == FileDialog.Kind.OpenFile)
    assert(got.filters == filters)
    assert(got.allowMany)
    assert(got.title == "Open project")

  test("save forwards a SaveFile request and never sets allowMany"):
    var got: FileDialog.Request = null
    FileDialog.impl = (req, cb) => got = req
    FileDialog.save(defaultLocation = "/tmp/untitled.kutter")(_ => ())
    assert(got.kind == FileDialog.Kind.SaveFile)
    assert(!got.allowMany)
    assert(got.defaultLocation == "/tmp/untitled.kutter")

  test("folder forwards an OpenFolder request with no filters"):
    var got: FileDialog.Request = null
    FileDialog.impl = (req, cb) => got = req
    FileDialog.folder(allowMany = true)(_ => ())
    assert(got.kind == FileDialog.Kind.OpenFolder)
    assert(got.filters.isEmpty)
    assert(got.allowMany)

  test("the installed impl's result reaches the caller's callback"):
    FileDialog.impl = (_, cb) => cb(FileDialog.Result.Chosen(Seq("/a/b.kutter")))
    var got: FileDialog.Result = null
    FileDialog.open()(r => got = r)
    assert(got == FileDialog.Result.Chosen(Seq("/a/b.kutter")))
