package part4infra

import akka.actor.testkit.typed.scaladsl.{ManualTime, ScalaTestWithActorTestKit}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import org.scalatest.wordspec.AnyWordSpecLike
import scala.concurrent.duration._

// we can set timers withing an Actor's behavior, so we can manipulate when we send certain messages to certain Actors
// and also we show how we can manually manipulate time within a test. for this purpose we need to import a config into ScalaTestWithActorTestKit
// this config is simple: akka.scheduler.implementation = "akka.testkit.ExplicitlyTriggeredScheduler"
class L3_TimersSpec extends ScalaTestWithActorTestKit(ManualTime.config) with AnyWordSpecLike {

  import L3_TimersSpec._

  "A reporter" should {
    "trigger report in an hour" in {
      val probe = testKit.createTestProbe[Command]()
      val time: ManualTime = ManualTime()

      testKit.spawn(Reporter(probe.ref))

      probe.expectNoMessage(1.second)
      // accelerate time
      time.timePasses(1.hour)
      // assertions "after" 1 hour
      probe.expectMessage(Report)
    }
  }

}

object L3_TimersSpec {

  trait Command

  case object Timeout extends Command

  case object Report extends Command

  object Reporter {
    // by using of Behaviors.withTimers, we can access to a timer, by which we can send a self message after a certain amount of time
    def apply(destination: ActorRef[Command]): Behavior[Command] = Behaviors.withTimers { timer =>
      val key = "myTestTimerKey"
      // timer has rich APIs, such as "startTimerAtFixedRate" and ...
      timer.startSingleTimer(key, Timeout, 1.hour)
      // timer is kinda like "context.system.scheduler", but they are easier to use
      // one advantage in "context.system.scheduler" is that they could accept and run a lambda
      // context.system.scheduler.scheduleOnce(... () => ...)

      // key parameter is optional, we can use it to cancel a timer:
      // timer.cancel(key)

      Behaviors.receiveMessage {
        case Timeout =>
          destination ! Report
          Behaviors.same
      }
    }
  }

}
