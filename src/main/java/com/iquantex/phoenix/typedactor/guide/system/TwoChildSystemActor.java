package com.iquantex.phoenix.typedactor.guide.system;

import com.iquantex.phoenix.typedactor.guide.actor.DavidBehavior;
import com.iquantex.phoenix.typedactor.guide.protocol.Message;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

/**
 * 该 Actor 演示创建了两个子 Actor 的例子, 除此之外没有其他行为，仅作为守护Actor(Guardian) 存在.
 *
 * <p>通过 {@link ActorContext#watch(ActorRef)} 守护childActor 的生命周期
 *
 * @author AndyChen
 */
public class TwoChildSystemActor extends AbstractBehavior<Message> {

    public TwoChildSystemActor(ActorContext<Message> context) {
        super(context);
        // 创建子 Actor, 并监督其生命周期
        ActorRef<Message> davidOne = context.spawn(DavidBehavior.create(), "DavidOne");
        ActorRef<Message> davidTwo = context.spawn(DavidBehavior.create(), "DavidTwo");
        context.watch(davidOne);
        context.watch(davidTwo);
    }

    public static Behavior<Message> create() {
        return Behaviors.setup(ctx -> new TwoChildSystemActor(ctx));
    }

    @Override
    public Receive<Message> createReceive() {
        return newReceiveBuilder()
                .onMessage(Message.class, msg -> Behaviors.empty()) // 不进行任何处理
                .build();
    }
}
