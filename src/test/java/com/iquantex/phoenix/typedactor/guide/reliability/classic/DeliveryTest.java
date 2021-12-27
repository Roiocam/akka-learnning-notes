package com.iquantex.phoenix.typedactor.guide.reliability.classic;

import com.iquantex.phoenix.typedactor.guide.reliability.protocol.OrderMessage.CreateOrder;
import com.iquantex.phoenix.typedactor.guide.reliability.service.PaymentService;

import com.typesafe.config.ConfigFactory;
import akka.actor.ActorPath;
import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.ActorSystem;
import akka.persistence.jdbc.testkit.javadsl.SchemaUtils;
import akka.testkit.javadsl.TestKit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;

class DeliveryTest {

    private static ActorSystem actorSystem;
    private static ActorRef payActor;
    private static ActorRef orderActor;

    @BeforeAll
    public static void setup() throws InterruptedException {
        actorSystem =
                ActorSystem.create(
                        "test",
                        ConfigFactory.parseResources("reference-delivery.conf")
                                .withFallback(
                                        ConfigFactory.parseString(
                                                "akka.persistence.at-least-once-delivery.warn-after-number-of-unconfirmed-attempts=3"))
                                .withFallback(
                                        ConfigFactory.parseString(
                                                "akka.persistence.at-least-once-delivery.redeliver-interval=0.1s"))
                                .resolve());
        SchemaUtils.createIfNotExists(actorSystem);
        Thread.sleep(1000);
        payActor = actorSystem.actorOf(PaymentActor.create());

        ActorPath paymentPath = payActor.path();
        ActorSelection paymentSelection = actorSystem.actorSelection(paymentPath);
        orderActor = actorSystem.actorOf(OrderActor.create(paymentSelection));
    }

    @Test
    public void twice_delivery_case() throws InterruptedException {
        PaymentService.updateDelayTime(150L);
        orderActor.tell(new CreateOrder(UUID.randomUUID().toString()), ActorRef.noSender());
        Thread.sleep(1000);
    }

    @Test
    public void unconfirmed_warning_case() throws InterruptedException {
        PaymentService.updateDelayTime(500L);
        orderActor.tell(new CreateOrder(UUID.randomUUID().toString()), ActorRef.noSender());
        Thread.sleep(1000);
    }

    @AfterAll
    public static void clear() {
        TestKit.shutdownActorSystem(actorSystem);
    }
}
