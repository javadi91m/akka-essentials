package part4infra

import akka.actor.typed.{ActorSystem, Behavior, DispatcherSelector}
import akka.actor.typed.scaladsl.Behaviors
import com.typesafe.config.ConfigFactory
import utils._

import scala.concurrent.Future
import scala.util.Random
import scala.concurrent.duration._

object L5_DispatchersDemo {

  /*
  Dispatchers are in charge of delivering and handling messages within an actor system. we can consider them as the "thread-pool" in a high-level
  every ActorSystem must have a dispatcher. we can also configure Actors to have different Dispatchers
   */

  def demoDispatcherConfig(): Unit = {
    val userGuardian = Behaviors.setup[Unit] { context =>
      val childActorDispatcherDefault = context.spawn(LoggerActor[String](), "childActorDispatcherDefault", DispatcherSelector.default())
      val childActorBlocking = context.spawn(LoggerActor[String](), "childActorBlocking", DispatcherSelector.blocking()) // runs on a blocking thread-pool=> for example if we send a message to another Actor from within an Actor, it'll be blocking and synchronous
      val childActorInherit = context.spawn(LoggerActor[String](), "childActorInherit", DispatcherSelector.sameAsParent())
      val childActorConfig = context.spawn(LoggerActor[String](), "childActorConfig", DispatcherSelector.fromConfig("my-dispatcher"))

      val actors = (1 to 10).map(i => context.spawn(LoggerActor[String](), s"child$i", DispatcherSelector.fromConfig("my-dispatcher"))) // defined in application.cong

      val r = new Random()
      // since we have configured the dispatcher with thread-pool-executor.fixed-pool-size=1 and throughput=30, once a thread is assigned to an Actor,
      // it'll process 30 messages and then goes to another Actor. and since we have only one thread, we can see in the logs that one Actor is working on 30 messages in a row
      (1 to 1000).foreach(i => actors(r.nextInt(10)) ! s"task$i")

      Behaviors.empty
    }

    ActorSystem(userGuardian, "DemoDispatchers").withFiniteLifespan(2.seconds)
  }

  /*
   Dispatchers are meant to run Actors. on the other hand, because they have implemented ExecutionContext trait,
   they also can run Futures (Futures need an ExecutionContext to run upon)
   but if use the SAME dispatcher to run Futures and send messages to other Actors, we may end up running into trouble!
   consider the examples below to see the problem and the solution
   */

  // we have a DBActor which simulates a blocking call to the database
  object DBActor {
    def apply(): Behavior[String] = Behaviors.receive { (context, message) =>

      /*
       we need to specify an ExecutionContext for Future.
       if we don't specify any Dispatcher for the Actor running this Behavior, "context.executionContext" will be same as system.context.executionContext
       which is SAME AS THE SYSTEM'S DISPATCHER => the same thread-pool which is responsible for scheduling threads to run Actors.
       so if we run the Future on top of the same Dispatcher of the ActorSystem that has the task of sending/handling messages for the entire ActorSystem,
       we may run into a trouble because running these blocking Futures may starve ActorSystem for thread

       the solution is to provide a dedicated Dispatcher which is optimized to handle blocking interactions and not to use the default System's Dispatcher for this Actor
       */

      import context.executionContext

      Future {
        Thread.sleep(1000)
        println(s"Query successful: $message")
      }

      Behaviors.same
    }
  }

  def demoBlockingCalls(): Unit = {
    val userGuardian = Behaviors.setup[Unit] { context =>

      val loggerActor = context.spawn(LoggerActor[String](), "logger")

      // here we're not providing any dispatcher for this Actor, so it'll be using the default System's Dispatcher
//      val dbActor = context.spawn(DBActor(), "db")

      // here we're providing a dedicated Dispatcher for the blocking process, so the System's Dispatcher won't be starved
      // "dedicated-blocking-dispatcher" resides in "dispatchers-demo" config section in application.conf and we have loaded it while configuring ActorSystem
      val dbActor = context.spawn(DBActor(), "db", DispatcherSelector.fromConfig("dedicated-blocking-dispatcher"))

      /*
      here we are sending messages to two Actors. one of them runs a Future and requires some time for processing, but another one does not requires that amount of time
      so ideally the "fast" Actor should takes all the messages as soon as possible while the "slow" Actor can receive a bunch of messages, process them and then receive the next bunch
      but if we don't provide any dedicated Dispatcher for the "slow" Actor and use the default ActorSystem's Dispatcher,
      ActorSystem will be starving and cannot deliver the messages to the fast Actor as soon as possible. in fact it sends some messages to the fast actor,
      then goes to the slow actor, spends some significant amount of time there, and then comes back to the fast Actor
       */
      (1 to 100).foreach { i =>
        val message = s"query $i"
        dbActor ! message
        loggerActor ! message
      }

      Behaviors.same
    }

    // we have configured the system-level dispatcher in the application.conf
    // we're loading "dispatchers-demo" part from the application.conf => so only that part will be loaded and accessible as config
    val system = ActorSystem(userGuardian, "DemoBlockingCalls", ConfigFactory.load().getConfig("dispatchers-demo"))
  }

  def main(args: Array[String]): Unit = {
    demoBlockingCalls()
  }

}
