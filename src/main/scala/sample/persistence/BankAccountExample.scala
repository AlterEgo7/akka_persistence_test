package sample.persistence

import akka.actor.{ActorSystem, Props}

import scala.util.Random

sealed trait AccountCommand {
  def amount: Double
}

case class DebitAccount(amount: Double) extends AccountCommand

case class CreditAccount(amount: Double) extends AccountCommand


case class State(events: Seq[AccountEvent] = Stream()) {
  def updated(event: AccountEvent): State = copy(event +: events)

  def balance: Either[String, Double] = events.foldRight[Either[String, Double]](Right(0.0))((event, previousBalance) => previousBalance match {
    case Right(previousBalanceAmount) => event match {
      case AccountDebited(amount)  => Right(previousBalanceAmount + amount)
      case AccountCredited(amount) =>
        val newAmount = previousBalanceAmount - amount
        if (newAmount < 0) {
          Left("Insufficient funds")
        } else {
          Right(newAmount)
        }
    }
    case error                        => error
  })
}

object BankAccountExample extends App {

  val system = ActorSystem("account_example")

  val listener = system.actorOf(Props[AccountEventActor])
  system.eventStream.subscribe(listener, classOf[AccountEvent])

  val queryActor = system.actorOf(Props[AccountEventQueryActor])

  val actors = (for (
    i <- 1 to 20
  ) yield system.actorOf(Props[AccountPersistentActor], s"account-actor-$i")).par

  val orders = Stream(
    DebitAccount(100),
    DebitAccount(25),
    CreditAccount(115),
    DebitAccount(50),
    CreditAccount(Random.nextInt(6000) / 100)
  )

  for {
    actor <- actors
    order <- orders
  } yield actor ! order

  Thread.sleep(2500)

  queryActor ! AccountEventQueryActor.RunQuery

  Thread.sleep(1000)

  system.terminate()
}
