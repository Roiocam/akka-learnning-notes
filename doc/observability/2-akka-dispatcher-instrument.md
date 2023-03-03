# 1. 思路

由 akka 源码可知，Dispatcher 就是一个丰富了 Actor 能力的 Executor 框架，那么其监控方案本质上也就是 ThreadPool 的监控方案，

因为 ActorSystem 内置了多个 Dispatcher，所以在需要 Instrument 时必须拿到这个 DispatcherID 才能才监控时精细化性能分析。

# 2. 注入类选择

首先看一下 akka dispatcher 的源代码

![dispatcher.png](/img/dispatcher.png)

在 ExecutorService 的定义中, 使用了两个 Factory：

1. **ExecutorServiceFactory**： 顾名思义用来创建线程池
2. **ExecutorServiceFactoryProvider**：看起来是上面工厂类的工厂类

在上述源码里可以看到 ExecutorServiceFactory 的 `createExecutorService()` 并不提供任何参数, 假设我们要通过这个类确定是哪个线程池还是比较困难的。

目光转移到 ExecutorServiceFactoryProvider 的 `createExecutorServiceFactory(id, threadFactory)` 这个方法提供了两个参数，前者是我们关心的 DispatcherId。

至此我们拿到了两个关键路径：

- `ExecutorServiceFactory.createExecutorService()`: 返回参数是一个 ThreadPool，通过 ThreadPool 方法可以拿到性能指标
- `ExecutorServiceFactoryProvider.createExecutorServiceFactory(id, threadFactory)`：返回参数是前者，提供 Dispatcher 的 ID

因为上面两个类有前后关系，并且后者返回的是前者这个对象，那么通过 equals 就能将两者映射起来。


# 3. 代码编写

编写时以原代码执行的先后顺序编写

### 1. ExecutorServiceFactoryProvider 的代码注入 

```java
public class ESFactoryProviderInstrumentation implements TypeInstrumentation {
    @Override
    public ElementMatcher<TypeDescription> typeMatcher() {
        // 匹配实现了 ExecutorServiceFactoryProvider 接口的所有实现类.
        return ElementMatchers.hasSuperType(ElementMatchers.named("akka.dispatch.ExecutorServiceFactoryProvider"));
    }

    @Override
    public void transform(TypeTransformer typeTransformer) {
        // 用 ExecutorServiceFactoryAdvice 注入其 createExecutorServiceFactory 方法
        typeTransformer.applyAdviceToMethod(ElementMatchers.named("createExecutorServiceFactory"), this.getClass().getName() + "$ExecutorServiceFactoryAdvice");
    }

    @SuppressWarnings("unused")
    public static class ExecutorServiceFactoryAdvice {

        /**
         * 注入到方法结束后, 取返回对象和第一个参数
         * @param serviceFactory 返回对象
         * @param id 第一个参数
         */
        @Advice.OnMethodExit
        public static void onExit(@Advice.Return() ExecutorServiceFactory serviceFactory, @Advice.Argument(0) String id) {
            // 用帮助类注入指标采集, 传入关键参数, 暂定义一个空方法即可.
            DispatcherMetrics.registerFactory(id, serviceFactory);
        }
    }
}
```

### 2. ExecutorServiceFactory 的代码注入

ExecutorServiceFactory 的代码注入和 ExecutorServiceFactoryProvider 基本一样，不过传入参数有区别 

```java
public class DispatcherInstrumentation implements TypeInstrumentation {
    @Override
    public ElementMatcher<TypeDescription> typeMatcher() {
        // 匹配所有实现了 ExecutorServiceFactory 接口的实现类 
        return ElementMatchers.hasSuperType(ElementMatchers.named("akka.dispatch.ExecutorServiceFactory"));
    }

    @Override
    public void transform(TypeTransformer typeTransformer) {
        // 用 ThreadPoolAdvice 注入该类的 createExecutorService 方法 
        typeTransformer.applyAdviceToMethod(ElementMatchers.named("createExecutorService"), this.getClass().getName() + "$ThreadPoolAdvice");
    }

    @SuppressWarnings("unused")
    public static class ThreadPoolAdvice {

        /**
         * 在方法结束后注入逻辑, 取返回结果和自身对象
         * @param executorService 返回结果
         * @param thiz 自身对象
         */
        @Advice.OnMethodExit
        public static void onExit(@Advice.Return() ExecutorService executorService, @Advice.This Object thiz) {
            // 同样用帮助类, 注入关键参数
            DispatcherMetrics.registerThreadPool(thiz, executorService);
        }
    }
}
```

