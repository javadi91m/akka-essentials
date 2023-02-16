package part2actors

import akka.actor.typed.{ActorSystem, Behavior}
import akka.actor.typed.scaladsl.Behaviors

object L2_ActorState extends App {

  /*
  Akka has a thread pool that is shared with Actors (plz check src/main/scala/part2actors/L2-01.png)
  an Actor is just a data structure that has a message handler (the same Behavior we define for it) and a message Queue that keeps that message sent to it
  this Actor is passive and cannot handle any message, so it needs a thread to process the message stored in its queue
  so the way that Akka works is that it has a thread pool (maybe tens or at most hundreds). then this thread pool can handle millions of Actors.

  how it works?
  Akka schedules actors to be executed by thread pool.
  when we send a message to an actor, the message will be added to its queue. then Akka schedules a thread to run this actor,
  so at some point, a thread will take control of this actor and it'll start extracting messages from actor's queue and this happens in-order. then for each message, message-handler will be called by thread
  and as result, actor might change its state or even send messages to other actors. after that, the message will be simply discarded.
  at some point, Akka might decide to release the thread from that actor and do something else by that thread (maybe it takes control of another actor)

  this whole process, offers us some guarantees:
  1.  only one thread operates on an actor at any time, which makes actors effectively single-threaded.
      it means we don't need to do any locking/synchronization over the actor state.
      a thread will never abandon processing a message at the middle of it. so processing a message will be atomic.

  2. Akka offers "at most once delivery" as message delivery guarantee, so an Actor never receives duplicate messages

  3. for any sender/receiver pair, the message order is kept.
   */


  /*
    Exercise: use the setup method to create a word counter which
      - splits each message into words
      - keeps track of the TOTAL number of words received so far
      - log the current # of words + TOTAL # of words
   */
  object WordCounter {
    def apply(): Behavior[String] = Behaviors.setup { context =>
      var total = 0

      Behaviors.receiveMessage { message =>
        val newCount = message.split(" ").length
        total += newCount
        context.log.info(s"Message word count: $newCount - total count: $total")
        Behaviors.same
      }
    }
  }

  // although using var is discouraged, using var (inside a Behaviors.setup) can solve out problem.
  // but handling state using a var like above, can be tedious. as an example, consider below:

  trait SimpleThing
  case object EatChocolate extends SimpleThing
  case object CleanUpTheFloor extends SimpleThing
  case object LearnAkka extends SimpleThing
  /*
    Message types must be IMMUTABLE and SERIALIZABLE.
    - use case classes/objects
    - use a flat type hierarchy
   */

  object SimpleHuman {
    def apply(): Behavior[SimpleThing] = Behaviors.setup { context =>
      var happiness = 0

      Behaviors.receiveMessage {
        case EatChocolate =>
          context.log.info(s"[$happiness] Eating chocolate")
          happiness += 1
          Behaviors.same
        case CleanUpTheFloor =>
          context.log.info(s"[$happiness] Wiping the floor, ugh...")
          happiness -= 2
          Behaviors.same
        case LearnAkka =>
          context.log.info(s"[$happiness] Learning Akka, YAY!")
          happiness += 99
          Behaviors.same
      }
    }
  }

  def demoSimpleHuman(): Unit = {
    val human = ActorSystem(SimpleHuman_V2(), "DemoSimpleHuman")

    human ! LearnAkka
    human ! EatChocolate
    (1 to 30).foreach(_ => human ! CleanUpTheFloor)

    Thread.sleep(1000)
    human.terminate()
  }

  object SimpleHuman_V2 {
    def apply(): Behavior[SimpleThing] = statelessHuman(0)

    def statelessHuman(happiness: Int): Behavior[SimpleThing] = Behaviors.receive { (context, message) =>
      message match {
        case EatChocolate =>
          context.log.info(s"[$happiness] Eating chocolate")

          // NOTE: it's not a recursion: it's just invocation of a method that returns a new data structure (Actor)
          // this new Behavior will be used to handle NEXT message at some point in the future. it might not be even the same thread
          // here instead of using a variable for managing the state, we're returning a NEW Behavior with a new argument as its state
          statelessHuman(happiness + 1)
        case CleanUpTheFloor =>
          context.log.info(s"[$happiness] Wiping the floor, ugh...")
          statelessHuman(happiness - 2)
        case LearnAkka =>
          context.log.info(s"[$happiness] Learning Akka, YAY!")
          statelessHuman(happiness + 99)
      }
    }
  }

  /*
    Tips:
    - each var/mutable field becomes an immutable METHOD ARGUMENT
    - each state change = new behavior obtained by calling the method with a different argument
   */

  /**
   * Exercise: refactor the "stateful" word counter into a "stateless" version.
   */
  object WordCounter_V2 {
    def apply(): Behavior[String] = active(0)

    def active(total: Int): Behavior[String] = Behaviors.setup { context =>
      Behaviors.receiveMessage { message =>
        val newCount = message.split(" ").length
        context.log.info(s"Message word count: $newCount - total count: ${total + newCount}")
        active(total + newCount)
      }
    }
  }

  def demoWordCounter(): Unit = {
    val wordCounter = ActorSystem(WordCounter_V2(), "WordCounterDemo")

    wordCounter ! "I am learning Akka"
    wordCounter ! "I hope you will be stateless one day"
    wordCounter ! "Let's see the next one"

    Thread.sleep(1000)
    wordCounter.terminate()
  }

}
