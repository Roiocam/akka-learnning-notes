# 性能优化指南

Akka Persistence 模块本身的性能足够好，因此数据库的写入都是异步的，可以及时释放掉 CPU 的资源给其他 Actor.

但是在使用过程中常常遇到一些 StashOverflow、CircuitBreaker 的错误, 这些错误通常都是不正确使用的结果.

本文从三个方面的介绍提供性能优化的思路：

- 相关的性能参数有哪些
- 一些错误的原因
- Tips

## 1. 识别到的性能优化参数

- 当 Actor 读取快照失败时，可以选择从事件中溯源状态
    - 参数：`akka.persistence.snapshot-store-plugin-fallback.snapshot-is-optional` 默认是 false
- 当数据库 OverLoad 时，查询快照&事件的时间可能会比较高, 可以通过调大等待查询的时间以及调小查询窗口的时间
    - 等待查询时间：`akka.persistence.journal.plugin.recovery-event-timeout` 默认是 30s
    - 开启窗口查询事件: `akka.persistence.journal-plugin-fallback.replay-filter.mode` 默认是 repair-by-discard-old, 也就是开启
    - 调节查询窗口大小:  `akka.persistence.journal-plugin-fallback.replay-filter.window-size` 默认是 100
- 当数据库过载时，持久化的请求需要的时间比较久，此时 Actor 会暂存那些 Persist 请求，一种方案是做攒批，另一种是调节每个 Actor 内部的暂存大小
    - 持久化 Actor 暂存溢出的策略: `akka.persistence.typed.stash-overflow-strategy` 默认是丢弃
    - 持久化 Actor 暂存大小: `akka.persistence.typed.stash-capacity` 默认是 4086


```conf
akka.persistence.typed {
    # 持久化暂存溢出策略
    stash-overflow-strategy = drop / fail
    # 持久化暂存容量
    stash-capacity =  4096 
}
akka.persistence.journal.plugin {
     # 事件溯源恢复的时间
    recovery-event-timeout =  30s
}
akka.persistence {
   # 快照插件容错
   snapshot-store-plugin-fallback {
     snapshot-is-optional = false
   }
}

```

## 2. 一些错误的原因

### 数据库瓶颈

如果是在溯源恢复 Actor 期间时数据库 OverLoad，Akka 在内部做了类似于断路的处理，在数据库过载时

- 查询快照：在`recovery-event-timeout`后如果没有得到数据库响应，则取消该 Actor 的溯源请求，并抛出异常
- 查询事件：如果开启窗口查询，则在`recovery-event-timeout`的窗口时间内，没有得到指定数量大小的数据库回复，则取消该 Actor 的溯源请求，并抛出异常


### Stash 瓶颈

从持久化 Actor 运行时图可以看到， 其运行时依赖两个内部的 Stash，分别是处理内部的 Stash 和用户的 Stash。

- 用户 Stash 目前只有用户主动调用 `Effect.stash` 才会存储.
- 内部 Stash 当 Actor 处理非处理状态时, 暂存. 如重放事件时接收到了命令消息

有两种情况会引发 Stash Full 的问题

- 事件溯源恢复阶段耗时太久：此时不断有流量进入，会导致丢弃掉 capacity 之后的消息。这种情况应该在Actor溯源前 Stash 住，让流量只在溯源之后进来
- 事件持久化耗时太久：当数据库有显著瓶颈，或者 Actor 本身流量过高时，在持久化的过程中瞬间击垮 Stash。这种情况应该让 Actor 在做持久化的时候将消息 Stash 住


## 3. Tips

从上述的描述以及整个架构不难看出，StashOverflow 并非整体系统的负载能力不够，而是没有相应的背压机制。

本文不讨论背压的实现，而是在流量的入口处攒批，从降低流量的角度解决 StashOverflow 的问题。