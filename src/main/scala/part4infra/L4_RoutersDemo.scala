package part4infra

import akka.actor.typed.{ActorSystem, Behavior}
import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import utils._
import akka.actor.typed.scaladsl.{Behaviors, Routers}

import scala.concurrent.duration._

object L4_RoutersDemo {

  /*
  Routers are a technique to distribute work in Akka automatically
  if you remember, previously in WordCountMaster we did some manual things to distribute work between a bunch of Actors
  now in Routers we can do such a thing in a more simple way
  there are two techniques we can use:
    - PoolRouter:
        this one is simpler. we just need to create a Behavior, an then tell Akka how many actors we want to be created and put in the pool.
        so we don't create any worker Actors, we just define one behavior and then tell Akka how many worker Actors we need with that Behavior
        it allows us to distribute work in between Actors of the exact same type

    - GroupRouter:
        this one is more complex. we need to define Actors by ourself. and these Actors can be created in different parts of the code.
        then we need to have a ServiceKey. now every Actor that registers itself with this ServiceKey, will be part of the group.
        in order to register an Actor to a ServiceKey, we need to use "context.system.receptionist":
            context.system.receptionist ! Receptionist.Register(serviceKey, worker)
        after registering the Actors to the ServiceKey, we need to define the Group's Behavior:
            val groupBehavior: Behavior[String] = Routers.group(serviceKey).withRoundRobinRouting() // random by default
        then we create the group Actor:
            val groupRouter = context.spawn(groupBehavior, "workerGroup")

        in a GroupRouter, we can register a new Actor to the group after its creation, or even deregister an existing member from the group

        if we want to remove a registered Actor from the group while Router is working, we might lose messages sent to that particular Actor
        so we must be really careful. here is the best practice for removing an Actor from the group:
        send a message to the receptionist: Receptionist.Deregister(serviceKey, worker, someActorToReceiveConfirmation)
        whenever receptionist successfully removes the worker, it'll send a confirmation message (Receptionist.Deregistered) to the "someActorToReceiveConfirmation" actor
        now the best practice is to use the same "worker" (which we want to remove) as the "someActorToReceiveConfirmation"
        and then in the "worker" by receiving the Receptionist.Deregistered message, we can safely stop the worker.
        NOTE: even by following this best-practice, we still might lose some messages by a low chance!


best practice, someActorToReceiveConfirmation == worker
  --- in this time, there's a risk that the router might still use the worker as the routee
  - safe to stop the worker

   */


  // Some good resources to read about consistent hashing if you're not familiar with it:
  // http://tom-e-white.com/2007/11/consistent-hashing.html
  // https://www.toptal.com/big-data/consistent-hashing

  def demoPoolRouter(): Unit = {
    val workerBehavior = LoggerActor[String]()
    // here the "poolRouter" will be a master BEHAVIOR (it's not an actor itself) which receives messages of type String,
    // and then forward that message to one of the workers in a round robin order by default
    // we can also define our custom order
    val poolRouter = Routers.pool(5)(workerBehavior)
      // we can optionally pass a predicate in "withBroadcastPredicate", if this Predicate returns true for an incoming message, that message will be broad-casted to all workers
      .withBroadcastPredicate(_.length > 11)
    // instead of default round robin dispatching mechanism, we can also use any of these:
    // .withRandomRouting OR .withConsistentHashingRouting OR .withRoundRobinRouting()

    val userGuardian = Behaviors.setup[Unit] { context =>
      val poolActor = context.spawn(poolRouter, "pool")

      (1 to 10).foreach(i => poolActor ! s"work task $i")

      Behaviors.empty
    }

    ActorSystem(userGuardian, "DemoPoolRouter").withFiniteLifespan(2.seconds)
  }

  def demoGroupRouter(): Unit = {
    val serviceKey = ServiceKey[String]("logWorker")
    // service keys are used by a core akka module for discovering actors and fetching their Refs

    val userGuardian = Behaviors.setup[Unit] { context =>
      // in real life the workers may be created elsewhere in your code
      val workers = (1 to 5).map(i => context.spawn(LoggerActor[String](), s"worker$i"))
      // register the workers with the service key
      workers.foreach(worker => context.system.receptionist ! Receptionist.Register(serviceKey, worker))

      val groupBehavior: Behavior[String] = Routers.group(serviceKey).withRoundRobinRouting() // random by default
      val groupRouter = context.spawn(groupBehavior, "workerGroup")

      (1 to 10).foreach(i => groupRouter ! s"work task $i")

      // add new workers later
      Thread.sleep(1000)
      val extraWorker = context.spawn(LoggerActor[String](), "extraWorker")
      context.system.receptionist ! Receptionist.Register(serviceKey, extraWorker)
      (1 to 10).foreach(i => groupRouter ! s"work task $i")

      /*
        removing workers:
        - send the receptionist a Receptionist.Deregister(serviceKey, worker, someActorToReceiveConfirmation)
        - receive Receptionist.Deregistered in someActorToReceiveConfirmation, best practice is to use same "worker" as the "someActorToReceiveConfirmation"
        --- in this time, there's a risk that the router might still use the worker as the routee
        - after receiving Receptionist.Deregistered signal in the worker, we can safely stop the worker
        NOTE: even by following this best-practice, we still might lose some messages!
       */
      Behaviors.empty
    }

    ActorSystem(userGuardian, "DemoGroupRouter").withFiniteLifespan(2.seconds)
  }

  def main(args: Array[String]): Unit = {
    demoGroupRouter()
  }

}
