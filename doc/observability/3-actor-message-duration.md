# 1. 介绍

在上个文章里面，使用了 OpenTelemetry 成功拿到了 Dispatcher 背后的线程池，使我们能够在运行时通过外部监控采集时，触发

监控的监听器然后采集 Executor 的一些指标（静态和动态），上文演示了 Queue Size 和 Active Thread 等，对于 ForkJoinPool

和 ThreadPoolExecutor 还有不同的指标（上文暂不涉及，自己展开）。

上文的指标能够只能帮我们了解到线程的利用率，对于每个线程的负载，整个 Executor 的排队情况，暂时无法得知，基于 Lightbend Telemetry

的 Grafana Dashboard，我们还有两个 Duration 相关的指标没有实现，原来的思路也不适用：

- message on queue time.
- message processing time.

对于 Request/Response 的指标，OpenTelemetry Java Instrumentation 中有相关的实现（只有链路追踪部分，Metrics 的原理基本类似，

都是基于  Context 实现的），而 Mailbox 的 Message 会跨越多个线程上下文，经过我的探索之后，有一个简单方案可用于采集 Metrics。

对于链路追踪这些，请参考 OpenTelemetry 的 Instrumenter。

# 2. 思路


## 2.1 On Queue Time 指标采集

Akka 的 Mailbox 本质上就是一个 Queue，Akka 在这基础上实现了 Runnable、ForkJoinTask 接口，让其可执行消息，并有相应的

Actor 作为运行时。因此 Akka Mailbox 的 Queue Time 就是 Java Queue 的 Queue time。

思路也比较简单：在 Queue 的 `enqueue()` 调用之前改变 Message，附加 Start Time，Attribute 等属性，在 `dequeue()` 时记录指标并还原 Message。

而 Akka 的优先级 Mailbox 的实现思路也是这种方式：

![priority_mailbox](/img/priority_mailbox.png)

## 2.2 Processing Time 指标采集

上面我们提到 Mailbox 实现了 Runnable 和 ForkJoinTask 接口，前者是 `ThreadPoolExecutor` 内 Task 的定义，后者是 `ForkJoinPool` 中

Task 的定义，而 Akka 的 ForkJoinTask 的 `exec()` 方法最终调用了 `run()` 使 Mailbox 同时兼容两类线程池。

因此 Processing 采集的思路也很简单：记录 `run()` 的执行时间。

原本一开始看了 ThreadPoolExecutor 的源码，其执行任务的 `run()` 留有两个模版方法: `beforeExecute()` 和 `afterExecute()`, 在这里实现监控埋点是个不错的方案.

但实际执行时有两个问题：

- ForkJoinPool 没有这个模版方案
- 跨方法的 Advice, OpenTelemetry 上下文的传递比较麻烦.

因此直接对 `run()` 方法切面就可以了.


# 3. 实现

## 3.1 Queue Time

```java
public class AkkaMailboxInstrumentation implements TypeInstrumentation {

    @Override
    public ElementMatcher<TypeDescription> typeMatcher() {
        // AOP Mailbox 类
        return ElementMatchers.named("akka.dispatch.Mailbox")
                   .and(
                       AgentElementMatchers.extendsClass(
                           ElementMatchers.named("java.util.concurrent.ForkJoinTask")));
    }

    @Override
    public void transform(TypeTransformer typeTransformer) {
        // 拦截入队和出队方法
        typeTransformer.applyAdviceToMethod(
            ElementMatchers.named("enqueue"), EnqueueAdvice.class.getName());
        typeTransformer.applyAdviceToMethod(
            ElementMatchers.named("dequeue"), DequeueAdvice.class.getName());
    }
}

/**
 * 入队方法切面
 */
@SuppressWarnings("unused")
public static class EnqueueAdvice {

    /**
     * 方法进入时, 修改 message，替换为 Wrapper Message，并附加参数和时间
     * @param actorRef
     * @param envelope
     * @param thiz
     */
    @Advice.OnMethodEnter
    public static void onMessage(
        @Advice.Argument(0) ActorRef actorRef,
        @Advice.Argument(value = 1, readOnly = false) Envelope envelope,
        @Advice.This Mailbox thiz) {
        Object message = envelope.message();
        WrapperMessage newMessage =
            new WrapperMessage(message, thiz.dispatcher().id(), System.nanoTime());
        envelope = Envelope.apply(newMessage, envelope.sender());
    }
}

/**
 * 出队方法切面
 */
@SuppressWarnings("unused")
public static class DequeueAdvice {

    /**
     * 出队的方法结束时，判断消息是否是 WrapperMessage，是的话还原消息，并记录指标
     * @param envelope
     * @param context
     * @param scope
     */
    @Advice.OnMethodExit
    public static void exit(
        @Advice.Return(readOnly = false) Envelope envelope,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
        if (envelope == null) {
            return;
        }
        Object message = envelope.message();
        if (message == null) {
            return;
        }
        if (message instanceof WrapperMessage) {
            WrapperMessage wrapperMessage = (WrapperMessage) message;
            Object origin = wrapperMessage.getOrigin();
            String dispatcherId = wrapperMessage.getDispatcherId();
            long duration = System.nanoTime() - wrapperMessage.getStartNanos();
            // 记录指标
            DispatcherMetrics.recordQueueDuration(duration, dispatcherId);
            envelope = Envelope.apply(origin, envelope.sender());
        }
    }
}

/**
 * 自定义的消息封装，附加指标参数
 */
public class WrapperMessage {

    private final Object origin;
    private final String dispatcherId;
    private final long startNanos;

    public WrapperMessage(Object origin, String dispatcherId, long startNanos) {
        this.origin = origin;
        this.dispatcherId = dispatcherId;
        this.startNanos = startNanos;
    }

    public Object getOrigin() {
        return origin;
    }

    public String getDispatcherId() {
        return dispatcherId;
    }

    public long getStartNanos() {
        return startNanos;
    }
}
```


