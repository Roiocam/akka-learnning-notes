package com.iquantex.phoenix.typedactor.guide.reliability.typed.workpull;

import com.iquantex.phoenix.typedactor.guide.reliability.protocol.ConversionJob;
import com.iquantex.phoenix.typedactor.guide.reliability.protocol.ImgConvertMessage;
import com.iquantex.phoenix.typedactor.guide.reliability.protocol.ImgConvertMessage.WrapperDelivery;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.delivery.ConsumerController;
import akka.actor.typed.delivery.ConsumerController.Delivery;
import akka.actor.typed.delivery.ConsumerController.Start;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.actor.typed.receptionist.ServiceKey;
import lombok.extern.slf4j.Slf4j;

import java.util.Random;
import java.util.concurrent.CompletableFuture;

/** @author AndyChen */
@Slf4j
public class ImageConverterActor extends AbstractBehavior<ImgConvertMessage> {

    private long lastWorkTimestamp = 0L;
    public static ServiceKey<ConsumerController.Command<ConversionJob>> serviceKey =
            ServiceKey.create(ConsumerController.serviceKeyClass(), "ImageConverterActor");

    public ImageConverterActor(ActorContext<ImgConvertMessage> context) {
        super(context);
    }

    public static Behavior<ImgConvertMessage> create() {
        return Behaviors.setup(
                ctx -> {
                    ActorRef<Delivery<ConversionJob>> deliveryAdapter =
                            ctx.messageAdapter(
                                    ConsumerController.deliveryClass(), WrapperDelivery::new);
                    ActorRef<ConsumerController.Command<ConversionJob>> consumerController =
                            ctx.spawn(ConsumerController.create(serviceKey), "ControllerName");
                    consumerController.tell(new Start<>(deliveryAdapter));
                    return new ImageConverterActor(ctx);
                });
    }

    @Override
    public Receive<ImgConvertMessage> createReceive() {
        return newReceiveBuilder().onMessage(WrapperDelivery.class, this::handleConversion).build();
    }

    private Behavior<ImgConvertMessage> handleConversion(WrapperDelivery wrapper)
            throws InterruptedException {
        ConversionJob job = wrapper.getDelivery().message();
        byte[] image = job.getImage();
        String fromFormat = job.getFromFormat();
        String toFormat = job.getToFormat();
        long sleep = new Random().nextInt(1000) + 1L;
        long currentTimestamp = System.currentTimeMillis();
        long delayTime = lastWorkTimestamp == 0L ? currentTimestamp : lastWorkTimestamp;
        log.info(
                "模拟消费者[{}]工作,模拟随机耗时[{}ms],距离上一次工作时间[{}ms] ,{},{},{}",
                getContext().getSelf().path(),
                sleep,
                currentTimestamp - delayTime,
                fromFormat,
                toFormat,
                image);
        lastWorkTimestamp = delayTime;
        CompletableFuture.runAsync(
                () -> {
                    try {
                        Thread.sleep(sleep);
                        log.info("{} 模拟工作完成, 接收下一次任务", getContext().getSelf().path());
                        wrapper.getDelivery().confirmTo().tell(ConsumerController.confirmed());
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                });

        return Behaviors.same();
    }
}
