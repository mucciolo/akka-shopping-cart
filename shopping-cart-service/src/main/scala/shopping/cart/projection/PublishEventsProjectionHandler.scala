package shopping.cart.projection

import akka.Done
import akka.actor.typed.ActorSystem
import akka.kafka.scaladsl.SendProducer
import akka.projection.eventsourced.EventEnvelope
import akka.projection.scaladsl.Handler
import com.google.protobuf.any.{Any => ScalaPBAny}
import org.apache.kafka.clients.producer.ProducerRecord
import shopping.cart.core.ShoppingCart
import shopping.cart.proto
import shopping.cart.util.Log

import scala.concurrent.{ExecutionContext, Future}

class PublishEventsProjectionHandler(
  system      : ActorSystem[_],
  topic       : String,
  sendProducer: SendProducer[String, Array[Byte]]
) extends Handler[EventEnvelope[ShoppingCart.Event]] with Log {

  private implicit val ec: ExecutionContext = system.executionContext

  override def process(envelope: EventEnvelope[ShoppingCart.Event]): Future[Done] = {

    val event = envelope.event

    // using the cartId as the key and `DefaultPartitioner` will select partition based on the key
    // so that events for same cart always ends up in same partition
    val key = event.cartId
    val producerRecord = new ProducerRecord(topic, key, serialize(event))
    val result = sendProducer.send(producerRecord).map { recordMetadata =>
      log.info("Published event [{}] to topic/partition {}/{}", event, topic, recordMetadata.partition)
      Done
    }

    result
  }

  private def serialize(event: ShoppingCart.Event): Array[Byte] = {

    val protoMessage = event match {

      case ShoppingCart.ItemAdded(cartId, itemId, quantity) =>
        proto.ItemAdded(cartId, itemId, quantity)

      case ShoppingCart.CheckedOut(cartId, _) =>
        proto.CheckedOut(cartId)

      case ShoppingCart.ItemRemoved(cartId, itemId) =>
        proto.ItemRemoved(cartId, itemId)

      case ShoppingCart.ItemQuantityUpdated(cartId, itemId, _, updatedQuantity) =>
        proto.ItemQuantityAdjusted(cartId, itemId, updatedQuantity)

    }

    // pack in Any so that type information is included for deserialization
    ScalaPBAny.pack(protoMessage, "shopping-cart-service").toByteArray
  }
}