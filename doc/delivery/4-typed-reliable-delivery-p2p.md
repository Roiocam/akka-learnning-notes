# Typed 可靠交付 (一): 点对点模式

点对点模式实现了两个 Actor 之间的工作拉取式的可靠交付。

![p2p.png](/img/p2p.png)

由上图可得，点对点模式主要基于 Actor 之间的通信，通过 Controller 屏蔽了消息投递的重试和确认。

## 1. 特性

点对点模式的可靠交付有以下特点：

1. 使用工作拉取的方式，支持流量控制
2. 支持非持久化 Actor 使用。(`但是当生产者崩溃时，消息可能丢失`)
3. 在消费端检测丢失的消息并实现重发，而不是在生产端以积极的策略重发。
4. 保证有序
5. 支持将大消息分块


## 3. 交付语义

点对点模式的交付语义不是单纯的至少一次交付语义：

1. 当生产者和消费者都没有崩溃时，此时的交付语义是 **`Effectively-Once`** , 即精确一次：消息不会丢失也不会重复。
2. 当生产者崩溃时，此时交付语义是 **`至少一次`** ：未确认的消息可能会被重复投递，而且需要在生产者中对消息列表持久化。
3. 当消费者崩溃时，此时交付语义是 **`至少一次`** ：未确认的消息会被投递给新的消费者，但是前一个消费者已经消费该消息。

## 4. API 

在 Typed 中，点对点的可靠交付由两个控制器 Actor 完成，在用户的使用层面上也更加"消息"式风格。

```java

// 创建 ProducerController Actor
ActorRef<ProducerController.Command<Command>> producerController =
    context.spawn(
        ProducerController.create(Command.class, "producerId", Optional.empty()),
        "ControllerName"
    );
// 启动 ProducerController
producerController.tell(new ProducerController.Start<>(producerActorRef));
// 创建 ConsumerController Actor
ActorRef<ConsumerController.Command<Command>> consumerController =
    context.spawn(
        ConsumerController.create(), 
        "ControllerActorName"
    );
// 启动 ConsumerController
consumerController.tell(new ConsumerController.Start<>(consumerActorRef));

```

    Controller 和实际的 Actor 必须在同一 JVM 中。

## 5. 案例

还是以订单和支付的案例来实现，不过其流程稍微有些不同。

1. 订单 Actor 接受创建订单的消息, 将向支付 Actor 发送请求支付的消息放入待发送消息列表。
2. 支付 Actor 启动 ConsumerController
3. ConsumerController 准备好之后，向生产者发起拉取请求，ProducerController 发送请求消息给订单 Actor
4. 订单 Actor 将待发送消息列表的消息发送给 ProducerController，并等待支付 Actor 下次请求
5. 支付 Actor 接收到请求，并处理完之后，向 ConsumerController 发送确认消息，并拉取下一条消息。

在这个案例中，通过阻塞确认消息来模拟网络延迟的情况，演示了流量控制的能力。

![p2pTest.png](/img/p2pTest.png)

**点对点可靠交付示例：:**[P2PTest.java](/src/test/java/com/iquantex/phoenix/typedactor/guide/reliability/typed/P2PTest.java)

Actor 的实现如下：

- [订单Actor.java](/src/main/java/com/iquantex/phoenix/typedactor/guide/reliability/typed/p2p/OrderActor.java)
- [支付Actor.java](/src/main/java/com/iquantex/phoenix/typedactor/guide/reliability/typed/p2p/PaymentActor.java)
- [消息协议,订单状态](/src/main/java/com/iquantex/phoenix/typedactor/guide/reliability/protocol/)