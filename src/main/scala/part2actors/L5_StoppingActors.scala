package part2actors

import akka.actor.typed.{ActorSystem, Behavior, PostStop}
import akka.actor.typed.scaladsl.Behaviors

object L5_StoppingActors {

  object SensitiveActor {
    def apply(): Behavior[String] = Behaviors.receive[String] { (context, message) =>
      context.log.info(s"Received: $message")
      // if this actor receives a specific message, it'll stop and then ignores further messages and not be reachable from other Actors
      if (message == "you're ugly") {
        // as we saw earlier, here we need to define the Behavior for the NEXT message
        // "Behaviors.stopped" means this Actor will not receive any more messages and Akka runtime will terminate this Actor
        Behaviors.stopped // optionally pass a callback: {() => Unit} this callback will be executed after this Actor is stopped. use case: to clear up resources after the actor is stopped
        // NOTE: a parent can also stop its DIRECT children by calling: {context.stop(childRef)} => this method only works for direct CHILD Actors, i.e. the context which you're using, must belong to the i=the direct parent of the child you try to stop
        // in such a case, we can receive the signal in the parent as well. see part2actors/L3_ChildActors.scala:77
      }
      else
        Behaviors.same
    }
      // an alternative to the lambda we can pass to Behaviors.stopped( () => Unit ) is to use receiveSignal
      // ever Behavior can be enhanced with another receive-handler for "special messages". this "special messages" are going to be sent to the Actor by Akka-runtime
      // the method that is used for this purpose is "receiveSignal"
      // so whenever we return "Behaviors.stopped", Akka-runtime sends a "PostStop" signal to this Actor which can be caught as below
      // there are some other signals as well. (receiveSignal accepts a partial function)
      .receiveSignal {
        case (context, PostStop) =>
          // clean up resources that this actor might use
          context.log.info("I'm stopped now.")
          Behaviors.same // not used anymore in case of stopping, but for the rest of Signals, it might be important
      }
  }

  def main(args: Array[String]): Unit = {
    val userGuardian = Behaviors.setup[Unit] { context =>
      val sensitiveActor = context.spawn(SensitiveActor(), "sensitiveActor")

      sensitiveActor ! "Hi"
      sensitiveActor ! "How are you"
      sensitiveActor ! "you're ugly"

      // this message won't be delivered and such a long will be printed in the console:
      // akka.actor.LocalActorRef - Message [java.lang.String] to Actor[akka://DemoStoppingActor/user/sensitiveActor#-371909489] was not delivered. [1] dead letters encountered. If this is not an expected behavior then Actor[akka://DemoStoppingActor/user/sensitiveActor#-371909489] may have terminated unexpectedly. This logging can be turned off or adjusted with configuration settings 'akka.log-dead-letters' and 'akka.log-dead-letters-during-shutdown'.
      // log above will be printed when the destination was not found. "dead letters" is a especial Actor which is spawned along side the ActorSystem which is the recipient of all messages that could not find their destination.
      // so when we see "dead letters", it means our Actor is not there
      sensitiveActor ! "sorry about that"

      Behaviors.empty
    }

    val system = ActorSystem(userGuardian, "DemoStoppingActor")
    Thread.sleep(1000)
    system.terminate()
  }
}
