package part2actors

import akka.actor.typed.{ActorSystem, Behavior, SupervisorStrategy, Terminated}
import akka.actor.typed.scaladsl.Behaviors
import scala.concurrent.duration._

object L7_Supervision extends App {

  /*
  Supervision: what we can do if an Actor dies. in Akka, Actors might die for a different variety of reasons
  and this is our Akka programmers responsibility to decide what should be done about those failures so our system remains resilient
  for example if an Actor throw an exception, it'll be killed. generally when a Behavior crashes, the whole Behavior will be killed.
  it means if Behavior of the guardian of the ActorSystem encounters an exception, the entire ActorSystem will crash.
  when an Actor crashes like that, a Terminated signal will be sent to any actors that have been registered themselves for the termination signal

  with the help of supervision, we can recover from those failures.
  one of the ways is that parent can do something about it => parent can restart the failed child. see "demoSupervisionWithRestart" method
   */

  object FussyWordCounter {
    def apply(): Behavior[String] = active()

    def active(total: Int = 0): Behavior[String] = Behaviors.receive { (context, message) =>
      val wordCount = message.split(" ").length
      context.log.info(s"Received piece of text: '$message', counted $wordCount words, total: ${total + wordCount}")
      // throw some exceptions (maybe unintentionally)
      if (message.startsWith("Q")) throw new RuntimeException("I HATE queues!")
      if (message.startsWith("W")) throw new NullPointerException

      active(total + wordCount)
    }
  }

  // actor throwing exception gets killed

  def demoCrash(): Unit = {
    val guardian: Behavior[Unit] = Behaviors.setup { context =>
      val fussyCounter = context.spawn(FussyWordCounter(), "fussyCounter")

      fussyCounter ! "Starting to understand this Akka business..."
      fussyCounter ! "Quick! Hide!"
      fussyCounter ! "Are you there?"

      Behaviors.empty
    }

    val system = ActorSystem(guardian, "DemoCrash")
    Thread.sleep(1000)
    system.terminate()
  }

  // when an Actor crashes like that, a Terminated signal will be sent to any actors that have been registered themselves for the termination signal (by calling context.watch)
  def demoWithParent(): Unit = {
    val parentBehavior: Behavior[String] = Behaviors.setup { context =>
      val child = context.spawn(FussyWordCounter(), "fussyChild")
      context.watch(child)

      Behaviors.receiveMessage[String] { message =>
        child ! message
        Behaviors.same
      }
        .receiveSignal {
          case (context, Terminated(childRef)) =>
            context.log.warn(s"Child failed: ${childRef.path.name}")
            Behaviors.same
        }
    }

    val guardian: Behavior[Unit] = Behaviors.setup { context =>
      val fussyCounter = context.spawn(parentBehavior, "fussyCounter")

      fussyCounter ! "Starting to understand this Akka business..."
      fussyCounter ! "Quick! Hide!"
      fussyCounter ! "Are you there?"

      Behaviors.empty
    }

    val system = ActorSystem(guardian, "DemoCrashWithParent")
    Thread.sleep(1000)
    system.terminate()
  }

  def demoSupervisionWithRestart(): Unit = {
    val parentBehavior: Behavior[String] = Behaviors.setup { context =>
      // supervise the child with a restart "strategy"
      // if Actors fails, it will never get killed, so the Terminated signal also won't get published as well.
      // instead, it'll be simply restarted, i.e. Akka just calls FussyWordCounter() again and therefore, Actor will be restarted with its initial state.
      /*
      there are some other strategies:
        1. resume => the exception will be ignored and Actor will keep going. so its state will be kept.
        2. restart => is what we explained above => the actor state will be reset to its initial value.
        3. stop => it's the default behavior. Actor will be stopped and a Terminated signal will be sent. if an Actor stops, all of its children in its hierarchy also will be stopped
        4. restartWithBackoff => consider this scenario when an Actor fails after it gets restarted.
              for example maybe the Actor tries to establish a database connection and it fails. in sucha case, we may want to retry after a certain amount of time
              this method tries to restart the Actor repeatedly, until some conditions are met:
              import scala.concurrent.duration._
              Behaviors.supervise(FussyWordCounter()).onFailure[RuntimeException](SupervisorStrategy.restartWithBackoff(1.second, 1.minute, 0.2))
              first parameter is initial retry delay attempt
              second one is the maximum delay for retry
              third parameter is: every time that a retry attempts and fails, the initial time gets (doubled + a random value between 0 and this third parameter as percent) until we reach to the maximum amount (the second parameter)

        NOTE: if we place Behaviors.supervise on the Actor's Behavior directly and not go wia its parent, the Actor will be killed by Akka anyway.
        and there's no need to do so! we could put a try/catch block in the Actor itself!!!

        since the Behaviors.supervise is a Behavior itself, we can wrap it in other Behaviors. it means we can wrap it again in another Behaviors.supervise
        by doing so, we'll be able to handle different failures/exceptions and define different strategies per each
        OO equivalent:
        try { .. }
        catch {
          case NullPointerException =>
          case IndexOutOfBoundsException =>
        }

        NOTE: place the most specific exception handlers INSIDE and the most general exception handlers OUTSIDE.
        so the below strategies are kinda wrong, because always the (onFailure[RuntimeException](SupervisorStrategy.restart)) will be run
       */
      val childBehavior = Behaviors.supervise(
        Behaviors.supervise(FussyWordCounter()).onFailure[RuntimeException](SupervisorStrategy.restart)
      ).onFailure[NullPointerException](SupervisorStrategy.resume)

      val child = context.spawn(childBehavior, "fussyChild")
      context.watch(child)

      Behaviors.receiveMessage[String] { message =>
        child ! message
        Behaviors.same
      }
        .receiveSignal {
          case (context, Terminated(childRef)) =>
            context.log.warn(s"Child failed: ${childRef.path.name}")
            Behaviors.same
        }
    }

    val guardian: Behavior[Unit] = Behaviors.setup { context =>
      val fussyCounter = context.spawn(parentBehavior, "fussyCounter")

      fussyCounter ! "Starting to understand this Akka business..."
      fussyCounter ! "Quick! Hide!"
      fussyCounter ! "Are you there?"
      fussyCounter ! "What are you doing?"
      fussyCounter ! "Are you still there?"

      Behaviors.empty
    }

    val system = ActorSystem(guardian, "DemoCrashWithParent")
    Thread.sleep(1000)
    system.terminate()
  }

  /**
   * Exercise: how do we specify different supervisor strategies for different exception types?
   */
  val differentStrategies = Behaviors.supervise(
    Behaviors.supervise(FussyWordCounter()).onFailure[NullPointerException](SupervisorStrategy.resume)
  ).onFailure[IndexOutOfBoundsException](SupervisorStrategy.restart)


  demoSupervisionWithRestart()
}
