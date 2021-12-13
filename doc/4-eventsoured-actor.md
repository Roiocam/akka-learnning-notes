# 一. 经典 Actor 事件溯源

在经典 Actor 事件溯源由 `PersistenceActor` 实现，其工作流如下：

![classic_eventsoured](/img/classic_eventsoured.png)

其流程如下：

- Actor 接收 Command 后进行验证，接受或拒绝
- Command 生成 Event
- Event 被保存到事件日志中(EventStore)
- 事件日志确认Event被保存，则执行 `eventHandler`
- 运行 `eventHandler` 的副作用，如回复发送初始命令的 Actor

# 二. Typed 事件溯源


## 1. 定义
在 Typed 一切都是一种行为（Behavior），因此持久性也可以用一种特殊类型的行为来表示`EventSourcedBehavior<Command,Event,State>`。这三个类型的含义是：

- Command：Actor 能够接收的消息，称为命令
- Event：命令产生的效果，称为事件，事件会被持久化，事件用于改变 Actor 状态，一个命令可以产生一系列事件，在持久化时会保证原子写入这些事件
- State：Actor 的状态，Akka的快照功能会持久化状态，用于在事件回溯时加快速度

继承`EventSourcedBehavior<Command,Event,State>`之后需要以下核心组件：

- 持久化时的唯一标识：PersistenceID
- 定义如何处理传入的命令：Effect commandHandler(state, command)
- 定义如何处理命令生成的事件：State eventHandler(state, event)
- 定义初始化时的空状态：emptyState

EventSourcedBehavior 处理命令流程：

- Behavior（Actor）接受命令后，commandHandler 有两个参数，分别是当前状态和传入的消息命令
- commandHandler 处理完命令之后生成 事件Event，并且使用 `Effect` 包装，Effect 是事件如何持久化的策略
- eventHandler 接受到commandHandler生成的事件之后，如果根据事件的信息成功改变当前状态state，则执行 Effect 定义的持久化策略持久化当前事件(按事件生成顺序,通过Effect内部的Stash保证)

总结来说就是：EventSouredActor（也称为持久Actor）接收（非持久）命令 `Command`，如果它可以应用于当前状态 `State` (命令可以指定State来接收)，则首先验证该命令。例如，这里的验证可以意味着任何事情，从简单检查命令消息的字段到与多个外部服务的对话。如果验证成功，则从命令生成事件 `Event`，表示命令的效果 `Effect`。
然后这些事件被持久化 `Effect().persist(event)`，并在成功持久化后用于改变Actor的状态`State`。当需要恢复 EventSouredActor 的状态时，只重放 `replaying` 我们知道它们可以成功应用的持久事件。换句话说，与命令相反，事件在重播`replaying`给持久参与者时不会失败。

## 2. 产生效果(Effect)的命令(Command)

命令处理程序（下文称：commandHandler）接收当前状态和新命令，并返回一个 `Effect`.

Effect 可以是以下任意一项：

- `Effect.persist` : 持久化一个或者多个事件到事件日志
- `Effect.none` : 什么都不做
- `Effect.unhandled` :  表示不支持当前命令，或者当前状态不支持该命令
- `Effect.stop` : 停止 Actor

## 3. 产生副作用(Side-Effect)的命令(Command)

除了持久化事件(或者不持久化)之外，如果初始 Effect 成功执行，commandHandler 还可以指定一个或者多个要执行的操作。如：

```java
Effect().persist()
    .thenRun(...) // 第一个执行的操作
    .thenRun(...) // 第二个执行的操作
    .thenReply(...) // 第三个操作，回复 Actor
    .thenStop()     // 停止 Actor
```

上述的 Side-Effect 会依次执行。在持久化 Actor 中，常见的 Side-Effect 是回复发起命令的 Actor，用来实现`至少一次交付`或`恰好一次交付`的语义。

## 4. 强制回复的命令(command)

本着使用编译器来防止编程错误的精神，在 Typed 中有一种方式可以让持久化 Actor 在事件持久化之后回复给客户端（发送消息的 Actor），要使用这个特性，需要继承 `EventSourcedBehaviorWithEnforcedReplies<Command,Event,State`。

在继承 `EventSourcedBehaviorWithEnforcedReplies` 之后，重写的 `commandHandler` 方法现在的返回类型是 `CommandHandlerWithReply`，Typed 在内部中重载了此方法。
如果用户使用原来的返回类型 `CommandHandlerBuilder` 则会抛出 `UnsupportedOperationException` 异常。

另一个需要遵循的规范是，CommandHandlerWithReply 中，命令执行产生的效果 Effect，现在强制为 `ReplyEffect` 即强制带有回复的效果。ReplyEffect 只能通过 `Effect().persist().thenReply()` 即在持久化/或者不执行持久化之后，
指定`thenReply()` 的 SideEffect 才能获取。实现了编译层面强制回复。


## 5. 代码示例和测试用例

<details><summary>代码示例</summary>

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
</details>

<details>
<summary>Akka 配置</summary>

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
</details>

- [持久化Actor定义例子(BookBehavior.java)](/src/main/java/com/iquantex/phoenix/typedactor/guide/persistence/BookBehavior.java)
- [持久化Actor测试案例(BookBehaviorTest.java)](/src/test/java/com/iquantex/phoenix/typedactor/guide/persistence/BookBehaviorTest.java)

## 6. API 对比表格

