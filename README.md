# 一. Message

在 Akka 中事件/消息代替了对象之间的方法调用。因此我们首要目标是定义一个消息接口，以及相应的消息序列化.

### [Message.java](src/main/java/com/iquantex/phoenix/typedactor/guide/protocol/Message.java)

# 二. Classic Actor 和 Typed Actor

Akka 在 2.6 之后对 API 做了很多改动，主要是增加的 Typed Actor 让 Actor 有了类型。

在新版本中，用户不再直接创建 Actor ，而是创建 Actor 的行为 Behavior，下面是 Behavior 的定义，涵盖了大多数Actor API的变动及用法

### [DavidBehavior.java](src/main/java/com/iquantex/phoenix/typedactor/guide/actor/DavidBehavior.java)

# 三. ActorSystem

在新版的 Typed Actor 中，ActorSystem 也做了一些改动，其中比较重要的一点时，Akka 不会自动帮用户创建用户 Guardian Actor，因此我们需要手动创建。

```
                                         Root guardian
                                             │
                     ┌───────────────────────┴─────────────────────────┐
                     │                                                 │
                     │                                                 │
                     │                                                 │
                User guardian                                   System guardian
    ActorSystem.create(守护Actor的工厂方法,"name")
```

下面的测试用例中演示了如何创建ActorSystem

### [ActorSystemTest](src/test/java/com/iquantex/phoenix/typedactor/guide/system/ActorSystemTest.java)

# 四. Actor 消息的接受和回复

在下面的测试用例中，提供了 Actor 接收并处理消息的测试例子，并且演示了能够改变 Actor 处理消息行为的功能。(DavidBehavior中定义的能力)

### [DavidBehaviorTest](src/test/java/com/iquantex/phoenix/typedactor/guide/actor/DavidBehaviorTest.java)

# 五. Actor 实现的请求/响应通信模式

在 TypedActor 中提供了 ask() 方法，用于向 Actor 发送消息并等待返回结果. 在下面的测试用例中，演示了两种请求场景，以及数据库i/o的场景

- 在启动时请求其他Actor，并等待结果初始化自身
- 在接受命令时请求其他Actor
- 在接受名了后异步请求数据库I/O，并将结果封装后发给自身

### [ActorAskTest](/src/test/java/com/iquantex/phoenix/typedactor/guide/actor/ActorAskTest.java)

# 六. 如何获取 ActorRef

在Akka，所有Actor都在用户守护Actor下创建，作为守护Actor的子Actor，组成了一种树形结构。在 TypedActor
对这一概念更加强化了，ActorSystem的API也贯彻了这一点。

因此在新版ActorSystem 中，移除了 `actorOf(...)` 方法，所以用户不能在 System 外部拿到 ActorRef，需要借助一种特殊的Actor，作为 ActorSystem
的网关，向他发送请求拿到其他Actor的Ref

下面的测试用例演示了这一过程. 除此之外，每个Actor内部能够获取到自身的孩子，在下面的测试用例中也体现了这一点。

### [GetActorRefTest](/src/test/java/com/iquantex/phoenix/typedactor/guide/actor/GetActorRefTest.java)

# 七. 持久化 Actor

# ...