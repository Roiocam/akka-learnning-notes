# 介绍

OpenTelemetry 是云原生基金的一个统一可观测性三大支柱的框架，制定了统一的协议以在不同的语言、指标收集平台（Prometheus、Jagger 等）使用。

关于 OTEL 不过多介绍，具体参考官网。

对于 Java 应用而言，任何代码都可以基于 Java Instrumentation API 也就是公众号等常说的插桩技术，Instrumentation API 开发的目的也是用来做一些

类似于日志、指标埋点这种和业务十分解耦的事情，OTEL 对于 Java Instrumentation API 的运用主要是基于 ByteBuddy（一个在 ASM 之上的框架），除了

OTEL 之外，国内十分出名的开源链路追踪方案 Sky-Walking 也是基于 ByteBuddy 做的 Agent。

OTEL 官方提供了一个 java-agent 的 jar，避免让用户咏 ByteBuddy 来实现 “Helper” 类的在不同类加载器的热加载（否则无法识别类），简而言之就是提供了一套 API

用户只需要关注埋点逻辑即可，下面就来说说 OTEL java-agent 的扩展（extension）如何编写。

# 编写指南

## 1. 引入 Extension API

```xml
<!-- 监控 API -->
<dependency>
    <groupId>io.opentelemetry.javaagent</groupId>
    <artifactId>opentelemetry-javaagent-extension-api</artifactId>
</dependency>
```

## 2. 编写  InstrumentationModule

InstrumentationModule 是 OpenTelemetry 中的模块概念, 一般是多个 TypeInstrumentation 的集合.

TypeInstrumentation 则是描述如何增强一个类, 以及相关的帮助类...


```java

import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers;
import net.bytebuddy.matcher.ElementMatcher;

import java.util.List;

/**
 * 每个 Agent 的核心模块.
 * {@link InstrumentationModule} 用于描述一组 {@link TypeInstrumentation} 一起应用以正确检测目标.
 * 这些 `TypeInstrumentation` 共享相应的帮助类(HelperClasses), 运行时检查, 相应的类加载器.
 * OpenTelemetry javaagent 用 Java SPI 机制查找所有 `InstrumentationModule`.
 *
 * @author AndyChen
 */
public abstract class AbstractModule extends InstrumentationModule {
    /**
     * 示例: phoenix-lite-telemetry, phoenix-lite-telemetry-1.0
     *
     * @param mainInstrumentationName        主要的 Instrumentation 名称. 应该和 Maven 的模块名称一样.
     * @param additionalInstrumentationNames 如果 Maven 模块有版本名，则需要额外的名称、例如提供版本化.
     */
    protected AbstractModule(String mainInstrumentationName, String... additionalInstrumentationNames) {
        super(mainInstrumentationName, additionalInstrumentationNames);
    }

    /**
     * 应用 Instrumentation 的顺序. 越小的优先级越高, 默认 0
     *
     * @return
     */
    @Override
    public int order() {
        return super.order();
    }

    /**
     * 帮助 OpenTelemetry javaagent 在类加载时, 将帮助的类注入到应用程序的 ClassLoader.
     *
     * @param className The name of the class that may or may not be a helper class.
     * @return
     */
    @Override
    public boolean isHelperClass(String className) {
        return super.isHelperClass(className);
    }

    /**
     * 手动添加帮助类.  OpenTelemetry javaagent 会注入到应用程序的 ClassLoader. 从而让代码织入运行生效.
     *
     * @return
     */
    @Override
    public List<String> getAdditionalHelperClassNames() {
        return super.getAdditionalHelperClassNames();
    }

    /**
     * 版本化 Agent 时使用, 判断是否应用此 `InstrumentationModule`.
     *
     * @return
     */
    @Override
    public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
        return AgentElementMatchers.hasClassesNamed("com.iquantex.xxxxx");
    }

    /**
     * `TypeInstrumentation` 实现集合, 如何代理一个 agent, 需要做什么.
     *
     * @return
     */
    public abstract List<TypeInstrumentation> typeInstrumentations();
}


```

## 3. 编写 JavaAgent 的代理逻辑

实现 TypeInstrumentation 的类核心逻辑是两件事情：

1. 描述代理哪个类、里面的哪个方法
2. 对于代理的这个方法，需要怎么增强.

