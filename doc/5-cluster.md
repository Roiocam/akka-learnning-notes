# 一. 集群

组建集群的方式在 Typed 中没有变动。变动的主要是围绕 Actor 的创建和使用方式等。本章略

# 二. 集群分片

在 Akka Cluster Sharding 中，要被分片的 Actor 被称为分片实体。每个 Actor 都拥有一个全局唯一的标识ID。在集群节点，Sharding 是由 ShardRegion 管理。

## 1. 经典 Actor

在经典 Actor 中，在集群中使用分片扩展需要使用 `ClusterSharding.start` 在集群中注册受支持的实体类型。如：

```java
ClusterShardingSettings settings = ClusterShardingSettings.create(system);
ActorRef startedCounterRegion =
    ClusterSharding.get(system)
        .start("Counter", Props.create(Counter.class), settings, messageExtractor);
```

上述代码中，`ClusterSharding.start` 返回了一个可以传递的 `ActorRef`，用于标识此分片实体。

除此之外上面的代码中还有个未定义变量名：`messageExtractor` ，它是应用程序中特定的方法，用于在传入的消息中提取实体唯一标识（entity identifier）以及分片唯一标识 (shard identifier )

<details>

<summary>官方代码示例</summary>

```java
import akka.cluster.sharding.ShardRegion;

ShardRegion.MessageExtractor messageExtractor =
    new ShardRegion.MessageExtractor() {

      @Override
      public String entityId(Object message) {
        if (message instanceof Counter.EntityEnvelope)
          return String.valueOf(((Counter.EntityEnvelope) message).id);
        else if (message instanceof Counter.Get)
          return String.valueOf(((Counter.Get) message).counterId);
        else return null;
      }

      @Override
      public Object entityMessage(Object message) {
        if (message instanceof Counter.EntityEnvelope)
          return ((Counter.EntityEnvelope) message).payload;
        else return message;
      }

      @Override
      public String shardId(Object message) {
        int numberOfShards = 100;
        if (message instanceof Counter.EntityEnvelope) {
          long id = ((Counter.EntityEnvelope) message).id;
          return String.valueOf(id % numberOfShards);
        } else if (message instanceof Counter.Get) {
          long id = ((Counter.Get) message).counterId;
          return String.valueOf(id % numberOfShards);
        } else {
          return null;
        }
      }
    };
```
</details>

分片在 Akka 中的含义是一组将一起管理的实体。实体的分组由 上述的 `shardId` 函数定义，对于特定实体唯一标识对应某一个实体，其分片唯一标识必须始终相同。否则 实体 Actor 可能会意外地在多个地方启动。

发往分片实体Actor的消息总是会先经过 `ShardRegion`, `ShardRegion` Actor 的引用可以通过 `ClusterSharding.shardRegion(name)` 并指定某个实体类型的 name 获取。`ShardRegion` 会查找分片中的本地节点是否存在该实体，
如果未找到，它将把消息委托给正确的节点，并按需创建实体 Actor。

## 2. Typed

在 Typed 中集群分片主要是抽象了 API，将实体的概念具体化成了类。

```java
 // Sharding 配置
ClusterShardingSettings settings = ClusterShardingSettings.create(actorSystem);
// 首先需要创建实体类型的Key，其name = "Book" 必须是唯一的。
EntityTypeKey<BookCommand> typeKey = EntityTypeKey.create(BookCommand.class, "Book");
// 然后定义实体的创建，相关设置现在都在这里配置
Entity<BookCommand, ShardingEnvelope<BookCommand>> entity = Entity.of(
    typeKey,   // 实体类型Key
    ctx -> BookBehavior.create(
        PersistenceId.of(ctx.getEntityTypeKey().name(), ctx.getEntityId())))  // 实体工厂
    .withSettings(settings) // 配置分片 setting
    .withMessageExtractor(new HashCodeMessageExtractor<>(4)); // 设置了4个分片数量,通过hashcode 分片
// 最后在集群分片中初始化实体
ActorRef<ShardingEnvelope<BookCommand>> shardRegion = ClusterSharding.get(actorSystem)
    .init(entity);
```

## 3. API 变更

| 经典 Actor | Typed |
| ------ | ------ |
| `typeName<String>`| `EntityTypeKey` |
| `ClusterSharding.start()`| `ClusterSharding.init()` |

## 4. 测试用例

下面的测试用例中，以 [持久化 Actor](/doc/4-eventsoured-actor.md) 中的测试为例子，在集群中创建 BookBehavior, 并以两种方式向集群分片实体发送 AddBook 消息，
然后发起 ask GetBook 消息，测试分片实体的正常收发能力，以及持久化正常。最后通过一个错误的案例演示 EntityID 的重要性。

####[ShardingTest.java](/src/test/java/com/iquantex/phoenix/typedactor/guide/cluster/ShardingTest.java)


# 三. 集群单例

Akka Cluster 的 Cluster Singleton 功能负责管理集群中 Actor 的部署，以便始终只有一个实例处于活动状态。为此，它：

- 在集群最老的节点上运行单例 Actor
- 如果最旧的节点不可用，则在另一个节点上重新启动 Actor
- 只要没有正在运行的集群单例实例，就会处理缓冲消息

## 1. 经典 Actor

```java
// 创建
final ClusterSingletonManagerSettings settings =
    ClusterSingletonManagerSettings.create(system).withRole("worker");

system.actorOf(
    ClusterSingletonManager.props(
        Props.create(Consumer.class, () -> new Consumer(queue, testActor)),
        TestSingletonMessages.end(),
        settings),
    "consumer");
// 访问
ClusterSingletonProxySettings proxySettings = ClusterSingletonProxySettings.create(system).withRole("worker");

ActorRef proxy = system.actorOf(ClusterSingletonProxy.props("/user/consumer", proxySettings), "consumerProxy");
```

## 2. Typed

```java
 // 集群单例配置
ClusterSingletonSettings settings = ClusterSingletonSettings.create(system);
// 定义集群单例 Actor
SingletonActor<BookCommand> bookSingleton =
        SingletonActor.of(
                        BookBehavior.create(
                                PersistenceId.ofUniqueId("book-01")), // 创建 Actor
                        "bookSingleton") // 集群单例全局唯一的n ame
                .withSettings(settings); // 配置
// 初始化集群单例对象
ClusterSingleton singleton = ClusterSingleton.get(system);
// 最后在集群单例中初始化单例Actor
ActorRef<BookCommand> bookActorRef = singleton.init(bookSingleton);
```

## 3. API 变更

| 经典 Actor | Typed |
| ------ | ------ |
| `ClusterSingletonManagerSettings`| `ClusterSingletonSettings` |
| `ClusterSingletonManager` 和 `ClusterSingltonProxy`| `ClusterSingleton(system).init(...)` |

## 4. 测试用例

下面的测试用例中，以 [持久化 Actor](/doc/4-eventsoured-actor.md) 中的测试为例子，在集群中创建单例 Actor。

#### [SingletonTest.java](/src/test/java/com/iquantex/phoenix/typedactor/guide/cluster/SingletonTest.java)


