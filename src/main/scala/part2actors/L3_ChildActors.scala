package part2actors

import akka.actor.typed.{ActorRef, ActorSystem, Behavior, PostStop, Terminated}
import akka.actor.typed.scaladsl.Behaviors

object L3_ChildActors extends App {

  /*
    - actors can create other actors (children): parent -> child -> grandChild -> ...
                                                        -> child2 -> ...
    an ActorSystem is a hierarchy of Actors creating other Actors in a tree-like structure
    - actor hierarchy = tree-like structure

    - the root of the whole hierarchy is called "guardian" actor (created by using ActorSystem)
    - actors can be identified via a path: /user/parent/child/grandChild/
    - ActorSystem creates
      - the top-level (root) guardian, with children. this guardian also contains:
        - system guardian (for Akka internal messages)
        - user guardian (for our custom actors) => ALL OUR ACTORS are child actors of the user guardian
              => ActorSystem(behavior, "name"): name belongs to the system but behavior is the behavior of user guardian
              user guardian has some important roles, so from now on, we use it only for setting up our message interactions
        - some other guardians that we don't need in this course (for example if we want to do clustering with Akka actors)
   */

  object Parent {

    // based on the Command, parent can spawn other child actors
    // for spawning child actors, we use "context" in the parent
    trait Command

    case class CreateChild(name: String) extends Command

    case class TellChild(message: String) extends Command

    case object StopChild extends Command

    case object WatchChild extends Command

    def apply(): Behavior[Command] = idle()

    def idle(): Behavior[Command] = Behaviors.receive { (context, message) =>
      message match {
        case CreateChild(name) =>
          context.log.info(s"[parent] Creating child with name $name")
          // creating a child actor REFERENCE (it's used to send messages to this child)
          //  ActorRef[String] => String is the type of the message that this child Actor receives
          // spawn also takes an optional Props which is basically an untyped HashMap
          // by using this childRef, we can communicate with the child Actor.
          // so we should save this ref (we can consider it as an internal state for the parent actor) to be able to communicate with it
          val childRef: ActorRef[String] = context.spawn(Child(), name)

          // after creating the child, we change the behavior (of the parent) in a way that messages are sent to the child
          active(childRef)
      }
    }

    // after adding .receiveSignal, we need to specify the type [Command] in Behaviors.receive[Command], otherwise the compiler will complain
    def active(childRef: ActorRef[String]): Behavior[Command] = Behaviors.receive[Command] { (context, message) =>
      message match {
        case TellChild(message) =>
          context.log.info(s"[parent] Sending message $message to ${childRef.path.name}")
          childRef ! message // <- send a message to another actor
          Behaviors.same
        case StopChild =>
          context.log.info("[parent] stopping child")
          context.stop(childRef) // only works with DIRECT CHILD actors
          idle()
        case WatchChild =>
          context.log.info("[parent] watching child")
          // by calling "context.watch(anyActorRef)", this Actor will receive "Terminated(anyActorRef)" signal when "anyActorRef" has stopped
          // this "anyActorRef" doesn't have to be its direct child and can be simply any Actor reference
          // after watching a ref and registering for its Termination, we can call unwatch with passing the same ref and deregister ourself from getting its Termination signal
          // some NOTES here:
              // 1. the Terminated signal will be sent even if the watched Actor is already dead at registration time
              // 2. registering multiple times may/may not generate multiple Terminated signals
              // 3. unwatching will not process Terminated signals even if they have already been enqueued
          context.watch(childRef) // can use any ActorRef
          Behaviors.same
        case _ =>
          context.log.info("[parent] command not supported")
          Behaviors.same
      }
    }
      .receiveSignal {
        // we can receive the signal when the child is Stopped, but first the parent must watch the child: L3_ChildActors.scala:70
        // NOTE: child itself can still receive its PostStop signal. see L3_ChildActors.scala:94
        case (context, Terminated(childRefWhichStopped)) =>
          context.log.info(s"[parent] Child ${childRefWhichStopped.path} was killed by something...")
          idle()
      }
  }

