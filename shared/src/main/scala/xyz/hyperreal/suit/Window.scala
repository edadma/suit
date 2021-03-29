package xyz.hyperreal.suit

class Window extends Box {

  override val name: String = "Window"

  padding = 5

  var repaintWindow: () => Unit = () => sys.error("can't repaint")

  override def repaint(): Unit = repaintWindow()

//  override val screen: (Double, Double) = (x, y)
}

object Window {

  def apply(cs: Component*): Window = new Window { contents ++= cs }

}
