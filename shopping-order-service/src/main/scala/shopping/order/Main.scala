package shopping.order

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.management.scaladsl.AkkaManagement
import shopping.order.core.{ShoppingOrderServer, ShoppingOrderServiceImpl}
import shopping.order.util.Log

import scala.util.control.NonFatal

object Main extends Log {

  def main(args: Array[String]): Unit = {

    val system = ActorSystem[Nothing](Behaviors.empty, "ShoppingOrderService")

    try {
      init(system)
    } catch {
      case NonFatal(e) =>
        log.error("Terminating due to initialization failure.", e)
        system.terminate()
    }
  }

  private def init(system: ActorSystem[_]): Unit = {

    AkkaManagement(system).start()
    ClusterBootstrap(system).start()

    val grpcInterface = system.settings.config.getString("shopping-order-service.grpc.interface")
    val grpcPort = system.settings.config.getInt("shopping-order-service.grpc.port")
    val shoppingOrderService = new ShoppingOrderServiceImpl
    ShoppingOrderServer.start(grpcInterface, grpcPort, system, shoppingOrderService)
  }
}