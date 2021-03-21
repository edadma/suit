package xyz.hyperreal.suit

class Window extends Composite {
  def publish(e: Event): Unit = ???

  def deafTo(ps: Publisher*): Unit = ???

  def listenTo(ps: Publisher*): Unit = ???

  val reactions: Reactions = _
}
