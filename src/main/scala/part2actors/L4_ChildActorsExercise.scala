package part2actors

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}

object L4_ChildActorsExercise extends App {

  /**
   * Exercise: distributed word counting
   * requester ----- (computational task) ----> WCM ------ (computational task) ----> one child of type WCW
   * requester <---- (computational res) <---- WCM ------ (computational res) <----
   *
   * Scheme for scheduling tasks to children: round robin
   * [1-10]
   * task 1 - child 1
   * task 2 - child 2
   * .
   * .
   * .
   * task 10 - child 10
   * task 11 - child 1
   * task 12 - child 2
   * .
   * .
   */

  trait UserProtocol

  trait MasterProtocol

  trait WorkerProtocol

  // master receives this and then creates this number of children
  case class Initialize(nChildren: Int) extends MasterProtocol

  // worker receives the task and then tries to send result back to the caller (replyTo)
  case class WordCountTask(text: String, replyTo: ActorRef[UserProtocol]) extends MasterProtocol

  // the result that Worker sends to the master
  case class WordCountReplyTask(id: Long, count: Int) extends MasterProtocol


  // worker Messages
  case class WorkerTask(id: Long, text: String) extends WorkerProtocol


  // requester (user) messages
  case class Reply(count: Int) extends UserProtocol


  private def getWorkerId(num: Long) = s"worker-$num"

  object WordCounterMaster {

    def apply(): Behavior[MasterProtocol] = Behaviors.receive { (context, message) =>
      message match {
        case Initialize(nChildren) =>
          context.log.info(s"[master] creating $nChildren number of children")
          val children = (0 until nChildren).map { num =>
            val id = getWorkerId(num)
            val child = context.spawn(WordCounterWorker(context.self), id)
            (id, child)
          }.toMap
          active(children)

        case _ => context.log.info("[master] you need to initialize the children first")
          Behaviors.same
      }
    }

    // we consider currentTaskId as "messageId", so each messageId will be unique.
    // we store (messageId, ActorRef[UserProtocol]) in a Map. so whenever the respective Worker replied, we know which ActorRef[UserProtocol] needs to be notified
    // NOTE: we know that communication in Akka is thread-safe: only one thread at a time works on an Actor, so changing the state (requestMap for example) will be safe
    def active(children: Map[String, ActorRef[WorkerProtocol]], currentTaskId: Long = 1, requestMap: Map[Long, ActorRef[UserProtocol]] = Map.empty): Behavior[MasterProtocol] = Behaviors.receive { (context, message) =>
      message match {
        case WordCountTask(text, replyTo) =>
          val id = getWorkerId(currentTaskId % children.size)
          val worker = children(id)
          context.log.info(s"[master] sending work($currentTaskId) to: $id")
          worker ! WorkerTask(currentTaskId, text)
          active(children, currentTaskId + 1, requestMap + (currentTaskId -> replyTo))


        // reply from worker => notify the caller
        case WordCountReplyTask(id, count) =>
          context.log.info(s"[master] receiving result of the work($id): $count")
          val user = requestMap(id)
          context.log.info(s"[master] sending result of the work($id) to: ${user.path}")
          user ! Reply(count)
          active(children, currentTaskId, requestMap - id)

        case _ => context.log.info("[master] unrecognized message")
          Behaviors.same
      }
    }

  }

  object WordCounterWorker {

    def apply(masterRef: ActorRef[MasterProtocol]): Behavior[WorkerProtocol] = active(masterRef)

    def active(masterRef: ActorRef[MasterProtocol]): Behavior[WorkerProtocol] = Behaviors.receive { (context, message) =>
      message match {

        case WorkerTask(id, text) =>
          context.log.info(s"[${context.self.path.name}] receiving message: $text")
          context.log.info(s"[${context.self.path.name}] sending the result to the master")
          masterRef ! WordCountReplyTask(id, text.split(" ").length)
          Behaviors.same

        case _ => context.log.info(s"[${context.self.path.name}] unrecognized message")
          Behaviors.same
      }
    }
  }

  object Aggregator {
    def apply(): Behavior[UserProtocol] = active()

    def active(totalWords: Int = 0): Behavior[UserProtocol] = Behaviors.receive { (context, message) =>
      message match {
        case Reply(count) => context.log.info(s"[aggregator] I've received: $count, total is ${totalWords + count}")
          active(totalWords + count)
      }
    }

  }

  def testWordCounter(): Unit = {
    val userGuardian: Behavior[Unit] = Behaviors.setup { context =>
      val aggregator = context.spawn(Aggregator(), "aggregator")
      val master = context.spawn(WordCounterMaster(), "master")

      master ! Initialize(3)
      master ! WordCountTask("I love akka", aggregator)
      master ! WordCountTask("Scala is super dope", aggregator)
      master ! WordCountTask("Yes it is", aggregator)
      master ! WordCountTask("Yep!!!", aggregator)
      master ! WordCountTask("Just another one!!!", aggregator)

      Behaviors.empty
    }

    val system = ActorSystem(userGuardian, "system")
    Thread.sleep(1000)
    system.terminate()
  }

  testWordCounter()


}
