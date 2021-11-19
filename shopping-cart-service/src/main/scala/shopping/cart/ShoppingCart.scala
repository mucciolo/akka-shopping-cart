package shopping.cart

import akka.actor.typed.{ActorRef, ActorSystem, Behavior, SupervisorStrategy}
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityContext, EntityTypeKey}
import akka.pattern.StatusReply
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, ReplyEffect, RetentionCriteria}

import java.time.Instant
import scala.concurrent.duration.DurationInt

object ShoppingCart {

  /**
   * This interface defines all the commands (messages) that the ShoppingCart actor supports.
   */
  sealed trait Command extends CborSerializable

  /**
   * A command to add an item to the cart.
   *
   * It replies with `StatusReply[Summary]`, which is sent back to the caller when
   * all the events emitted by this command are successfully persisted.
   */
  final case class AddItem(itemId  : String,
                           quantity: Int,
                           replyTo : ActorRef[StatusReply[Summary]]) extends Command

  /**
   * A command to checkout all the current items in the cart.
   *
   * It replies with `StatusReply[Summary]`, which is sent back to the caller when
   * all the events emitted by this command are successfully persisted.
   */
  final case class Checkout(replyTo: ActorRef[StatusReply[Summary]]) extends Command

  /**
   * A command to get the current items in the cart.
   *
   * It replies with just `Summary`, since no events are persisted in this command.
   */
  final case class Get(replyTo: ActorRef[Summary]) extends Command

  final case class RemoveItem(itemId: String, replyTo: ActorRef[StatusReply[Summary]]) extends Command

  final case class UpdateItemQuantity(itemId  : String,
                                      quantity: Int,
                                      replyTo : ActorRef[StatusReply[Summary]]) extends Command

  /**
   * This interface defines all the events that the ShoppingCart supports.
   */
  sealed trait Event extends CborSerializable {
    def cartId: String
  }

  final case class ItemAdded(cartId: String, itemId: String, quantity: Int) extends Event

  final case class CheckedOut(cartId: String, eventTime: Instant) extends Event

  final case class ItemRemoved(cartId: String, itemId: String) extends Event

  final case class ItemQuantityUpdated(cartId: String, itemId: String, quantity: Int) extends Event

  final case class State(items: Map[String, Int], checkoutDate: Option[Instant]) extends CborSerializable {

    def isCheckedOut: Boolean = checkoutDate.isDefined

    def checkout(now: Instant): State = copy(checkoutDate = Some(now))

    def toSummary: Summary = Summary(items, isCheckedOut)

    def hasItem(itemId: String): Boolean = items.contains(itemId)

    def isEmpty: Boolean = items.isEmpty

    def updateItem(itemId: String, quantity: Int): State = quantity match {
      case 0 => copy(items = items - itemId)
      case _ => copy(items = items + (itemId -> quantity))
    }
  }

  object State {
    val empty: State = State(items = Map.empty, checkoutDate = None)
  }

  final case class Summary(items: Map[String, Int], checkedOut: Boolean) extends CborSerializable

  private def handleCommand(cartId : String,
                            state  : State,
                            command: Command): ReplyEffect[Event, State] = {

    if (state.isCheckedOut)
      checkedOutShoppingCart(state, command)
    else
      openShoppingCart(cartId, state, command)
  }

  private def checkedOutShoppingCart(state  : State,
                                     command: Command): ReplyEffect[Event, State] = command match {
    case cmd: AddItem =>
      Effect.reply(cmd.replyTo)(StatusReply.Error("Can't add an item to an already checked out shopping cart"))

    case cmd: Checkout =>
      Effect.reply(cmd.replyTo)(StatusReply.Error("Can't checkout already checked out shopping cart"))

    case Get(replyTo) =>
      Effect.reply(replyTo)(state.toSummary)

    case cmd: RemoveItem =>
      Effect.reply(cmd.replyTo)(StatusReply.Error("Can't remove an item of an already checked out shopping cart"))

    case cmd: UpdateItemQuantity =>
      Effect.reply(cmd.replyTo)(
        StatusReply.Error("Can't update an item quantity of an already checked out shopping cart")
      )
  }

