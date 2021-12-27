# 经典 Actor 最少一次交付

在经典 Actor 中，Akka 提供了消息投递的最少一次交付的语义的实现。只需要继承`AbstractPersistentActorWithAtLeastOnceDelivery`

在使用时有如下特性：
- 对于至少一次语义中发送端状态的维护，需要开发者自行维护. `AbstractPersistentActorWithAtLeastOnceDelivery` 本身不会持久化任何东西。
- 至少一次语义意味着无法保证原始的消息发送顺序（同一个发送方和接收方之间的消息顺序无法保证）。
- 在JVM重启之后，消息仍然会投递给新的 Actor
- 其他特性...

下面将以如下的 Actor 结构. 演示 Akka 的最少一次交付

![经典交付](/doc/img/classic_delivery.png)


## 1. API

`AtLeastOnceDelivery` 主要提供了两个核心的 API
- `deliver(目标 Actor, Function<Long,Msg>)`：交付方法，该方法会向第一个参数可靠地投递第二个参数生成的消息。
- `confirmDelivery(Long)`：确认方法，该方法在标志一个消息投递成功。

## 2. 实现


## 3. 配置

### 方法配置

### 配置文件配置

## 4. 测试用例

**经典 Actor 最少一次性交付测试：**[DeliveryTest](/src/test/java/com/iquantex/phoenix/typedactor/guide/reliability/classic/DeliveryTest.java)





