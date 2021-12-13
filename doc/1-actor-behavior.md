# 一. 定义

    新版 API 称为 Typed Actor
    旧版 API 称为 Classic Actor
    为了简化理解，后面的文章统一为：经典 Actor 和 Typed

### 经典 Actor

一个经典的 Actor 的定义需要继承 `akka.actor.AbstractActor`

```java
public class HelloWorld extends AbstractActor {

    public static Props props() {
        return Props.create(HelloWorld.class, HelloWorld::new);
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
            .match(Message.class, this::onMessage)
            .build();
    }

    private void onGreet(Message command) {
        getSender().tell(new ResponseMessage("Hello"), getSelf());
    }
}
```

### 新版 Actor

一个Typed Actor 的定义需要继承 `akka.actor.typed.javadsl.AbstractBehavior<T>`, 或者是函数式编程的写法,在这里不演示。

```java
public class HelloWorld extends AbstractBehavior<Message> {

    public static Behavior<Message> create() {
        return Behaviors.setup(HelloWorld::new);
    }

    private HelloWorld(ActorContext<Message> context) {
        super(context);
    }

    @Override
    public Receive<Message> createReceive() {
        return newReceiveBuilder()
            .onMessage(Greet.class, this::onMessage)
            .build();
    }

    private Behavior<Message> onMessage(Message command) {
        command.replyTo.tell(new ResponseMessage("Hello"), getContext().getSelf());
        return Behaviors.same();
    }
}
```

# 二. 为什么是 Behavior, 为什么需要 Typed

**为什么叫它 Behavior 而不叫它 Actor ？**

为了解答这个问题，首先我们对比两种 Actor 分别继承的抽象类 Java API 中的方法：

<details>

<summary>Actor</summary>

![classic_api](/img/classic_api.png)

</details>

<details>

<summary>Behavior</summary>

![typed_api](/img/typed_api.png)

</details>

由 API 可以大概知道，Behavior 简化了大多数方法，几乎是只留下了定义如何接收并处理消息的方法。没错，Behavior 被定义为 Actor 接收(receive) 哪些消息作出什么样的反应(react). 其余 Actor 相关的内容, 被 Akka 封装到了内部 API。

也就是说，在 Typed 中，Behavior 类似于 JDK 中的 Runnable，Actor 类似于 JDK 中的 Thread，但是在 Typed 中无法直接创建 Actor（还是能混用经典 Actor API）。

那么问题来了，在 Typed 中 Actor 运行时的上下文信息由谁维护？答案是 `ActorContext<T>`。ActorContext 从哪里来？答案是由 `Behaviors.setup()` 工厂方法中作为参数提供，Akka 建议开发者应该每次创建
Actor 时都创建一个新的 ActorContext 而不应该复用。*所以应该使用 Behaviors.setup() 工厂方法，而不是手动传入*。

**为什么是 Typed ？**

在 Typed 中，Behavior 有一个类型参数，描述它可以处理的消息类型。

在经典 Actor 中，没有对类型进行约束，用户可以传入任意消息，带来了部分缺点：

- receive 接收任何消息，并且接收到无法处理的消息类型时，默认行为是记录一条 `unhandled` 日志。对于新手来说，系统正常工作，但是却找不到任何问题，它不会显式告诉用户。
- 随着业务发展，当需要引入新增的 `protocol` 时很困难，可能在漏写了某处。

因此，在 Typed 中，提倡**协议优先**，Typed Actor 不仅仅是为了能用结构化的方式声明消息，确保不会错过一些未处理的消息。而更重要的目的是为了引导开发者提前考虑系统设计，设计出正确粒度和
正确的消息通信模式的 Actor 集合，以构建出强大的系统。

# 三. API 变更

### 1. 创建方式区别

下面是实例化一个 Actor 方法的对比表格

| 创建位置 | 经典 Actor | Typed |
| ------ | ------ | ------ |
| context 中创建子actor | ActorContext.actorOf() | ActorContext<T>.spawn() |
| system 中创建用户顶级actor | ActorSystem.actorOf() | 没有 spawn() 方法创建顶级用户 Actor,而是创建 system 时传入一个顶级 Actor |

两者实例化 Actor 的方法传入的参数也不一样。

- 经典 Actor：actorOf 接收 `akka.actor.Props` 参数，类似于 actor 实例工厂，用于创建和重启，在 Props 中也可以为 actor 指定调度器 dispatcher
- Typed：spawn 直接接收 Behavior<T> 而不是 Props 工厂。工厂方面由 `Behaviors.setup()` 定义。spawn也可以接收一个 Props 参数，例如指定调度器。

