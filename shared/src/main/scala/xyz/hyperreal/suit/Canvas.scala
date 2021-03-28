package xyz.hyperreal.suit

class Canvas(w: Double, h: Double) extends Component {

  val name: String = "Canvas"

  override def layout(): Unit = {
    super.layout()
    width += w
    height += h
  }

}
