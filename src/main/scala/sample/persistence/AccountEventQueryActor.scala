package sample.persistence

import akka.NotUsed
import akka.actor.Actor
import akka.persistence.cassandra.query.scaladsl.CassandraReadJournal
import akka.persistence.query.{EventEnvelope, PersistenceQuery}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Keep, RunnableGraph, Sink, Source}
import com.typesafe.scalalogging.Logger

import scala.concurrent.Future
import scala.util.{Failure, Success}

object AccountEventQueryActor {
  object RunQuery
}

class AccountEventQueryActor extends Actor {
  import AccountEventQueryActor._
  val logger = Logger("AccountEventQueryActor")

  val readJournal: CassandraReadJournal = PersistenceQuery(context.system).readJournalFor[CassandraReadJournal](CassandraReadJournal.Identifier)

  val source: Source[EventEnvelope, NotUsed] = readJournal.currentEventsByPersistenceId("account-actor", 0, Long.MaxValue)
  implicit val mat: ActorMaterializer = ActorMaterializer()

  import context.dispatcher
  override def receive: Receive = {
    case RunQuery =>
      runQuery() onComplete {
        case Success((totalDebited, totalCredited)) =>
          logger.info(s"Total amount debited: $totalDebited")
          logger.info(s"Total amount credited: $totalCredited")
        case Failure(exception) =>
          logger.info(s"Exception thrown: ${exception.getMessage}")
      }
  }

  private def runQuery(): Future[(Double, Double)] = {
    val sink = Sink.fold[(Double, Double), EventEnvelope]((0, 0)){ case ((sum_debited, sum_credited), eventEnvelope) => eventEnvelope.event match {
      case AccountDebited(amount) => (sum_debited + amount, sum_credited)
      case AccountCredited(amount) => (sum_debited, sum_credited + amount)
      case _ => (sum_debited, sum_credited)
    }}

    val runnable: RunnableGraph[Future[(Double, Double)]] = source.toMat(sink)(Keep.right)
    runnable.run
  }
}
