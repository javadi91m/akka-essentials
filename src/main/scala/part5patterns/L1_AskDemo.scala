package part5patterns

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, Scheduler}
import akka.util.Timeout

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}
import scala.concurrent.duration._
import utils._
import scala.util.{Try, Success, Failure}

object L1_AskDemo {

  // Ask pattern is useful for interaction patterns in the form of Request/Response.
  // the goal of this pattern is to obtain a Future of the result which we can process later outside the context of an Actor interaction

  // we're going to create an Actor that gets a request and returns a response.
  // we send a "ComputationalTask" and we expect a "ComputationalResult"
  trait WorkProtocol

  case class ComputationalTask(payload: String, replyTo: ActorRef[WorkProtocol]) extends WorkProtocol

  case class ComputationalResult(result: Int) extends WorkProtocol

  object Worker {
    def apply(): Behavior[WorkProtocol] = Behaviors.receive { (context, message) =>
      message match {
        case ComputationalTask(text, destination) =>
          context.log.info(s"[worker] Crunching data for $text")
          destination ! ComputationalResult(text.split(" ").length)
          Behaviors.same
        case _ => Behaviors.same
      }
    }
  }

  // we can communicate with "Worker" without actually having any recipient Actor.
  // we just send a message to it and then get its response as a Future
  def askSimple(): Unit = {
    // 1 - import the right package
    // this AskPattern object contains some extension methods for an ActorSystem or an Actor Context (we'll see this in the next example)
    import akka.actor.typed.scaladsl.AskPattern._

    // 2 - set up some implicits
    val system = ActorSystem(Worker(), "DemoAskSimple").withFiniteLifespan(5.seconds)
    implicit val timeout: Timeout = Timeout(3.seconds) // after this duration the future gets failed
    implicit val scheduler: Scheduler = system.scheduler // to schedule the Future for execution

    // 3 - call the ask method
    val reply: Future[WorkProtocol] = system.ask(ref => ComputationalTask("Trying the ask pattern, seems convoluted", ref))
    //                                           ^ temporary actor   ^ message that gets sent to the worker (in this case it's the user guardian)

    // 4 - process the Future
    implicit val ec: ExecutionContext = system.executionContext
    reply.foreach(println)
  }

  // in this example, the user guardian is not the Actor itself but its parent!
  // the parent creates a child, asks something, and then handle its response.
  def askFromWithinAnotherActor(): Unit = {
    val userGuardian = Behaviors.setup[WorkProtocol] { context =>
      val worker = context.spawn(Worker(), "worker")

      // 0 - define extra messages that I should handle as results of ask
      case class ExtendedComputationalResult(count: Int, description: String) extends WorkProtocol

      // 1 - set up the implicits
      implicit val timeout: Timeout = Timeout(3.seconds)

      // 2 - ask
      context.ask(worker, ref => ComputationalTask("This ask pattern seems quite complicated", ref)) {
        // here we're defining a (partial) function (we need to pass it as second argument lists
        // this function needs to handle the response from Worker which is of type "ComputationalResult"
        // this function needs to take this "ComputationalResult" and then return a new message of type "WorkProtocol"
        // then we can receive this "new message" in the Actor Behavior

        // Try[WorkProtocol] => WorkProtocol message that will be sent TO ME later
        case Success(ComputationalResult(count)) => ExtendedComputationalResult(count, "This is pretty damn hard")
        case Failure(ex) => ExtendedComputationalResult(-1, s"Computation failed: $ex")
      }

      // 3 - handle the result (messages) from the ask pattern
      Behaviors.receiveMessage {
        case ExtendedComputationalResult(count, description) =>
          context.log.info(s"Ask and ye shall receive: $description - $count")
          Behaviors.same
        case _ => Behaviors.same
      }
    }

    ActorSystem(userGuardian, "DemoAskConvoluted").withFiniteLifespan(5.seconds)
  }

  def main(args: Array[String]): Unit = {
    askFromWithinAnotherActor()
  }

}
