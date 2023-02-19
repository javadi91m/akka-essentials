package part2actors

import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.actor.typed.scaladsl.Behaviors

import scala.collection.mutable.{Map => MutableMap}

/*
  remember we said Actors are effectively single-threaded and encapsulated,
  because we can never an Actor's mutable state unless we interact with it via sending a message
  and only that Actor SHOULD have access to its own internal state.
  but this is a delicate subject and can be easily mistreated!

  A) NEVER PASS MUTABLE STATE TO OTHER ACTORS. => because there will be a race condition for sure!!!
  - if you want to pass data to other Actors, that's fine. but make sure it is IMMUTABLE.
  - if you have to use a mutable data structure (or even using a var) in an Actor, make sure that data will never leave your Actor and stays only within your Actor

  B) NEVER PASS THE CONTEXT REFERENCE TO OTHER ACTORS. => because other Actors may influence your internal state and then you'll have no idea where that state change came from!!!
  points A, B are the same when you use Futures in Actors. the reason is that when we write some code on "onComplete" or even "map",
  that code will be run somewhere in the future and probably by a different thread.
  so even if the code belongs to the internal of the same actor, that code will actually run on another thread from outside of the Actor!
 */
object L6_BreakingActorEncapsulation {

  // naive bank account
  // we should always avoid such a structure!
  trait AccountCommand

  case class Deposit(cardId: String, amount: Double) extends AccountCommand

  case class Withdraw(cardId: String, amount: Double) extends AccountCommand

  case class CreateCreditCard(cardId: String) extends AccountCommand

  case object CheckCardStatuses extends AccountCommand

  trait CreditCardCommand

  case class AttachToAccount(balances: MutableMap[String, Double], cards: MutableMap[String, ActorRef[CreditCardCommand]]) extends CreditCardCommand

  case object CheckStatus extends CreditCardCommand

  object NaiveBankAccount {
    def apply(): Behavior[AccountCommand] = Behaviors.setup { context =>
      val accountBalances: MutableMap[String, Double] = MutableMap()
      val cardMap: MutableMap[String, ActorRef[CreditCardCommand]] = MutableMap()

      Behaviors.receiveMessage {
        case CreateCreditCard(cardId) =>
          context.log.info(s"Creating card $cardId")
          // create a CreditCard child
          val creditCardRef = context.spawn(CreditCard(cardId), cardId)
          // give a referral bonus
          accountBalances += cardId -> 10
          // send an AttachToAccount message to the child
          creditCardRef ! AttachToAccount(accountBalances, cardMap)
          // change behavior
          Behaviors.same
        case Deposit(cardId, amount) =>
          val oldBalance: Double = accountBalances.getOrElse(cardId, 0)
          context.log.info(s"Depositing $amount via card $cardId, balance on card: ${oldBalance + amount}")
          accountBalances += cardId -> (oldBalance + amount)
          Behaviors.same
        case Withdraw(cardId, amount) =>
          val oldBalance: Double = accountBalances.getOrElse(cardId, 0)
          if (oldBalance < amount) {
            context.log.warn(s"Attempted withdrawal of $amount via card $cardId: insufficient funds")
            Behaviors.same
          } else {
            context.log.info(s"Withdrawing $amount via card $cardId, balance on card: ${oldBalance - amount}")
            accountBalances += cardId -> (oldBalance - amount)
            Behaviors.same
          }
        case CheckCardStatuses =>
          context.log.info(s"Checking all card statuses")
          cardMap.values.foreach(cardRef => cardRef ! CheckStatus)
          Behaviors.same
      }
    }
  }

  object CreditCard {
    def apply(cardId: String): Behavior[CreditCardCommand] = Behaviors.receive { (context, message) =>
      message match {
        case AttachToAccount(balances, cards) =>
          context.log.info(s"[$cardId] Attaching to bank account")
          balances += cardId -> 0
          cards += cardId -> context.self
          Behaviors.same
        case CheckStatus =>
          context.log.info(s"[$cardId] All things green.")
          Behaviors.same
      }
    }
  }

  def main(args: Array[String]): Unit = {
    val userGuardian: Behavior[Unit] = Behaviors.setup { context =>
      val bankAccount = context.spawn(NaiveBankAccount(), "bankAccount")

      bankAccount ! CreateCreditCard("gold")
      bankAccount ! CreateCreditCard("premium")
      bankAccount ! Deposit("gold", 1000)
      bankAccount ! CheckCardStatuses

      Behaviors.empty
    }

    val system = ActorSystem(userGuardian, "DemoNaiveBankAccount")
    Thread.sleep(1000)
    system.terminate()
  }
}