  private def openShoppingCart(cartId : String,
                               state  : State,
                               command: Command): ReplyEffect[Event, State] = command match {

    case AddItem(itemId, quantity, replyTo) =>
      if (state.hasItem(itemId))
        Effect.reply(replyTo)(StatusReply.Error(s"Item '$itemId' was already added to this shopping cart"))
      else if (quantity <= 0)
        Effect.reply(replyTo)(StatusReply.Error("Quantity must be greater than zero"))
      else
        Effect.persist(ItemAdded(cartId, itemId, quantity))
          .thenReply(replyTo)(updatedCart => StatusReply.Success(updatedCart.toSummary))

    case Checkout(replyTo) =>
      if (state.isEmpty)
        Effect.reply(replyTo)(StatusReply.Error("Cannot checkout an empty shopping cart"))
      else
        Effect.persist(CheckedOut(cartId, Instant.now()))
          .thenReply(replyTo)(updatedCart => StatusReply.Success(updatedCart.toSummary))

    case Get(replyTo) =>
      Effect.reply(replyTo)(state.toSummary)

    case RemoveItem(itemId, replyTo) =>
      if (state.hasItem(itemId))
        Effect.persist(ItemRemoved(cartId, itemId))
          .thenReply(replyTo)(updatedCart => StatusReply.Success(updatedCart.toSummary))
      else
        Effect.reply(replyTo)(StatusReply.Error(s"Item '$itemId' not found in this shopping cart"))

    case UpdateItemQuantity(itemId, quantity, replyTo) =>
      if (state.hasItem(itemId)) {
        if (quantity <= 0)
          Effect.reply(replyTo)(StatusReply.Error("Quantity must be greater than zero"))
        else
          Effect.persist(ItemQuantityUpdated(cartId, itemId, quantity))
            .thenReply(replyTo)(updatedCart => StatusReply.Success(updatedCart.toSummary))
      } else {
        Effect.reply(replyTo)(StatusReply.Error(s"Item '$itemId' not found in this shopping cart"))
      }
  }

  private def handleEvent(state: State, event: Event): State = event match {

    case ItemAdded(_, itemId, quantity) =>
      state.updateItem(itemId, quantity)

    case CheckedOut(_, eventTime) =>
      state.checkout(eventTime)

    case ItemRemoved(_, itemId) =>
      state.updateItem(itemId, 0)

    case ItemQuantityUpdated(_, itemId, quantity) =>
      state.updateItem(itemId, quantity)
  }

  val EntityKey: EntityTypeKey[Command] = EntityTypeKey[Command]("ShoppingCart")
  val tags = Vector.tabulate(5)(i => s"carts-$i")

  def init(system: ActorSystem[_]): Unit = {

    val behaviorFactory: EntityContext[Command] => Behavior[Command] = {
      entityContext =>
        val i = math.abs(entityContext.entityId.hashCode % tags.size)
        val selectedTag = tags(i)
        ShoppingCart(entityContext.entityId, selectedTag)
    }

    ClusterSharding(system).init(Entity(EntityKey)(behaviorFactory))
  }

  def apply(cartId: String, projectionTag: String): Behavior[Command] =
    EventSourcedBehavior
      .withEnforcedReplies[Command, Event, State](
        persistenceId = PersistenceId(EntityKey.name, cartId),
        emptyState = State.empty,
        commandHandler = (state, command) => handleCommand(cartId, state, command),
        eventHandler = (state, event) => handleEvent(state, event))
      .withTagger(_ => Set(projectionTag))
      .withRetention(RetentionCriteria.snapshotEvery(numberOfEvents = 100, keepNSnapshots = 3))
      .onPersistFailure(SupervisorStrategy.restartWithBackoff(200.millis, 5.seconds, 0.1))
}
