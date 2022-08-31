# 监控指南

监控方面，主要有这几部分：

1. Akka 自身的 Lightbend Telemetry / Akka Insight (同一个东西)
2. 第三方扩展：kamon-io/kanela, ScalaConsultants/mesmer
3. Java Instrumentation API + 字节码增强（ByteBuddy）
4. 用户层监控

这里不扩展 1 、2


## 实现部分

我更愿意将 3、4 作为同一个 topic 来实现，我也是这么做的，用户层代码可控，快速出活，当需要深入 Akka 的性能指标时，考虑通过阅读 Akka 源代码的方式实现。

对于用户层监控的实现，JMX 是一种方式，MicroMeter 是一种方式，我个人更喜欢 OpenTelemetry.(这里不讲 OpenTelemetry)

### 1. 用户层代码埋点

在用户层代码中预留 interceptor/listener 接口和埋点, 通过 OpenTelemetry 中使用 ByteBuddy 增强字节码，在实现上为用户注入监控逻辑的实现. 

如：一个简单的拦截器实现, 注入层的逻辑则通过 OpenTelemetry Extension 的方式编写, 然后在代码层注入; 或者用户直接创建并使用此 Interceptor.

```java
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongUpDownCounter;
import io.opentelemetry.api.metrics.Meter;

public class MyActorTelemetryInterceptor implements Interceptor {
    private LongUpDownCounter runningCount;

    MyActorTelemetryInterceptor(Meter meter){
        this.runningCount = meter.upDownCounterBuilder("running actor")
                .build();
    }
    
    @Override
    public void start(){
        runningCount.add(1L,Attributes.builder().put("key","value").build());
    }
    
    @Override
    public void stop(){
        runningCount.add(-1L,Attributes.builder().put("key","value").build());
    }
    
}
```

### 2. Akka 框架代码埋点

Akka 自身也类似于基于上述的方式实现 `Lightbend Telemetry`, 但可能大部分的埋点需要阅读源码找一找... 以 Akka Persistence 指标为例:

先从 Lightbend 网站上下载 Grafana 的面板：https://developer.lightbend.com/docs/telemetry/current//visualizations/grafana.html

然后根据持久化的指标: `Recovery permit metrics` 得知 Akka 有一些指标埋点在了请求溯源 Actor 的许可上.

阅读 Akka 源代码中一个持久化 Actor 请求溯源许可的代码. 可以发现有一段没有任何行为的模版方法.

[monitor_1](/img.monitor_1.png)

[monitor_2](/img.monitor_2.png)

这里的代码也就是 Akka 指标埋点的代码, 因此可以只增强这个方法, 注入一些指标埋点的逻辑.

当然，除了这种方式 Akka 内部也有其他的埋点逻辑、或者更难或者更简单。以简单的为例子: Akka Projection 直接提供了收集指标的 Telemetry.

![img.png](/img/monitor_3.png)