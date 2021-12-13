package com.iquantex.phoenix.typedactor.guide.adapter;

import com.iquantex.phoenix.typedactor.guide.adapter.Backend.BackendActor;
import com.iquantex.phoenix.typedactor.guide.adapter.Backend.Request;
import com.iquantex.phoenix.typedactor.guide.adapter.Frontend.Command;
import com.iquantex.phoenix.typedactor.guide.adapter.Frontend.Translate;
import com.iquantex.phoenix.typedactor.guide.adapter.Frontend.Translator;

import com.typesafe.config.ConfigFactory;
import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.javadsl.AskPattern;
import akka.actor.typed.javadsl.Behaviors;
import akka.japi.function.Function;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

/** @author AndyChen */
public class AdapterTest {

    /**
     * 消息适配器的测试用例，基于 Adapter Response，使用了 {@link
     * akka.actor.typed.javadsl.ActorContext#messageAdapter(Class, Function)}
     *
     * <p>1. 向 {@link Translator} 发送 {@link Backend.StartTranslationJob} 消息
     *
     * <p>2. {@link Translator} 向 {@link BackendActor} 发送 {@link Backend.StartTranslationJob} 消息
     *
     * <p>3. {@link BackendActor} 处理任务时，会发送 {@link Backend.JobStarted},{@link
     * Backend.JobProgress},{@link Backend.JobCompleted} 三个包装的 {@link Backend.Response} 消息
     *
     * <p>4. {@link Translator} 中的 {@link
     * akka.actor.typed.javadsl.ActorContext#messageAdapter(Class, Function)}将接收到的 {@link
     * Backend.Response} 请求转换为自己可以接收的消息协议 {@link
     * com.iquantex.phoenix.typedactor.guide.adapter.Frontend.WrappedBackendResponse}
     */
    @Test
    public void adapterResponse_case() throws InterruptedException {
        ActorSystem<Object> actorSystem =
                ActorSystem.create(
                        Behaviors.setup(
                                ctx -> {
                                    ActorRef<Request> backend =
                                            ctx.spawn(BackendActor.create(), "backend");
                                    ActorRef<Command> translator =
                                            ctx.spawn(Translator.create(backend), "translator");
                                    URI uri = new URI("test");
                                    CompletionStage<URI> ask =
                                            AskPattern.ask(
                                                    translator,
                                                    replyTo -> new Translate(uri, replyTo),
                                                    Duration.ofMillis(500),
                                                    ctx.getSystem().scheduler());

                                    URI res =
                                            ask.toCompletableFuture()
                                                    .get(500, TimeUnit.MILLISECONDS);
                                    ctx.getLog().info("ask complete,uri = {}", res);

                                    return Behaviors.empty();
                                }),
                        "test",
                        ConfigFactory.load("reference-simple.conf"));
        Thread.sleep(600);
    }
}
