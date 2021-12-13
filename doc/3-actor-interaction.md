# 一. Akka 交互模式

## 1. 回复机制

在 [Actor 和 Behavior](/doc/1-diff.md#sender-/-发件人) 中提到 `ActorContext<T>` 不再维护发件人信息，而是需要在消息中明确定义回复的 `ActorRef<T>`, 因此，在 Typed 中的回复机制有一些变化。

| 经典 Actor | Typed |
| ------ | ------ |
| `getSender().tell()`| `msg.getReplyTo().tell()` |
| 从`ActorContext`中获取发件人| 从`消息(Protocol)`中获取发件人 |

<details>

<summary>Typed回复</summary>

```java
@Getter
@AllArgsConstructor
class Request {
    public final String query;
    public final ActorRef<Response> replyTo; // 在消息中添加发送者引用
}

@AllArgsConstructor
class Response {
    public final String result;
}
// 定义Actor
public class Test extends AbstractBehavior<Request> {

    @Override
    public Receive<Request> createReceive() {
        return newReceiveBuilder()
                .onMessage(Request.class, this::onRequest)
                .build();
    }

    private Behavior<Request> onRequest(Request request) {
        // do something
        ActorRef<Response> replyTo = request.getReplyTo(); // 获取Actor引用
        replyTo.tell(new Response("result")); // 回复 Actor 消息
        return this;
    }
}
```

</details>

## 2. ask | 请求/响应

在经典 Actor 中，只支持通过 `Patterns.ask()` 实现外部向 Actor 发送请求并返回 Future 的响应。在 Typed 中，API 有了一些变化，并且支持在 Actor 内部发起 ask 请求。

| 经典 Actor | Typed |
| ------ | ------ |
| `Patterns.ask()`| `AskPattern.ask()` |
| `Patterns.askWithStatus`| `AskPattern.askWithStatus()` |
| 无| `ActorContext<T>.ask()` actor 内部发起请求 |
| 无| `ActorContext<T>.askWithStatus()`  actor 内部发起请求|



<details>

<summary>示例代码</summary>

```java
public class Test extends AbstractBehavior<Request> {

    public Test(ActorContext<Request> ctx){
        // 内部发起 ask
        ctx.ask(...);
    }

    @Override
    public Receive<Request> createReceive() {
        return newReceiveBuilder()
            .onMessage(Request.class, this::onRequest)
            .build();
    }

    private Behavior<Request> onRequest(Request request) {
        ActorRef<Response> replyTo = request.getReplyTo();
        replyTo.tell(new Response("result"));
        return this;
    }
}

// 外部系统，需要拿到ActorRef和ActorSystem
public class Main {

    public void askAndPrint(ActorSystem<Void> system, ActorRef<Request> testActor) {
        CompletionStage<Response> result = AskPattern.ask(
            testActor,
            replyTo -> new Request("result", null),
            Duration.ofSeconds(3),
            system.scheduler()
        );

        //回调方法
        result.whenComplete((response, throwable) -> {
            if (response != null) {
                //.. 
            } else {
                // ...
            }
        });
    }
}
```

</details>


## 3. Patterns 拆分

在上面的案例中，如果阅读 `akka.pattern.Patterns` 则可发现，在 Typed 中，对 Patterns 的职责进行了拆分。由界限不清晰的 Patterns 拆分到 `ActorContext<T>` 和 `AskPattern` 中。如下表所示：

| 经典 Actor | Typed |
| ------ | ------ |
| `Patterns.ask()`| `AskPattern.ask()` |
| `Patterns.askWithStatus`| `AskPattern.askWithStatus()` |
|  `Patterns.askWithReplyTo()`| 在消息中附带`ActorRef<T> replyTo`变量  |
|  `Patterns.pipe()`| `ActorContext<T>.pipeToSelf()`  |
|  `Patterns.gracefulStop()`| `ActorContext<T>.stop()`  |


上面的表格只是简单梳理一些差不多的 API，实际上可能不完全正确，但是在 API 变动层面不难看出，在 Typed 中强化了 Actor 的层次结构，如停止 Actor 的 `stop()` 由 Actor 的创建者（parent）来请求执行，而不是经典 Actor 中的任意位置。


<details><summary> 代码示例 </summary><blockquote>


<details><summary>1. 在启动时请求其他Actor，并等待结果初始化自身</summary>

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

</details>

<details><summary>2. 在接受命令时请求其他Actor</summary>

```java
// 这里需要 getContext()拿到上下文引用
getContext().ask(
    Message.class,
    actorRef,
    Duration.ofMillis(100),
    relyTo -> new Message(getContext().getSelf()),
    (response, throwable) -> {
        if (throwable instanceof TimeoutException) {
            getContext().getLog().info("因为上面 sayHello 没有传匿名 Actor,所以这里拿不到任何回复");
        }
        return response;
    }
);
```

</details>


<details><summary>3. 在接受名了后异步请求数据库I/O，并将结果封装后发给自身</summary>

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

</details>

</blockquote>
</details>


## 4. 测试示例

在 [Actor和Behavior](/doc/1-actor-behavior.md) 中，简单介绍了 Behavior 的 API，加上本文上述内容。有如下测试用例帮助理解 Typed Actor 的交互模式。

- **演示 Behavior 如何处理消息的测试用例：**[DavidBehaviorTest](/src/test/java/com/iquantex/phoenix/typedactor/guide/actor/DavidBehaviorTest.java)
- **演示 Ask 通信的测试用例：** [ActorAskTest](//src/test/java/com/iquantex/phoenix/typedactor/guide/actor/ActorAskTest.java)


# 二. 消息适配器

如果你已经阅读了之前所有的发现，你可能已经发现使用Typed Actor 和消息定义会出现问题。即请求者不知道请求的 Actor 会响应什么消息类型。

如果你有使用经典 Actor 实现请求/响应模式时应该会发现这有些笨重。balabala

在 Typed 中，`Adapter Response` 解决了这个问题。

## 1. Adapter Response

假设有两个 Actor，`Translator` 和 `Backend`, Translator 接收翻译消息`Translate`之后向 Backend 发送消息并接收其响应结果`Response`。为了能使前者使用后者的响应消息，我们需要：

- 在 PaymentHandler 中定义响应如何响应这些消息
- 将 Configuration 消息转换成 PaymentHandler 能够接收的消息，我们使用 Adapter Response

<details>
<summary>示例代码</summary>

```java
// 定义消息接口
public interface Command {}
// 定义 Translator 能够接收处理的消息 Translate
public class Translate implements Command {
    public final URI site;
    public final ActorRef<URI> replyTo;

    public Translate(URI site, ActorRef<URI> replyTo) {
        this.site = site;
        this.replyTo = replyTo;
    }
}
// 定义 Translator 对 Backend 响应消息的封装
private class WrappedBackendResponse implements Command {
    final Backend.Response response;

    public WrappedBackendResponse(Backend.Response response) {
        this.response = response;
    }
}
// 定义 Translator
public class Translator extends AbstractBehavior<Command> {
    private final ActorRef<Backend.Request> backend;
    private final ActorRef<Backend.Response> backendResponseAdapter;
    // 任务计数
    private int taskIdCounter = 0;
    // 在途任务
    private Map<Integer, ActorRef<URI>> inProgress = new HashMap<>();

    public Translator(ActorContext<Command> context, ActorRef<Backend.Request> backend) {
        super(context);
        this.backend = backend;
        // 定义 Adapter Response
        this.backendResponseAdapter =
            context.messageAdapter(Backend.Response.class, WrappedBackendResponse::new);
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
            .onMessage(Translate.class, this::onTranslate)
            .onMessage(WrappedBackendResponse.class, this::onWrappedBackendResponse)
            .build();
    }

    private Behavior<Command> onTranslate(Translate cmd) {
        taskIdCounter += 1;
        inProgress.put(taskIdCounter, cmd.replyTo);
        // 向 Backend 发送消息，并期待返回 Response
        // 返回的 Response 会经过 messageAdapter 转换成 Translator 可处理的协议 WrappedBackendResponse
        backend.tell(
            new Backend.StartTranslationJob(taskIdCounter, cmd.site, backendResponseAdapter));
        return this;
    }
    // 接收到 messageAdapter 转换后的 Backend.Response
    private Behavior<Command> onWrappedBackendResponse(WrappedBackendResponse wrapped) {
        Backend.Response response = wrapped.response;
        if (response instanceof Backend.JobStarted) {
            Backend.JobStarted rsp = (Backend.JobStarted) response;
            getContext().getLog().info("Started {}", rsp.taskId);
        } else if (response instanceof Backend.JobProgress) {
            Backend.JobProgress rsp = (Backend.JobProgress) response;
            getContext().getLog().info("Progress {}", rsp.taskId);
        } else if (response instanceof Backend.JobCompleted) {
            Backend.JobCompleted rsp = (Backend.JobCompleted) response;
            getContext().getLog().info("Completed {}", rsp.taskId);
            inProgress.get(rsp.taskId).tell(rsp.result);
            inProgress.remove(rsp.taskId);
        } else {
            return Behaviors.unhandled();
        }

        return this;
    }
}
```
</details>

## 2. 测试用例

下面是基于示例代码的 Adapter Response 测试用例。

- 包含接收翻译任务并处理的BackendActor，及其相关消息协议定义的 [Backend.java](/src/main/java/com/iquantex/phoenix/typedactor/guide/adapter/Backend.java)
- 包含接收翻译请求的Translator Actor，及其相关消息协议定义的 [Frontend.java](/src/main/java/com/iquantex/phoenix/typedactor/guide/adapter/Frontend.java)
- 包含使用上面两者的测试用例。[AdapterTest.java](/src/test/java/com/iquantex/phoenix/typedactor/guide/adapter/AdapterTest.java)

# 三. Actor 发现

## 1. 经典 Actor

每个 Actor 都有一个唯一的逻辑路径，它是通过从子 Actor -> 父 Actor 直到到达 ActorSystem 的 Root 之间的链路获得，并且它有一个物理路径。系统使用这些路径来查找参与者。例如，当 system 接收到远程消息并搜索接收者时

在经典 Actor 中，Actor 还可以使用这些绝对路径或者相对路径来查找其他 Actor，并且得到一个 `ActorSelection` 结果.

<details>
<summary>示例代码</summary>

```java
// 通过绝对路径查找
getContext().actorSelection("/user/serviceA/actor");
// 会查找当前 Actor 的兄弟姐妹（相对路径）
getContext().actorSelection("../joe");
```

</details>

上述的代码中，`ActorContext` 继承了 `ActorRefFactory`, 而 `ActorRefFactory` 的 `actorSelection` 维护了 `ActorSelection` 的创建。

## 2. Typed

在 Typed 中，`ActorContext<T>` 不再继承 `ActorRefFactory`, 也没有 `ActorSelection`, 有关 `ActorRef` 的查找，即 Actor 发现通过 `Receptionist` 维护。

在 Typed 中，获取 `ActorRef` 的方式有两种：

- 创建 Actor 时获取
- 通过 Receptionist 发现

### Receptionist

当一个 Actor 需要被其他 Actor 发现，但是无法在传入消息中放置它的引用时。可以使用 `Receptionist` 进行发现，**Receptionist 同时支持本地和集群**。在集群场景下，每个节点的注册表被自动分发到集群内的其他节点，注册表是动态更新的。

使用 `Receptionist` 查找时之前需要将 Actor 手动注册到 Receptionist。

<details>

<summary>示例代码</summary>

```java
ServiceKey<Ping> pingServiceKey = ServiceKey.create(Ping.class, "pingService");
// 获取 ActorSystem 的 Receptionist，并发送注册消息
context.getSystem().receptionist()
              .tell(Receptionist.register(pingServiceKey, context.getSelf()));
```

</details>

更多 Receptionist 的使用内容可以参考 Akka 官方文档：[Akka-Discovery](https://doc.akka.io/docs/akka/current/typed/actor-discovery.html)

## 3. API 对比

| 查找方式 | 经典 Actor | Typed |
| ------ | ------ | ------ |
| 查找具体的ActorRef | 通过路径创建`ActorSelection`| 将 Actor 注册到 `Receptionist` 并通过其查找 |
| 将消息发到非具体的ActorRef，而是目的地路径 | 通过相对根路径创建`ActorSelection` | 需要使用 `Group Router`,详细内容参考 [Akka文档](https://doc.akka.io/docs/akka/current/typed/routers.html#group-router) |

## 4. 测试用例

下面的测试用例中，演示了使用了 Receptionist 获取具体的 ActorRef 的案例，以及使用 `ActorContext<T>` 获取自身的子 ActorRef

#### [GetActorRefTest.java](/src/test/java/com/iquantex/phoenix/typedactor/guide/actor/GetActorRefTest.java)

