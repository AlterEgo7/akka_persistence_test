package sample.persistence

import akka.actor.Actor
import com.typesafe.scalalogging.Logger

class AccountEventActor extends Actor {
  val logger = Logger("Event listener actor")

  override def receive: Receive = {
    case AccountDebited(amount) => logger.info(s"Picked from event bus: Account Debited for $amount")
    case AccountCredited(amount) => logger.info(s"Picked from event bus: Account Credited for $amount")
  }
}
