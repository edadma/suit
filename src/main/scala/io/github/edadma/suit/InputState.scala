package io.github.edadma.suit

// Mouse + keyboard state for the current frame. Maps to a sysl struct.
// Mutated by the platform layer (SwingRenderer) before each frame.
final class InputState:

  var mouseX: Int          = 0
  var mouseY: Int          = 0
  var mouseDown: Boolean   = false   // primary button held this frame
  var mousePressed: Boolean = false  // primary button pressed this frame (edge)
  var mouseReleased: Boolean = false // primary button released this frame (edge)

  // Set true by the platform layer whenever any AWT event arrives. Cleared by
  // Engine after dispatching. The host uses it to decide whether a frame is
  // worth running — at idle this stays false and CPU stays at zero.
  var hadEvents: Boolean   = false

  // For text widgets — a small queue of typed characters this frame.
  // Fixed capacity to match a sysl `[16]i32` array + `len: int`.
  val typedChars: Array[Int] = new Array[Int](16)
  var typedLen: Int          = 0

  // Special keys pressed this frame (edge). One bool per key — equivalent to
  // sysl bitfield bytes. Add more as widgets need them.
  var keyBackspace: Boolean = false
  var keyDelete: Boolean    = false
  var keyLeft: Boolean      = false
  var keyRight: Boolean     = false
  var keyHome: Boolean      = false
  var keyEnd: Boolean       = false
  var keyEnter: Boolean     = false
  var keyTab: Boolean       = false
  var keySpace: Boolean     = false

  def pushChar(ch: Int): Unit =
    if typedLen < 16 then
      typedChars(typedLen) = ch
      typedLen = typedLen + 1

  def clearFrameEdges(): Unit =
    mousePressed  = false
    mouseReleased = false
    typedLen      = 0
    keyBackspace  = false
    keyDelete     = false
    keyLeft       = false
    keyRight      = false
    keyHome       = false
    keyEnd        = false
    keyEnter      = false
    keyTab        = false
    keySpace      = false
    hadEvents     = false
