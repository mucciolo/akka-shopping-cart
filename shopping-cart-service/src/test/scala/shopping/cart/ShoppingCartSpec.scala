package shopping.cart

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.pattern.StatusReply
import akka.persistence.testkit.scaladsl.EventSourcedBehaviorTestKit
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.wordspec.AnyWordSpecLike

object ShoppingCartSpec {
  val config: Config = ConfigFactory
    .parseString(
      """
      akka.actor.serialization-bindings {
        "shopping.cart.CborSerializable" = jackson-cbor
      }
      """)
    .withFallback(EventSourcedBehaviorTestKit.config)
}

class ShoppingCartSpec
  extends ScalaTestWithActorTestKit(ShoppingCartSpec.config)
  with AnyWordSpecLike
  with BeforeAndAfterEach {

  private val cartId = "test-id"
  private val projectionTag = "test-tag"
  private val eventSourcedTestKit =
    EventSourcedBehaviorTestKit[ShoppingCart.Command, ShoppingCart.Event, ShoppingCart.State](
      system, ShoppingCart(cartId, projectionTag))

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    eventSourcedTestKit.clear()
  }

  "The Shopping Cart" should {

    "add item" in {
      val result1 =
        eventSourcedTestKit.runCommand[StatusReply[ShoppingCart.Summary]](ShoppingCart.AddItem("foo", 42, _))
      result1.reply should ===(StatusReply.Success(ShoppingCart.Summary(Map("foo" -> 42), checkedOut = false)))
      result1.event should ===(ShoppingCart.ItemAdded(cartId, "foo", 42))
    }

    "reject already added item" in {
      val result1 =
        eventSourcedTestKit.runCommand[StatusReply[ShoppingCart.Summary]](ShoppingCart.AddItem("foo", 42, _))
      result1.reply.isSuccess should ===(true)

      val result2 =
        eventSourcedTestKit.runCommand[StatusReply[ShoppingCart.Summary]](ShoppingCart.AddItem("foo", 13, _))
      result2.reply.isError should ===(true)
    }

    "checkout" in {
      val result1 =
        eventSourcedTestKit.runCommand[StatusReply[ShoppingCart.Summary]](ShoppingCart.AddItem("foo", 42, _))
      result1.reply.isSuccess should ===(true)

      val result2 = eventSourcedTestKit.runCommand[StatusReply[ShoppingCart.Summary]](ShoppingCart.Checkout)
      result2.reply should ===(StatusReply.Success(ShoppingCart.Summary(Map("foo" -> 42), checkedOut = true)))
      result2.event.asInstanceOf[ShoppingCart.CheckedOut].cartId should ===(cartId)

      val result3 =
        eventSourcedTestKit.runCommand[StatusReply[ShoppingCart.Summary]](ShoppingCart.AddItem("bar", 13, _))
      result3.reply.isError should ===(true)

      val result4 =
        eventSourcedTestKit.runCommand[StatusReply[ShoppingCart.Summary]](ShoppingCart.RemoveItem("bar", _))
      result4.reply.isError should ===(true)

      val result5 =
        eventSourcedTestKit.runCommand[StatusReply[ShoppingCart.Summary]](ShoppingCart.UpdateItemQuantity("foo", 10, _))
      result5.reply.isError should ===(true)
    }

    "open cart get" in {
      val result1 =
        eventSourcedTestKit.runCommand[StatusReply[ShoppingCart.Summary]](ShoppingCart.AddItem("foo", 42, _))
      result1.reply.isSuccess should ===(true)

      val result2 = eventSourcedTestKit.runCommand[ShoppingCart.Summary](ShoppingCart.Get)
      result2.reply should ===(ShoppingCart.Summary(Map("foo" -> 42), checkedOut = false))
    }

    "checked out cart get" in {
      val result1 =
        eventSourcedTestKit.runCommand[StatusReply[ShoppingCart.Summary]](ShoppingCart.AddItem("foo", 42, _))
      result1.reply.isSuccess should ===(true)

      val result2 =
        eventSourcedTestKit.runCommand[StatusReply[ShoppingCart.Summary]](ShoppingCart.Checkout)
      result2.reply should ===(StatusReply.Success(ShoppingCart.Summary(Map("foo" -> 42), checkedOut = true)))
      result2.event.asInstanceOf[ShoppingCart.CheckedOut].cartId should ===(cartId)

      val result3 = eventSourcedTestKit.runCommand[ShoppingCart.Summary](ShoppingCart.Get)
      result3.reply should ===(ShoppingCart.Summary(Map("foo" -> 42), checkedOut = true))
    }

    "remove item" in {
      eventSourcedTestKit.runCommand[StatusReply[ShoppingCart.Summary]](ShoppingCart.AddItem("foo", 42, _))

      val result2 =
        eventSourcedTestKit.runCommand[StatusReply[ShoppingCart.Summary]](ShoppingCart.RemoveItem("foo", _))
      result2.reply should ===(StatusReply.Success(ShoppingCart.Summary(Map(), checkedOut = false)))
      result2.event should ===(ShoppingCart.ItemRemoved(cartId, "foo"))
    }

    "update item quantity" in {
      eventSourcedTestKit.runCommand[StatusReply[ShoppingCart.Summary]](ShoppingCart.AddItem("foo", 42, _))

      val result2 =
        eventSourcedTestKit.runCommand[StatusReply[ShoppingCart.Summary]](ShoppingCart.UpdateItemQuantity("foo", 10, _))
      result2.reply should ===(StatusReply.Success(ShoppingCart.Summary(Map("foo" -> 10), checkedOut = false)))
      result2.event should ===(ShoppingCart.ItemQuantityUpdated(cartId, "foo", 10))

      val result3 =
        eventSourcedTestKit.runCommand[StatusReply[ShoppingCart.Summary]](ShoppingCart.UpdateItemQuantity("foo", -1, _))
      result3.reply.isError should ===(true)


      val result4 =
        eventSourcedTestKit.runCommand[StatusReply[ShoppingCart.Summary]](ShoppingCart.UpdateItemQuantity("bar", 1, _))
      result4.reply.isError should ===(true)
    }
  }
}