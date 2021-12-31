# Typed 可靠交付 (一): 点对点模式

在经典 Actor 使用了推送方式的至少一次交付，但是 Akka 开发者认为推送的方式并不友好，因而在 Typed 中使用的是工作拉取的方式实现至少一次交付。

## 1. 特性

点对点模式的至少一次交付有以下特点：

1. 使用工作拉取的方式，支持流量控制
2. 支持非持久化 Actor 使用。(`但是当生产者崩溃时，消息可能丢失`)
3. 在消费端检测丢失的消息并实现重发，而不是在生产端以积极的策略重发。
4. 保证有序

## 2. 时序图

![typed-p2p.png](/img/typed-p2p.png)

## 3. API 

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

## 4. 案例

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
- [消息协议,订单状态](/src/main/java/com/iquantex/phoenix/typedactor/guide/reliability/protocol)