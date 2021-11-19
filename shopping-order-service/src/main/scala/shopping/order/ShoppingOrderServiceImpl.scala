package shopping.order

import scala.concurrent.Future

import org.slf4j.LoggerFactory
import shopping.order.proto.OrderRequest
import shopping.order.proto.OrderResponse

class ShoppingOrderServiceImpl extends proto.ShoppingOrderService {

  private val logger = LoggerFactory.getLogger(getClass)

  override def order(request: OrderRequest): Future[OrderResponse] = {
    val totalNumberOfItems = request.items.iterator.map(_.quantity).sum
    logger.info("OrderRequest: {} items from cart {}", totalNumberOfItems, request.cartId)
    Future.successful(OrderResponse(ok = true))
  }
}