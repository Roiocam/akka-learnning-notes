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


## 3.2 Processing Time


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

