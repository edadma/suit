package io.github.edadma.suit

// A flat 8-bit-per-channel pixel buffer addressed by byte offset. It is the seam that lets the
// blur convolution run over a Cairo image surface's native buffer at runtime (a `Ptr[Byte]`)
// and a plain array in tests, so the arithmetic — the part worth getting right — is verified
// off-device, the same way the rest of the renderer is. Offsets are absolute byte indices into
// the buffer; `stride` is the bytes per row (Cairo pads rows for alignment, so it can exceed
// `width * 4`).
trait ByteSurface:
  def width: Int
  def height: Int
  def stride: Int
  def get(offset: Int): Int          // the byte at `offset`, unsigned (0..255)
  def set(offset: Int, value: Int): Unit

// A separable box blur over a premultiplied ARGB32 buffer — the convolution behind a soft drop
// shadow. Cairo has no blur primitive, so a shadow is rendered by drawing its (rounded) shape
// into an offscreen surface, blurring that surface here, and compositing the result. A box blur
// repeated three times approximates a Gaussian closely enough for a shadow at a fraction of the
// cost, and being separable it runs in two one-dimensional passes per repetition.
object BoxBlur:

  /** Blur `surface` (premultiplied ARGB32, four bytes per pixel) in place: `passes` box passes
    * of the given `radius`. Each colour channel is blurred independently, which is correct for
    * premultiplied alpha and avoids the dark halos a straight (non-premultiplied) blur produces
    * around a transparent edge. Samples outside the buffer count as zero — a transparent border
    * — so a shape drawn with a margin of at least `radius * passes` pixels feathers cleanly to
    * nothing at its edge rather than smearing against the buffer's sides. A non-positive radius
    * or pass count is a no-op. */
  def blur(surface: ByteSurface, radius: Int, passes: Int): Unit =
    val w = surface.width
    val h = surface.height
    if radius <= 0 || passes <= 0 || w <= 0 || h <= 0 then return
    val stride = surface.stride

    // Pack the surface into a tight w×h Int array (one 0xAARRGGBB per pixel, from the b,g,r,a
    // byte order of a little-endian ARGB32) so the inner passes touch contiguous memory rather
    // than going through the byte seam per channel.
    val pixels = new Array[Int](w * h)
    var y      = 0
    while y < h do
      val row  = y * stride
      val prow = y * w
      var x    = 0
      while x < w do
        val o = row + x * 4
        pixels(prow + x) =
          (surface.get(o) & 0xff) |
            ((surface.get(o + 1) & 0xff) << 8) |
            ((surface.get(o + 2) & 0xff) << 16) |
            ((surface.get(o + 3) & 0xff) << 24)
        x += 1
      y += 1

    val scratch = new Array[Int](w * h)
    var p       = 0
    while p < passes do
      horizontal(pixels, scratch, w, h, radius) // pixels  -> scratch
      vertical(scratch, pixels, w, h, radius)   // scratch -> pixels
      p += 1

    y = 0
    while y < h do
      val row  = y * stride
      val prow = y * w
      var x    = 0
      while x < w do
        val px = pixels(prow + x)
        val o  = row + x * 4
        surface.set(o, px & 0xff)
        surface.set(o + 1, (px >>> 8) & 0xff)
        surface.set(o + 2, (px >>> 16) & 0xff)
        surface.set(o + 3, (px >>> 24) & 0xff)
        x += 1
      y += 1

  // One horizontal pass: each output pixel is the average of the window [x-radius, x+radius] on
  // its row, divided by the full window width with out-of-range samples treated as zero. The
  // average is kept as a running sum that gains one pixel and loses one as the window slides, so
  // the pass is linear in the pixel count regardless of the radius.
  private def horizontal(src: Array[Int], dst: Array[Int], w: Int, h: Int, radius: Int): Unit =
    val window = 2 * radius + 1
    var y      = 0
    while y < h do
      val base = y * w
      var sb   = 0
      var sg   = 0
      var sr   = 0
      var sa   = 0
      var i    = -radius
      while i <= radius do
        if i >= 0 && i < w then
          val px = src(base + i)
          sb += px & 0xff
          sg += (px >>> 8) & 0xff
          sr += (px >>> 16) & 0xff
          sa += (px >>> 24) & 0xff
        i += 1
      var x = 0
      while x < w do
        dst(base + x) =
          (sb / window) |
            ((sg / window) << 8) |
            ((sr / window) << 16) |
            ((sa / window) << 24)
        val add = x + radius + 1
        val rem = x - radius
        if add < w then
          val pa = src(base + add)
          sb += pa & 0xff; sg += (pa >>> 8) & 0xff; sr += (pa >>> 16) & 0xff; sa += (pa >>> 24) & 0xff
        if rem >= 0 then
          val pr = src(base + rem)
          sb -= pr & 0xff; sg -= (pr >>> 8) & 0xff; sr -= (pr >>> 16) & 0xff; sa -= (pr >>> 24) & 0xff
        x += 1
      y += 1

  // One vertical pass: the same running-average window, run down each column (a step of `w`
  // between a column's pixels).
  private def vertical(src: Array[Int], dst: Array[Int], w: Int, h: Int, radius: Int): Unit =
    val window = 2 * radius + 1
    var x      = 0
    while x < w do
      var sb = 0
      var sg = 0
      var sr = 0
      var sa = 0
      var i  = -radius
      while i <= radius do
        if i >= 0 && i < h then
          val px = src(i * w + x)
          sb += px & 0xff
          sg += (px >>> 8) & 0xff
          sr += (px >>> 16) & 0xff
          sa += (px >>> 24) & 0xff
        i += 1
      var y = 0
      while y < h do
        dst(y * w + x) =
          (sb / window) |
            ((sg / window) << 8) |
            ((sr / window) << 16) |
            ((sa / window) << 24)
        val add = y + radius + 1
        val rem = y - radius
        if add < h then
          val pa = src(add * w + x)
          sb += pa & 0xff; sg += (pa >>> 8) & 0xff; sr += (pa >>> 16) & 0xff; sa += (pa >>> 24) & 0xff
        if rem >= 0 then
          val pr = src(rem * w + x)
          sb -= pr & 0xff; sg -= (pr >>> 8) & 0xff; sr -= (pr >>> 16) & 0xff; sa -= (pr >>> 24) & 0xff
        y += 1
      x += 1
