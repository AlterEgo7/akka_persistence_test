package sample.persistence

import akka.persistence.{PersistentActor, SnapshotOffer}
import com.typesafe.scalalogging.Logger

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