### 3. 指标采集 DispatcherMetrics 编写

从上面代码基本可以猜出来了，思路就是用 `ExecutorServiceFactoryProvider` 返回的结果对象和 `ExecutorServiceFactory` 自身对象来做  ID 的匹配.

用 `ExecutorServiceFactory` 的返回结果 ThreadPool 对象来采集指标.


```java
public class DispatcherMetrics {

    // 全局指标
    private static final Meter meter = GlobalOpenTelemetry.get().getMeter("akka-telemetry");

    // es factory 的 map. Key = ID, Value = Factory
    private static final Map<String, Object> factoryMap = new ConcurrentHashMap<>();
    // thread pool map. Key = Factory
    private static final Map<Object, DispatcherMetrics> threadPoolMap = new ConcurrentHashMap<>();
    // 关于 ID 的指标
    private final Attributes attributes;
    // 针对不同的线程池实现的变量区分, 这里可以进一步优化.
    private final Optional<ForkJoinExecutorConfigurator.AkkaForkJoinPool> forkJoinPool;
    private final Optional<ThreadPoolExecutor> executor;
    // 利用率指标
    private ObservableDoubleGauge utilization;
    // 队列大小指标
    private ObservableLongGauge queueSize;
    // FactoryMap 的合并 Function
    private static final FactoryMerge FACTORY_MERGE = new FactoryMerge();
    // ThreadPoolMap 的合并 Function，主要是避免重复注入监控对象
    private static final MetricsMerge METRICS_MERGE = new MetricsMerge();

    public DispatcherMetrics(String id, ForkJoinExecutorConfigurator.AkkaForkJoinPool forkJoinPool) {
        this.forkJoinPool = Optional.of(forkJoinPool);
        this.executor = Optional.empty();
        this.attributes = Attributes.of(AttributeKey.stringKey("dispatcher_id"), id);
    }

    public DispatcherMetrics(String id, ThreadPoolExecutor executor) {
        this.attributes = Attributes.of(AttributeKey.stringKey("dispatcher_id"), id);
        this.executor = Optional.of(executor);
        this.forkJoinPool = Optional.empty();
    }

    /**
     * Instrument 中的模板方法, 将 Provider 传入的参数 ID，返回对象 Factory 放入 Map，如果存在相同的，则合并。
     * @param id
     * @param object
     */
    public static void registerFactory(String id, Object object) {
        factoryMap.merge(id, object, FACTORY_MERGE);
    }

    /**
     *  Instrument 中的模板方法, 将 Factory 对象自身以及返回结果传进来
     * @param factory
     * @param threadPool
     */
    public static void registerThreadPool(Object factory, Object threadPool) {
        String id = null;
        // 找到当前 Factory 的 DispatcherID
        for (Map.Entry<String, Object> entry : factoryMap.entrySet()) {
            if (entry.getValue().equals(factory)) {
                id = entry.getKey();
                break;
            }
        }
        // 如果不存在 ID，那么不记录
        if (id == null) {
            System.out.println("can not find specific factory: " + factory);
            return;
        }
        // 根据不同的对象拿到不同的 ThreadPool
        DispatcherMetrics metrics = null;
        if (threadPool instanceof ForkJoinExecutorConfigurator.AkkaForkJoinPool) {
            ForkJoinExecutorConfigurator.AkkaForkJoinPool pool =
                    (ForkJoinExecutorConfigurator.AkkaForkJoinPool) threadPool;
            metrics = new DispatcherMetrics(id, pool);
        } else if (threadPool instanceof ThreadPoolExecutor) {
            ThreadPoolExecutor executor = (ThreadPoolExecutor) threadPool;
            metrics = new DispatcherMetrics(id, executor);
        }
        if (metrics == null) {
            return;
        }
        // 一个 Dispatcher 只能有一个 Metrics 采集，这里去重
        threadPoolMap.merge(factory, metrics, METRICS_MERGE);
        // 为这个 metrics 注入采集监听器
        metrics.registerMetrics();
    }

    /**
     * 关闭方法， 合并时使用. 如果存在覆盖的情况，取消前者的监控监听器。
     */
    public void close() {
        if (utilization != null) {
            utilization.close();
        }
        if (queueSize != null) {
            queueSize.close();
        }
    }

    /**
     * 注册指标的监听器
     */
    private void registerMetrics() {
        if (forkJoinPool.isPresent()) {
            ForkJoinExecutorConfigurator.AkkaForkJoinPool pool = forkJoinPool.get();
            // 使用 OpenTelemetry 注入监听器
            utilization =
                    meter.gaugeBuilder("akka.dispatcher.utilization")
                            .setDescription("线程池利用率")
                            .setUnit("%")
                            .buildWithCallback(
                                    observableDoubleMeasurement -> {
                                        // 拿到活跃线程和线程池大小, 计算利用率
                                        int activeThreadCount = pool.getActiveThreadCount();
                                        int poolSize = pool.getPoolSize();
                                        double utilization =
                                                ((double) activeThreadCount / poolSize) * 100.0;
                                        // 将利用率记录起来，附加属性（ID）
                                        observableDoubleMeasurement.record(utilization, attributes);
                                    });
            queueSize =
                    meter.gaugeBuilder("akka.dispatcher.queue-size")
                            .ofLongs()
                            .setDescription("线程池等待队列大小")
                            .setUnit("count")
                            .buildWithCallback(
                                    ob -> {
                                        int queuedSubmissionCount = pool.getQueuedSubmissionCount();
                                        ob.record(queuedSubmissionCount, attributes);
                                    });
        }
        if (executor.isPresent()) {

            ThreadPoolExecutor pool = executor.get();
            utilization =
                    meter.gaugeBuilder("akka.dispatcher.utilization")
                            .setDescription("线程池利用率")
                            .setUnit("%")
                            .buildWithCallback(
                                    observableDoubleMeasurement -> {
                                        int activeThreadCount = pool.getActiveCount();
                                        int poolSize = pool.getPoolSize();
                                        double utilization =
                                                ((double) activeThreadCount / poolSize) * 100.0;
                                        observableDoubleMeasurement.record(utilization, attributes);
                                    });
            queueSize =
                    meter.gaugeBuilder("akka.dispatcher.queue-size")
                            .ofLongs()
                            .setDescription("线程池等待队列大小")
                            .setUnit("count")
                            .buildWithCallback(
                                    ob -> {
                                        int queuedSubmissionCount = pool.getQueue().size();
                                        ob.record(queuedSubmissionCount, attributes);
                                    });
        }
    }

    /**
     * 合并方法. 如果存在相同的, 取消旧的，使用新的
     */
    public static class FactoryMerge implements BiFunction<Object, Object, Object> {

        @Override
        public Object apply(Object oldest, Object newest) {
            DispatcherMetrics metrics = threadPoolMap.remove(oldest);
            if (metrics != null) {
                metrics.close();
            }
            return newest;
        }
    }

    /**
     * 合并方法. 如果存在相同的, 取消旧的，使用新的
     */
    public static class MetricsMerge
            implements BiFunction<DispatcherMetrics, DispatcherMetrics, DispatcherMetrics> {

        @Override
        public DispatcherMetrics apply(DispatcherMetrics oldest, DispatcherMetrics newest) {
            oldest.close();
            return newest;
        }
    }
}
```

