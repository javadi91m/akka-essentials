package part4infra

import akka.actor.Cancellable
import akka.actor.typed.{ActorSystem, Behavior}
import akka.actor.typed.scaladsl.Behaviors

import scala.concurrent.duration._

object L2_Schedulers {

  // Actors can be scheduled to send a message at a particular time
  // IMPORTANT: when the scheduler is triggered, it won't necessarily be run in the same thread which is assigned to the actor.
  // therefore it won't be safe to change the state of the self in the schedulers
  // Akka uses the same thread pool for Actors to run context.scheduleOnce as well.

  // IMPORTANT: always cancel the schedulers you don't need anymore. otherwise you may encounter some serious bugs
  object LoggerActor {
    def apply(): Behavior[String] = Behaviors.receive { (context, message) =>
      context.log.info(s"[${context.self.path}] Received: $message")
      Behaviors.same
    }
  }

  def demoScheduler(): Unit = {
    val userGuardian = Behaviors.setup[Unit] { context =>
      val loggerActor = context.spawn(LoggerActor(), "loggerActor")

      // Actors can be scheduled to only send a message once
      // we can have access to the system scheduler from within the Actor context: context.system.scheduler
      context.log.info("[system] System starting")
      context.scheduleOnce(1.second, loggerActor, "reminder")

      Behaviors.empty
    }

    val system = ActorSystem(userGuardian, "DemoScheduler")

    // ActorSystem has richer APIs regarding scheduling
    // it can be scheduled at fixed rates. it also can take an arbitrary lambda and execute it on top of a thread pool => we can use System's thread pool system.executionContext
    // we can also have access to the system scheduler from within the Actor context: context.system.scheduler

    // while importing system.executionContext and working with Futures, ..., be careful not to use system.executionContext for running Futures
    import system.executionContext
    // be careful not to schedule too many by using system.executionContext, otherwise the Actor system will starve
    system.scheduler.scheduleOnce(2.seconds, () => system.terminate())
  }

  // context.scheduleOnce is really useful when we want to implement a timeout pattern

  def demoActorWithTimeout(): Unit = {
    // we can create an Actor that closes itself after a duration
    val timeoutActor: Behavior[String] = Behaviors.receive { (context, message) =>
      // schedule below has its own APIs and we can call cancel on it and cancel the scheduler
      val schedule = context.scheduleOnce(1.second, context.self, "timeout")

      message match {
        case "timeout" =>
          context.log.info("Stopping!")
          Behaviors.stopped
        case _ =>
          context.log.info(s"Received $message")
          Behaviors.same
      }
    }

    val system = ActorSystem(timeoutActor, "TimeoutDemo")
    system ! "trigger"
    Thread.sleep(2000)
    system ! "are you there?"
  }

  /**
   * Exercise: enhance the timeoutActor to reset its timer with every new message (except the "timeout" message)
   *
   */
  object ResettingTimeoutActor {
    def apply(): Behavior[String] = Behaviors.receive { (context, message) =>
      context.log.info(s"Received: $message")
      resettingTimeoutActor(context.scheduleOnce(1.second, context.self, "timeout"))
    }

    def resettingTimeoutActor(schedule: Cancellable): Behavior[String] = Behaviors.receive { (context, message) =>
      message match {
        case "timeout" =>
          context.log.info("Stopping!")
          Behaviors.stopped
        case _ =>
          context.log.info(s"Received: $message")
          // reset scheduler
          schedule.cancel()
          // start another scheduler
          resettingTimeoutActor(context.scheduleOnce(1.second, context.self, "timeout"))
      }
    }
  }

  def demoActorResettingTimeout(): Unit = {
    val userGuardian = Behaviors.setup[Unit] { context =>
      val resettingTimeoutActor = context.spawn(ResettingTimeoutActor(), "resetter")

      resettingTimeoutActor ! "start timer"
      Thread.sleep(500)
      resettingTimeoutActor ! "reset"
      Thread.sleep(700)
      resettingTimeoutActor ! "this should still be visible"
      Thread.sleep(1200)
      resettingTimeoutActor ! "this should NOT be visible"

      Behaviors.empty
    }

    val system = ActorSystem(userGuardian, "DemoResettingTimeoutActor")
    import system.executionContext
    system.scheduler.scheduleOnce(4.seconds, () => system.terminate())
  }


  def main(args: Array[String]): Unit = {
    demoActorResettingTimeout()
  }

}
