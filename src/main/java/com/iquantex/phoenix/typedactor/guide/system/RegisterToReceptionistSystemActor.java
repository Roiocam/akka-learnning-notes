package com.iquantex.phoenix.typedactor.guide.system;

import com.iquantex.phoenix.typedactor.guide.actor.DavidBehavior;
import com.iquantex.phoenix.typedactor.guide.constant.ServiceKeys;
import com.iquantex.phoenix.typedactor.guide.protocol.Message;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.actor.typed.receptionist.Receptionist;

import java.util.List;

/**
 * 该 Actor 作为守护Actor 存在。创建了一个子Actor，并将其注册到 {@link Receptionist},这是在新版 Actor 中，在 System 外部拿到 {@link
 * ActorRef} 的唯一方式
 *
 * @author AndyChen
 */
public class RegisterToReceptionistSystemActor extends AbstractBehavior<Message> {

    public RegisterToReceptionistSystemActor(ActorContext<Message> context) {
        super(context);
        // 创建child
        ActorRef<Message> david = context.spawn(DavidBehavior.create(), "David");
        context.watch(david);
        // 注入到 receptionist, 用于查询 ActorRef
        context.getSystem()
                .receptionist()
                .tell(Receptionist.register(ServiceKeys.DAVID_KEY, david));
    }

    public static Behavior<Message> create() {
        return Behaviors.setup(ctx -> new RegisterToReceptionistSystemActor(ctx));
    }

    public static class GetChild implements Message {}

    @Override
    public Receive<Message> createReceive() {
        return newReceiveBuilder().onMessage(GetChild.class, this::onGetChild).build();
    }

    public Behavior<Message> onGetChild(GetChild getChild) {
        List<ActorRef<Void>> children = getContext().getChildren();
        getContext().getLog().info("Found Child {}", children);
        return Behaviors.same();
    }
}