### 4. Module 编写

Instrumentation, Helper Class 都编写好了，最后一步是将整体制成 OpenTelemetry 的一个模块(帮助识别，Class 注入)

```java
@SuppressWarnings("unused")
@AutoService(InstrumentationModule.class) // Google AutoService 快速实现 SPI 
public class AkkaDispatcherModule extends InstrumentationModule {

    public AkkaDispatcherModule() {
        super("akka.telemetry.dispatcher", "akka.telemetry.dispatcher-1.0.0");
        System.out.println(String.format("loaded %s", this.getClass().getSimpleName()));
    }

    /**
     * 这个模版包含了两个 Instrumentation. 
     * @return
     */
    @Override
    public List<TypeInstrumentation> typeInstrumentations() {
        return Arrays.asList(new DispatcherInstrumentation(),new ESFactoryProviderInstrumentation());
    }

    /** 当类加载器加载了 akka.dispatch.Dispatcher 时, 才会执行这个 Module，开启注入 */
    @Override
    public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
        return AgentElementMatchers.hasClassesNamed(
                "akka.dispatch.Dispatcher");
    }

    /** 需要被加载上来的关联类. */
    @Override
    public List<String> getAdditionalHelperClassNames() {
        return Arrays.asList(
                "com.iquantex.phoenix.lite.telemetry.instrumentation.dispatcher.DispatcherMetrics$FactoryMerge",
                "com.iquantex.phoenix.lite.telemetry.instrumentation.dispatcher.DispatcherMetrics$MetricsMerge",
                "com.iquantex.phoenix.lite.telemetry.instrumentation.dispatcher.DispatcherMetrics");
    }
}
```