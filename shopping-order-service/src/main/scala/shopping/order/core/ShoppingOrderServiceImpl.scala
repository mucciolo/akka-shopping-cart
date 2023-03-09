package shopping.order.core

import shopping.order.proto.{OrderRequest, OrderResponse}
import shopping.order._
import shopping.order.util.Log

import scala.concurrent.Future

class ShoppingOrderServiceImpl extends proto.ShoppingOrderService with Log {

  override def order(request: OrderRequest): Future[OrderResponse] = {
    val totalNumberOfItems = request.items.iterator.map(_.quantity).sum
    log.info("OrderRequest: {} items from cart {}", totalNumberOfItems, request.cartId)
    Future.successful(OrderResponse(ok = true))
  }
}