package com.iquantex.phoenix.typedactor.guide.reliability.typed;

import com.iquantex.phoenix.typedactor.guide.reliability.protocol.OrderMessage;
import com.iquantex.phoenix.typedactor.guide.reliability.protocol.OrderMessage.CreateOrder;
import com.iquantex.phoenix.typedactor.guide.reliability.protocol.PaymentMessage;
import com.iquantex.phoenix.typedactor.guide.reliability.typed.p2p.OrderActor;
import com.iquantex.phoenix.typedactor.guide.reliability.typed.p2p.PaymentActor;

import akka.actor.testkit.typed.javadsl.ActorTestKit;
import akka.actor.typed.ActorRef;
import akka.actor.typed.delivery.ConsumerController;
import akka.actor.typed.delivery.ConsumerController.Command;
import akka.actor.typed.delivery.ProducerController;
import akka.persistence.testkit.javadsl.EventSourcedBehaviorTestKit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

/** @author AndyChen */
public class P2PTest {

    private static ActorTestKit testKit;

    @BeforeAll
    public static void setup() throws InterruptedException {
        testKit = ActorTestKit.create(EventSourcedBehaviorTestKit.config());
    }

    @Test
    public void test() throws InterruptedException {

        ActorRef<Command<PaymentMessage>> consumerController =
                testKit.spawn(ConsumerController.create(), "consumerController");
        ActorRef<PaymentMessage> consumer =
                testKit.spawn(PaymentActor.create(consumerController), "consumer");

        String producerId = UUID.randomUUID().toString();
        ActorRef<ProducerController.Command<PaymentMessage>> producerController =
                testKit.spawn(
                        ProducerController.create(
                                PaymentMessage.class, producerId, Optional.empty()),
                        "producerController");
        ActorRef<OrderMessage> producer =
                testKit.spawn(OrderActor.create(producerId, producerController), "producer");

        consumerController.tell(
                new ConsumerController.RegisterToProducerController<>(producerController));
        producer.tell(new CreateOrder(UUID.randomUUID().toString()));
        // 直接发送第二次, 此时消息
        producer.tell(new CreateOrder(UUID.randomUUID().toString()));
        producer.tell(new CreateOrder(UUID.randomUUID().toString()));

        Thread.sleep(3000);
    }
}
