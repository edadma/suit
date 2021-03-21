package xyz.hyperreal.suit

trait Publisher extends Reactor {

  def publish(e: Event): Unit

}
