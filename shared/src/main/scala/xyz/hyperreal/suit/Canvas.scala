package xyz.hyperreal.suit

class Canvas(w: Double, h: Double) extends Component {

  override def layout(): Unit = {
    super.layout()
    width += w
    height += h
  }

}
