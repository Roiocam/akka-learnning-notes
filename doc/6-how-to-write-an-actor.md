# 如何正确编写一个 Actor

![actor.svg](/img/actor.svg)

## 1. 定义

对于一个普通的 Actor, 通常有三个组成部分：

- Message/Command: 消息协议, 一个 Actor 交互时的信息.
- Behavior: Actor 的行为
    - 与 其他 Actor 交互
    - 持久化
    - 记录日志
    - 计算行为
    - 应用事件改变状态(持久化 Actor)
- State: Actor 的状态, 对于无状态的 Actor 如转发, 则无需考虑. 但大部分 Actor 都或多或少有一些状态, 如心跳发送 Actor
  需要维护上一次心跳的时间

> 在 Typed Actor 中还增加了状态机的语义. 一个 Actor 在每次处理完消息之后都需要 `return Behaviros.same()` 或者返回其他行为的
> Actor.

## 2. 指南

为了避免后续因为不正确的 Actor 理解导致开发的 Actor 有问题，在 Lite 中新增 Actor 一定要遵循以下原则：

1. 事件是第一公民
2. 使用 State 类维护 Actor 状态
3. 可测试的 Actor 交互
4. Actor 是异步的
5. ...

### 2.1 事件是第一公民

在 Actor 系统中，事件/消息驱动是 Actor 的指令，也是不可变的事实。

在编写 Actor 时，需要**优先考虑事件的设计**，如事件需要包含哪些内容，Actor 需要接收哪些事件。

对于 EventSouring 持久化场景下驱动状态变更事件，应该尽可能只包含变更状态所需要的内容即可，否则过大的事件会增加消息传输和存储的成本.
在 EventSouring 下总是会持久化每一个事件。

举个例子: `下面的案例中, 事件存储了完整的 State, 包含大量无用的信息(无变动), 这样徒增了消息传输以及持久化的成本.`

```java

import java.io.Serializable;

@Data
public class State implements Serializable {

    private Map<String, LargeObject> map;

    public void setMap(Map<String, LargeObject> map) {
        this.map = map;
    }

}

public class Event implements Serializable {
    private Map<String, LargeObject> map;

}

```

### 2.2 使用 State 类维护 Actor 状态

对于一个 Actor 组成的系统，一定会存在维护状态的 Actor，无论是计数器还是复杂的状态。

当 Actor 存在状态时，由谁来修改状态则是一个需要考虑的事情: **在 Actor 模型，状态应由事件驱动(对于非持久化 Actor
则是消息驱动)，一切涉及到状态的读写，统一由
State 的 API 提供。**

对于普通 Actor，不允许外部对象直接修改 Actor 内部的状态。

举个例子：

```java
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.Behaviors;
import lombok.Data;

import java.io.Serializable;
import java.util.HashMap;

@Data
public class State implements Serializable {

    private int counter = 0;
    private long version = 0L;
}


/**
 * 这个示例导致 State 与 Actor 强耦合, 当需要修改这个业务逻辑时, 需要同时修改 State 和 Actor. 并且需要考虑如何重新构建 Actor 的测试案例.
 */
public class WrongActor {

    private State state;
    // private Long version = 0L;


    public Behavior<Object> runActor() {
        return Behaviors.receive(Object.class)
                .onMessage(String.class, this::incr)
                .onMessage(Integer.class, this::handleInt)
                .build();
    }

    public Behavior<Object> incr(String s) {
        state.setCounter(state.getCounter() + 1);
        version += 1;
        return Behaviors.same();
    }

    public Behavior<Object> decr(Integer i) {
        state.setCounter(state.getCounter() - 1);
        version += 1;
        return Behaviors.same();
    }

}

/**
 * 在此示例中, Actor 始终通过 State 的 `incr()` `decr` 方法操作状态. 无需关心 State 内部实现是 + 1 还是 - 1, 以及内部状态的
 * 也无需关心内部存储的数据类型是 Int 还是 Long.
 * 基于这种规范编写的 Actor 更容易便携单测.
 */
public class CorrectActor {
    private State state;

    public Behavior<Object> runActor() {
        return Behaviors.receive(Object.class)
                .onMessage(String.class, this::incr)
                .onMessage(Integer.class, this::handleInt)
                .build();
    }

    public Behavior<Object> incr(String s) {
        state.incr();
        return Behaviors.same();
    }

    public Behavior<Object> decr(Integer i) {
        state.decr();
        return Behaviors.same();
    }
}
```

### 2.3 可测试的 Actor 交互

思想来自于 Akka 的可靠交付源码, Christopher Batey 的博客文章, 以及自身的实践理解.

- 测试 Actor 回复: https://www.batey.info/akka-testing-that-actor-sends-message.html
- 测试父 Actor 发往子 Actor 的消息: https://www.batey.info/akka-testing-messages-sent-to-child.html
- 测试子 Actor 发往父 Actor 的消息: https://www.batey.info/akka-testing-messages-sent-to-actors.html

#### 2.3.1 测试 Actor 的回复机制.

这一点与 Akka Typed 推荐的方式一致, 主要是在消息中声明回复 Actor.

