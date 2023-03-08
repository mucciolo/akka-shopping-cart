List topics
```shell
docker exec -ti shopping-cart-service-kafka-1 /opt/bitnami/kafka/bin/kafka-topics.sh --list --bootstrap-server localhost:9092
```

Consume events
```shell
docker exec -ti shopping-cart-service-kafka-1 /opt/bitnami/kafka/bin/kafka-console-consumer.sh --topic shopping-cart-events --from-beginning --bootstrap-server localhost:9092
```