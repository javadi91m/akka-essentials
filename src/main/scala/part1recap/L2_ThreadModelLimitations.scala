package part1recap

import java.util.concurrent.Executors
import scala.concurrent.{ExecutionContext, Future}

object L2_ThreadModelLimitations {

  // There are some problems with the traditional threading model

  // PROBLEM #1: OO encapsulation is only valid in the SINGLE-THREADED MODEL => in usual OO mental model, we have mutable object with mutable variables
  // those variables can be accessed through getters/setters.
  // in a single-threaded application, it works fine, but obviously not in a multi-threaded application

  class BankAccount(private var amount: Int) {
    override def toString = s"$amount"

    def withdraw(money: Int) = /*synchronized*/ {
      this.amount -= money
    }

    def deposit(money: Int) = /*synchronized*/ {
      this.amount += money
    }

    def getAmount = amount
  }

  val account = new BankAccount(2000)
  val depositThreads = (1 to 1000).map(_ => new Thread(() => account.deposit(1)))
  val withdrawThreads = (1 to 1000).map(_ => new Thread(() => account.withdraw(1)))

  def demoRace(): Unit = {
    (depositThreads ++ withdrawThreads).foreach(_.start())
    println(account.getAmount)
    // now if we execute demoRace() method, we expect the result remain "2000"
    // but the problem is by the time of printing the result, all those threads might not be finished and therefore the result might be incorrect.
    // one naive way is to wait a little before printing the result to make sue all the threads are finished.
    //    Thread.sleep(1000)
    // but we may encounter another problem: race condition
    // there are lots of threads trying to access the same resource (here a variable) at the same time and change its value
    // so we have a race condition and we might end up having a wrong outcome
    // one way to overcome this problem is to make withdraw/deposit methods synchronized
    // using synchronization may produce other problems like deadlocks and livelocks
        // A deadlock is a situation that occurs when processes block each other with resource acquisition and makes no further progress. Livelock is a deadlock-like situation in which processes block each other with a repeated state change yet make no progress.
        // Livelock occurs when two or more processes continually repeat the same interaction in response to changes in the other processes without doing any useful work. These processes are not in the waiting state, and they are running concurrently.
  }

  /*
    - we don't know when the threads are finished
    - race conditions

    solution: synchronization
    other problems:
      - deadlocks
      - livelocks
   */

  // PROBLEM #2 - delegating a task to a thread
  // let's assume we have a thread and some potential tasks. as soon as a task is created, we want to delegate this task to the thread (if it's free)
  // here is a sample implementation which is complicated, fragile and not easy to debug/maintains!

  var task: Runnable = null

  val runningThread: Thread = new Thread(() => {
    while (true) {
      while (task == null) {
        runningThread.synchronized {
          println("[background] waiting for a task")
          runningThread.wait()
        }
      }

      task.synchronized {
        println("[background] I have a task!")
        task.run()
        task = null
      }
    }
  })

  def delegateToBackgroundThread(r: Runnable) = {
    if (task == null) {
      task = r
      runningThread.synchronized {
        runningThread.notify()
      }
    }
  }

  def demoBackgroundDelegation(): Unit = {
    runningThread.start()
    Thread.sleep(1000)
    delegateToBackgroundThread(() => println("I'm running from another thread"))
    Thread.sleep(1000)
    delegateToBackgroundThread(() => println("This should run in the background again"))
  }


  // PROBLEM #3: tracing and dealing with errors is really painful in multithreaded/distributed apps
  // assume we want to sum 1M numbers in between 10 threads

  implicit val ec: ExecutionContext = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(8))

  val futures = (0 to 9)
    .map(i => BigInt(100000 * i) until BigInt(100000 * (i + 1))) // 0 - 99999, 100000 - 199999, and so on
    .map(range => Future {
      // bug => debugging such an error is really hard, especially if futures come from different parts of the application
      if (range.contains(BigInt(546732))) throw new RuntimeException("invalid number")
      range.sum
    })

  val sumFuture = Future.reduceLeft(futures)(_ + _)

  def main(args: Array[String]): Unit = {
    sumFuture.onComplete(println)
  }

}
