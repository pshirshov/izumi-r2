package com.github.pshirshov.izumi.logstage.model.logger

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks._
import com.github.pshirshov.izumi.logstage.model.Log

import scala.concurrent.duration._



class QueueingSink(target: LogSink, sleepTime: FiniteDuration = 50.millis) extends LogSink with AutoCloseable {
  private val queue = new ConcurrentLinkedQueue[Log.Entry]()
  private val maxBatchSize = 100
  private val stop = new AtomicBoolean(false)

  import QueueingSink._

  def start(): Unit = {
    pollingThread.start()
  }

  override def close(): Unit = {
    stop.set(true)
    pollingThread.join()
    finish()
  }

  private val poller = new Runnable {
    override def run(): Unit = {
      while (!stop.get()) {
        try {
          // in case queue was empty we (probably) may sleep, otherwise it's better to continue working asap
          if (doFlush(new CountingStep(maxBatchSize)) == null) {
            Thread.sleep(sleepTime.toMillis)
          } else {
            Thread.`yield`()
          }
        } catch {
          case _: InterruptedException =>
            stop.set(true)

          case e: Throwable => // bad case!
            FallbackLogOutput.flush("Logger polling failed", e)
        }
      }

      finish()
    }
  }

  private def finish(): Unit = {
    doFlush(NullStep).discard()
  }

  private val pollingThread = new Thread(new ThreadGroup("logstage"), poller, "logstage-poll")

  pollingThread.setDaemon(true)

  private def doFlush(step: Step): Log.Entry = {
    var entry = queue.poll()

    while (entry != null && step.continue) {
      target.flush(entry)
      entry = queue.poll()
      step.onStep()
    }

    entry
  }

  override def flush(e: Log.Entry): Unit = {
    queue.add(e).discard()
  }

}

object QueueingSink {

  trait Step {
    def continue: Boolean

    def onStep(): Unit
  }

  class CountingStep(max: Int) extends Step {
    var counter: Int = 0

    override def continue: Boolean = counter <= max

    override def onStep(): Unit = counter += 1
  }

  object NullStep extends Step {
    override def continue: Boolean = true

    override def onStep(): Unit = {}
  }

}
