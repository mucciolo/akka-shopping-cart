package shopping.analytics

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.management.scaladsl.AkkaManagement
import shopping.analytics.util.Log

import scala.util.control.NonFatal

object Main extends Log {

  def main(args: Array[String]): Unit = {

    val system = ActorSystem[Nothing](Behaviors.empty, "ShoppingAnalyticsService")

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

    ShoppingCartEventConsumer.init(system)
  }
}