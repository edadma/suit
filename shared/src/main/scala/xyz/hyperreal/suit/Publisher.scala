package xyz.hyperreal.suit

import scala.collection.mutable.ArrayBuffer

trait Publisher extends Reactor {

  def publish(e: Event): Unit

  def subscribe(r: Reactor): Unit

  def unsubscribe(r: Reactor): Unit

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
