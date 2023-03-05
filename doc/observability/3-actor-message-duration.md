# 1. 思路

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

# 2. 实现


## 2.1 On Queue Time 指标采集

Akka 的 Mailbox 本质上就是一个 Queue，Akka 在这基础上实现了 Runnable、ForkJoinTask 接口，让其可执行消息，并有相应的

Actor 作为运行时。因此 Akka Mailbox 的 Queue Time 就是 Java Queue 的 Queue time。

思路也比较简单：在 Queue 的 `enqueue()` 调用之前改变 Message，附加 Start Time，Attribute 等属性，在 `dequeue()` 时记录指标并还原 Message。

## 2.2 Processing Time 指标采集

上面我们提到 Mailbox 实现了 Runnable 和 ForkJoinTask 接口，前者是 `ThreadPoolExecutor` 内 Task 的定义，后者是 `ForkJoinPool` 中

Task 的定义，而 Akka 的 ForkJoinTask 的 `exec()` 方法最终调用了 `run()` 使 Mailbox 同时兼容两类线程池。

因此 Processing 采集的思路也很简单：记录 `run()` 的执行时间。