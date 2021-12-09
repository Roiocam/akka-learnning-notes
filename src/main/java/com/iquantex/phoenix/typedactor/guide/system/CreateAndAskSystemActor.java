package com.iquantex.phoenix.typedactor.guide.system;

import com.iquantex.phoenix.typedactor.guide.actor.DavidBehavior;
import com.iquantex.phoenix.typedactor.guide.actor.DavidBehavior.SayHello;
import com.iquantex.phoenix.typedactor.guide.protocol.HelloResponse;
import com.iquantex.phoenix.typedactor.guide.protocol.Message;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.RecipientRef;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.AskPattern;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.japi.function.Function;
import akka.japi.function.Function2;
import lombok.AllArgsConstructor;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeoutException;

/**
 * 该 Actor 演示了内部向其他Actor发起请求的例子,基于 {@link ActorContext#ask(Class, RecipientRef, Duration, Function,
 * Function2)} 代表内部向其他Actor发起ask.
 *
 * <p>1. 启动时请求 {@link #CreateAndAskSystemActor(ActorContext)}
 *
 * <p>2. 接受命令时请求 {@link #onAskChild(AskChild)}
 *
 * @author AndyChen
 */
public class CreateAndAskSystemActor extends AbstractBehavior<Message> {

    private ActorRef<Message> childRef;

    public CreateAndAskSystemActor(ActorContext<Message> context) {
        super(context);
        // 1. 创建子 Actor, 并监督其生命周期
        ActorRef<Message> davidOne = context.spawn(DavidBehavior.create(), "DavidOne");
        context.watch(davidOne);
        // 2. 在创建时发起请求,可以看作是Actor内部向其他Actor发起请求
        context.ask(
                Message.class,
                davidOne,
                Duration.ofMillis(100),
                relyTo -> new SayHello(relyTo),
                (response, throwable) -> {
                    HelloResponse helloResponse = (HelloResponse) response;
                    context.getLog()
                            .info(
                                    "GetResponse From {}, Response={}",
                                    helloResponse.getReplyTo().path(),
                                    helloResponse.getResponse());
                    // 处理后,传给自身
                    return response;
                });
        // 3. 或者保存 Child 引用，在接受事件时ask
        childRef = davidOne;
    }

    public static Behavior<Message> create() {
        return Behaviors.setup(ctx -> new CreateAndAskSystemActor(ctx));
    }

    @AllArgsConstructor
    public static class AskChild implements Message {}

    @Override
    public Receive<Message> createReceive() {
        return newReceiveBuilder()
                .onMessage(HelloResponse.class, this::onHelloResponse)
                .onMessage(AskChild.class, this::onAskChild)
                .build();
    }

    /**
     * 接受到 {@link AskChild} 消息后，向 {@link DavidBehavior} 发起了一次请求, 然后将结果回传给自身,这里用到了 {@link
     * ActorContext#pipeToSelf(CompletionStage, Function2)} 这个方法将 {@link
     * java.util.concurrent.Future} 结果封装后发给自身，可以是异步请求.
     *
     * <p>我觉得这个pipeToSelf能解决ReceiverActor阻塞的问题，但是Kafka这边支持Future的Poll吗？官方推荐这个pipeSelf用来请求数据库
     *
     * <p>能否在 {@link AskChild} 中添加一个被请求的 {@link ActorRef} 以及回复的 {@link ActorRef}, 这样此Actor
     * 仅做了ask中间人的作用,但是这样似乎没什么意义. 作为参考提一下
     *
     * @param msg
     * @return
     */
    public Behavior<Message> onAskChild(AskChild msg) {
        // 1. 第一种请求方式
        CompletionStage<Message> ask =
                AskPattern.ask(
                        childRef,
                        replyTo -> new SayHello(replyTo),
                        Duration.ofMillis(100),
                        getContext().getSystem().scheduler());
        CompletableFuture<Message> future = ask.toCompletableFuture();
        // 2. 向自身发送Future结果(模拟异步i/o，如数据库)
        getContext()
                .pipeToSelf(
                        future,
                        (ok, exc) -> {
                            // 不管是否有异常 直接返回
                            return (HelloResponse) ok;
                        });
        // 3. 第二种方式，是基于context ask,类似于构造函数
        getContext()
                .ask(
                        Message.class,
                        childRef,
                        Duration.ofMillis(100),
                        relyTo -> new SayHello(getContext().getSelf()),
                        // 日志出会出现How Are you?akka://test/user/askChild
                        (response, throwable) -> {
                            if (throwable instanceof TimeoutException) {
                                getContext().getLog().info("因为上面 sayHello 没有传匿名 Actor,所以这里拿不到任何回复");
                            }
                            return response;
                        });
        return Behaviors.same();
    }

    /**
     * 此方法是用于接收 使用{@link ActorContext#ask(Class, RecipientRef, Duration, Function, Function2)} 请求
     * {@link DavidBehavior} 后响应的结果.
     *
     * @param helloResponse
     * @return
     */
    public Behavior<Message> onHelloResponse(HelloResponse helloResponse) {
        getContext()
                .getLog()
                .info(
                        "Receiver HelloResponse {}, Response={}",
                        helloResponse.getReplyTo().path(),
                        helloResponse.getResponse());
        return Behaviors.same();
    }
}