```java
public class ActorReplyTest {
    private ActorTestKit testKit = ActorTestKit.create();

    @Test
    public void test() {
        // 待测试 Actor
        ActorRef<Msg> testActor = testKit.spawn(EchoActor.create());
        // 回复探针
        TestProbe<String> echoReplyProbe = testKit.createTestProbe(String.class);
        // 发送消息
        testActor.tell(new EchoMsg("echo", echoReplyProbe.ref()));
        // 验证回复
        String echo = echoReplyProbe.expectMessageClass(String.class);
        assert echo.equals("echo");
    }

    /**
     * Always implements Serializable (or custom serialize interface).
     */
    public interface Msg extends Serializable {
    }

    @Data
    @AllArgsConstructor
    public static class EchoMsg implements Msg {
        private String content;
        private ActorRef<String> echoReply;
    }

    public static class EchoActor extends AbstractBehavior<Msg> {
        public static Behavior<Msg> create() {
            return Behaviors.setup(ctx -> new EchoActor(ctx));
        }

        EchoActor(ActorContext<Msg> context) {
            super(context);
        }

        @Override
        public Receive<Msg> createReceive() {
            return newReceiveBuilder()
                    .onMessage(EchoMsg.class, msg -> {
                        // echo
                        msg.getEchoReply().tell(msg.getContent());
                        return Behaviors.same();
                    })
                    .build();
        }
    }

}
```

#### 2.3.2 测试 Actor 与关联 Actor的交互

Actor 的交互除了回复，还可以有转发（可靠交付中），父子结构。这一类 Actor 的测试，应当将相关 Actor 依赖解耦合，在声明时指定:

如，下面的案例可以是子 Actor 向上层 Actor 的通信. 在测试时只需要构建 Parent 的 TestProbe, 然后让 Consumer 的行为发往
TestProbe 即可.

```java
public class Test {
  @Test
  public void test() {
    TestProbe<Msg> parentProbe = testKit.createTestProbe(Msg.class);
    ActorRef<Msg> childActor = testKit.spawn(InteractionActor.create(msg -> parentProbe.tell(msg)));
    childActor.tell(new Updated());
    SendToOtherMsg sendToOtherMsg = parentProbe.expectMessageClass(SendToOtherMsg.class);
  }
}
public static class InteractionActor extends AbstractBehavior<Msg> {

  private final Consumer<Msg> sendToOther;

  public static Behavior<Msg> create(Consumer<Msg> sendToOther) {
    return Behaviors.setup(ctx -> new InteractionActor(ctx, sendToOther));
  }

  InteractionActor(ActorContext<Msg> context, Consumer<Msg> sendToOther) {
    super(context);
    this.sendToOther = sendToOther;
  }


  @Override
  public Receive<Msg> createReceive() {
    return newReceiveBuilder()
            .onMessage(Updated.class, u -> {
              sendToOther.accept(new SendToOtherMsg());
              return Behaviors.same();
            })
            .build();
  }
}
```

另外一个案例则是对 Parent Actor 的测试. 也可以使用解耦合的方式让 Actor 之间的交互可以测试。

举一反三，此种方式也可以用在有订阅关系的 Actor 中...

```java
 public static class InteractionParentActor extends AbstractBehavior<Msg> {

        private final ActorRef<Msg> childRef;

        public static Behavior<Msg> create(Supplier<ActorRef<Msg>> childFactory) {
            return Behaviors.setup(ctx -> new InteractionParentActor(ctx, childFactory));
        }

        InteractionParentActor(ActorContext<Msg> context, Supplier<ActorRef<Msg>> childFactory) {
            super(context);
            this.childRef = childFactory.get();
        }


        @Override
        public Receive<Msg> createReceive() {
            return newReceiveBuilder()
                    .onMessage(Updated.class, u -> {
                        childRef.tell(new SendToOtherMsg());
                        return Behaviors.same();
                    })
                    .build();
        }
    }

    @Test
    public void childTest() {
        TestProbe<Msg> childProbe = testKit.createTestProbe(Msg.class);
        ActorRef<Msg> parentActor = testKit.spawn(InteractionParentActor.create(()-> childProbe.getRef()));
        parentActor.tell(new Updated());
        SendToOtherMsg sendToOtherMsg = childProbe.expectMessageClass(SendToOtherMsg.class);
        Assert.assertNotNull(sendToOtherMsg);
    }
```


### 2.4 Actor 是异步的

虽然 Actor 模型避免了线程的开销，但始终是运行在线程之上的，当多个 Actor 阻塞式的发起了向另一个 Actor 发起 Ask 请求（RPC）时，线程池资源耗尽，此时 Actor 运行的线程池就会进入线程死锁的状态。

如何正确处理异步，可以在 Akka 文档中找到：https://doc.akka.io/docs/akka/current/typed/dispatchers.html#blocking-needs-careful-management

### 示例代码和测试

[**ActorInteractionTest.java**](/src/test/java/com/iquantex/phoenix/typedactor/guide/actor/ActorInteractionTest.java)