## 3.2 Processing Time

处理实现更简单，只需要 AOP 执行方法即可。这里仍然对 Mailbox 切面

```java
public class AkkaMailboxInstrumentation implements TypeInstrumentation {
    // ...略
    @Override
    public void transform(TypeTransformer typeTransformer) {
        // ... 略
        // 这里拦截的是 run，而不是 ForkJoinTask 的 exec, 因为 akka mailbox 中的 exec 实现也是执行 run
        typeTransformer.applyAdviceToMethod(
            ElementMatchers.named("run")
                .and(ElementMatchers.takesArguments(0))
                .and(ElementMatchers.not(ElementMatchers.isAbstract())),
           ForkJoinTaskAdvice.class.getName());
    }
}

/**
 * Mailbox 方法执行切面
 */
@SuppressWarnings("unused")
public static class ForkJoinTaskAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Scope enter(
        @Advice.This ForkJoinTask<?> task,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
        // 这里用到了 OpenTelemetry 的 Context 用于帮助我们在方法切面的上下文中传递参数
        Context parentContext = Context.current();
        if (task instanceof Mailbox) {
            Mailbox mailbox = (Mailbox) task;
            String id = mailbox.dispatcher().id();
            parentContext = parentContext.with(DispatcherMetrics.DISPATCHER_ID, id);
            parentContext =
                parentContext.with(DispatcherMetrics.START_NANOS, System.nanoTime());
            context = parentContext;
        }
        scope = context.makeCurrent();
        return scope;
    }

    @Advice.OnMethodExit
    public static void exit(
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
        String dispatcherId = context.get(DispatcherMetrics.DISPATCHER_ID);
        Long startNanos = context.get(DispatcherMetrics.START_NANOS);
        Long duration =
            System.nanoTime()
                - Optional.ofNullable(startNanos).orElse(System.nanoTime() - 1);
        DispatcherMetrics.recordProcessingDuration(duration, dispatcherId);
        if (scope == null) {
            return;
        }
        scope.close();
    }
}
```


# 4. Priority Mailbox

为什么要增加这个 Topic，主要是应用有这个需求，以场景为例：

> 用户开发的应用需要支持消息的优先级处理. 在同一优先级下仍然按 FIFO 顺序。（例如 akka 内部里，Poison 内部消息一半是放在最后处理的，让消息处理完之后再停止：Graceful Shutdown）
> 
> 这个场景下，低优先级消息的 Queue Time 和高优先级的 Queue Time 不能同日而语，而直接使用平均值更无法体现每个消息的均值。
> 
> 例如：高优先级平均 1ms, 低优先级消息平均 100ms, 两者的平均值是 50ms, 对用户而言, 无法观测到高优先级在内部的延迟（1ms），而错估了应用性能
> 
> 当然，这个问题可以通过链路追踪解决，但这不是这个话题内应该提及的。

## 4.1 思路

有了 Queue Time 的基础，以及 Akka 内部中 Priority Mailbox 的处理方式，那么 Priority Mailbox 中 Priority 的获取也是简单明了的：**通过在 WrapperMessage 中增加属性即可**

实际实现的时候也遇到了一个问题，那就是 Akka Typed 里面，Actor 的 Dispatcher 指定的问题，最终通过排查 Akka 的代码，定位到了一个可以说是 Akka 配置加载上的 BUG，详细的上下文请看此 ISSUE：https://github.com/akka/akka/issues/31862

## 4.2 实现


### 4.2.1 获取 PriorityGenerator

PriorityGenerator 是 akka 的一个抽象类，用于在 Mailbox 里面是实现优先级，其中有个待用户实现的 gen 方法用于生成优先级。

