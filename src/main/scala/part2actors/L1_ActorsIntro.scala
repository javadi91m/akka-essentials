package part2actors

import akka.actor.typed.{ActorSystem, Behavior}
import akka.actor.typed.scaladsl.Behaviors

object L1_ActorsIntro {

  /*
  in Actors we use a different programming paradigm.

  with traditional objects (the paradigm we all know and are familiar with), we model our code around instances of classes:
      - each instance has its own state stored as data
      - we interact with each instance via its methods.

  but in Actors, things can be a little different:
      - still we store state as data (similar to what we see in objects)
      - but interaction with the world is different. the way that we interact with Actors, is by sending messages to them.
        we can say Actors are object that we cannot access directly and we can only send messages to them.

  there are some NATURAL principals engaged in AkkaL
    1. every interaction happens via sending and receiving messages
    2. messages are asynchronous by nature:
        - it takes time for a message to travel
        - sending and receiving may not happen at the same time
   */

  // in Akka we define an Actor in terms of its behavior. a behavior is the description of what an actor will do when it receives a message
  // so in order to create an actor we need to define a behavior

  // part 1: behavior
  // Behavior[String] => [String] defines the type of the message this Actor will receive
  // Behaviors.receiveMessage => takes a function that receives the message, do something with it, and then returns another BEHAVIOR of the SAME type (here String)
  // the returned Behavior is the behavior that this Actor will take after receiving this message. so the Actor receives a message, then changes its behavior. then receives another message and changes its behavior again and so on
  // it's common that we keep the behavior the same
  val simpleActorBehavior: Behavior[String] = Behaviors.receiveMessage { (message: String) =>
    // do something with the message
    println(s"[simple actor] I have received: $message")

    // new behavior for the NEXT message
    // so you can get a message in the Actor, process it and then you can specify the behavior of this Actor for the NEXT incoming message. we can even change the behavior
    // here we define the exact same logic for processing the next message
    Behaviors.same
  }

  // in the above piece of code, we have defined the Actor, but we haven't instantiated it yet.
  def demoSimpleActor(): Unit = {
    // part 2: instantiate
    // here we instantiate our Actor as an ActorSystem. we'll describe ActorSystem more later on
//    val actorSystem = ActorSystem(simpleActorBehavior, "FirstActorSystem")
//    val actorSystem = ActorSystem(SimpleActor(), "FirstActorSystem")
//    val actorSystem = ActorSystem(SimpleActor_V2(), "FirstActorSystem")
    val actorSystem = ActorSystem(SimpleActor_V3(), "FirstActorSystem")
//    val actorSystem = ActorSystem(Person.happy(), "FirstActorSystem")

    // part 3: communicate!
    actorSystem ! "I am learning Akka" // asynchronously send a message
    // ! = the "tell" method

    // part 4: gracefully shut down
    Thread.sleep(1000) // this is not advised and later on we'll see a better solution
    actorSystem.terminate()
  }

  // as a best practice, we usually don't create Actors as values (like what we saw above) but rather, we create an object with apply method
  // we can refactor above Actor as below
  object SimpleActor {
    def apply(): Behavior[String] = Behaviors.receiveMessage { (message: String) =>
      // do something with the message
      println(s"[simple actor] I have received: $message")

      // new behavior for the NEXT message
      Behaviors.same
    }
  }

  // with SimpleActor above, now we can instantiate an ActorSystem as below:
  val actorSystem = ActorSystem(SimpleActor(), "FirstActorSystem")



  // there are many ways of creating Actors and Behaviors
  // so far we used Behaviors.receiveMessage factory method. now we're exploring Behaviors.receive method
  object SimpleActor_V2 {
    def apply(): Behavior[String] = Behaviors.receive { (context, message) =>
      // context is a data structure (ActorContext) with access to a variety of APIs
      // context stays the same when the actor is instantiated. it means this ActorContext is created alongside with the actor
      // and is being passed around by the underlying Akka APIs to us so we can have access to this data structure
      // simple example: logging
      context.log.info(s"[simple actor] I have received: $message")
      Behaviors.same
    }
  }



  // there is another, even more general way of creating an actor Behavior with Behaviors.setup
  object SimpleActor_V3 {
    def apply(): Behavior[String] = Behaviors.setup { context =>
      // here we can define/access some "private" data and methods, behaviors etc
      // YOUR CODE HERE

      // behavior used for the FIRST message
      // the advantage of Behaviors.setup is that we can have access to the context BEFORE returning the Behavior for the FIRST message
      Behaviors.receiveMessage { message =>
        context.log.info(s"[simple actor] I have received: $message")
        Behaviors.same
      }
    }
  }



  /**
   * Exercises
   * 1. Define two "person" actor behaviors, which receive Strings:
   *  - "happy", which logs your message, e.g. "I've received ____. That's great!"
   *  - "sad", .... "That sucks."
   *  Test both.
   *
   * 2. Change the actor behavior:
   *  - the happy behavior will turn to sad() if it receives "Akka is bad."
   *  - the sad behavior will turn to happy() if it receives "Akka is awesome!"
   *
   * 3. Inspect my code and try to make it better.
   */

  object Person {
    def happy(): Behavior[String] = Behaviors.receive { (context, message) =>
      message match {
        case "Akka is bad." =>
          context.log.info("Don't you say anything bad about Akka!!!")
          sad()
        case _ =>
          context.log.info(s"I've received '$message'.That's great!")
          Behaviors.same
      }
    }

    def sad(): Behavior[String] = Behaviors.receive { (context, message) =>
      message match {
        case "Akka is awesome!" =>
          context.log.info("Happy now!")
          happy()
        case _ =>
          context.log.info(s"I've received '$message'.That sucks!")
          Behaviors.same
      }
    }

    def apply(): Behavior[String] = happy()
  }

  def testPerson(): Unit = {
    val person = ActorSystem(Person(), "PersonTest")

    person ! "I love the color blue."
    person ! "Akka is bad."
    person ! "I also love the color red."
    person ! "Akka is awesome!"
    person ! "I love Akka."

    Thread.sleep(1000)
    person.terminate()
  }

  object WeirdActor {
    // wants to receive messages of type Int AND String
    def apply(): Behavior[Any] = Behaviors.receive { (context, message) =>
      message match {
        case number: Int =>
          context.log.info(s"I've received an int: $number")
          Behaviors.same
        case string: String =>
          context.log.info(s"I've received a String: $string")
          Behaviors.same
      }
    }
  }

  // solution: add wrapper types & type hierarchy (case classes/objects)
  object BetterActor {
    trait Message
    case class IntMessage(number: Int) extends Message
    case class StringMessage(string: String) extends Message

    def apply(): Behavior[Message] = Behaviors.receive { (context, message) =>
      message match {
        case IntMessage(number) =>
          context.log.info(s"I've received an int: $number")
          Behaviors.same
        case StringMessage(string) =>
          context.log.info(s"I've received a String: $string")
          Behaviors.same
      }
    }
  }


  def demoWeirdActor(): Unit = {
    import BetterActor._
    val weirdActor = ActorSystem(BetterActor(), "WeirdActorDemo")
    weirdActor ! IntMessage(43) // ok
    weirdActor ! StringMessage("Akka") // ok
    // weirdActor ! '\t' // not ok

    Thread.sleep(1000)
    weirdActor.terminate()
  }

  def main(args: Array[String]): Unit = {
    useActor()
  }
}