在 actorOf 中，`name` 参数是可选的；在 spawn 中 name 参数是强制的。如果想要创建没有 name 属性的 actor 其替代方法为 `spawnAnonymous()`

### 2. become / 改变行为

经典 Actor 中，`ActorContext.become()` 提供了改变处理 Receive 消息处理的行为。通过 `ActorContext.unbecome` 能够恢复上一个行为。

在 Typed 中，`ActorContext<T>` 没有 become 方法，而是在 Behavior 处理接收的消息时，每次都需要返回下一次处理消息的 Behavior。

<details>
<summary>示例代码</summary>

```java
@Override
public Receive<Message> createReceive() {
    return newReceiveBuilder()
    .onMessage(SayHello.class, msg -> {
        // 这里返回了上一次的 Behavior，其效果等同于 return this;
        return Behaviors.same();
    })
    .onMessage(HelloResponse.class, msg -> {
        // 这里返回了的空的 Behavior，意味着下次接收到此消息时不再进行任何处理。
        return Behaviors.empty();
    })
    .build();
    }
```
</details>

### 3. sender / parent

#### sender / 发件人

| 创建位置 | 经典 Actor | Typed |
| ------ | ------ | ------ |
| AbstractActor/AbstractBehavior | getSender(), 实际是从 ActorContext 中获取 | 无，需要在消息中明确包含一个代表发件人的信息 `ActorRef<T>` 成员 |
| ActorContext | getContext().getSender() | 同上，`ActorContext<T>` 不再维护 sender 成员 |



<details>
<summary>在 Typed 中，从消息获取发件人信息的例子</summary>

```java
class AddBook implements Message {

    private Book payload;
    // 在消息的成员中，明确包含了发件人信息的 ActorRef
    private ActorRef<StatusReply> replyToActorRef;
}
```
</details>

#### parent

| 创建位置 | 经典 Actor | Typed |
| ------ | ------ | ------ |
| AbstractActor/AbstractBehavior | getParent(), 实际是从 ActorContext 中获取 | 无，需要在构造函数中作为参数传入 `ActorRef<T>` |
| ActorContext | getContext().getParent() | 同上，`ActorContext<T>` 不再维护 sender 成员 |

### 4. 生命周期 

| 经典 Actor | Typed |
| ------ | ------ |
| `preStart()`<br> `preRestart()`<br> `postRestart()`<br> `postStop()`| 无,可用 `Behaviors.setup()` 代替<br> PreRestart <br> 无,可用 `Behaviors.setup()` 代替<br> PostStop |
| 以钩子函数的方式| 以特殊消息 `Signal` 的方式 <br> 在 receiverHandler 中, 以 onSignal 方法接收并处理 |


![生命周期信号](/img/typed_life_signal.png)

### 5. 监督机制 Supervisor

### 6. watch

### 7. stop

### 8. 示例代码

下面的类是新版 Actor 的示例定义：

[**DavidBehavior.java**](/src/main/java/com/iquantex/phoenix/typedactor/guide/actor/DavidBehavior.java)


# 四. Actor 类型变更

在经典 Actor 中，可以继承的 Actor 大致有如下类型：

![经典Actor类型](/img/classic_actor_type.png)

在 Typed 中，因为 Behavior 的思想，因此用户可以继承的 Actor 类型比较少，而是通过 `Behaviors` 工厂类创建不同种类的Actor。下表是 Actor 类型的变更：


| 经典 Actor | Typed |
| ------ | ------ |
| 继承 `AbstractActor` <br> 实例化创建 | 继承 `AbstractBehavior<T>` <br> 通过 `Behaviors.setup()` 创建 |
| 继承 `AbstractActorWithStash` <br> 实例化创建 | 继承 `AbstractBehavior<T>` <br> 通过 `Behaviors.withStash()` 创建 |
| 继承 `AbstractActorWithTimers` <br> 实例化创建 | 继承 `AbstractBehavior<T>` <br> 通过 `Behaviors.withTimers()` 创建 |
| 继承 `AbstractPersistentActor` <br> 实例化创建 | 继承 `EventSourcedBehavior<Command,Event,State>` <br> 通过 `Behaviors.setup()` 创建 |
| Stash 和 Timers 类型的 Actor 同上，以继承不同类创建 | Stash 和 Timers 类型的 Actor 同上，以 Behaviors 工厂的不同方法创建 |


