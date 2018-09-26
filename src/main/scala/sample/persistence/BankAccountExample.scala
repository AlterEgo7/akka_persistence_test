package sample.persistence

import java.nio.charset.StandardCharsets

import akka.actor.{ActorSystem, Props}
import akka.persistence.{PersistentActor, SnapshotOffer}
import akka.serialization.SerializerWithStringManifest
import com.typesafe.scalalogging.Logger
import models._

sealed trait AccountCommand {
  def amount: Double
}

case class DebitAccount(amount: Double) extends AccountCommand

case class CreditAccount(amount: Double) extends AccountCommand


trait AccountEvent {
  def amount: Double
}

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

class AccountPersistentActor extends PersistentActor {

  var state = State()
  val logger = Logger(persistenceId)

  def updateState(event: AccountEvent): Unit =
    state = state.updated(event)

  override def receiveRecover: Receive = {
    case event: AccountEvent               =>
      updateState(event)
      logger.info(s"Actor $persistenceId: Balance recovered = ${state.balance.right.get}")
    case SnapshotOffer(_, snapshot: State) => state = snapshot
  }

  override def receiveCommand: Receive = {
    case DebitAccount(amount) => persist(AccountDebited(amount)) { event =>
      updateState(event)
      state.balance match {
        case Left(_)  => logger.error(s"Insufficient funds")
        case Right(value) => logger.info(s"Balance = $value")
      }
      context.system.eventStream.publish(event)
    }
    case CreditAccount(amount) => persist(AccountCredited(amount)) { event =>
      updateState(event)
      state.balance match {
        case Left(_)  => logger.error(s"Insufficient funds")
        case Right(value) => logger.info(s"Balance = $value")
      }
      context.system.eventStream.publish(event)
    }
  }

  override def persistenceId: String = {
    val hostName = java.net.InetAddress.getLocalHost.getHostName
    s"$hostName - ${self.path.name}"
  }
}

object BankAccountExample extends App {

  val system = ActorSystem("account_example")

  val actors = (for (
    i <- Range(1, 20)
  ) yield system.actorOf(Props[AccountPersistentActor], s"account-actor-$i")).par

  val orders = Stream(
    DebitAccount(100),
    DebitAccount(25),
    CreditAccount(115),
    DebitAccount(50),
    CreditAccount(60)
  )

  for {
    actor <- actors
    order <- orders
  } yield actor ! order

  Thread.sleep(5000)

  system.terminate()
}

class AccountEventStringSerializer extends SerializerWithStringManifest {
  val AccountDebitedManifest: String = classOf[AccountDebited].getName
  val AccountCreditedManifest: String = classOf[AccountCredited].getName
  val UTF_8: String = StandardCharsets.UTF_8.name()

  override def identifier: Int = 77

  override def manifest(o: AnyRef): String = o.getClass.getName

  override def toBinary(o: AnyRef): Array[Byte] = o match {
    case AccountDebited(amount) => s"Account debited by $amount".getBytes(UTF_8)
    case AccountCredited(amount) => s"Account credited by $amount".getBytes(UTF_8)
    case _ => throw new IllegalArgumentException("WTF")
  }

  override def fromBinary(bytes: Array[Byte], manifest: String): AnyRef = manifest match {
    case AccountDebitedManifest =>
      val amount: Double = """Account debited by (\s+)""".r.findFirstIn(bytes.mkString).get.toDouble
      AccountDebited(amount)
    case AccountCreditedManifest =>
      val amount: Double = """Account credited by (\s+)""".r.findFirstIn(bytes.mkString).get.toDouble
      AccountCredited(amount)
  }
}