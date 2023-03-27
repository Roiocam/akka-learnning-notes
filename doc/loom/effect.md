# 动机

在简单阅读 JEP-425 后，看起来虚拟线程能够优化基于 EventSouring + akka cluster sharding 的应用，此类应用中对 EventStore 的 append 操作适合用虚拟线程来解耦 IO ，
以此实现更高的吞吐量（释放线程，让其他 Actor 能够继续处理）

# 研究

在深入探索 JEP-425 之后，有一些新的想法：

- JEP 模型对传统服务型应用的变革较大，主要是 Tomcat 这类
- 虚拟线程（Fiber）并不是革命性的产物，如果你的应用已经是基于 akka，那么变革很少
- 虚拟线程用和处理器相同功能的核心数量来实现`Mechanical sympathy`
- 虚拟线程的目标是支持大量的线程（例如服务型应用）并在阻塞时使用 OS 级别的异步卸载虚拟线程，直到该阻塞完成时挂载到实际的处理器核心中执行。

为什么说 Loom 对 akka 的变革较少呢。首先 Loom 不会徒增处理器处理能力，只是增加程序对 CPU 的利用效率，例如阻塞模式下由于利特尔法则导致吞吐量瓶颈，Loom 可以避免这个瓶颈。其次 akka 的 actor 模式下，只要设计合理（参考 akka-persistence），那么 actor 基本上不会阻塞，那么 Loom 也就没有效果。最后，Loom 对于阻塞的虚拟线程，仅会在非 Pinned 的时候卸载，对于 OS 级别的阻塞（例如文件 IO 这种），还是会阻塞线程，不过 Loom 会通过增加平台线程的机制来补偿这个阻塞；Loom 和 akka 处理阻塞的理念是非常类似的。

此外还有一些其他问题：

- JEP-425 中提到，虚拟线程目前对于 pinning 虚拟线程到平台线程的阻塞问题，比较棘手。
- Loom 的线程亲和性问题：https://www.morling.dev/blog/loom-and-thread-fairness/，当 Loom 执行 CPU 密集型时应用时，Loom 会失效并阻塞其他大量的虚拟线程
- 如果用 Loom 实现 Actor 模型，那么 Actor 会阻塞虚拟线程在阻塞阶段被卸载而无法响应其他消息（例如 systemMessage），这一点 akka-persistence 用 FSM 规避了此问题：https://discuss.lightbend.com/t/on-the-positive-impacts-of-project-loom-and-akka/8123/14

总的来说，Loom 对于大部分不熟悉 Fiber 的开发者（例如我）好像是个很高级的玩意，而且网络上铺天盖地的文章感觉 Java 会因此欣欣向荣，发生变革，但深入之后发现 Loom 只是现代处理器硬件提升下，避免利特尔法则的一个方案（而避免利特尔法则的方式有很多种）

但 Loom 也不完全没有优势：

- Loom 下，_thread-pre-request_ 风格可以沿用，debugger、profiler、故障排查更容易，能复用之前的经验，_thread-pre-request_ 也是 Java 平台推荐的风格，毕竟响应式框架下，debugger和故障排查让人头疼。
- 成本极低：对开发者而言，不用熟悉和学习新的理念，长远来看，Loom 能提高 Java 社区的竞争力

最后，对于不熟悉 Loom 的用户，在 JEP-425 中特别提到一个提示：`虚拟线程不需要池化，虚拟线程的成本极低`，所以，在迁移原有应用到 Loom 时，不要用虚拟线程重写你的 `ThreadPoolExecutor` 了.