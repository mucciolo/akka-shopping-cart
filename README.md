# Scala Shopping Cart
The project consist of three microservices, namely,
- shopping-cart-service where cart items are managed
- shopping-order-service where card is checked out
- shopping-analytics-service where cart item's popularity are tracked

## Architecture overview
![Architecture Overview](/home/mucciolo/dev/scala/scala-shopping-cart/img/architecture-overview.svg)

Each user’s cart is represented by a Cart Entity. Cart state is persisted using Event Sourcing:
When a user updates their cart, the Entity persists events in an Event Journal database.
Using Command Query Responsibility Segregation (CQRS), which separates read and write responsibility,
Akka Projections new tab provide the data necessary for the Order and Analytics services.

The PopularityProjection uses the events from all shopping carts to store a representation in the database to answer
queries of how popular the items are.

The following Akka framework tools are used

- Akka Cluster Sharding to distribute Event Sourced cart entities and Projections
- Akka Event Sourcing to store the state of the shopping carts
- Akka Projections to enable CQRS and event based communication with other services
- Akka gRPC to implement a gRPC service API and communication with other services
- Akka Management for Akka Cluster formation and health checks

Developed following [Lightbend's Microservices Tutorial](https://developer.lightbend.com/docs/akka-platform-guide/microservices-tutorial/index.html).

## Running locally

Create and start the Docker containers located at shopping-cart-service project's folder
```shell
docker-compose -f shopping-cart-service/docker-compose.yml up -d
```

Execute PostgreSQL DDL script
```shell
docker exec -i shopping-cart-service_postgres-db_1 psql -U shopping-cart -t < shopping-cart-service/ddl-scripts/postgres_create_tables.sql
```

Three configuration files named `local1.conf`, `local2.conf`, and `local3.conf` are provided in each one of the
projects to run multiple nodes locally. The local configuration files set the following ports for each of the services:

#### Table 1. shopping-cart-service ports

| Node        | Akka Cluster | Akka Management HTTP | Akka gRPC |
|-------------|--------------|----------------------|-----------|
| local1.conf | 2551         | 9101                 | 8101      |
| local2.conf | 2552         | 9102                 | 8102      |
| local3.conf | 2553         | 9103                 | 8103      |

##### Table 2. shopping-order-service ports

| Node        | Akka Cluster | Akka Management HTTP | Akka gRPC |
|-------------|--------------|----------------------|-----------|
| local1.conf | 4551         | 9301                 | 8301      |
| local2.conf | 4552         | 9302                 | 8302      |
| local3.conf | 4553         | 9303                 | 8303      |

##### Table 3. shopping-analytics-service ports

| Node        | Akka Cluster | Akka Management HTTP | Akka gRPC |
|-------------|--------------|----------------------|-----------|
| local1.conf | 3551         | 9201                 | -         |
| local2.conf | 3552         | 9202                 | -         |
| local3.conf | 3553         | 9203                 | -         |

Start each one of the services
```shell
(cd shopping-cart-service && sbt -Dconfig.resource=local1.conf run)
(cd shopping-order-service && sbt -Dconfig.resource=local1.conf run)
(cd shopping-analytics-service && sbt -Dconfig.resource=local1.conf run)
```

Optionally check for service's readiness
```shell
curl http://localhost:9101/ready
curl http://localhost:9201/ready
curl http://localhost:9301/ready
```

### Interaction examples via `gcpcurl`

With shopping-cart-service
```shell
grpcurl -d '{"cartId": "1", "itemId": "pencil", "quantity": 1}' -plaintext 127.0.0.1:8101 shoppingcart.ShoppingCartService.AddItem
grpcurl -d '{"cartId": "1", "itemId": "pencil", "quantity": 2}' -plaintext 127.0.0.1:8101 shoppingcart.ShoppingCartService.UpdateItemQuantity
grpcurl -d '{"cartId": "1", "itemId": "pencil"}' -plaintext 127.0.0.1:8101 shoppingcart.ShoppingCartService.RemoveItem
grpcurl -d '{"cartId": "1"}' -plaintext 127.0.0.1:8101 shoppingcart.ShoppingCartService.GetCart
grpcurl -d '{"cartId": "1"}' -plaintext 127.0.0.1:8101 shoppingcart.ShoppingCartService.Checkout
grpcurl -d '{"itemId": "pencil"}' -plaintext 127.0.0.1:8101 shoppingcart.ShoppingCartService.GetItemPopularity
```

With shopping-order-service
```shell
grpcurl -d '{"cartId": "1", "items":[{"itemId": "pencil", "quantity": 1}]}' -plaintext 127.0.0.1:8301 shoppingorder.ShoppingOrderService.Order
```