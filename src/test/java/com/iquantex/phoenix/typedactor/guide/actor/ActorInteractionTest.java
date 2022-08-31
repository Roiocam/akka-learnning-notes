package com.iquantex.phoenix.typedactor.guide.actor;

import akka.actor.testkit.typed.javadsl.ActorTestKit;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * @author AndyChen
 */
public class ActorInteractionTest {
    private ActorTestKit testKit = ActorTestKit.create();

    @Test
    public void test() {
        // 待测试 Actor
        ActorRef<Msg> testActor = testKit.spawn(EchoActor.create());
        // 回复探针
        TestProbe<String> echoReplyProbe = testKit.createTestProbe(String.class);
        // 发送消息
        testActor.tell(new EchoMsg("echo", echoReplyProbe.ref()));
        // 验证回复
        String echo = echoReplyProbe.expectMessageClass(String.class);
        assert echo.equals("echo");
    }

    /**
     * Always implements Serializable (or custom serialize interface).
     */
    public interface Msg extends Serializable {
    }

    @Data
    @AllArgsConstructor
    public static class EchoMsg implements Msg {
        private String content;
        private ActorRef<String> echoReply;
    }

    public static class EchoActor extends AbstractBehavior<Msg> {
        public static Behavior<Msg> create() {
            return Behaviors.setup(ctx -> new EchoActor(ctx));
        }

        EchoActor(ActorContext<Msg> context) {
            super(context);
        }

        @Override
        public Receive<Msg> createReceive() {
            return newReceiveBuilder()
                    .onMessage(EchoMsg.class, msg -> {
                        // echo
                        msg.getEchoReply().tell(msg.getContent());
                        return Behaviors.same();
                    })
                    .build();
        }
    }

    public static class Updated implements Msg {
    }

    public static class SendToOtherMsg implements Msg {
    }

    public static class InteractionActor extends AbstractBehavior<Msg> {

        private final Consumer<Msg> sendToOther;

        public static Behavior<Msg> create(Consumer<Msg> sendToOther) {
            return Behaviors.setup(ctx -> new InteractionActor(ctx, sendToOther));
        }

        InteractionActor(ActorContext<Msg> context, Consumer<Msg> sendToOther) {
            super(context);
            this.sendToOther = sendToOther;
        }


        @Override
        public Receive<Msg> createReceive() {
            return newReceiveBuilder()
                    .onMessage(Updated.class, u -> {
                        sendToOther.accept(new SendToOtherMsg());
                        return Behaviors.same();
                    })
                    .build();
        }
    }

    @Test
    public void parentTest() {
        TestProbe<Msg> parentProbe = testKit.createTestProbe(Msg.class);
        ActorRef<Msg> childActor = testKit.spawn(InteractionActor.create(msg -> parentProbe.tell(msg)));
        childActor.tell(new Updated());
        SendToOtherMsg sendToOtherMsg = parentProbe.expectMessageClass(SendToOtherMsg.class);
        Objects.requireNonNull(sendToOtherMsg);
    }

    public static class InteractionParentActor extends AbstractBehavior<Msg> {

        private final ActorRef<Msg> childRef;

        public static Behavior<Msg> create(Supplier<ActorRef<Msg>> childFactory) {
            return Behaviors.setup(ctx -> new InteractionParentActor(ctx, childFactory));
        }

        InteractionParentActor(ActorContext<Msg> context, Supplier<ActorRef<Msg>> childFactory) {
            super(context);
            this.childRef = childFactory.get();
        }


        @Override
        public Receive<Msg> createReceive() {
            return newReceiveBuilder()
                    .onMessage(Updated.class, u -> {
                        childRef.tell(new SendToOtherMsg());
                        return Behaviors.same();
                    })
                    .build();
        }
    }

    @Test
    public void childTest() {
        TestProbe<Msg> childProbe = testKit.createTestProbe(Msg.class);
        ActorRef<Msg> parentActor = testKit.spawn(InteractionParentActor.create(() -> childProbe.getRef()));
        parentActor.tell(new Updated());
        SendToOtherMsg sendToOtherMsg = childProbe.expectMessageClass(SendToOtherMsg.class);
        Objects.requireNonNull(sendToOtherMsg);
    }

}
