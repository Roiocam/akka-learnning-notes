package com.iquantex.phoenix.typedactor.guide.system;

import com.iquantex.phoenix.typedactor.guide.actor.DavidBehavior;
import com.iquantex.phoenix.typedactor.guide.actor.DavidBehavior.SayHello;
import com.iquantex.phoenix.typedactor.guide.protocol.Message;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

/**
 * 此时演示了一个守护Actor，该守护Actor 创建了两个ChildActor，并演示 {@link DavidBehavior} 的大多数功能
 *
 * <p>1. Actor 1 向 Actor 2 发起tell 请求，并在Actor 2 内部回复 Actor 1
 *
 * <p>2. 演示有限状态机的功能，在到达一定程序后，将Actor的行为变更
 *
 * @author AndyChen
 */
public class TwoActorTellSystemActor extends AbstractBehavior<Message> {

    public TwoActorTellSystemActor(ActorContext<Message> context) {
        super(context);
        // 1. 创建两个子 Actor, 并监督其生命周期
        ActorRef<Message> davidOne = context.spawn(DavidBehavior.create(), "DavidOne");
        ActorRef<Message> davidTwo = context.spawn(DavidBehavior.create(), "DavidTwo");
        context.watch(davidOne);
        context.watch(davidTwo);
        // 2. 创建新的Child
        // ActorRef.ask() 没有ask方法，必须在 Actor 内部定义
        // 3. 向 davidOne 发送两次消息, 指定回复给 davidTwo, davidTwo 只会处理一次响应
        davidOne.tell(new SayHello(davidTwo));
        davidOne.tell(new SayHello(davidTwo));
        davidOne.tell(new SayHello(davidTwo));
    }

    public static Behavior<Message> create() {
        return Behaviors.setup(ctx -> new TwoActorTellSystemActor(ctx));
    }

    @Override
    public Receive<Message> createReceive() {
        return newReceiveBuilder()
                .onMessage(Message.class, msg -> Behaviors.empty()) // 不进行任何处理
                .build();
    }
}
