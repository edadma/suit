package io.github.edadma.suit

import io.github.edadma.suit.dsl.*

// Shared helpers mixed into every widget group. The clamps are `protected` so the
// component traits can call them while staying invisible outside the facade.
private[suit] trait WidgetsSupport:

  protected def clamp01(x: Double): Double =
    if x < 0.0 then 0.0 else if x > 1.0 then 1.0 else x

  protected def clampFrac(x: Double, lo: Double, hi: Double): Double =
    if x < lo then lo else if x > hi then hi else x

  protected def clampIdx(i: Int, n: Int): Int =
    if i < 0 then 0 else if i > n then n else i
