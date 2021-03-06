package xyz.hyperreal.suit

import scala.collection.mutable.ArrayBuffer

class Publisher {

  val listening = new ArrayBuffer[Reactor]

  def publish(e: Event): Unit =
    for (r <- listening)
      r.reactions(e)

  def subscribe(r: Reactor): Unit =
    if (!listening.contains(r))
      listening += r

  def unsubscribe(r: Reactor): Unit =
    if (listening.contains(r))
      listening -= r

  //  val listening = new ArrayBuffer[Reactor]
//
//  def publish(e: Event): Unit = {
//    for (r <- listening)
//      r.reactions(e)
//  }
//
//  def subscribe(r: Reactor): Unit = listening += r
//
//  def unsubscribe(r: Reactor): Unit = listening -= r

}
