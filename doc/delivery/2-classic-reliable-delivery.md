# 经典 Actor 最少一次交付

在经典 Actor 中，Akka 提供了消息投递的最少一次交付的语义的实现。只需要继承`AbstractPersistentActorWithAtLeastOnceDelivery`

## 1. API

`AtLeastOnceDelivery` 主要提供了两个核心的 API

- `deliver(目标 Actor, Function<Long,Msg>)`：交付方法，该方法会向第一个参数可靠地投递第二个参数生成的消息。
- `confirmDelivery(Long)`：确认方法，该方法在标志一个消息投递成功。

## 2. 特性,注意事项 

至少一次交付的 Actor 发送语义与一般的 Actor 不太相同：

1. 它不是至少一次交付
2. 因为重试机制, 同一发送方和接收方之间的消息顺序不能保证
3. 目标 Actor 崩溃或者重启之后，消息仍会发送给新的目标 Actor


    需要注意的是，AtLeastOnceDelivery 实现的是推送形式的至少一次交付，并且只提供了重试机制，对于待发送列表的持久化，需要开发者自行维护。

## 3. 案例

下面以一个订单和支付的案例来演示至少一次交付，其流程如下

1. 订单 Actor 接收创建订单的消息，向支付 Actor 发起支付请求
2. 支付 Actor 接收到支付请求后，内部处理完成之后响应给订单 Actor

其流程可以参考下图：

![order-payment.png](/img/order-payment.png)

在这个案例中，以两个 Actor 的事件模拟可靠交付中消息的投递过程，其中 2,4 阶段可能出现消息丢失，在下面的测试用例中，演示了经典 Actor 在消息丢失时如何保证可靠交付。

**经典 Actor 最少一次性交付演示用例：**[DeliveryTest](/src/test/java/com/iquantex/phoenix/typedactor/guide/reliability/classic/DeliveryTest.java)

Actor 的实现如下：

- [订单Actor.java](/src/main/java/com/iquantex/phoenix/typedactor/guide/reliability/classic/OrderActor.java)
- [支付Actor.java](/src/main/java/com/iquantex/phoenix/typedactor/guide/reliability/classic/PaymentActor.java)
- [消息协议,订单状态](/src/main/java/com/iquantex/phoenix/typedactor/guide/reliability/protocol)