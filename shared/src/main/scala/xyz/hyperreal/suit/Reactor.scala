package xyz.hyperreal.suit

import scala.collection.mutable.ArrayBuffer

class Reactor {

  def deafTo(ps: Publisher*): Unit =
    for (p <- ps)
      p.unsubscribe(this)

  def listenTo(ps: Publisher*): Unit =
    for (p <- ps)
      p.subscribe(this)

  val reactions: Reactions = new Reactions {

    private val handlers = new ArrayBuffer[Reaction]

    def isDefinedAt(e: Event): Boolean = handlers.exists(_.isDefinedAt(e))

    def apply(e: Event): Unit = handlers filter (_.isDefinedAt(e)) foreach (_(e))

    def +=(r: Reaction): Reactions = {
      handlers += r
      this
    }

    def -=(r: Reaction): Reactions = {
      handlers -= r
      this
    }

  }

}
