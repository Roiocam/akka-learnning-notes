# 前言

Akka 发布了新版的 Actor API，新旧版API能够兼容使用，但是两者的 API 有着很大的差别。

# 一. Java API 对比

### a. 经典 Actor API

![classic_api.png](/img/classic_api.png)

### b. 新版 Actor API
![截屏2021-12-06_上午10.12.42](/uploads/a28b775eb16bf67450a988c9301ac180/截屏2021-12-06_上午10.12.42.png)

经典 Actor API 中，定义了很多方法，包括：
- 接受的消息定义：receive
- 上下文：context
- 自身引用：self
- 发送者：sender
- 生命周期切面方法：preStart，postStop等

而新版 Actor API 中，几乎没有相应的方法。这是因为，在新版 Actor 中，有了 Behavior 的概念，也就是定义了上图截图中的API的类。Behavior 在新版 Actor 中是个很重要的概念，在API中这样介绍了 Behavior：`Behavior`定义了 Actor 对接受(receive) 消息作出什么样的反应(react)。这个概念类似于JDK线程 Runable 之于 Thread，新版 Actor 中对 Actor 内部做了更高层次的抽象。

在经典 Actor 源码，也能看到 Actor 类中维护了很多相关的成员，包括 receive，context，sender，selft
![截屏2021-12-06_上午10.37.27](/uploads/cf1f35a87065d1be0039c110d168e254/截屏2021-12-06_上午10.37.27.png)

## 2. 创建方式对比
因为引入了 Behavior 概念，因此开发者在编写“Actor”类时，不带有上下文等信息，仅仅定义了对接收信息作出反应的方法，所以需要手动传入 ActorContext。并且 Behavior 是泛型类，所以
### a. 经典 Actor

<details><summary>示例代码</summary>

```java
class Test extends AbstractActor{

    @Override
    public Receive createReceive() {
        return null;
    }
}
// 匿名类
Props.create(new Creator<Actor>() {
    @Override
    public Actor create() throws Exception, Exception {
        return new Test();
    }
});
// lambda
Props.create(()-> new Test());
// lambda 匿名方法
Props.create(Test::new);
```

</details>


### b. 新版 Actor

<details><summary>示例代码</summary>

```java
// 定义
class Test extends AbstractBehavior<String>{
    public Test(ActorContext<String> context) {
        super(context);
    }

    @Override
    public Receive<String> createReceive() {
        return null;
    }
}
// 传统
Behaviors.<String>setup(ctx-> new Test(ctx));
// lambda
Behaviors.setup(Test::new);
```

</details>

## 3. 生命周期对比
上面提到，开发者编写的Acto是Behavior，而Behavior 仅仅是定义了对接收消息的响应，那么原有模型下的生命周期钩子方法在新版API中如何实现？
在源码中，AbstractBehavior 包含了 Receiver 类，用于定义接收信息的反应，而 Receiver 类在源码中是 Behavior 的扩展类，在同一文件中。其定义如下：

![截屏2021-12-06_上午11.14.36](/uploads/8114e9255d270156eb1f4feb187c06e1/截屏2021-12-06_上午11.14.36.png)

Receiver 类定义的消息 Signal 有如下实现： 在新版 Actor API 中，对生命周期的钩子有着不同的实现

![截屏2021-12-06_上午11.17.38](/uploads/81e8ca41e47dab25faf159e74429a2f7/截屏2021-12-06_上午11.17.38.png)


### a. 原版 API 启动前钩子

<details><summary>示例代码</summary>

```java
class Test extends AbstractActor {

    @Override
    public void preStart() throws Exception {
        super.preStart();
    }
    
}
```

</details>


### b. 新版 API 启动前钩子

<details><summary>示例代码</summary>

```java
class Test extends AbstractBehavior<String> {
    
    public Behavior<String> toString(PreRestart preRestart) {
        preRestart.toString();
        return this;
    }

    @Override
    public Receive<String> createReceive() {
        return newReceiveBuilder()
                .onSignal(PreRestart.class,this::toString)
                .build();
    }
}
```
</details>

### c. 监听机制

这种方式的不同也带来新的监听机制，在旧版API中，一个Actor的生命周期由父级监听，用户只能在钩子方法中实现监听机制，在新版API中则提供了更完善的监听机制：

<details><summary>示例代码</summary>

```java
interface Command extends Serializable {} // 定义 Actor Protocol
class Test extends AbstractBehavior<Command> {
    class SpawnJob implements Command {} 
    class ActorTerminated implements Command{}  // 自定义监听停止的事件

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(SpawnJob.class,this::onSpawnJob) // 创建监听的Actor
                .onMessage(ActorTerminated.class,this::onActorTerminated) // 监听自定义停止事件
                .onSignal(Terminated.class, this::onTerminated) // 监听上述 Actor 停止的信号
                .build();
    }

    private Behavior<Command> onActorTerminated(ActorTerminated terminated) {
        terminated.toString();
        return this;
    }

    private Behavior<Command> onTerminated(Terminated terminated) {
        String terminatedActorName = terminated.getRef().path().name();
        return this;
    }

    private Behavior<Command> onSpawnJob(SpawnJob cmd) {
        ActorRef<Command> anotherActorRef = getContext().spawn(AnotherActor.create(), cmd.toString());
        getContext().watch(anotherActorRef); // 停止时发出信号
        getContext().watchWith(anotherActorRef,new ActorTerminated()); // 停止时发出自定义事件
        return this;
    }
}
```

</details>