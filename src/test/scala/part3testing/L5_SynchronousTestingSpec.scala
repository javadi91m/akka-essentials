package part3testing

import akka.actor.testkit.typed.CapturedLogEvent
import akka.actor.testkit.typed.Effect.Spawned
import akka.actor.testkit.typed.scaladsl.{BehaviorTestKit, ScalaTestWithActorTestKit, TestInbox}
import org.scalatest.wordspec.AnyWordSpecLike
import org.slf4j.event.Level
import part2actors.ChildActorsExercise._

class L5_SynchronousTestingSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {

  // because Akka actors are asynchronous in nature, sending and receiving messages happen somewhere in the future that we cannot control
  // synchronous testing is useful when we want to replay a sequence of events, directly.

  "A word counter master" should {

    // we want to check whether the child Actor has been actually created => we want to check the creation "effect" of an Actor
    "spawn a child upon reception of the initialize message" in {
      // we use "BehaviorTestKit" for testing synchronous effects. by using them, we can run some actual effect such as sending messages
      // but the difference is that everything will be synchronous and not asynchronous. i.e. everything will be run on the same thread
      val master = BehaviorTestKit(WordCounterMaster())
      master.run(Initialize(1)) // synchronous "sending" of the message Initialize(1)

      // check that an effect was produced =>
      // there are several effects that we can expect: Spawned, Stopped, Watched, ReceiveTimeoutSet, Scheduled, NoEffects, ...
      // Spawned[WorkerProtocol] => indicates that an Actor was spawned which accept messages of type 'WorkerProtocol'
      // if there are more than one effects produced, we can inspect them all with the same approach
      val effect = master.expectEffectType[Spawned[WorkerProtocol]]
      // inspect the contents of those effects
      effect.childName should equal("worker1")
    }

    "send a task to a child" in {
      val master = BehaviorTestKit(WordCounterMaster())
      master.run(Initialize(1))

      // from the previous test - "consume" the event
      val effect = master.expectEffectType[Spawned[WorkerProtocol]]

      val mailbox = TestInbox[UserProtocol]() // the "requester"'s inbox => a queue of messages: an Actor without behavior
      // start processing
      master.run(WordCountTask("Akka testing is pretty powerful!", mailbox.ref)) // mailbox.ref is an empty kind of Actor that just dequeue the messages from the mailbox and does not do anything with it
      // mock the reply from the child
      master.run(WordCountReply(0, 5))
      // test that the requester got the right message
      mailbox.expectMessage(Reply(5))
    }

    // we can even inspect logs
    "log messages" in {
      val master = BehaviorTestKit(WordCounterMaster())
      master.run(Initialize(1))
      master.logEntries() shouldBe Seq(CapturedLogEvent(Level.INFO, "[master] initializing with 1 children"))
    }
  }
}
