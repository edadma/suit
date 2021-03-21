package xyz.hyperreal.suit

class Button(text: String)(action: => Unit) extends Composite {
  contents += new Border() {}
}
