# 前言


# 目录

### 1. [Actor 和 Behavior](/doc/1-actor-behavior.md)
### 2. [ActorSystem](/doc/2-actor-system.md)
### 3. [Actor 交互模式 / 发现](/doc/3-actor-interaction.md)
### 4. [持久化 Actor](/doc/4-eventsoured-actor.md)
### 5. [集群 Actor](/doc/5-cluster.md)



# 一. Message

在 Akka 中事件/消息代替了对象之间的方法调用。因此我们首要目标是定义一个消息接口，以及相应的消息序列化.


### [Message.java](src/main/java/com/iquantex/phoenix/typedactor/guide/protocol/Message.java)

# 二. Classic Actor 和 Typed Actor

Akka 在 2.6 之后对 API 做了很多改动，主要是增加的 Typed Actor 让 Actor 有了类型。

在新版本中，用户不再直接创建 Actor ，而是创建 Actor 的行为 Behavior，下面是 Behavior 的定义，涵盖了大多数Actor API的变动及用法

### [DavidBehavior.java](src/main/java/com/iquantex/phoenix/typedactor/guide/actor/DavidBehavior.java)

# 三. ActorSystem



# 四. Actor 消息的接收和回复

在下面的测试用例中，提供了 Actor 接收并处理消息的测试例子，并且演示了能够改变 Actor 处理消息行为的功能。(DavidBehavior中定义的能力)

### [DavidBehaviorTest](src/test/java/com/iquantex/phoenix/typedactor/guide/actor/DavidBehaviorTest.java)

### 1. 接收和处理
```java
// 接收和处理是Behavior的基本能力
@Override
public Receive<Message> createReceive() {
    return newReceiveBuilder()
        .onMessage(HelloResponse.class, this::onHelloResponse) // 引用方法
        .onMessage(AskChild.class,msg -> {
            // 处理逻辑
            return Behaviors.same();
        })
        .build();
}
```

### 2. 回复消息
```java
// 消息的回复依赖于ActorRef，因此当需要回复发送者消息时，需要在消息中定义回复者的ActorRef（通常由发送消息的人提供）
// 不同于 Classic Actor，Actor中不再保存最后一个消息发送者的引用。即 getSender()
public Behavior<Message> onHelloResponse(HelloResponse helloResponse) {
    helloResponse.getReplyTo().tell(new Message(getContext().getSelf()));
    return Behaviors.same();
}
```


# 五. Actor 实现的请求/响应通信模式

在 TypedActor 中提供了 ask() 方法，用于向 Actor 发送消息并等待返回结果. 在下面的测试用例中，演示了两种请求场景，以及数据库i/o的场景

### 1. 在启动时请求其他Actor，并等待结果初始化自身

这里需要借助初始化Actor时的 `ActorContext<T>`

```java
actorContext.ask(
        Message.class,
        actorRef,
        Duration.ofMillis(100),
        relyTo -> new Message(relyTo), // Message 工厂方法, 这里 relyTo 是匿名 Actor
        (res, throwable) -> {     // 回调方法，在这里可以处理异常以及转换消息格式
            Response response = (Response) res;
            actorContext.getLog()
                    .info(
                            "GetResponse From {}, Response={}",
                            response.getReplyTo().path(),
                            response.getResponse());
            // 处理后,传给自身
            return response;
        });
```

### 2. 在接受命令时请求其他Actor


### 3. 在接受名了后异步请求数据库I/O，并将结果封装后发给自身
```java
// 还有一种方案是通过 ask 客户端,即 AskPattern
CompletionStage<Message> ask = AskPattern.ask(
        actorRef,
        replyTo -> new Message(replyTo),
        Duration.ofMillis(100),
        getContext().getSystem().scheduler());
// 这里将异步结果转换为 Future
CompletableFuture<Message> future = ask.toCompletableFuture();
// 通过 pipeToSelf,在转换消息格式后发送给自身,这个API能够用于异步请求数据库
getContext()
    .pipeToSelf(
        future,
        (ok, exc) -> {
            // 不管是否有异常 直接返回
            return (Response) ok;
        });
```


### [ActorAskTest](/src/test/java/com/iquantex/phoenix/typedactor/guide/actor/ActorAskTest.java)

# 六. 如何获取 ActorRef

在Akka，所有Actor都在用户守护Actor下创建，作为守护Actor的子Actor，组成了一种树形结构。在 TypedActor
对这一概念更加强化了，ActorSystem的API也贯彻了这一点。

因此在新版ActorSystem 中，移除了 `actorOf(...)` 方法，所以用户不能在 System 外部拿到 ActorRef，需要借助一种特殊的Actor，作为 ActorSystem
的网关，向他发送请求拿到其他Actor的Ref

