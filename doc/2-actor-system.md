# API 变动


## 1. 经典 Actor

经典 Actor 中 `ActorSystem.actorOf()` 通常用于创建几个（或者多个）Top-Level 的 Actor，这种初始化通常是从"外部"(outside of actorSystem) 执行. 

## 2. Typed Actor

在 Typed 中，上述的操作不被支持（不支持创建多个顶级 Actor），用户也无法在任意位置创建没有指定接收消息的任意 Actor，也无法从 `ActorSystem<T>` 外部创建 Actor。

在 Typed 中，上述所有 Top-Level Actor 都必须在 User Guardian Actor 下创建，这也符合 Actor 概念中树形的层次结构的概念。但如果用户有特殊需求需要在用户守护 Actor 外创建，那么 Akka 还是提供了相关 API。其步骤是向 ActorSystem 发送 `SpawnProtocol` 消息。详细请见：[SpawnProtocol](https://doc.akka.io/docs/akka/current/typed/actor-lifecycle.html#spawnprotocol)

`akka.actor.ActorSystem`在 中有其对应关系akka.actor.typed.ActorSystem。一个区别是，当创建一个ActorSystemin Typed 时，你给它一个Behavior将用作顶级角色的角色，也称为用户监护人。

还有一个变更是，在 Typed 中，在创建 `ActorSystem` 时需要手动指定用户守护 Actor（`Behavior<T>`）。

| 经典 Actor | Typed |
| ------ | ------ |
| ActorSystem.create("systemName",Config) | `ActorSystem<T>.create(Behavior<T>,"systemName",Config)` |


```
                                         Root guardian
                                             │
                     ┌───────────────────────┴─────────────────────────┐
                     │                                                 │
                     │                                                 │
                     │                                                 │
                User guardian                                   System guardian
    ActorSystem<T>.create(守护Actor的工厂方法,"name")
```

# 示例代码


下面的测试用例中演示了如何创建ActorSystem

### [ActorSystemTest](src/test/java/com/iquantex/phoenix/typedactor/guide/system/ActorSystemTest.java)
