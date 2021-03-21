package xyz.hyperreal.suit

abstract class Border(c: Component) extends Single(c) {}

class Padding(c: Component, padding: Double = 5) extends Border(c) {}

class SolidBorder(c: Component, thickness: Double = 1, color: Int = Color.LIGHT_GRAY, padding: Double = 5)
    extends Border(new Padding(c, padding)) {}
