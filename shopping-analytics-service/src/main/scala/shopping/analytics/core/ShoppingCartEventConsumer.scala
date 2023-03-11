package shopping.analytics.core

import akka.Done
import akka.actor.typed.ActorSystem
import akka.kafka.{CommitterSettings, ConsumerSettings, Subscriptions}
import akka.kafka.scaladsl.{Committer, Consumer}
import akka.stream.RestartSettings
import akka.stream.scaladsl.RestartSource
import com.google.protobuf.CodedInputStream
import com.google.protobuf.any.{Any => ScalaPBAny}
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.serialization.{ByteArrayDeserializer, StringDeserializer}
import shopping.analytics.util.Log
import shopping.cart.proto

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.control.NonFatal

object ShoppingCartEventConsumer extends Log {

  def init(implicit system: ActorSystem[_]): Unit = {

    implicit val ec: ExecutionContext = system.executionContext

    val topic = system.settings.config.getString("shopping-analytics-service.shopping-cart-kafka-topic")
    val consumerSettings = ConsumerSettings(
      system,
      new StringDeserializer,
      new ByteArrayDeserializer
    ).withGroupId("shopping-cart-analytics")
    val committerSettings = CommitterSettings(system)

    RestartSource
      .onFailuresWithBackoff(
        RestartSettings(
          minBackoff = 1.second,
          maxBackoff = 30.seconds,
          randomFactor = 0.1)) {
        () =>
          Consumer
            .committableSource(
              consumerSettings,
              Subscriptions.topics(topic)
            )
            .mapAsync(1) { msg =>
              handleRecord(msg.record).map(_ => msg.committableOffset)
            }
            .via(Committer.flow(committerSettings))
      }
      .run()
  }

  private def handleRecord(record: ConsumerRecord[String, Array[Byte]]): Future[Done] = {

    val bytes: Array[Byte] = record.value()
    val x: ScalaPBAny = ScalaPBAny.parseFrom(bytes)
    val typeUrl: String = x.typeUrl

    try {
      val inputBytes: CodedInputStream = x.value.newCodedInput()
      val event = typeUrl match {

          case "shopping-cart-service/shoppingcart.ItemAdded" =>
            proto.ItemAdded.parseFrom(inputBytes)

          case "shopping-cart-service/shoppingcart.CheckedOut" =>
            proto.CheckedOut.parseFrom(inputBytes)

          case "shopping-cart-service/shoppingcart.ItemQuantityAdjusted" =>
            proto.ItemQuantityAdjusted.parseFrom(inputBytes)

          case _ =>
            throw new IllegalArgumentException(s"Unknown record type [$typeUrl]")
        }

      event match {
        case proto.ItemAdded(cartId, itemId, quantity, _) =>
          log.info("ItemAdded: {} {} to cart {}", quantity, itemId, cartId)

        case proto.CheckedOut(cartId, _) =>
          log.info("CheckedOut: cart {} checked out", cartId)

        case proto.ItemQuantityAdjusted(cartId, itemId, quantity, _) =>
          log.info("ItemQuantityAdjusted: {} {} at cart", quantity, itemId, cartId)
      }

      Future.successful(Done)

    } catch {
      case NonFatal(e) =>
        log.error("Could not process event of type [{}]", typeUrl, e)
        Future.successful(Done)
    }
  }
}