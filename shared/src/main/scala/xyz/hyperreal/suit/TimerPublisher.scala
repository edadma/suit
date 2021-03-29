package xyz.hyperreal.suit

import java.util.{Timer, TimerTask}

import collection.mutable

class TimerPublisher extends Publisher {

  val timers = new mutable.HashMap[String, Timer]

  def started(timer: String): Boolean = timers contains timer

  def start(timer: String, ms: Int): Unit = {
    val t = new Timer

    timers(timer) = t

    t.schedule(new TimerTask {
      def run(): Unit = publish(Tick(timer))
    }, ms, ms)
  }

//  def startFixedRate(ms: Int): Unit =
//    t.scheduleAtFixedRate(new TimerTask {
//      def run(): Unit = publish(Tick)
//    }, ms, ms)

  def stop(timer: String): Unit = {
    timers(timer).cancel()
    timers -= timer
  }

}
