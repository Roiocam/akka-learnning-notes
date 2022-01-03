# Typed 可靠交付 (二): 工作拉取模式

此文的工作拉取模式与点对点模式的工作拉取不同，但其底层都是工作拉取。不同点在于，本文的工作拉取模式中，多个消费者拉取共享的生产者 Manager。

## 1. 特性

工作拉取模式的可靠交付有一个重要的特性是： **消息的顺序必须是无关的，两个后续消息可能被路由到不同的消费者。**

## 2. 交付语义

1. 当生产者和消费者都没有崩溃时，此时的交付语义是**`Effectively-Once`**, 即精确一次：消息不会丢失也不会重复。
2. 当生产者崩溃时，此时交付语义是**`至少一次`**：未确认的消息可能会被路由到不同的消费者。
3. 当消费者崩溃时，此时交付语义是**`至少一次`**：未确认的消息会被投递给新的消费者，但是前一个消费者已经消费该消息。

## 3. API

工作拉取模式的 API 与点对点模式大致相同，不同点在于：

- 生产者使用的是 `WorkPullingProducerController`
- 消费者创建时需要指定 `ServiceKey`, 而不是发送消息注册 Producer 

```java

// 创建 ProducerController Actor
ActorRef<WorkPullingProducerController.Command<ConversionJob>> producerController = ctx.spawn(
    WorkPullingProducerController.create(ConversionJob.class, "workManager",
        ImageConverterActor.serviceKey,
        Optional.empty()),
    "WorkPullControllerName");
producerController.tell(new Start<>(messageAdapter));
// 创建并启动 ConsumerController Actor
ActorRef<ConsumerController.Command<ConversionJob>> consumerController = ctx.spawn(
    ConsumerController.create(serviceKey),
    "ControllerName");
consumerController.tell(new Start<>(deliveryAdapter));

```

    Controller 和实际的 Actor 必须在同一 JVM 中。

## 4. 案例

订单和支付的案例无法案例工作拉取功能，这里直接参考官方文档的[案例](https://doc.akka.io/docs/akka/current/typed/reliable-delivery.html#work-pulling)。

在此案例中，演示了:

1. 消息(工作任务)被随机分配到不同的消费者
2. 消费者只有回复确认之后，才会拉取下一条消息
3. 消费者拉取的消息是乱序的

**工作拉取可靠交付示例：:**[WorkPullTest.java](/src/test/java/com/iquantex/phoenix/typedactor/guide/reliability/typed/WorkPullTest.java)

Actor 的实现如下：

- [消费者Actor.java](/src/main/java/com/iquantex/phoenix/typedactor/guide/reliability/typed/workpull/ImageConverterActor.java)
- [生产者Actor.java](/src/main/java/com/iquantex/phoenix/typedactor/guide/reliability/typed/workpull/WorkManager.java)
- [消息协议,订单状态](/src/main/java/com/iquantex/phoenix/typedactor/guide/reliability/protocol/)