```java

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.field.VirtualField;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;

/**
 * 描述如何改变类的类型.
 *
 * @author AndyChen
 */
public abstract class AbstractTypeInstrumentation implements TypeInstrumentation {

    /**
     * 声明需要被代理的类.
     *
     * @return
     */
    public abstract ElementMatcher<TypeDescription> typeMatcher();

    /**
     * 优化代理速度的方法. 上述的匹配中, 用名称匹配类很快, 但是检测实际的字节码（如实现、注视、方法）则是昂贵的操作.
     * 此方法返回的匹配器能让 `TypeInstrumentation` 检测不存在匹配的库时加速检测.
     *
     * @return
     */
    public abstract ElementMatcher<ClassLoader> classLoaderOptimization();

    /**
     * 定义代码转换的逻辑.
     * <pre>
     *     applyAdviceToMethod(ElementMatcher,String): 第一个参数是匹配的方法、第二个是Advice的类名. 允许应用所有匹配的方法.
     *     applyTransformer() 注入任意的 ByteBuddy 转换器.(不受 muzzle 安全检查限制和 helper 类检测, 谨慎使用)
     * </pre>
     */
    public abstract void transform(TypeTransformer transformer);

    /**
     * 匹配 `public void methodName(Long arg1,some.arguments.Clz arg2);
     * adviceClass 是故意设置成字符串连接, 防止对应类被加载到代理类的类加载器.
     *
     * @param transformer
     */
    void transformDemo(TypeTransformer transformer) {
        transformer.applyAdviceToMethod(
                ElementMatchers.isPublic()
                        .and(ElementMatchers.named("methodName"))
                        .and(ElementMatchers.takesArgument(0, Long.class))
                        .and(ElementMatchers.takesArgument(1, ElementMatchers.named("some.arguments.Clz")))
                        .and(ElementMatchers.returns(Void.TYPE)),
                this.getClass().getName() + "$AdviceClzDemo"
        );
    }

    /**
     * Advice 类示例.
     * <pre>
     *     1. 推荐设置为内部类, 因为其不是真正的类, 只是被注入代理类的逻辑. 为内部类时, 必须是静态.
     *     2. 只能包含静态方法, 不能包含字段, 只有方法内的逻辑才会被织入, 字段不会.
     *     3. 不能重用代码, 如果需要, 使用 Helper 类.
     *     4. 不应该包含 @Advice 注解方法之外的方法.
     *     5. 不应该包含外部类的任何内容(recorder, constant)
     * </pre>
     */
    public static class AdviceClzDemo {

        /**
         * 使用前缀为  otel 的局部变量存储 Advice 方法中的上下文,scope 和其他数据.
         *
         * @param request
         * @param context
         * @param scope
         */
        @Advice.OnMethodEnter(suppress = Throwable.class)
        public static void onEnter(@Advice.Argument(1) Object request,
                                   @Advice.Local("otelContext") Context context,
                                   @Advice.Local("otelScope") Scope scope) {
            // 如果检测 Java8 之前的库, 需要 Java8BytecodeBridge.currentContext(); 因为 JDK8 之前不支持接口的 default
            Context parentContext = Context.current();

            // if (!instrumenter().shouldStart(parentContext, request)) {
            //     return;
            // }
            //
            // context = instrumenter().start(parentContext, request);
            scope = context.makeCurrent();
        }

        /**
         * suppress 帮助捕获异常, 当Advice的逻辑中异常时,能够被 OpenTelemetry javaagent 定义的特殊 ExceptionHandler 捕获和处理。
         * 让程序能正确打印所有异常行为.
         */
        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
        public static void onExit(@Advice.Argument(1) Object request,
                                  @Advice.Return Object response,
                                  @Advice.Thrown Throwable exception,
                                  @Advice.Local("otelContext") Context context,
                                  @Advice.Local("otelScope") Scope scope) {
            if (scope == null) {
                return;
            }
            scope.close();
            // instrumenter().end(context, request, response, exception);
        }

        /**
         * 当需要在 instrumentation 类和被代理类之间建立联系时, 使用 {@link VirtualField}.
         * `VirtualField` 和 Map 接口类似, 此类必须接受类的引用作为参数, 必须在编译时知道这个类和字段类才能工作.
         * 因此不适合用在变量和方法参数.
         */
        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
        public static void associate() {
            VirtualField<Runnable, Context> virtualField = VirtualField.find(Runnable.class, Context.class);
        }
        /**
         * 不推荐使用 {@link Advice.Origin} 加上 {@link java.lang.reflect.Method} , 而是使用
         * `@Advice.Origin("#t") Class<?> declaringClass` 的方式.
         * 因为前者会调用 `Class.getMethod(...)`. 后者则使用常量池中获取引用.
         */
    }
}
```


## 使用方式

写完代理和注入逻辑之后，只需要打包成 Jar 即可，因为 OTEL Extension 也就是我们编写代理 Agent 可能在启动时（在 Main 方法之前启动）可能需要依赖（如果是 Spring Boot Jar 会制作

一个特别的 Jar，用 Spring 自己的类加载器加载依赖的 Jar，所以 Agent 方法可能找不到依赖），所以 Agent 打包时必须要引入相关依赖然后打包成 Fat Jar（用 Maven Shade Plugin）

下面的 Jar 给出了冗余的很多 relocations，自己规律改写即可。

```xml
<plugin>
<groupId>org.apache.maven.plugins</groupId>
<artifactId>maven-shade-plugin</artifactId>
<version>3.3.0</version>
<executions>
    <execution>
        <phase>package</phase>
        <goals>
            <goal>shade</goal>
        </goals>
        <configuration>
            <!-- 减少重复依赖对使用了 Shade 的 Library 模块可能有用, 但 Agent 模块不会被其他工程依赖, 因此无需创建精简的 Pom -->
            <createDependencyReducedPom>false</createDependencyReducedPom>
            <relocations>
                <relocation>
                    <pattern>org.slf4j</pattern>
                    <shadedPattern>io.opentelemetry.javaagent.slf4j</shadedPattern>
                </relocation>
                <!-- rewrite dependencies calling Logger.getLogger-->
                <relocation>
                    <pattern>java.util.logging.Logger</pattern>
                    <shadedPattern>io.opentelemetry.javaagent.bootstrap.PatchLogger</shadedPattern>
                </relocation>

                <!-- prevents conflict with library instrumentation-->
                <relocation>
                    <pattern>io.opentelemetry.instrumentation</pattern>
                    <shadedPattern>io.opentelemetry.javaagent.shaded.instrumentation</shadedPattern>
                </relocation>

                <!-- relocate(OpenTelemetry API)-->
                <relocation>
                    <pattern>io.opentelemetry.api</pattern>
                    <shadedPattern>io.opentelemetry.javaagent.shaded.io.opentelemetry.api</shadedPattern>
                </relocation>
                <relocation>
                    <pattern>io.opentelemetry.semconv</pattern>
                    <shadedPattern>io.opentelemetry.javaagent.shaded.io.opentelemetry.semconv</shadedPattern>
                </relocation>
                <relocation>
                    <pattern>io.opentelemetry.context</pattern>
                    <shadedPattern>io.opentelemetry.javaagent.shaded.io.opentelemetry.context</shadedPattern>
                </relocation>

                <!-- relocate(the OpenTelemetry extensions that are used by instrumentation modules)-->
                <!-- these extensions live in the AgentClassLoader, and are injected into the user's class-->
                loader
                <!-- by the instrumentation modules that use them-->
                <relocation>
                    <pattern>io.opentelemetry.extension.aws</pattern>
                    <shadedPattern>io.opentelemetry.javaagent.shaded.io.opentelemetry.extension.aws</shadedPattern>
                </relocation>
                <relocation>
                    <pattern>io.opentelemetry.extension.kotlin</pattern>
                    <shadedPattern>io.opentelemetry.javaagent.shaded.io.opentelemetry.extension.kotlin</shadedPattern>
                </relocation>

                <!-- this is for instrumentation of opentelemetry-api and opentelemetry-instrumentation-api-->
                <relocation>
                    <pattern>application.io.opentelemetry</pattern>
                    <shadedPattern>io.opentelemetry</shadedPattern>
                </relocation>
                <relocation>
                    <pattern>application.io.opentelemetry.instrumentation.api</pattern>
                    <shadedPattern>io.opentelemetry.instrumentation.api</shadedPattern>
                </relocation>

                <!-- this is for instrumentation on java.util.logging (since java.util.logging itself is shaded above)-->
                <relocation>
                    <pattern>application.java.util.logging</pattern>
                    <shadedPattern>java.util.logging</shadedPattern>
                </relocation>
            </relocations>
        </configuration>
    </execution>
</executions>
</plugin>
```

制成 Jar 包之后，只需要跟随 OTEL Java Agent 并作为其参数启动即可：

```SHELL
java -jar -javaagent:/opentelemetry/agent.jar -Dotel.javaagent.extensions=/agent/extension.jar app.jar
```

OTEL 参数 `otel.javaagent.extensions` 可传入目录，逗号分割的多个 extension jar 路径，单个 jar（在 1.16.0 我提 ISSUE 时是这几个方式），

最新文档更新了很多说明，这里不再赘述：https://opentelemetry.io/docs/instrumentation/java/automatic/agent-config/#extensions



