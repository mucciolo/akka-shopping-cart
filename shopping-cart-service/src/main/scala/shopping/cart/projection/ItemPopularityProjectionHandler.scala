package shopping.cart.projection

import akka.projection.eventsourced.EventEnvelope
import akka.projection.jdbc.scaladsl.JdbcHandler
import shopping.cart.core.ShoppingCart
import shopping.cart.repository.{ItemPopularityRepository, ScalikeJdbcSession}
import shopping.cart.util.Log

class ItemPopularityProjectionHandler(
  tag: String, repo: ItemPopularityRepository
) extends JdbcHandler[EventEnvelope[ShoppingCart.Event], ScalikeJdbcSession] with Log {

  override def process(
    session: ScalikeJdbcSession, envelope: EventEnvelope[ShoppingCart.Event]
  ): Unit = {
    envelope.event match {

      case ShoppingCart.ItemAdded(_, itemId, quantity) =>
        repo.update(session, itemId, quantity)
        logItemCount(session, itemId)

      case ShoppingCart.ItemQuantityUpdated(_, itemId, currentQuantity, updatedQuantity) =>
        val delta = updatedQuantity - currentQuantity
        repo.update(session, itemId, delta)
        logItemCount(session, itemId)

      case _ =>

    }
  }

  private def logItemCount(session: ScalikeJdbcSession, itemId: String): Unit = {
    log.info("ItemPopularityProjectionHandler({}) item popularity for '{}': [{}]",
      tag, itemId, repo.getItem(session, itemId).getOrElse(0))
  }

}