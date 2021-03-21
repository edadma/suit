package xyz.hyperreal.suit

trait Reactor {

  def deafTo(ps: Publisher*): Unit

  def listenTo(ps: Publisher*): Unit

  val reactions: Reactions

}
