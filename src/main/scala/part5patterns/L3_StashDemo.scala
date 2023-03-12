package part5patterns

import akka.actor.typed.{ActorSystem, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import utils._
import scala.concurrent.duration._

object L3_StashDemo {

  // Actors can buffer away messages that they can't or should not process at the time they're received.
  // and this buffer can be emptied and the messages be pushed into the front of the mailbox at our command

  // use case: an actor with a locked access to a resource
  trait Command
  case object Open extends Command
  case object Close extends Command
  case object Read extends Command
  case class Write(data: String) extends Command

  // this ResourceActor has two states: open and closed
  object ResourceActor {
    def apply(): Behavior[Command] = closed("42") // the resource starts as closed with some initial data

    // the Resource starts with the "closed" state. obviously at this state we can only handle "Open" message.
    // so any other messages ("Read", "Write") need to be somehow buffered and then be applied when the resource switched the state into "open"
    // when the Actor switches its state into a state that can process the buffered (stashed) messages,
    // those messages will be moved into mailbox and be processed with priority
    // if the capacity gets fulled, we're going to see this exception:
    // ERROR akka.actor.SupervisorStrategy - Couldn't add [part5patterns.L3_StashDemo$Read$] because stash with capacity [X] is full
    //akka.actor.typed.javadsl.StashOverflowException: Couldn't add [part5patterns.L3_StashDemo$Read$] because stash with capacity [X] is full
    def closed(data: String): Behavior[Command] = Behaviors.withStash(128) { buffer =>
      Behaviors.receive { (context, message) =>
        message match {
          case Open =>
            context.log.info("Opening Resource")
            // we also have "buffer.unstash" which pop only one buffered message
            buffer.unstashAll(open(data)) // open(data) is the next behavior, AFTER unstashing
          case _ =>
            context.log.info(s"Stashing '$message' because the resource is closed")
            buffer.stash(message) // buffer is MUTABLE => we need to be careful not to send it outside of the Actor
            Behaviors.same
        }
      }
    }

    // summary: by using this pattern, we can have a state machine whereby messages not associated to a particular state will be stashed until we switch to the rightful state

    def open(data: String): Behavior[Command] = Behaviors.receive { (context, message) =>
      message match {
        case Read =>
          context.log.info(s"I have read '$data''") // <- in real life you would fetch some actual data
          Behaviors.same
        case Write(newData) =>
          context.log.info(s"I have written '$newData''")
          open(newData)
        case Close =>
          context.log.info(s"Closing Resource")
          closed(data)
        case message =>
          context.log.info(s"$message not supported while resource is open")
          Behaviors.same
      }

    }
  }

  def main(args: Array[String]): Unit = {
    val userGuardian = Behaviors.setup[Unit] { context =>
      val resourceActor = context.spawn(ResourceActor(), "resource")

      resourceActor ! Read // stashed
      resourceActor ! Open // unstash the Read message after opening
      resourceActor ! Open // unhandled (because the resource is already  open)
      resourceActor ! Write("I love stash") // overwrite
      resourceActor ! Write("This is pretty cool") // overwrite
      resourceActor ! Read
      resourceActor ! Read
      resourceActor ! Close
      resourceActor ! Read // stashed: resource is closed

      Behaviors.empty
    }
    ActorSystem(userGuardian, "DemoStash").withFiniteLifespan(2.seconds)
  }
}