| | 经典 Actor | Typed |
| ------ | ------ | ------ |
| 创建 | 继承 `PersistenceActor`| 继承 `EventSouredBehavior<Command,Event,State>` |
| 定义命令处理| `receiveCommand` | `commandHandler` |
| 定义事件处理| `receiveRecover` | `eventHandler` |
| 持久化顺序| `persist(event){ callback } ` <br> 持久化之后回调 | - `Effect.persist(event)` <br> - `eventHandler` <br> - `Effect.andThen()` 副作用 |
| Replaying/ 重播/ 事件溯源 | 接收 `RecoveryCompleted.class`  | 接收 `RecoveryCompleted` 信号 |
｜ 强制回复的EventSoured | 无 ｜继承`EventSourcedBehaviorWithEnforcedReplies<Command,Event,State>` ｜

# 三. Akka 持久化内部保证及其他功能


### a. Akka对消息顺序的保证

EventSourcedBehavior 处理事件的顺序是？当持久化时，传入的消息如何保证有序？

Akka 保证传入的消息处理是有序的，一个消息处理的流程是：`Command -> Event -> Effect -> SideEffect`

当 Effect执行persist持久化时，所有传入的消息都会被暂存到 Effect 内部的 Stash，直到 Persist -> SideEffect 执行完成之后，再执行 Stash 暂存的消息。

    Stash 有容量限制，超出会丢弃新消息，配置： akka.persistence.typed.stash-capacity = 10000

### b. 原子写入

Akka Effect 的持久化策略时，支持原子写入多个事件，`Effect().persist(List<Event>...)`，要么所有持久化，要么都不持久化（错误时）

### c. 多状态 Actor

在非持久化的 Actor 中，Behavior 处理传入消息之后，可以返回新的 Behavior ，从而改变当前 Actor 的行为（Behavior）。

但是持久化的 Actor 不支持这种模式，因为 Akka 认为 Behavior 是 Actor 状态（State） 的一部分。当需要恢复（Recovery）Actor时，需要重新构建 Behavior，这意味着 Behavior 可能也需要在 事件溯源（event replying）时恢复（Restored），如果涉及到快照，还需要正确表达快照中的Behavior，所以 持久化的Actor 不支持改变Behavior。

所以 Akka 使用了另一种机制，也就是 State 来区分。Actor 可以根据不同的状态在接受命令时以不同的行为（Behavior）处理。

这种能力也能用于实现有限状态机，不管是 持久化的Actor 还是普通 Actor 都可以使用（新版Actor）

假设 Actor 有四种状态（`BlankState`,`DraftState`,`PublishedState`,默认），则示例代码如下：

<details><summary>示例代码</summary>

```java
@Override
public CommandHandler<Cmd, Event, State> commandHandler() {
    return newCommandHandlerBuilder()
            // BlankState
            .forStateType(BlankState.class)
            .onCommand(AddPost.class, this::onAddPost)
            // DraftState
            .forStateType(DraftState.class)
            .onCommand(ChangeBody.class, this::onChangeBody)
            .onCommand(Publish.class, this::onPublish)
            .onCommand(GetPost.class, this::onGetPost)
            // PublishedState
            .forStateType(PublishedState.class)
            .onCommand(ChangeBody.class, this::onChangeBody)
            .onCommand(GetPost.class, this::onGetPost)
            // 其他状态
            .forAnyState()
            .onCommand(AddPost.class, (state, cmd) -> Effect().unhandled())
            .build();
}

@Override
public EventHandler<State, Event> eventHandler() {
    return newEventHandlerBuilder()
            // BlankState
            .forStateType(BlankState.class)
            .onEvent(PostAdded.class, event -> new DraftState(event.content))
            // DraftState
            .forStateType(DraftState.class)
            .onEvent(BodyChanged.class, (state, chg) -> state.withBody(chg.newBody))
            .onEvent(Published.class, (state, event) -> new PublishedState(state.content))
            // PublishedState
            .forStateType(PublishedState.class)
            .onEvent(BodyChanged.class, (state, chg) -> state.withBody(chg.newBody))
            .build();
}
```

</details>

### d. 序列化

需要实现序列化的类：
- Command
- Event
- State（快照）

Akka 支持以下序列化：

- Jackson
- Protocol Buffer
- 自定义

### e. Replying （溯源，重播）

Actor 崩溃时，会从 EventStore 中读取事件以及快照（State）来恢复状态，并在最后接受到一个恢复完成的信号，开发者可以根据这个信号来执行一些恢复后的操作，以及关闭溯源。
<details><summary>代码示例</summary>

```java
// 监听恢复完成信号
@Override
public SignalHandler<State> signalHandler() {
  return newSignalHandlerBuilder()
      .onSignal(
          RecoveryCompleted.instance(),
          state -> {
            // 这里拿到的就是 Actor 恢复后的状态
          })
      .build();
}
// 关闭事件溯源
@Override
public Recovery recovery() {
  return Recovery.disabled();
}
```

</details>


### f. 快照

快照相关配置：

```java
@Override
public RetentionCriteria retentionCriteria() {
    // snapshotEvery(多少个事件打一次快照, 保留多少个快照)，这里只保留两个快照，发生新的快照时会自动删除旧快照
    return RetentionCriteria.snapshotEvery(100, 2);
    // 当发生快照并成功存储时，删除快照之前的所有事件，事件日志序号保证删除后最新序号不变。
    return RetentionCriteria.snapshotEvery(100, 2).withDeleteEventsOnSnapshot();
}
@Override
public Recovery recovery() {
    // 选择最近的快照
    return Recovery.withSnapshotSelectionCriteria(SnapshotSelectionCriteria.latest());
    // 不选择快照恢复
    return Recovery.withSnapshotSelectionCriteria(SnapshotSelectionCriteria.none());
}
```


### g. 事件包装

- 通过 `EventAdapter`  Akka 支持将 Event 包装成其他类.
- 也可以不使用 `EventAdapter`，并通过重写事件溯源Actor的 `tagsFor`方法为事件记录标签 ,Tag 可用于 Projection 和 读取事件日志