下面的测试用例演示了这一过程. 除此之外，每个Actor内部能够获取到自身的孩子，在下面的测试用例中也体现了这一点。

### [GetActorRefTest](/src/test/java/com/iquantex/phoenix/typedactor/guide/actor/GetActorRefTest.java)

### 1. 注册自身到Receptionist

```java
getContext().getSystem()
        .receptionist()
        .tell(Receptionist.register(ServiceKeys.DAVID_KEY, getContext().getSelf()));
```

### 2. 向receptionist发起ask请求查找ActorRef

```java
// 通过ActorSystem拿receptionist
ActorRef<Command> receptionist = actorSystem.receptionist();
// 向receptionist发送ask查找，查找指定ServiceKey的Actor是否注册
CompletionStage<Listing> result =
        AskPattern.ask(
                receptionist,
                replyTo -> Receptionist.find(ServiceKeys.DAVID_KEY, replyTo),
                Duration.ofMillis(100),
                actorSystem.scheduler());
```

### 3. 查找自己的孩子Actor

```java
List<ActorRef<Void>> children = getContext().getChildren();
```


# 七. 持久化 Actor

Akka 原生支持事件的持久化，并支持多种持久化插件。持久化的Actor 是有状态的Actor，其状态会被持久化，不只是用完即弃，所以与普通的Actor略微不同。

持久化 Actor 需要继承抽象类 `EventSourcedBehavior<Command,Event,State>` 该类有三个泛型类型

- Command：Actor 能够接收的消息，称为命令
- Event：命令产生的效果，称为事件，事件会被持久化，事件用于改变 Actor 状态，一个命令可以产生一系列事件，在持久化时会保证原子写入这些事件
- State：Actor 的状态，Akka的快照功能会持久化状态，用于在事件回溯时加快速度

总结来说就是：EventSouredActor（也称为持久Actor）接收（非持久）命令 `Command`，如果它可以应用于当前状态 `State` (命令可以指定State来接收)，则首先验证该命令。例如，这里的验证可以意味着任何事情，从简单检查命令消息的字段到与多个外部服务的对话。如果验证成功，则从命令生成事件 `Event`，表示命令的效果 `Effect`。
然后这些事件被持久化 `Effect().persist(event)`，并在成功持久化后用于改变Actor的状态`State`。当需要恢复 EventSouredActor 的状态时，只重放 `replaying` 我们知道它们可以成功应用的持久事件。换句话说，与命令相反，事件在重播`replaying`给持久参与者时不会失败。

### [持久化Actor定义例子(BookBehavior.java)](/src/main/java/com/iquantex/phoenix/typedactor/guide/persistence/BookBehavior.java)

### [持久化Actor测试案例(BookBehaviorTest.java)](/src/test/java/com/iquantex/phoenix/typedactor/guide/persistence/BookBehaviorTest.java)

### 1. 如何定义持久化Actor

```java
public class PersistenceActor extends EventSourcedBehavior<SomeCommand,SomeEvent,SomeState> {

    public PersistenceActor(PersistenceId persistenceId) {
        super(persistenceId);
    }

    @Override
    public SomeState emptyState() {
        return new SomeState(); // 空状态
    }

    @Override
    public CommandHandler<SomeCommand, SomeEvent, SomeState> commandHandler() {
        return newCommandHandlerBuilder()
            .forAnyState() // 对于任何状态
            .onCommand(AddCommand.class,(someState, someCommand) -> {
                // 该命令产生效果是持久化事件的效果 => Effect().persist()
                // 该效果会持久化传入的事件 AddEvent
                return Effect().persist(new AddEvent());
            })
            .build();
    }

    @Override
    public EventHandler<SomeState, SomeEvent> eventHandler() {
        return newEventHandlerBuilder()
            .forAnyState() // 对于任何状态
            .onEvent(AddEvent.class,(state,event)->{
                // 事件改变状态
                state.updateByEvent(event);
                // 将状态更新
                return new SomeState(state);
            })
            .build();
    }
}
```

### 2. 如何持久化事件
```java
// 只需要在 CommandHandler 中，返回效果 Effect().persist() 并传入需要持久化的事件即可，事件会在 eventHandler 处理之后持久化
Effect().persist(new AddEvent());
```
### 3. 相关配置
```conf
akka {
  persistence {
    # 内存版本持久化，不会持久化到文件
    #journal.plugin = "akka.persistence.journal.inmem"
    #snapshot-store.plugin = "akka.persistence.snapshot-store.local"
    #snapshot-store.local.dir = "target/snapshot"
    # JDBC 持久化
    journal {
      plugin = "jdbc-journal"
      auto-start-journals = ["jdbc-journal"]
    }
    snapshot-store {
      plugin = "jdbc-snapshot-store"
      auto-start-snapshot-stores = ["jdbc-snapshot-store"]
    }
  }

}
```


# ...