  object Child {
    // context.self is the ActorRef in Akka
    def apply(): Behavior[String] = Behaviors.receive[String] { (context, message) =>
      context.log.info(s"[${context.self.path.name}] Received $message")
      Behaviors.same
    }
      .receiveSignal {
        case (context, PostStop) =>
          context.log.info(s"${context.self.path.name}: I'm stopped!!!!!!")
          Behaviors.same
      }
  }

  def demoParentChild(): Unit = {
    import Parent._
    // user guardian has some important roles, so from now on, we use it only for setting up our message interactions
    // this user guardian does not receive any message, so we can consider it as a Behavior[Unit] or Behavior[NotUsed]
    val userGuardianBehavior: Behavior[Unit] = Behaviors.setup { context =>
      // set up all the important actors in your application
      val parent = context.spawn(Parent(), "parent")
      // set up the initial interaction between the actors
      parent ! CreateChild("child")
      parent ! TellChild("hey kid, you there?")
      parent ! WatchChild
      parent ! StopChild
      parent ! CreateChild("child2")
      parent ! TellChild("yo new kid, how are you?")

      // user guardian usually has no behavior of its own
      Behaviors.empty
    }

    val system = ActorSystem(userGuardianBehavior, "DemoParentChild")
    Thread.sleep(1000)
    system.terminate()
  }

  demoParentChild_v2()

  /**
   * Exercise: write a Parent_V2 that can manage MULTIPLE child actors.
   * TellChild needs to accept a child name argument
   */
  object Parent_V2 {
    trait Command

    case class CreateChild(name: String) extends Command

    case class TellChild(name: String, message: String) extends Command

    case class StopChild(name: String) extends Command

    case class WatchChild(name: String) extends Command

    def apply(): Behavior[Command] = active(Map())

    def active(children: Map[String, ActorRef[String]]): Behavior[Command] = Behaviors.receive[Command] { (context, message) =>
      message match {
        case CreateChild(name) =>
          context.log.info(s"[parent] Creating child '$name'")
          val childRef = context.spawn(Child(), name)
          active(children + (name -> childRef))
        case TellChild(name, message) =>
          val childOption = children.get(name)
          childOption.fold(context.log.info(s"[parent] Child '$name' could not be found"))(child => child ! message)
          Behaviors.same
        case StopChild(name) =>
          context.log.info(s"[parent] attempting to stop child with name $name")
          val childOption = children.get(name)
          childOption.fold(context.log.info(s"[parent] Child $name could not be stopped: name doesn't exist"))(context.stop(_))
          active(children - name)
        case WatchChild(name) =>
          context.log.info(s"[parent] watching child actor with the name $name")
          val childOption = children.get(name)
          childOption.fold(context.log.info(s"[parent] Cannot watch $name: name doesn't exist"))(context.watch(_))
          Behaviors.same
      }
    }
      .receiveSignal {
        case (context, Terminated(ref)) =>
          context.log.info(s"[parent] Child ${ref.path} was killed.")
          val childName = ref.path.name
          active(children - childName)
      }
  }

  def demoParentChild_v2(): Unit = {
    import Parent_V2._
    val userGuardianBehavior: Behavior[Unit] = Behaviors.setup { context =>
      val parent = context.spawn(Parent_V2(), "parent")
      parent ! CreateChild("alice")
      parent ! CreateChild("bob")
      parent ! WatchChild("alice")
      parent ! TellChild("alice", "living next door to you")
      parent ! TellChild("daniel", "I hope your Akka skills are good")
      parent ! StopChild("alice")
      parent ! TellChild("alice", "hey Alice, you still there?")

      Behaviors.empty
    }

    val system = ActorSystem(userGuardianBehavior, "DemoParentChildV2")
    Thread.sleep(1000)
    system.terminate()
  }

}