假设用户的优先级基于 PriorityGenerator 实现，那么可以基于此类获取优先级 

```java
public class PriorityMailboxInstrumentation implements TypeInstrumentation {
    @Override
    public ElementMatcher<TypeDescription> typeMatcher() {
        // 拦截 akka dispatcher
        return ElementMatchers.named("akka.dispatch.Dispatcher");
    }

    @Override
    public void transform(TypeTransformer typeTransformer) {
        // 拦截 mailbox，主要是要取 mailboxType 的内容
        typeTransformer.applyAdviceToMethod(
                ElementMatchers.named("createMailbox")
                        .and(
                                ElementMatchers.takesArgument(
                                        0, ElementMatchers.named("akka.actor.Cell")))
                        .and(
                                ElementMatchers.takesArgument(
                                        1, ElementMatchers.named("akka.dispatch.MailboxType"))),
                this.getClass().getName() + "$MailboxFactoryAdvice");
    }

    @SuppressWarnings("unused")
    public static class MailboxFactoryAdvice {

        /**
         * 拦截 Mailbox Type，获取 Mailbox 的类型，如果是优先级的实现，那么拿到内部的 PriorityGenerator
         * @param mailboxType
         * @param mailbox
         */
        @Advice.OnMethodExit
        public static void onMessage(
                @Advice.Argument(1) MailboxType mailboxType, @Advice.Return Mailbox mailbox) {
            if (mailboxType instanceof UnboundedStablePriorityMailbox) {
                Comparator<Envelope> cmp = ((UnboundedStablePriorityMailbox) mailboxType).cmp();
                DispatcherHelper.registerMailbox(mailbox, cmp);
            } else if (mailboxType instanceof BoundedPriorityMailbox) {
                Comparator<Envelope> cmp = ((BoundedPriorityMailbox) mailboxType).cmp();
                DispatcherHelper.registerMailbox(mailbox, cmp);
            } else if (mailboxType instanceof UnboundedPriorityMailbox) {
                Comparator<Envelope> cmp = ((UnboundedPriorityMailbox) mailboxType).cmp();
                DispatcherHelper.registerMailbox(mailbox, cmp);
            }
        }
    }
}
```
## 4.2.2 消息中附加优先级

修改 EnqueueAdvice 的方法，附加优先级信息

```java

    @SuppressWarnings("unused")
    public static class EnqueueAdvice {

        @Advice.OnMethodEnter
        public static void onMessage(
                @Advice.Argument(0) ActorRef actorRef,
                @Advice.Argument(value = 1, readOnly = false) Envelope envelope,
                @Advice.This Mailbox thiz) {
            if (envelope == null || envelope.message() == null) {
                return;
            }
            if (thiz.actor() == null || thiz.dispatcher() == null) {
                return;
            }
            Object message = envelope.message();
            String id = thiz.dispatcher().id();
            long start = System.nanoTime();
            // 这里用之前创建 Mailbox 时，绑定上去的 Comparator, 也就是 PriorityMailbox
            PriorityGenerator mailboxComparator = DispatcherHelper.getMailboxComparator(thiz);
            WrapperMessage newMessage = new WrapperMessage(message, id, start);
            if (mailboxComparator != null) {
                newMessage.setPriority(Optional.of(mailboxComparator.gen(message)));
            }
            envelope = envelope.copy(newMessage, envelope.sender());
        }
    }
```

## 4.2.3 重构 dequeue 实现

优先级队列需要重构一下 dequeue 的实现，不能直接用 Mailbox 的 Dequeue

```java
public class MessageQueueInstrumentation implements TypeInstrumentation {
    @Override
    public ElementMatcher<TypeDescription> typeMatcher() {
        // 拦截 Mailbox 内部的 MQ
        return AgentElementMatchers.hasSuperType(
            ElementMatchers.named("akka.dispatch.MessageQueue"));
    }

    @Override
    public void transform(TypeTransformer typeTransformer) {
        typeTransformer.applyAdviceToMethod(
            ElementMatchers.named("dequeue"), this.getClass().getName() + "$DequeueAdvice");
    }

    /**
     * 切面方法基本一样，主要在 Metrics 层面做文章就好了
     */
    @SuppressWarnings("unused")
    public static class DequeueAdvice {

        @Advice.OnMethodExit
        public static void exit(@Advice.Return(readOnly = false) Envelope envelope) {
            if (envelope == null || envelope.message() == null) {
                return;
            }
            Object message = envelope.message();
            if (message instanceof WrapperMessage) {
                WrapperMessage wrapperMessage = (WrapperMessage) message;
                Object origin = wrapperMessage.getOrigin();
                // 在这里去判断 wrapperMessage 有没有 priority 即可。
                DurationMetrics.recordQueueDuration(wrapperMessage);
                envelope = envelope.copy(origin, envelope.sender());
            }
        }
    }
}

```
