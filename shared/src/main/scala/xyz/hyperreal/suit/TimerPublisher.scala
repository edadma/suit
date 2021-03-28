package xyz.hyperreal.suit

import java.util.{Timer, TimerTask}

class TimerPublisher extends Publisher {

  val t = new Timer

  def start(ms: Int): Unit =
    t.schedule(new TimerTask {
      def run(): Unit = publish(Tick)
    }, ms, ms)

  def startFixedRate(ms: Int): Unit =
    t.scheduleAtFixedRate(new TimerTask {
      def run(): Unit = publish(Tick)
    }, ms, ms)

  def stop(): Unit = t.cancel()

}
