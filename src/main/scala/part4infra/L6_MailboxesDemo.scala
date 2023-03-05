package part4infra

import akka.actor.typed.{ActorSystem, MailboxSelector}
import akka.actor.typed.scaladsl.Behaviors
import akka.dispatch.{ControlMessage, PriorityGenerator, UnboundedPriorityMailbox}
import com.typesafe.config.{Config, ConfigFactory}
import utils._

import scala.concurrent.duration._

object L6_MailboxesDemo {

  /*
  mailboxes control how messages are stored for Actors. mailboxes are basically queues.
  we can configure mailboxes. we can even implement a Custom mailbox and use it in our Actor
   */

  /**
   * A custom priority mailbox: a support ticketing system.
   * P0, P1, P2, P3, ...
   *  - "[P1] Bug fix #43: ..."
   *  - "[P0] Urgent fix needed"
   */
  trait Command

  case class SupportTicket(contents: String) extends Command

  case class Log(contents: String) extends Command

  // although we don't use either of settings: akka.actor.ActorSystem.Settings, config: Config,
  // we need to add them in constructor, otherwise Akka reflection won't instantiate this SupportTicketPriorityMailbox
  // (akka.actor.ActorSystem.Settings is from classic Akka and not the typed one)
  class SupportTicketPriorityMailbox(settings: akka.actor.ActorSystem.Settings, config: Config)
  // we need to extend UnboundedPriorityMailbox which takes a PriorityGenerator as constructor argument
  // PriorityGenerator takes a Partial Function as constructor argument.
  // this Partial Function is of type Any => Number. the lower the Number is, the higher priority will be
    extends UnboundedPriorityMailbox(
      PriorityGenerator {
        case SupportTicket(contents) if contents.startsWith("[P0]") => 0
        case SupportTicket(contents) if contents.startsWith("[P1]") => 1
        case SupportTicket(contents) if contents.startsWith("[P2]") => 2
        case SupportTicket(contents) if contents.startsWith("[P3]") => 3
        case _ => 4 // for Log messages
      }
    )

  def demoSupportTicketMailbox(): Unit = {
    val userGuardian = Behaviors.setup[Unit] { context =>
      // while spawning the Actor, we need to pass the Mailbox config.
      val actor = context.spawn(LoggerActor[Command](), "ticketLogger", MailboxSelector.fromConfig("support-ticket-mailbox"))

      actor ! Log("This is a log that is received first but processed last")
      actor ! SupportTicket("[P1] this thing is broken")
      actor ! SupportTicket("[P0] FIX THIS NOW!")
      actor ! SupportTicket("[P3] something nice to have")

      // When a thread gets assigned to an Actor, whatever is in the mailbox (already ordered) will get handled.
      // so after a thread started working on an Actor, if a higher priority messages comes in, it needs to wait until the previous messages are handled
      Behaviors.empty
    }

    ActorSystem(userGuardian, "DemoMailbox", ConfigFactory.load().getConfig("mailboxes-demo")).withFiniteLifespan(2.seconds)
  }


  // if we just want to give higher priority to a certain kind of messages, we don't need to implement a Custom logic.
  // we just need to extend ControlMessage for those messages we want to give them higher priority.
  // then we need to use "akka.dispatch.UnboundedControlAwareMailbox" as mailbox-type
  case object ManagementTicket extends ControlMessage with Command
  case object ManagementBossTicket extends ControlMessage with Command

  def demoControlAwareMailbox(): Unit = {
    val userGuardian = Behaviors.setup[Unit] { context =>
      val actor = context.spawn(LoggerActor[Command](), "controlAwareLogger", MailboxSelector.fromConfig("control-mailbox"))

      actor ! SupportTicket("[P1] this thing is broken")
      actor ! SupportTicket("[P0] FIX THIS NOW!")
      actor ! ManagementTicket

      Behaviors.empty
    }

    ActorSystem(userGuardian, "DemoControlAwareMailbox", ConfigFactory.load().getConfig("mailboxes-demo")).withFiniteLifespan(2.seconds)
  }

  def main(args: Array[String]): Unit = {
    demoControlAwareMailbox()
  }

}
