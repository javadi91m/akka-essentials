package part3testing

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import org.scalatest.wordspec.AnyWordSpecLike

class UsingProbesSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {

  import UsingProbesSpec._

  "A master actor" should {
    "register a worker" in {
      val master = testKit.spawn(Master(), "master")
      val workerProbe = testKit.createTestProbe[WorkerTask]()
      val externalProbe = testKit.createTestProbe[ExternalProtocol]()

      master ! Register(workerProbe.ref, externalProbe.ref)
      externalProbe.expectMessage(RegisterAck)
    }

    // we can mock the interaction with the worker actor: we can simply send a message to the master on behalf of Worker
    "send a task to the worker actor" in {
      val master = testKit.spawn(Master())
      val workerProbe = testKit.createTestProbe[WorkerTask]()
      val externalProbe = testKit.createTestProbe[ExternalProtocol]()

      master ! Register(workerProbe.ref, externalProbe.ref)
      // when we send a message to a probe, it stores them in a Queue. and whenever we call probe.expectMessage, it'll extract the first message from the queue and use it for comparison
      externalProbe.expectMessage(RegisterAck)

      val taskString = "I love Akka"
      master ! Work(taskString, externalProbe.ref)

      workerProbe.expectMessage(WorkerTask(taskString, master.ref, externalProbe.ref))
      // mocking the interaction with the worker actor
      master ! WorkCompleted(3, externalProbe.ref)
      externalProbe.expectMessage(Report(3))
    }

    // we can mock the whole Worker Actor: we can customize the Behavior of the TestProbe
    "aggregate data correctly" in {
      val master = testKit.spawn(Master())
      val externalProbe = testKit.createTestProbe[ExternalProtocol]()

      val mockedWorkerBehavior = Behaviors.receiveMessage[WorkerTask] {
        case WorkerTask(_, masterRef, replyTo) =>
          masterRef ! WorkCompleted(3, replyTo)
          Behaviors.same
      }

      // this is how we mock the Behavior of the Probe: by calling Behaviors.monitor
      val workerProbe = testKit.createTestProbe[WorkerTask]()
      val mockedWorker = testKit.spawn(Behaviors.monitor(workerProbe.ref, mockedWorkerBehavior))

      // here we need to register the mocked worker and not the probe one
      master ! Register(mockedWorker, externalProbe.ref)
      externalProbe.expectMessage(RegisterAck)

      val taskString = "I love Akka"
      master ! Work(taskString, externalProbe.ref)
      master ! Work(taskString, externalProbe.ref)


      externalProbe.expectMessage(Report(3))
      externalProbe.expectMessage(Report(6))
    }
  }
}

object UsingProbesSpec {

  /*
    requester -> master -> worker
              <-        <-
   */
  trait MasterProtocol
  case class Work(text: String, replyTo: ActorRef[ExternalProtocol]) extends MasterProtocol
  case class WorkCompleted(count: Int, originalDestination: ActorRef[ExternalProtocol]) extends MasterProtocol
  case class Register(workerRef: ActorRef[WorkerTask], replyTo: ActorRef[ExternalProtocol]) extends MasterProtocol

  case class WorkerTask(text: String, master: ActorRef[MasterProtocol], originalDestination: ActorRef[ExternalProtocol])

  trait ExternalProtocol
  case class Report(totalCount: Int) extends ExternalProtocol
  case object RegisterAck extends ExternalProtocol

  object Master {
    def apply(): Behavior[MasterProtocol] = Behaviors.receiveMessage {
      case Register(workerRef, replyTo) =>
        replyTo ! RegisterAck
        active(workerRef)
      case _ =>
        Behaviors.same
    }

    def active(workerRef: ActorRef[WorkerTask], totalCount: Int = 0): Behavior[MasterProtocol] =
      Behaviors.receive { (context, message) =>
        message match {
          case Work(text, replyTo) =>
            workerRef ! WorkerTask(text, context.self, replyTo)
            Behaviors.same
          case WorkCompleted(count, destination) =>
            val newTotalCount = totalCount + count
            destination ! Report(newTotalCount)
            active(workerRef, newTotalCount)
        }
      }
  }

  // object Worker { ... }
}
