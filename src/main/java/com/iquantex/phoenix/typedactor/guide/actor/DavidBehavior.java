package com.iquantex.phoenix.typedactor.guide.actor;

import com.iquantex.phoenix.typedactor.guide.protocol.HelloResponse;
import com.iquantex.phoenix.typedactor.guide.protocol.Message;

import com.typesafe.config.Config;
import akka.actor.Actor;
import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.Props;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.japi.function.Function;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 基本的Actor类，在 Typed Actor 中，一切都具有类型. 需要显式定义接受消息的类型.
 *
 * <p>1. 所有的 Actor 现在都叫 {@link Behavior}，有点类似于 {@link Thread} 和 {@link Runnable} 的区别, 现在 Actor 由
 * Akka 内部维护
 *
 * <p>2. 因为 Behavior 只有行为, 因此不同于之前的 Actor，该抽象类只有处理消息相关的API, 移除了之前的 getSelf 等API
 *
 * <p>3. Typed Actor 中, Actor 拥有独立的上下文对象 {@link ActorContext}，用来表示 Actor 对象的一些属性，如 getSelf() 获取自身的
 *
 * <p>4. 因为 Behavior 不再是真实 Actor，因此也没有原来的 {@link Actor#postStop()} 等生命周期方法，取而代之的是 {@link
 * akka.actor.typed.PostStop} 信号，也就是 {@link akka.actor.typed.Signal}
 *
 * <p>5. Typed Actor 中, Behavior 除了能够接受用户定义协议的 Message，还会接受Akka内部的Message，即信号，通过 {@link
 * akka.actor.typed.javadsl.ReceiveBuilder#onSignal(Class, Function)} 方法则可以声明如何处理信号 {@link ActorRef}
 *
 * @author AndyChen
 */
public class DavidBehavior extends AbstractBehavior<Message> {

    public DavidBehavior(ActorContext<Message> context) {
        super(context);
    }

    /** 定义处理响应的次数 */
    private AtomicInteger handleResponseCount = new AtomicInteger(0);

    @AllArgsConstructor
    @Getter
    public static class SayHello implements Message {

        /**
         * 消息中附带了发送者,不同于之前的getSender() -> 在Actor级别存储最后一个发送者. 这种方式能对POC方案提供一定的帮助？
         *
         * <p>getSender() 方法已移除
         */
        private ActorRef<Message> replyTo;
    }

    /**
     * 新建一个Behavior对象没有任何含义, 只是定义了处理消息的方法, 因此需要通过 {@link Behaviors#setup(Function)} 创建带有上下文的
     * Behavior 工厂。
     *
     * <p>1. {@link akka.actor.typed.Props} 现在以 {@link Behaviors} 工厂类代替, 但大多数API仍然保留了参数 {@link
     * akka.actor.typed.ActorSystem#create(Behavior, String, Config, Props)}
     *
     * <p>2. {@link Behaviors#setup(Function)} 方法用于创建 Behavior 的工厂对象，该方法会提供 {@link ActorContext} 给用户
     *
     * @return
     */
    public static Behavior<Message> create() {
        return Behaviors.setup(DavidBehavior::new);
    }

    /**
     * 这是错误的用法, 除了 {@link Behaviors#setup(Function)} 暂时没找到其他方法能够提供 Actor 自身的 {@link ActorContext}，其他
     * Actor 的上下文对象不可用于该Actor
     *
     * @param actorContext
     * @return
     */
    public static Behavior<Message> wrongCreate(ActorContext actorContext) {
        return new DavidBehavior(actorContext);
    }

    @Override
    public Receive<Message> createReceive() {
        return newReceiveBuilder()
                .onMessage(SayHello.class, this::responseHello) // 与原来基本一致.
                .onMessage(HelloResponse.class, this::wontHandleAnyMore)
                .onSignal(PostStop.class, this::afterStop) // 处理信号
                .build();
    }

    /**
     * 处理Actor死亡的信号
     *
     * @param signal
     * @return
     */
    private Behavior<Message> afterStop(PostStop signal) {
        // 做一些资源关闭等..
        return Behaviors.empty();
    }

    /**
     * 处理消息的方法, 这里对消息做了回复. 并从上下文中获取自身 {@link ActorRef}
     *
     * <p>返回的结果 {@link Behaviors#same()} 代表返回相同的行为, 如果用户更改了，则该 Actor 下次处理该事件时，是用户返回的行为,如 {@link
     * #wontHandleAnyMore(HelloResponse)}
     *
     * @param msg
     * @return
     */
    public Behavior<Message> responseHello(SayHello msg) {
        msg.getReplyTo()
                .tell(
                        new HelloResponse(
                                getContext().getSelf(), "How Are you?" + msg.getReplyTo().path()));
        return Behaviors.same();
    }

    /**
     * 这里定义了处理 {@link SayHello} 不同的策略, 最后返回的结果是 {@link Behaviors#empty()} ,这个方法代表不对这个消息做任何处理.
     *
     * <p>如果用户使用了 {@link #wontHandleAnyMore(HelloResponse)} 代替 {@link #responseHello(SayHello)}
     * 时，{@link DavidBehavior} 只会处理一次 {@link SayHello} ,之后不再处理
     *
     * <p>这种运行时改变 Actor 行为的方式，能够实现有限状态机(官方文档)
     *
     * @param msg
     * @return
     */
    public Behavior<Message> wontHandleAnyMore(HelloResponse msg) {
        int count = handleResponseCount.incrementAndGet();
        getContext()
                .getLog()
                .info(
                        "Get Response From:{}, Msg={}, Count={}",
                        msg.getReplyTo(),
                        msg.getResponse(),
                        count);
        // 处理两次后，不再处理
        if (count >= 2) {
            return Behaviors.empty();
        }
        return Behaviors.same();
    }
}
