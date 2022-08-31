# 整体架构

从整体架构上看，持久化的 Actor 内部使用了大量的 FSM 机制。由于需要和其他 Actor（事件存储Actor、快照存储 Actor）交互，因此在外层使用了一个 BehaviorInterceptor 用于转换消息.

```scala
def interceptor: BehaviorInterceptor[Any, InternalProtocol] = new BehaviorInterceptor[Any, InternalProtocol] {

  import BehaviorInterceptor._
  override def aroundReceive(
      ctx: typed.TypedActorContext[Any],
      msg: Any,
      target: ReceiveTarget[InternalProtocol]): Behavior[InternalProtocol] = {
    // 将消息转换为内部的包装.
    val innerMsg = msg match {
      case res: JournalProtocol.Response           => InternalProtocol.JournalResponse(res)
      case res: SnapshotProtocol.Response          => InternalProtocol.SnapshotterResponse(res)
      case RecoveryPermitter.RecoveryPermitGranted => InternalProtocol.RecoveryPermitGranted
      case internal: InternalProtocol              => internal // such as RecoveryTickEvent
      case cmd                                     => InternalProtocol.IncomingCommand(cmd.asInstanceOf[Command])
    }
    target(ctx, innerMsg)
  }
  }

  override def toString: String = "EventSourcedBehaviorInterceptor"
}
```

除此之外，在状态机的过程中，Actor 处于不同的状态时可能会接收到其他状态处理的消息，这部分消息需要 Stash 或者丢弃。

Akka 使用了 BehaviorSetup 存储持久化 Actor 所需的消息，包括一个 StashBuffer 用于暂存其他状态的消息，BehaviorSetup 会在多个状态之间传递，并在 Actor 外部生成，在外部的好处就是当持久化 Actor 在溯源失败时回滚时，仍然能保留 BehaviorSetup 的状态。

![akka持久化](https://user-images.githubusercontent.com/26020358/185730007-dfca28db-bfe5-430a-9c41-a3ab3af53a47.svg)