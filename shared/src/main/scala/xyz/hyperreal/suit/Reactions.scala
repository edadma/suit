package xyz.hyperreal.suit

import scala.collection.mutable.ArrayBuffer

abstract class Reactions extends Reaction {

  def +=(r: Reaction): Reactions

  def -=(r: Reaction): Reactions

}

class Reactive extends Reactions {

  private val handlers = new ArrayBuffer[Reaction]

  def isDefinedAt(e: Event): Boolean = handlers.exists(_.isDefinedAt(e))

  def apply(e: Event): Unit =
    handlers.find(_.isDefinedAt(e)) match {
      case Some(r) => r(e)
      case None    => sys.error(s"can't react to event: $e")
    }

  def +=(r: Reaction): Reactions = {
    handlers += r
    this
  }

  def -=(r: Reaction): Reactions = {
    handlers -= r
    this
  }

}
