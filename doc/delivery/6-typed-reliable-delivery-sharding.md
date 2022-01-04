# Typed 可靠交付 (三): 分片模式

分片模式用于生产者 Actor 向集群分片的消费者之间发送消息的可靠交付。

分片模式下可靠交付则比较灵活，借助于集群分片功能，Typed 可靠交付能实现两种场景。

[sharding-consumer.png](/img/sharding-consumer.png)

**一对多模式**: 一个生产者向任意的分片消费者投递消息。

![sharing-either.png](/img/sharing-either.png)

**多对多模式**: 多个生产者，每个生产者都能向任意的分片消费者投递消息。

## 1. 特性

- 集群中的多个 ActorSystem 可以共享一个 ShardingProducerController, 后者可以发送给任意 EntityId 标识的 ShardingConsumerController
- 生产 Controller 和消费 Controller 不需要显式注册

## 2. 交付语义

1. 当生产者和消费者都没有崩溃时，此时的交付语义是 **`Effectively-Once`** , 即精确一次：消息不会丢失也不会重复。
2. 当生产者崩溃时，此时交付语义是 **`至少一次`** ：未确认的消息可能会被再次传递
3. 当消费者崩溃或者重平衡时，此时交付语义是 **`至少一次`** ：未确认的消息会被投递给新的消费者，并且可能该消息已经被其他消费者消费。

## 3. API

分片的 API 与前两者基本相同，只不过 Consumer 与 Producer 之间关系的建立不同：

- 消费者创建时需要创建在集群分片中初始化 `ShardingConsumerController` 并使用其提供的工厂方法获取其 ActorRef。
- 生产者使用的是 `ShardingProducerController`, 在创建时需要获取到消费者的分片引用 ShardingRegion

```java
EntityTypeKey<ConsumerController.SequencedMessage<TodoListMessage>> entityTypeKey = EntityTypeKey.create(ShardingConsumerController.entityTypeKeyClass(), "todo");
ActorRef<ShardingEnvelope<SequencedMessage<TodoListMessage>>> region = ClusterSharding.get(testKit.system()).init(
    Entity.of(entityTypeKey, entityContext -> ShardingConsumerController.create(
        start -> TodoListActor.create(entityContext.getEntityId(), db,start)
    )));
// 初始化 producer
Address selfAddress = Cluster.get(testKit.system()).selfMember().address();
String producerId = "todo-producer-" + selfAddress.hostPort();
ActorRef<ShardingProducerController.Command<TodoListMessage>> producerController =
    testKit.spawn(
        ShardingProducerController.create(TodoListMessage.class, producerId, region, Optional.empty()),
        "producerController"
    );
producer = testKit.spawn(TodoService.create(producerController), "producer");

```

## 4. 案例

这里使用官方的文档的 TodoList [案例](https://doc.akka.io/docs/akka/current/typed/reliable-delivery.html#sharding)。

在此案例中，演示了:

1. 生产者暂存发给每个分片ID消费者的消息
2. 消费者拉取消息时， 消息被发送到指定的分片 Actor
3. 消费者只有回复确认之后，才会拉取下一条消息
4. 同一份分片ID下的 Actor 消息有序

**分片 Actor 可靠交付示例：:**[ShardingTest.java](/src/test/java/com/iquantex/phoenix/typedactor/guide/reliability/typed/ShardingTest.java)

Actor 的实现如下：

- [消费者Actor.java](/src/main/java/com/iquantex/phoenix/typedactor/guide/reliability/typed/sharding/TodoListActor.java)
- [生产者Actor.java](/src/main/java/com/iquantex/phoenix/typedactor/guide/reliability/typed/sharding/TodoService.java)
- [消息协议,订单状态](/src/main/java/com/iquantex/phoenix/typedactor/guide/reliability/protocol/)