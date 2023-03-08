package shopping.cart.core

import akka.actor.typed.{ActorSystem, DispatcherSelector}
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.grpc.GrpcServiceException
import akka.util.Timeout
import io.grpc.Status
import shopping.cart.proto
import shopping.cart.proto.{Cart, UpdateItemQuantityRequest}
import shopping.cart.repository.{ItemPopularityRepository, ScalikeJdbcSession}
import shopping.cart.util.Log

import java.util.concurrent.TimeoutException
import scala.concurrent.{ExecutionContext, Future}

class ShoppingCartServiceImpl(
  system                  : ActorSystem[_],
  itemPopularityRepository: ItemPopularityRepository) extends proto.ShoppingCartService with Log {

  import system.executionContext

  implicit private val timeout: Timeout =
    Timeout.create(system.settings.config.getDuration("shopping-cart-service.ask-timeout"))

  private val sharding = ClusterSharding(system)

  override def addItem(request: proto.AddItemRequest): Future[proto.Cart] = {

    log.info("AddItemRequest: {} '{}' to cart '{}'", request.quantity, request.itemId, request.cartId)

    val entityRef = sharding.entityRefFor(ShoppingCart.EntityKey, request.cartId)
    val reply: Future[ShoppingCart.Summary] =
      entityRef.askWithStatus(ShoppingCart.AddItem(request.itemId, request.quantity, _))
    val response = reply.map(summary => toProtoCart(summary))

    wrapWithRecoveryStrategy(response)
  }

  override def checkout(request: proto.CheckoutRequest): Future[proto.Cart] = {

    log.info("CheckoutRequest: {}", request.cartId)

    val entityRef = sharding.entityRefFor(ShoppingCart.EntityKey, request.cartId)
    val reply: Future[ShoppingCart.Summary] =
      entityRef.askWithStatus(ShoppingCart.Checkout)
    val response = reply.map(summary => toProtoCart(summary))

    wrapWithRecoveryStrategy(response)
  }

  override def getCart(request: proto.GetCartRequest): Future[proto.Cart] = {

    log.info("GetCartRequest: {}", request.cartId)

    val entityRef = sharding.entityRefFor(ShoppingCart.EntityKey, request.cartId)
    val response =
      entityRef.ask(ShoppingCart.Get).map { summary =>
        if (summary.items.isEmpty)
          throw new GrpcServiceException(
            Status.NOT_FOUND.withDescription(s"Cart '${request.cartId}' is empty"))
        else
          toProtoCart(summary)
      }

    wrapWithRecoveryStrategy(response)
  }

  override def removeItem(request: proto.RemoveItemRequest): Future[proto.Cart] = {

    log.info("RemoveItemRequest: item '{}' from cart '{}'", request.itemId, request.cartId)

    val entityRef = sharding.entityRefFor(ShoppingCart.EntityKey, request.cartId)
    val reply: Future[ShoppingCart.Summary] =
      entityRef.askWithStatus(ShoppingCart.RemoveItem(request.itemId, _))
    val response = reply.map(summary => toProtoCart(summary))

    wrapWithRecoveryStrategy(response)
  }


  override def updateItemQuantity(request: UpdateItemQuantityRequest): Future[Cart] = {

    log.info("UpdateItemQuantityRequest: '{}' quantity to {} at cart '{}'",
      request.itemId, request.quantity, request.cartId)

    val entityRef = sharding.entityRefFor(ShoppingCart.EntityKey, request.cartId)
    val reply: Future[ShoppingCart.Summary] =
      entityRef.askWithStatus(ShoppingCart.UpdateItemQuantity(request.itemId, request.quantity, _))
    val response = reply.map(summary => toProtoCart(summary))

    wrapWithRecoveryStrategy(response)
  }

  private def toProtoCart(summary: ShoppingCart.Summary): proto.Cart = {
    proto.Cart(
      summary.items.iterator.map {
        case (itemId, quantity) => proto.Item(itemId, quantity)
      }.toSeq,
      summary.checkedOut
    )
  }

  private def wrapWithRecoveryStrategy[T](response: Future[T]): Future[T] = {
    response.recoverWith {
      case _: TimeoutException =>
        Future.failed(new GrpcServiceException(Status.UNAVAILABLE.withDescription("Operation timed out")))

      case ex =>
        Future.failed(new GrpcServiceException(Status.INVALID_ARGUMENT.withDescription(ex.getMessage)))
    }
  }

  private val blockingJdbcExecutor: ExecutionContext =
    system.dispatchers.lookup(DispatcherSelector.fromConfig("akka.projection.jdbc.blocking-jdbc-dispatcher"))

  override def getItemPopularity(in: proto.GetItemPopularityRequest): Future[proto.GetItemPopularityResponse] = {
    Future {
      ScalikeJdbcSession.withSession(itemPopularityRepository.getItem(_, in.itemId))
    }(blockingJdbcExecutor).map {
      case Some(count) =>
        proto.GetItemPopularityResponse(in.itemId, count)
      case None =>
        proto.GetItemPopularityResponse(in.itemId)
    }
  }
}